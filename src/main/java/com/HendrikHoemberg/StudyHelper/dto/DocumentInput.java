package com.HendrikHoemberg.StudyHelper.dto;

public sealed interface DocumentInput permits TextDocument, PdfDocument {
    String filename();
}
