package com.HendrikHoemberg.StudyHelper;

import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.service.StudySessionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudySessionSelectionTest {

    private final LocalDate today = LocalDate.of(2026, 5, 22);

    private Flashcard card(Long id, LocalDate due) {
        Flashcard f = new Flashcard();
        f.setId(id);
        f.setDueDate(due);
        return f;
    }

    @Test
    void selectsDueReviewsAndNewCardsUpToRemainingLimit() {
        List<Flashcard> all = List.of(
            card(1L, today.minusDays(1)),
            card(2L, today),
            card(3L, today.plusDays(3)),
            card(4L, null),
            card(5L, null),
            card(6L, null)
        );
        List<Flashcard> selected = StudySessionService.selectScheduledCards(all, today, 2, 0);
        assertThat(selected).extracting(Flashcard::getId)
            .containsExactlyInAnyOrder(1L, 2L, 4L, 5L);
    }

    @Test
    void newAllowanceReducedByCardsAlreadyIntroducedToday() {
        List<Flashcard> all = List.of(card(4L, null), card(5L, null));
        List<Flashcard> selected = StudySessionService.selectScheduledCards(all, today, 20, 19);
        assertThat(selected).hasSize(1);
    }

    @Test
    void noNewAllowanceMeansReviewsOnly() {
        List<Flashcard> all = List.of(card(1L, today.minusDays(1)), card(4L, null));
        List<Flashcard> selected = StudySessionService.selectScheduledCards(all, today, 0, 0);
        assertThat(selected).extracting(Flashcard::getId).containsExactly(1L);
    }
}
