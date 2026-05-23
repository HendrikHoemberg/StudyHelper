package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Concept;
import com.HendrikHoemberg.StudyHelper.dto.ConceptOutline;
import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiFlashcardServiceTests {

    private ConceptExtractionService extractor;
    private FlashcardWriterService writer;
    private AiFlashcardService service;

    @BeforeEach
    void setUp() {
        extractor = mock(ConceptExtractionService.class);
        writer = mock(FlashcardWriterService.class);
        service = new AiFlashcardService(extractor, writer);
    }

    @Test
    void generate_TextDocument_RunsExtractorThenWriterAndReturnsCards() {
        when(extractor.extract(any(List.class), any())).thenReturn(outline(
            new Concept("c1", "Topic 1", "Essence 1", 1, ""),
            new Concept("c2", "Topic 2", "Essence 2", 2, "")
        ));
        when(writer.write(any(ConceptOutline.class), any())).thenReturn(List.of(
            new GeneratedFlashcard("Front 1", "Back 1"),
            new GeneratedFlashcard("Front 2", "Back 2")
        ));

        List<GeneratedFlashcard> result = service.generate(new TextDocument("notes.txt", "Photosynthesis converts light energy."));

        assertThat(result).hasSize(2);
        ArgumentCaptor<List<DocumentInput>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(extractor).extract(docCaptor.capture(), eq(null));
        assertThat(docCaptor.getValue()).hasSize(1);
        assertThat(docCaptor.getValue().get(0)).isInstanceOf(TextDocument.class);
        verify(writer).write(any(ConceptOutline.class), eq(null));
    }

    @Test
    void generate_PdfDocument_RunsBothStages() {
        when(extractor.extract(any(List.class), any())).thenReturn(outline(
            new Concept("c1", "Topic 1", "Essence 1", 1, "page 3")
        ));
        when(writer.write(any(ConceptOutline.class), any())).thenReturn(List.of(
            new GeneratedFlashcard("Front 1", "Back 1")
        ));

        List<GeneratedFlashcard> result = service.generate(
            new PdfDocument("chapter5.pdf", new ByteArrayResource(new byte[] {0x25, 0x50, 0x44, 0x46}))
        );

        assertThat(result).hasSize(1);
        verify(extractor).extract(any(List.class), eq(null));
        verify(writer).write(any(ConceptOutline.class), eq(null));
    }

    @Test
    void generate_EmptyOutline_ThrowsExtractionEmptyDiagnostic() {
        when(extractor.extract(any(List.class), any())).thenReturn(new ConceptOutline(List.of()));

        assertThatThrownBy(() -> service.generate(new TextDocument("notes.txt", "topic")))
            .isInstanceOf(AiGenerationException.class)
            .hasMessageContaining("could not identify exam-worthy content")
            .satisfies(ex -> {
                AiGenerationException aiEx = (AiGenerationException) ex;
                assertThat(aiEx.diagnostics().stage()).isEqualTo("EXTRACTION_EMPTY");
            });
        verify(writer, never()).write(any(), any());
    }

    @Test
    void generate_WriterReturnsNoValidCards_ThrowsResponseValidationDiagnostic() {
        when(extractor.extract(any(List.class), any())).thenReturn(outline(
            new Concept("c1", "Topic 1", "Essence 1", 1, "")
        ));
        when(writer.write(any(ConceptOutline.class), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(new TextDocument("notes.txt", "topic")))
            .isInstanceOf(AiGenerationException.class)
            .hasMessage("AI returned no valid flashcards; please retry.")
            .satisfies(ex -> {
                AiGenerationException aiEx = (AiGenerationException) ex;
                assertThat(aiEx.diagnostics().stage()).isEqualTo("RESPONSE_VALIDATION");
            });
    }

    @Test
    void generate_MoreThanMaxFlashcards_CapsAtTwoHundred() {
        when(extractor.extract(any(List.class), any())).thenReturn(outline(manyConcepts(220)));
        when(writer.write(any(ConceptOutline.class), any())).thenReturn(manyCards(220));

        List<GeneratedFlashcard> result = service.generate(new TextDocument("notes.txt", "topic"));

        assertThat(result).hasSize(200);
        assertThat(result.get(0)).isEqualTo(new GeneratedFlashcard("Front 1", "Back 1"));
        assertThat(result.get(199)).isEqualTo(new GeneratedFlashcard("Front 200", "Back 200"));
    }

    @Test
    void generate_NoUsableSources_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> service.generate(new TextDocument("notes.txt", "   ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no usable");
        verify(extractor, never()).extract(any(), any());
    }

    @Test
    void generate_NullDocumentList_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> service.generate((List<DocumentInput>) null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one PDF");
        verify(extractor, never()).extract(any(), any());
    }

    @Test
    void generate_AdditionalInstructionsArePassedToBothStages() {
        when(extractor.extract(any(List.class), anyString())).thenReturn(outline(
            new Concept("c1", "Topic 1", "Essence 1", 1, "")
        ));
        when(writer.write(any(ConceptOutline.class), anyString())).thenReturn(List.of(
            new GeneratedFlashcard("Front 1", "Back 1")
        ));

        service.generate(new TextDocument("notes.txt", "topic"), "focus on definitions");

        verify(extractor).extract(any(List.class), eq("focus on definitions"));
        verify(writer).write(any(ConceptOutline.class), eq("focus on definitions"));
    }

    @Test
    void generate_MultipleTextDocuments_PassedToExtractor() {
        when(extractor.extract(any(List.class), any())).thenReturn(outline(
            new Concept("c1", "Topic 1", "Essence 1", 1, "")
        ));
        when(writer.write(any(ConceptOutline.class), any())).thenReturn(List.of(
            new GeneratedFlashcard("Front 1", "Back 1")
        ));

        service.generate(List.of(
            new TextDocument("notes1.txt", "Content one"),
            new TextDocument("notes2.txt", "Content two")
        ), null);

        ArgumentCaptor<List<DocumentInput>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(extractor).extract(docCaptor.capture(), eq(null));
        assertThat(docCaptor.getValue()).hasSize(2);
    }

    @Test
    void generate_MultiplePdfDocuments_PassedToExtractor() {
        when(extractor.extract(any(List.class), any())).thenReturn(outline(
            new Concept("c1", "Topic 1", "Essence 1", 1, "")
        ));
        when(writer.write(any(ConceptOutline.class), any())).thenReturn(List.of(
            new GeneratedFlashcard("Front 1", "Back 1")
        ));

        service.generate(List.of(
            new PdfDocument("chapter1.pdf", new ByteArrayResource(new byte[] {0x25, 0x50, 0x44, 0x46})),
            new PdfDocument("chapter2.pdf", new ByteArrayResource(new byte[] {0x25, 0x50, 0x44, 0x46}))
        ), "cover both chapters");

        ArgumentCaptor<List<DocumentInput>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(extractor).extract(docCaptor.capture(), eq("cover both chapters"));
        assertThat(docCaptor.getValue()).hasSize(2);
        assertThat(docCaptor.getValue()).allSatisfy(doc -> assertThat(doc).isInstanceOf(PdfDocument.class));
    }

    private ConceptOutline outline(Concept... concepts) {
        return new ConceptOutline(List.of(concepts));
    }

    private Concept[] manyConcepts(int count) {
        Concept[] concepts = new Concept[count];
        for (int i = 1; i <= count; i++) {
            concepts[i - 1] = new Concept("c" + i, "Topic " + i, "Essence " + i, 1, "");
        }
        return concepts;
    }

    private List<GeneratedFlashcard> manyCards(int count) {
        List<GeneratedFlashcard> cards = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            cards.add(new GeneratedFlashcard("Front " + i, "Back " + i));
        }
        return cards;
    }
}
