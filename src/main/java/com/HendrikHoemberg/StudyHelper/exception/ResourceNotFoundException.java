package com.HendrikHoemberg.StudyHelper.exception;

import java.util.NoSuchElementException;

/**
 * Thrown when a requested entity does not exist, or exists but is not owned by
 * the current user (the two are deliberately indistinguishable to callers).
 *
 * <p>Extends {@link NoSuchElementException} so that controllers which already
 * catch that type keep working, and a single exception handler can map both.
 */
public class ResourceNotFoundException extends NoSuchElementException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
