package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentModeResolverTests {

    @Test
    void resolve_pdfWithExplicitFullMode_returnsFullPdf() {
        FileEntry pdf = fileEntry(1L, "doc.pdf");
        assertThat(DocumentModeResolver.resolve(pdf, Map.of(1L, DocumentMode.FULL_PDF)))
                .isEqualTo(DocumentMode.FULL_PDF);
    }

    @Test
    void resolve_pdfWithExplicitTextMode_returnsText() {
        FileEntry pdf = fileEntry(1L, "doc.pdf");
        assertThat(DocumentModeResolver.resolve(pdf, Map.of(1L, DocumentMode.TEXT)))
                .isEqualTo(DocumentMode.TEXT);
    }

    @Test
    void resolve_pdfMissingFromMap_defaultsToText() {
        FileEntry pdf = fileEntry(1L, "doc.pdf");
        assertThat(DocumentModeResolver.resolve(pdf, Map.of()))
                .isEqualTo(DocumentMode.TEXT);
    }

    @Test
    void resolve_nullMap_defaultsToText() {
        FileEntry pdf = fileEntry(1L, "doc.pdf");
        assertThat(DocumentModeResolver.resolve(pdf, null))
                .isEqualTo(DocumentMode.TEXT);
    }

    @Test
    void resolve_nonPdfWithFullModeInMap_returnsText() {
        FileEntry md = fileEntry(2L, "notes.md");
        assertThat(DocumentModeResolver.resolve(md, Map.of(2L, DocumentMode.FULL_PDF)))
                .isEqualTo(DocumentMode.TEXT);
    }

    @Test
    void resolve_uppercasePdfExtension_treatedAsPdf() {
        FileEntry pdf = fileEntry(1L, "DOC.PDF");
        assertThat(DocumentModeResolver.resolve(pdf, Map.of(1L, DocumentMode.FULL_PDF)))
                .isEqualTo(DocumentMode.FULL_PDF);
    }

    @Test
    void parseFromRequest_fullPdfEntry_returnsMap() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[42]", "FULL_PDF");
        Map<Long, DocumentMode> result = DocumentModeResolver.parseFromRequest(request);
        assertThat(result).containsEntry(42L, DocumentMode.FULL_PDF);
    }

    @Test
    void parseFromRequest_textEntry_returnsMap() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[7]", "TEXT");
        Map<Long, DocumentMode> result = DocumentModeResolver.parseFromRequest(request);
        assertThat(result).containsEntry(7L, DocumentMode.TEXT);
    }

    @Test
    void parseFromRequest_noEntries_returnsEmptyMap() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("selectedFileIds", "99");
        Map<Long, DocumentMode> result = DocumentModeResolver.parseFromRequest(request);
        assertThat(result).isEmpty();
    }

    @Test
    void parseFromRequest_nullRequest_returnsEmptyMap() {
        Map<Long, DocumentMode> result = DocumentModeResolver.parseFromRequest(null);
        assertThat(result).isEmpty();
    }

    @Test
    void parseFromRequest_malformedKey_ignoresEntry() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[notANumber]", "FULL_PDF");
        Map<Long, DocumentMode> result = DocumentModeResolver.parseFromRequest(request);
        assertThat(result).isEmpty();
    }

    private FileEntry fileEntry(Long id, String filename) {
        FileEntry e = new FileEntry();
        e.setId(id);
        e.setOriginalFilename(filename);
        return e;
    }
}
