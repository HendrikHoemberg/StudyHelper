package com.HendrikHoemberg.StudyHelper.dto;

import org.springframework.core.io.Resource;

public record PdfDocument(String filename, Resource source) implements DocumentInput {}
