package com.HendrikHoemberg.StudyHelper.dto;

import java.util.Optional;

public sealed interface ActionResult {
    DungeonSessionState state();
    Optional<String> errorMessage();

    record Success(DungeonSessionState state) implements ActionResult {
        @Override
        public Optional<String> errorMessage() {
            return Optional.empty();
        }
    }

    record Failure(DungeonSessionState state, String message) implements ActionResult {
        @Override
        public Optional<String> errorMessage() {
            return Optional.of(message);
        }
    }

    static ActionResult success(DungeonSessionState state) {
        return new Success(state);
    }

    static ActionResult failure(DungeonSessionState state, String message) {
        return new Failure(state, message);
    }
}
