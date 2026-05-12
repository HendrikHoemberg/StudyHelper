package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.QuestionType;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionMode;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionsResponse;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiQuizService {

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

        String cardContent = buildCardContent(flashcards);
        String docContent  = buildTextDocContent(documents);
        String pdfListing  = buildPdfListing(documents);
        Media[] pdfMedia   = buildPdfMedia(documents);

        if (cardContent.isBlank() && docContent.isBlank() && pdfMedia.length == 0) {
            throw new IllegalArgumentException("Selected sources contain no usable text or PDFs. Pick a deck, a document with extractable content, or a PDF in full-document mode.");
        }

        int mcqCount = count / 2;
        int tfCount = count - mcqCount;

        String prompt = buildPrompt(cardContent, docContent, pdfListing, count, mode, difficulty, mcqCount, tfCount);

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
            throw new IllegalStateException("AI request failed, please retry with fewer or smaller PDFs.", e);
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
                throw new IllegalStateException("AI returned too few valid questions; please retry.");
            }

            return valid.stream().limit(count).toList();

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the AI response. Please try again.", e);
        }
    }

    private QuizQuestion normalizeQuestion(QuizQuestion q) {
        if (q.questionText() == null || q.questionText().isBlank()) return null;
        if (q.options() == null) return null;

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
            String o0 = options.get(0).trim().toLowerCase();
            String o1 = options.get(1).trim().toLowerCase();
            if ((o0.equals("true") || o0.equals("false")) && (o1.equals("true") || o1.equals("false"))) {
                return QuestionType.TRUE_FALSE;
            }
        }
        return QuestionType.MULTIPLE_CHOICE;
    }

    private String buildCardContent(List<Flashcard> flashcards) {
        if (flashcards == null || flashcards.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Flashcard card : flashcards) {
            String front = card.getFrontText();
            String back = card.getBackText();
            boolean hasFront = front != null && !front.isBlank();
            boolean hasBack = back != null && !back.isBlank();
            if (!hasFront && !hasBack) continue;
            count++;
            sb.append("Card ").append(count).append(":\n");
            if (hasFront) sb.append("  Q: ").append(front.strip()).append("\n");
            if (hasBack) sb.append("  A: ").append(back.strip()).append("\n");
        }
        return sb.toString();
    }

    private String buildTextDocContent(List<DocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (DocumentInput doc : documents) {
            if (!(doc instanceof TextDocument td)) continue;
            if (td.extractedText() == null || td.extractedText().isBlank()) continue;
            n++;
            sb.append("Document ").append(n).append(" — ").append(td.filename()).append(":\n")
              .append(td.extractedText()).append("\n\n");
        }
        return sb.toString();
    }

    private String buildPdfListing(List<DocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DocumentInput doc : documents) {
            if (doc instanceof PdfDocument pd) {
                sb.append("- ").append(pd.filename()).append("\n");
            }
        }
        return sb.toString();
    }

    private Media[] buildPdfMedia(List<DocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return new Media[0];
        return documents.stream()
                .filter(d -> d instanceof PdfDocument)
                .map(d -> (PdfDocument) d)
                .map(pd -> new Media(new MimeType("application", "pdf"), pd.source()))
                .toArray(Media[]::new);
    }

    private String buildPrompt(String cardContent, String docContent, String pdfListing,
                                int count, QuizQuestionMode mode, Difficulty difficulty,
                                int mcqCount, int tfCount) {
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

        return ("You are a study assistant. Generate %d %s based on the source material below.\n\n"
            + "LANGUAGE:\n"
            + "Detect the dominant natural language of the supplied source material (flashcards,\n"
            + "documents, and attached PDFs together). Write every output string — question text,\n"
            + "answer options, hints — in that same language. If sources mix languages, use the\n"
            + "most-prevalent one. Do not translate technical terms, proper nouns, or code.\n\n"
            + "DIFFICULTY: %s\n\n"
            + "TOPIC FOCUS:\n"
            + "First identify the dominant subject matter of the supplied content. Generate\n"
            + "questions only about concepts that belong to that subject. Ignore incidental\n"
            + "metadata such as author names, page numbers, publication dates, headers, footers,\n"
            + "or off-topic asides.\n\n"
            + "COVERAGE:\n"
            + "Distribute the %d requested questions evenly across the entire source material.\n"
            + "- Treat each attached PDF and each text document as a separate source.\n"
            + "- Within each source, sample roughly equally from the early third, middle third,\n"
            + "  and final third (by page count for PDFs, by length for text).\n"
            + "- If multiple sources are supplied, allocate questions proportionally to source\n"
            + "  length, but ensure every source contributes at least one question when N is\n"
            + "  large enough.\n"
            + "- Do not cluster on introductions, abstracts, tables of contents, or the first\n"
            + "  few pages. Skip these unless they contain core subject matter.\n"
            + "- Before writing questions, sketch a brief internal coverage plan (which\n"
            + "  pages/sections each question will draw from). Do not include the plan in\n"
            + "  the JSON output.\n\n"
            + "SOME CARDS MAY HAVE MISSING CONTEXT:\n"
            + "Some flashcards may have had images removed. If a card's text alone is\n"
            + "insufficient to form a meaningful question (e.g. it references \"this diagram\"\n"
            + "or \"the image above\"), skip that card.\n\n"
            + "QUESTION TYPE RULES:\n"
            + "%s\n\n"
            + "GENERAL RULES:\n"
            + "- Each question stands alone — do not reference \"the text\" or \"the document\".\n"
            + "- correctOptionIndex is 0-based.\n"
            + "- Vary which index is correct across questions.\n"
            + "- Test understanding, not exact wording.\n"
            + "- Use the attached PDF documents (including their figures, diagrams, and tables) as primary source material for question generation.\n\n"
            + "=== FLASHCARDS ===\n"
            + "%s\n\n"
            + "=== DOCUMENTS ===\n"
            + "%s\n\n"
            + "=== ATTACHED PDFs ===\n"
            + "%s\n"
        ).formatted(count, modeDescription, difficultyInstruction, count, typeRules, cardSection, docSection, pdfSection);
    }
}
