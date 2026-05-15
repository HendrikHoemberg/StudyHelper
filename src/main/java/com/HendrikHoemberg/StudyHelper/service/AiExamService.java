package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.ExamGradingResult;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestion;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionsResponse;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionSize;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiExamService {

    private static final Logger log = LoggerFactory.getLogger(AiExamService.class);

    private final ChatClient chatClient;
    private final String questionsResponseSchema;
    private final String gradingResponseSchema;

    public AiExamService(ChatClient.Builder builder, JsonMapper objectMapper) {
        this.chatClient = builder.build();
        this.questionsResponseSchema = new BeanOutputConverter<>(ExamQuestionsResponse.class, objectMapper).getJsonSchema();
        this.gradingResponseSchema = new BeanOutputConverter<>(ExamGradingResult.class, objectMapper).getJsonSchema();
    }

    public List<ExamQuestion> generate(
            List<Flashcard> flashcards,
            List<DocumentInput> documents,
            int questionCount,
            ExamQuestionSize size) {
        return generate(flashcards, documents, questionCount, size, null);
    }

    public List<ExamQuestion> generate(
            List<Flashcard> flashcards,
            List<DocumentInput> documents,
            int questionCount,
            ExamQuestionSize size,
            String additionalInstructions) {
        String cardContent = AiGenerationSupport.cards(flashcards);
        String docContent  = AiGenerationSupport.textDocuments(documents);
        String pdfListing  = AiGenerationSupport.pdfListing(documents);
        Media[] pdfMedia   = AiGenerationSupport.pdfMedia(documents);

        if (cardContent.isBlank() && docContent.isBlank() && pdfMedia.length == 0) {
            throw new IllegalArgumentException("Selected sources contain no usable text or PDFs. Pick a deck, a document with extractable content, or a PDF in full-document mode.");
        }

        String prompt = buildGenerationPrompt(cardContent, docContent, pdfListing, questionCount, size, additionalInstructions);

        ExamQuestionsResponse response;
        try {
            response = chatClient.prompt()
                    .options(GoogleGenAiChatOptions.builder()
                            .responseMimeType("application/json")
                            .responseSchema(questionsResponseSchema))
                    .user(u -> {
                        u.text(prompt);
                        if (pdfMedia.length > 0) u.media(pdfMedia);
                    })
                    .call()
                    .entity(ExamQuestionsResponse.class);
        } catch (Exception e) {
            throw AiGenerationSupport.failure(log, "EXAM", "PROVIDER_REQUEST",
                "AI request failed, please retry with fewer or smaller PDFs.", e);
        }

        try {
            List<ExamQuestion> rawList = response == null || response.questions() == null
                    ? List.of()
                    : response.questions();

            List<ExamQuestion> valid = new ArrayList<>();
            for (ExamQuestion q : rawList) {
                if (q == null || q.questionText() == null || q.questionText().isBlank()) continue;
                valid.add(q);
            }

            if (valid.size() < Math.max(1, questionCount / 2)) {
                throw AiGenerationSupport.failure(log, "EXAM", "RESPONSE_VALIDATION",
                    "AI returned too few valid questions; please retry.",
                    new IllegalStateException("AI response contained " + valid.size() + " valid exam questions for requested count " + questionCount + "."));
            }

            return valid.stream().limit(questionCount).toList();

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw AiGenerationSupport.failure(log, "EXAM", "RESPONSE_PARSE",
                "Could not parse the AI response. Please try again.", e);
        }
    }

    public ExamGradingResult grade(
            List<ExamQuestion> questions,
            Map<Integer, String> userAnswers,
            ExamQuestionSize size) {

        List<ExamQuestion> safeQuestions = questions == null ? List.of() : questions;
        Map<Integer, String> safeUserAnswers = userAnswers == null ? Map.of() : userAnswers;
        String prompt = buildGradingPrompt(safeQuestions, safeUserAnswers, size);

        try {
            return chatClient.prompt()
                    .options(GoogleGenAiChatOptions.builder()
                            .responseMimeType("application/json")
                            .responseSchema(gradingResponseSchema))
                    .user(u -> u.text(prompt))
                    .call()
                    .entity(ExamGradingResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("AI grading request failed. Please try again.", e);
        }
    }

    private String buildGenerationPrompt(String cardContent, String docContent, String pdfListing,
                                         int count, ExamQuestionSize size, String additionalInstructions) {
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
                + "LANGUAGE:\n"
                + "Detect the dominant natural language of the supplied source material (flashcards,\n"
                + "documents, and attached PDFs together). Write every output string — question text\n"
                + "and expectedAnswerHints — in that same language. If sources mix languages, use the\n"
                + "most-prevalent one. Do not translate technical terms, proper nouns, or code.\n\n"
                + "QUESTION DEPTH:\n"
                + "%s\n\n"
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
                + "GENERAL RULES:\n"
                + "- Each question stands alone — do not reference \"the text\" or \"the document\".\n"
                + "- Test understanding, not exact wording.\n"
                + "- Use all supplied sources (flashcards, text documents, and attached PDFs) consistently with the coverage plan.\n"
                + "- For each question, provide 'expectedAnswerHints' which is a 2–3 sentence rubric\n"
                + "  describing what a perfect answer should contain. This rubric is never shown to the user.\n\n"
                + "=== FLASHCARDS ===\n"
                + "%s\n\n"
                + "=== DOCUMENTS ===\n"
                + "%s\n\n"
                + "=== ATTACHED PDFs ===\n"
                + "%s\n"
        ).formatted(count, sizeInstruction, count, cardSection, docSection, pdfSection)
            + AiInstructionSupport.section(additionalInstructions);
    }

    private String buildGradingPrompt(List<ExamQuestion> questions, Map<Integer, String> userAnswers, ExamQuestionSize size) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert grader. Grade the following exam answers based on the provided questions and rubrics.\n\n");
        sb.append("LANGUAGE:\n");
        sb.append("Detect the dominant natural language of the questions and user answers below.\n");
        sb.append("Write every output string — feedback, strengths, weaknesses, topicsToRevisit,\n");
        sb.append("suggestedNextSteps — in that same language. Do not translate technical terms,\n");
        sb.append("proper nouns, or code.\n\n");
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

        return sb.toString();
    }
}
