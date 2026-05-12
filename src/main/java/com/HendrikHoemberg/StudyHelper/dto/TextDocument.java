package com.HendrikHoemberg.StudyHelper.dto;

public record TextDocument(String filename, String extractedText) implements DocumentInput {}
