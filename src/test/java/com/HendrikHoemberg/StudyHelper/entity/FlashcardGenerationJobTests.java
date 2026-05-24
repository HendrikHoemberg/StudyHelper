package com.HendrikHoemberg.StudyHelper.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlashcardGenerationJobTests {

    @Test
    void jobDefaultsToQueuedAndSetsCreatedAt() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.prePersist();

        assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.QUEUED);
        assertThat(job.getCreatedAt()).isNotNull();
    }
}
