package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.SplitPart;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfSplitServiceTests {

    @TempDir
    Path tempDir;

    private FileEntryRepository fileEntryRepository;
    private FileStorageService fileStorageService;
    private StorageQuotaService storageQuotaService;
    private PdfSplitService service;

    private User user;

    @BeforeEach
    void setUp() throws IOException {
        fileEntryRepository = mock(FileEntryRepository.class);
        fileStorageService = mock(FileStorageService.class);
        storageQuotaService = mock(StorageQuotaService.class);
        service = new PdfSplitService(fileEntryRepository, fileStorageService, storageQuotaService);

        user = new User();
        user.setId(1L);

        Folder folder = new Folder();
        folder.setId(42L);
        folder.setUser(user);

        Path sourcePdf = tempDir.resolve("source.pdf");
        writePdf(sourcePdf, 5);

        FileEntry source = new FileEntry();
        source.setId(7L);
        source.setOriginalFilename("source.pdf");
        source.setStoredFilename("stored-source.pdf");
        source.setMimeType("application/pdf");
        source.setFolder(folder);
        source.setUser(user);

        when(fileEntryRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(source));
        when(fileStorageService.resolvePath("stored-source.pdf")).thenReturn(sourcePdf);
        when(fileStorageService.storeBytes(any(), eq(".pdf"))).thenReturn("part-a.pdf", "part-b.pdf");
        when(fileEntryRepository.save(any(FileEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void splitPdfCreatesOneFileEntryPerPartWithCorrectPageCounts() throws IOException {
        List<SplitPart> parts = List.of(
            new SplitPart("intro.pdf", 1, 2),
            new SplitPart("rest.pdf", 3, 5)
        );

        Long folderId = service.splitPdf(7L, parts, user);

        assertThat(folderId).isEqualTo(42L);

        ArgumentCaptor<FileEntry> entryCaptor = ArgumentCaptor.forClass(FileEntry.class);
        verify(fileEntryRepository, times(2)).save(entryCaptor.capture());
        assertThat(entryCaptor.getAllValues())
            .extracting(FileEntry::getOriginalFilename)
            .containsExactly("intro.pdf", "rest.pdf");
        assertThat(entryCaptor.getAllValues())
            .allMatch(e -> "application/pdf".equals(e.getMimeType()));

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService, times(2)).storeBytes(bytesCaptor.capture(), eq(".pdf"));
        assertThat(pageCount(bytesCaptor.getAllValues().get(0))).isEqualTo(2);
        assertThat(pageCount(bytesCaptor.getAllValues().get(1))).isEqualTo(3);
    }

    @Test
    void splitPdfRejectsRangesThatDoNotCoverEveryPage() throws IOException {
        List<SplitPart> parts = List.of(
            new SplitPart("a.pdf", 1, 2),
            new SplitPart("b.pdf", 3, 4)
        );

        assertThatThrownBy(() -> service.splitPdf(7L, parts, user))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void splitPdfRejectsFewerThanTwoParts() throws IOException {
        List<SplitPart> parts = List.of(new SplitPart("a.pdf", 1, 5));

        assertThatThrownBy(() -> service.splitPdf(7L, parts, user))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void splitPdfFailsOnQuotaExceededAndPersistsNothing() throws IOException {
        doThrow(new StorageQuotaExceededException("Storage quota exceeded"))
            .when(storageQuotaService).assertWithinQuota(eq(user), eq(0L), anyLong());
        List<SplitPart> parts = List.of(
            new SplitPart("a.pdf", 1, 2),
            new SplitPart("b.pdf", 3, 5)
        );

        assertThatThrownBy(() -> service.splitPdf(7L, parts, user))
            .isInstanceOf(StorageQuotaExceededException.class);

        verify(fileStorageService, never()).storeBytes(any(), any());
        verify(fileEntryRepository, never()).save(any());
    }

    private int pageCount(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    private void writePdf(Path dest, int pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage(PDRectangle.LETTER));
            }
            doc.save(dest.toFile());
        }
    }
}
