package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record PdfFolderNode(
    Long id,
    String name,
    String colorHex,
    String iconName,
    List<FlashcardPdfOption> pdfs,
    List<PdfFolderNode> children
) implements Serializable {
}
