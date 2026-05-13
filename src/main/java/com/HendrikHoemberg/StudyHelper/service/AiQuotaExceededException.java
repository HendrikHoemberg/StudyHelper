package com.HendrikHoemberg.StudyHelper.service;

public class AiQuotaExceededException extends RuntimeException {

    public AiQuotaExceededException(String message) {
        super(message);
    }
}
