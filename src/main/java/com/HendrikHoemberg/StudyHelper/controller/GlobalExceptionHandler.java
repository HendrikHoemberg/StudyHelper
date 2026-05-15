package com.HendrikHoemberg.StudyHelper.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;

/**
 * Translates uncaught domain exceptions into proper HTTP responses so that a
 * missing or non-owned resource yields a clean 404 instead of a 500. Controllers
 * with their own local handling (e.g. redirects) are unaffected — a local
 * catch always takes precedence over this advice.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public String handleNotFound(NoSuchElementException ex,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 Model model) {
        log.warn("Resource not found for {} {}: {}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("status", HttpStatus.NOT_FOUND.value());
        model.addAttribute("error", "Not Found");
        return "error";
    }
}
