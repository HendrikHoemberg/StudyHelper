package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationRisk;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlashcardGenerationPlanServiceTests {

    private DocumentExtractionService documentExtractionService;
    private FlashcardGenerationPlanService service;
    private FileEntry pdf;

    @BeforeEach
    void setUp() {
        documentExtractionService = mock(DocumentExtractionService.class);
        service = new FlashcardGenerationPlanService(documentExtractionService);
        pdf = new FileEntry();
        pdf.setId(7L);
        pdf.setOriginalFilename("lecture.pdf");
    }

    @Test
    void planTextMode_splitsEveryTwoPagesWhenWordsAreSmall() throws Exception {
        when(documentExtractionService.extractPdfTextPages(pdf)).thenReturn(List.of("one", "two", "three", "four", "five"));

        var plan = service.plan(List.of(pdf), DocumentMode.TEXT);

        assertThat(plan.requestCost()).isEqualTo(3);
        assertThat(plan.chunks()).extracting("startPage").containsExactly(1, 3, 5);
        assertThat(plan.chunks()).extracting("endPage").containsExactly(2, 4, 5);
    }

    @Test
    void planTextMode_splitsOnFifteenHundredWords() throws Exception {
        String words = "word ".repeat(1600);
        when(documentExtractionService.extractPdfTextPages(pdf)).thenReturn(List.of(words));

        var plan = service.plan(List.of(pdf), DocumentMode.TEXT);

        assertThat(plan.requestCost()).isEqualTo(2);
        assertThat(plan.chunks()).hasSize(2);
    }

    @Test
    void plan_assignsRiskFromRequestCost() throws Exception {
        when(documentExtractionService.extractPdfTextPages(pdf)).thenReturn(List.of("x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x"));

        var plan = service.plan(List.of(pdf), DocumentMode.TEXT);

        assertThat(plan.requestCost()).isEqualTo(16);
        assertThat(plan.risk()).isEqualTo(FlashcardGenerationRisk.WARNING);
    }

    @Test
    void plan_emptyPdfList_throwsIllegalArgument() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> service.plan(List.of(), DocumentMode.TEXT));
    }

    @Test
    void plan_nullPdfList_throwsIllegalArgument() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> service.plan(null, DocumentMode.TEXT));
    }

    @Test
    void planFullPdfMode_splitsEveryTwoPages() throws Exception {
        when(documentExtractionService.pdfPageCount(pdf)).thenReturn(5);
        when(documentExtractionService.loadPdfPageRangeResource(pdf, 1, 2)).thenReturn(mock(org.springframework.core.io.Resource.class));
        when(documentExtractionService.loadPdfPageRangeResource(pdf, 3, 4)).thenReturn(mock(org.springframework.core.io.Resource.class));
        when(documentExtractionService.loadPdfPageRangeResource(pdf, 5, 5)).thenReturn(mock(org.springframework.core.io.Resource.class));

        var plan = service.plan(List.of(pdf), DocumentMode.FULL_PDF);

        assertThat(plan.requestCost()).isEqualTo(3);
        assertThat(plan.chunks()).hasSize(3);
        assertThat(plan.chunks().get(0).hasPdfResource()).isTrue();
        assertThat(plan.chunks().get(0).startPage()).isEqualTo(1);
        assertThat(plan.chunks().get(0).endPage()).isEqualTo(2);
        assertThat(plan.chunks().get(1).startPage()).isEqualTo(3);
        assertThat(plan.chunks().get(1).endPage()).isEqualTo(4);
        assertThat(plan.chunks().get(2).startPage()).isEqualTo(5);
        assertThat(plan.chunks().get(2).endPage()).isEqualTo(5);
    }

    @Test
    void plan_assignsNormalRiskForLowCost() throws Exception {
        when(documentExtractionService.extractPdfTextPages(pdf)).thenReturn(List.of("one", "two"));

        var plan = service.plan(List.of(pdf), DocumentMode.TEXT);

        assertThat(plan.requestCost()).isEqualTo(1);
        assertThat(plan.risk()).isEqualTo(FlashcardGenerationRisk.NORMAL);
    }

    @Test
    void plan_assignsHighRiskForHighCost() throws Exception {
        List<String> manyPages = new java.util.ArrayList<>();
        for (int i = 0; i < 62; i++) manyPages.add("x");
        when(documentExtractionService.extractPdfTextPages(pdf)).thenReturn(manyPages);

        var plan = service.plan(List.of(pdf), DocumentMode.TEXT);

        assertThat(plan.requestCost()).isEqualTo(31);
        assertThat(plan.risk()).isEqualTo(FlashcardGenerationRisk.HIGH);
        assertThat(plan.highRisk()).isTrue();
    }
}
