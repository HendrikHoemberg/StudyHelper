package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.QuestionType;
import com.HendrikHoemberg.StudyHelper.dto.TestQuestion;
import com.HendrikHoemberg.StudyHelper.dto.TestQuestionMode;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiTestService {

    public record DocumentInput(String filename, String extractedText) {}

    private final ChatClient chatClient;
    private final JsonMapper objectMapper;

    public AiTestService(ChatClient.Builder builder, JsonMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<TestQuestion> generate(
            List<Flashcard> flashcards,
            List<DocumentInput> documents,
            int count,
            TestQuestionMode mode,
            Difficulty difficulty) {

        String cardContent = buildCardContent(flashcards);
        String docContent = buildDocContent(documents);

        if (cardContent.isBlank() && docContent.isBlank()) {
            throw new IllegalArgumentException("Selected sources contain no usable text. Pick a deck with text or a document with extractable content.");
        }

        int mcqCount = count / 2;
        int tfCount = count - mcqCount;

        String prompt = buildPrompt(cardContent, docContent, count, mode, difficulty, mcqCount, tfCount);

        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            JsonNode questionsNode = root.get("questions");
            List<TestQuestion> rawList = questionsNode != null
                ? objectMapper.<List<TestQuestion>>readerForListOf(TestQuestion.class).readValue(questionsNode)
                : List.of();

            List<TestQuestion> valid = new ArrayList<>();
            for (TestQuestion q : rawList) {
                if (q == null) continue;
                TestQuestion normalized = normalizeQuestion(q);
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

    private TestQuestion normalizeQuestion(TestQuestion q) {
        if (q.questionText() == null || q.questionText().isBlank()) return null;
        if (q.options() == null) return null;

        QuestionType type = q.type() != null ? q.type() : inferType(q.options());

        if (type == QuestionType.MULTIPLE_CHOICE) {
            if (q.options().size() != 4) return null;
            if (q.correctOptionIndex() < 0 || q.correctOptionIndex() > 3) return null;
            return new TestQuestion(QuestionType.MULTIPLE_CHOICE, q.questionText(), q.options(), q.correctOptionIndex());
        } else {
            if (q.options().size() != 2) return null;
            String opt0 = q.options().get(0).trim();
            String opt1 = q.options().get(1).trim();
            if (!opt0.equalsIgnoreCase("true") || !opt1.equalsIgnoreCase("false")) return null;
            if (q.correctOptionIndex() < 0 || q.correctOptionIndex() > 1) return null;
            return new TestQuestion(QuestionType.TRUE_FALSE, q.questionText(), List.of("True", "False"), q.correctOptionIndex());
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

    private String buildDocContent(List<DocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (DocumentInput doc : documents) {
            if (doc.extractedText() == null || doc.extractedText().isBlank()) continue;
            n++;
            sb.append("Document ").append(n).append(" — ").append(doc.filename()).append(":\n")
              .append(doc.extractedText()).append("\n\n");
        }
        return sb.toString();
    }

    private String buildPrompt(String cardContent, String docContent, int count,
                                TestQuestionMode mode, Difficulty difficulty,
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
                - Approximately half each type as specified above.""";
        };

        String cardSection = cardContent.isBlank() ? "(none)" : cardContent;
        String docSection = docContent.isBlank() ? "(none)" : docContent;

        return ("You are a study assistant. Generate %d %s based on the source material below.\n\n"
            + "DIFFICULTY: %s\n\n"
            + "TOPIC FOCUS:\n"
            + "First identify the dominant subject matter of the supplied content. Generate\n"
            + "questions only about concepts that belong to that subject. Ignore incidental\n"
            + "metadata such as author names, page numbers, publication dates, headers, footers,\n"
            + "or off-topic asides.\n\n"
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
            + "- Test understanding, not exact wording.\n\n"
            + "=== FLASHCARDS ===\n"
            + "%s\n\n"
            + "=== DOCUMENTS ===\n"
            + "%s\n\n"
            + "Respond ONLY with valid JSON, no extra text. Schema:\n"
            + "{\"questions\":[\n"
            + "  {\"type\":\"MULTIPLE_CHOICE\",\"questionText\":\"...\",\"options\":[\"a\",\"b\",\"c\",\"d\"],\"correctOptionIndex\":0},\n"
            + "  {\"type\":\"TRUE_FALSE\",\"questionText\":\"...\",\"options\":[\"True\",\"False\"],\"correctOptionIndex\":0}\n"
            + "]}"
        ).formatted(count, modeDescription, difficultyInstruction, typeRules, cardSection, docSection);
    }

    private String extractJson(String response) {
        String s = response.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("```(?:json)?\\s*\n?", "");
            int codeEnd = s.lastIndexOf("```");
            if (codeEnd > 0) s = s.substring(0, codeEnd).trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return s;
    }
}
