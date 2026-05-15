package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.QuestionType;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionMode;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionsResponse;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AiQuizService {

    private static final Logger log = LoggerFactory.getLogger(AiQuizService.class);

    private final ChatClient chatClient;
    private final String responseSchema;

    public AiQuizService(ChatClient.Builder builder, JsonMapper objectMapper) {
        this.chatClient = builder.build();
        this.responseSchema = new BeanOutputConverter<>(QuizQuestionsResponse.class, objectMapper).getJsonSchema();
    }

    public List<QuizQuestion> generate(
            List<Flashcard> flashcards,
            List<DocumentInput> documents,
            int count,
            QuizQuestionMode mode,
            Difficulty difficulty) {
        return generate(flashcards, documents, count, mode, difficulty, null);
    }

    public List<QuizQuestion> generate(
            List<Flashcard> flashcards,
            List<DocumentInput> documents,
            int count,
            QuizQuestionMode mode,
            Difficulty difficulty,
            String additionalInstructions) {
        String cardContent = AiGenerationSupport.cards(flashcards);
        String docContent  = AiGenerationSupport.textDocuments(documents);
        String pdfListing  = AiGenerationSupport.pdfListing(documents);
        Media[] pdfMedia   = AiGenerationSupport.pdfMedia(documents);

        if (cardContent.isBlank() && docContent.isBlank() && pdfMedia.length == 0) {
            throw new IllegalArgumentException("Selected sources contain no usable text or PDFs. Pick a deck, a document with extractable content, or a PDF in full-document mode.");
        }

        int mcqCount = count / 2;
        int tfCount = count - mcqCount;

        String prompt = buildPrompt(cardContent, docContent, pdfListing, count, mode, difficulty, mcqCount, tfCount, additionalInstructions);

        QuizQuestionsResponse response;
        try {
            response = chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder()
                    .responseMimeType("application/json")
                    .responseSchema(responseSchema))
                .user(u -> {
                    u.text(prompt);
                    if (pdfMedia.length > 0) u.media(pdfMedia);
                })
                .call()
                .entity(QuizQuestionsResponse.class);
        } catch (Exception e) {
            throw AiGenerationSupport.failure(log, "QUIZ", "PROVIDER_REQUEST",
                "AI request failed, please retry with fewer or smaller PDFs.", e);
        }

        try {
            List<QuizQuestion> rawList = response == null || response.questions() == null
                ? List.of()
                : response.questions();

            List<QuizQuestion> valid = new ArrayList<>();
            for (QuizQuestion q : rawList) {
                if (q == null) continue;
                QuizQuestion normalized = normalizeQuestion(q);
                if (normalized != null) valid.add(normalized);
            }

            if (valid.size() < Math.max(1, count / 2)) {
                throw AiGenerationSupport.failure(log, "QUIZ", "RESPONSE_VALIDATION",
                    "AI returned too few valid questions; please retry.",
                    new IllegalStateException("AI response contained " + valid.size() + " valid quiz questions for requested count " + count + "."));
            }

            return valid.stream().limit(count).toList();

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw AiGenerationSupport.failure(log, "QUIZ", "RESPONSE_PARSE",
                "Could not parse the AI response. Please try again.", e);
        }
    }

    private QuizQuestion normalizeQuestion(QuizQuestion q) {
        if (q.questionText() == null || q.questionText().isBlank()) return null;
        if (q.options() == null) return null;
        if (q.options().stream().anyMatch(Objects::isNull)) return null;

        QuestionType type = q.type() != null ? q.type() : inferType(q.options());

        if (type == QuestionType.MULTIPLE_CHOICE) {
            if (q.options().size() != 4) return null;
            if (q.correctOptionIndex() < 0 || q.correctOptionIndex() > 3) return null;
            return new QuizQuestion(QuestionType.MULTIPLE_CHOICE, q.questionText(), q.options(), q.correctOptionIndex());
        } else {
            if (q.options().size() != 2) return null;
            String opt0 = q.options().get(0).trim();
            String opt1 = q.options().get(1).trim();
            if (!opt0.equalsIgnoreCase("true") || !opt1.equalsIgnoreCase("false")) return null;
            if (q.correctOptionIndex() < 0 || q.correctOptionIndex() > 1) return null;
            return new QuizQuestion(QuestionType.TRUE_FALSE, q.questionText(), List.of("True", "False"), q.correctOptionIndex());
        }
    }

    private QuestionType inferType(List<String> options) {
        if (options.size() == 2) {
            String raw0 = options.get(0);
            String raw1 = options.get(1);
            if (raw0 == null || raw1 == null) {
                return QuestionType.MULTIPLE_CHOICE;
            }
            String o0 = raw0.trim().toLowerCase();
            String o1 = raw1.trim().toLowerCase();
            if ((o0.equals("true") || o0.equals("false")) && (o1.equals("true") || o1.equals("false"))) {
                return QuestionType.TRUE_FALSE;
            }
        }
        return QuestionType.MULTIPLE_CHOICE;
    }

    private String buildPrompt(String cardContent, String docContent, String pdfListing,
                                int count, QuizQuestionMode mode, Difficulty difficulty,
                                int mcqCount, int tfCount, String additionalInstructions) {
        String modeDescription = switch (mode) {
            case MCQ_ONLY -> "multiple-choice questions";
            case TF_ONLY -> "true/false questions";
            case MIXED -> "questions: exactly " + mcqCount + " multiple-choice and " + tfCount + " true/false";
        };

        String difficultyInstruction = switch (difficulty) {
            case EASY -> "Direct recall. Questions test obvious facts. Distractors are clearly wrong.";
            case MEDIUM -> "Minor inference required. Distractors are plausible but distinguishable on careful reading.";
            case HARD -> "Synthesis across sources. Distractors share surface features with the answer; require precise understanding.";
        };

        String typeRules = switch (mode) {
            case MCQ_ONLY -> "- All questions are MULTIPLE_CHOICE with exactly 4 options. Exactly one correct.";
            case TF_ONLY -> "- All questions are TRUE_FALSE. options must be exactly [\"True\",\"False\"]. correctOptionIndex is 0 (True) or 1 (False).";
            case MIXED -> """
                - All MULTIPLE_CHOICE questions have exactly 4 options. Exactly one correct.
                - All TRUE_FALSE questions: options must be exactly ["True","False"]. correctOptionIndex is 0 (True) or 1 (False).
                - Generate exactly the requested split as specified above.""";
        };

        String cardSection = cardContent.isBlank() ? "(none)" : cardContent;
        String docSection  = docContent.isBlank()  ? "(none)" : docContent;
        String pdfSection  = pdfListing.isBlank()  ? "(none)" : pdfListing;

        return "You are a study assistant. Generate %d %s based on the source material below.\n\n".formatted(count, modeDescription)
            + AiGenerationSupport.languageSection("question text, answer options, hints") + "\n"
            + "DIFFICULTY: %s\n\n".formatted(difficultyInstruction)
            + AiGenerationSupport.topicFocusSection() + "\n"
            + AiGenerationSupport.coverageSection(count) + "\n"
            + AiGenerationSupport.missingContextSection() + "\n"
            + "QUESTION TYPE RULES:\n%s\n\n".formatted(typeRules)
            + "GENERAL RULES:\n"
            + "- Each question stands alone — do not reference \"the text\" or \"the document\".\n"
            + "- correctOptionIndex is 0-based.\n"
            + "- Vary which index is correct across questions.\n"
            + "- Test understanding, not exact wording.\n"
            + "- Use the attached PDF documents (including their figures, diagrams, and tables) as primary source material for question generation.\n\n"
            + "=== FLASHCARDS ===\n%s\n\n".formatted(cardSection)
            + "=== DOCUMENTS ===\n%s\n\n".formatted(docSection)
            + "=== ATTACHED PDFs ===\n%s\n".formatted(pdfSection)
            + AiInstructionSupport.section(additionalInstructions);
    }
}
