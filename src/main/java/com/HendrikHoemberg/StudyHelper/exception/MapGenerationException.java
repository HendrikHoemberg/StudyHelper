package com.HendrikHoemberg.StudyHelper.exception;

public class MapGenerationException extends RuntimeException {
    public MapGenerationException(String message) {
        super(message);
    }
    public MapGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
