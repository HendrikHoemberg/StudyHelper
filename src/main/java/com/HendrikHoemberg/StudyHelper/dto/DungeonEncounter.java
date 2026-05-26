package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record DungeonEncounter(
    String id,
    DungeonEncounterType type,
    DungeonEncounterStatus status,
    boolean boss,
    Long flashcardId,
    String frontText,
    String backText,
    String frontImageUrl,
    String backImageUrl,
    QuizQuestion quizQuestion,
    List<Integer> selectedOptions,
    Boolean correct
) implements Serializable {
    public static DungeonEncounter flashcard(String id, boolean boss, Long flashcardId, String frontText, String backText, String frontImageUrl, String backImageUrl) {
        return new DungeonEncounter(
            id,
            boss ? DungeonEncounterType.BOSS_FLASHCARD : DungeonEncounterType.FLASHCARD,
            DungeonEncounterStatus.PENDING,
            boss,
            flashcardId,
            frontText,
            backText,
            frontImageUrl,
            backImageUrl,
            null,
            List.of(),
            null
        );
    }

    public static DungeonEncounter quiz(String id, boolean boss, QuizQuestion quizQuestion) {
        return new DungeonEncounter(
            id,
            boss ? DungeonEncounterType.BOSS_QUIZ : DungeonEncounterType.QUIZ,
            DungeonEncounterStatus.PENDING,
            boss,
            null,
            null,
            null,
            null,
            null,
            quizQuestion,
            List.of(),
            null
        );
    }

    public DungeonEncounter activate() {
        return new DungeonEncounter(id, type, DungeonEncounterStatus.ACTIVE, boss, flashcardId, frontText, backText, frontImageUrl, backImageUrl, quizQuestion, selectedOptions, correct);
    }

    public DungeonEncounter clear(List<Integer> answer, boolean wasCorrect) {
        return new DungeonEncounter(id, type, DungeonEncounterStatus.CLEARED, boss, flashcardId, frontText, backText, frontImageUrl, backImageUrl, quizQuestion, List.copyOf(answer), wasCorrect);
    }
}
