package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.ExamGradingResult;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestion;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionSize;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiExamService {

    private final ChatClient chatClient;
    private final JsonMapper objectMapper;

    public AiExamService(ChatClient.Builder builder, JsonMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<ExamQuestion> generate(
            List<Flashcard> flashcards,
            List<DocumentInput> documents,
            int questionCount,
            ExamQuestionSize size) {

        String cardContent = buildCardContent(flashcards);
        String docContent  = buildTextDocContent(documents);
        String pdfListing  = buildPdfListing(documents);
        Media[] pdfMedia   = buildPdfMedia(documents);

        if (cardContent.isBlank() && docContent.isBlank() && pdfMedia.length == 0) {
            throw new IllegalArgumentException("Selected sources contain no usable text or PDFs. Pick a deck, a document with extractable content, or a PDF in full-document mode.");
        }

        String prompt = buildGenerationPrompt(cardContent, docContent, pdfListing, questionCount, size);

        String response = chatClient.prompt()
                .user(u -> {
                    u.text(prompt);
                    if (pdfMedia.length > 0) u.media(pdfMedia);
                })
                .call()
                .content();

        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            JsonNode questionsNode = root.get("questions");
            List<ExamQuestion> rawList = questionsNode != null
                    ? objectMapper.<List<ExamQuestion>>readerForListOf(ExamQuestion.class).readValue(questionsNode)
                    : List.of();

            List<ExamQuestion> valid = new ArrayList<>();
            for (ExamQuestion q : rawList) {
                if (q == null || q.questionText() == null || q.questionText().isBlank()) continue;
                valid.add(q);
            }

            if (valid.size() < Math.max(1, questionCount / 2)) {
                throw new IllegalStateException("AI returned too few valid questions; please retry.");
            }

            return valid.stream().limit(questionCount).toList();

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the AI response. Please try again.", e);
        }
    }

    public ExamGradingResult grade(
            List<ExamQuestion> questions,
            Map<Integer, String> userAnswers,
            ExamQuestionSize size) {

        String prompt = buildGradingPrompt(questions, userAnswers, size);

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        try {
            String json = extractJson(response);
            return objectMapper.readValue(json, ExamGradingResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the AI grading response. Please try again.", e);
        }
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

    private String buildGenerationPrompt(String cardContent, String docContent, String pdfListing,
                                         int count, ExamQuestionSize size) {
        String sizeInstruction = switch (size) {
            case SHORT -> "Each question should require a brief recall or definition answer (1–2 sentences, ~50 words).";
            case MEDIUM -> "Each question should require an explanatory answer (~150 words). Test understanding, not just recall.";
            case LONG -> "Each question should require an essay-style answer (~400 words). Test synthesis, application, or comparison.";
            case MIXED -> "Mix all three depths across the questions. Choose the most appropriate depth per topic.";
        };

        String cardSection = cardContent.isBlank() ? "(none)" : cardContent;
        String docSection  = docContent.isBlank()  ? "(none)" : docContent;
        String pdfSection  = pdfListing.isBlank()  ? "(none)" : pdfListing;

        return ("You are a study assistant. Generate %d exam questions based on the source material below.\n\n"
                + "QUESTION DEPTH:\n"
                + "%s\n\n"
                + "TOPIC FOCUS:\n"
                + "First identify the dominant subject matter of the supplied content. Generate\n"
                + "questions only about concepts that belong to that subject. Ignore incidental\n"
                + "metadata such as author names, page numbers, publication dates, headers, footers,\n"
                + "or off-topic asides.\n\n"
                + "SOME CARDS MAY HAVE MISSING CONTEXT:\n"
                + "Some flashcards may have had images removed. If a card's text alone is\n"
                + "insufficient to form a meaningful question (e.g. it references \"this diagram\"\n"
                + "or \"the image above\"), skip that card.\n\n"
                + "GENERAL RULES:\n"
                + "- Each question stands alone — do not reference \"the text\" or \"the document\".\n"
                + "- Test understanding, not exact wording.\n"
                + "- Use the attached PDF documents (including their figures, diagrams, and tables) as primary source material for question generation.\n"
                + "- For each question, provide 'expectedAnswerHints' which is a 2–3 sentence rubric\n"
                + "  describing what a perfect answer should contain. This rubric is never shown to the user.\n\n"
                + "=== FLASHCARDS ===\n"
                + "%s\n\n"
                + "=== DOCUMENTS ===\n"
                + "%s\n\n"
                + "=== ATTACHED PDFs ===\n"
                + "%s\n\n"
                + "Respond ONLY with valid JSON, no extra text. Schema:\n"
                + "{\"questions\":[\n"
                + "  {\"questionText\":\"...\",\"expectedAnswerHints\":\"...\"}\n"
                + "]}"
        ).formatted(count, sizeInstruction, cardSection, docSection, pdfSection);
    }

    private String buildGradingPrompt(List<ExamQuestion> questions, Map<Integer, String> userAnswers, ExamQuestionSize size) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert grader. Grade the following exam answers based on the provided questions and rubrics.\n\n");
        sb.append("GRADING RULES:\n");
        sb.append("- Score each answer 0–100 (integer).\n");
        sb.append("- Provide per-question feedback: 2–4 sentences. State what was correct, what was missing/incorrect, and what was expected.\n");
        sb.append("- Blank or whitespace-only answers MUST be scored 0 with feedback: \"Not answered.\"\n");
        sb.append("- Produce an overall report with mean score, strengths, weaknesses, topics to revisit, and next steps.\n\n");

        sb.append("=== EXAM DATA ===\n");
        for (int i = 0; i < questions.size(); i++) {
            ExamQuestion q = questions.get(i);
            String answer = userAnswers.getOrDefault(i, "").trim();
            sb.append("Question ").append(i + 1).append(": ").append(q.questionText()).append("\n");
            sb.append("Rubric: ").append(q.expectedAnswerHints()).append("\n");
            sb.append("User Answer: ").append(answer.isEmpty() ? "(EMPTY)" : answer).append("\n\n");
        }

        sb.append("Respond ONLY with valid JSON. Schema:\n");
        sb.append("{\n");
        sb.append("  \"perQuestion\": [\n");
        sb.append("    { \"scorePercent\": 85, \"feedback\": \"...\" }\n");
        sb.append("  ],\n");
        sb.append("  \"overall\": {\n");
        sb.append("    \"scorePercent\": 76,\n");
        sb.append("    \"strengths\": [\"...\", \"...\"],\n");
        sb.append("    \"weaknesses\": [\"...\", \"...\"],\n");
        sb.append("    \"topicsToRevisit\": [\"...\", \"...\"],\n");
        sb.append("    \"suggestedNextSteps\": [\"...\", \"... \"]\n");
        sb.append("  }\n");
        sb.append("}");

        return sb.toString();
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
