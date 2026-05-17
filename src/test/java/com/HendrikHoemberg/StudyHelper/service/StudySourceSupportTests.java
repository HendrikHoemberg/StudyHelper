package com.HendrikHoemberg.StudyHelper.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudySourceSupportTests {

    @Test
    void normalizeQuestionCount_AllowsUpToOneHundredQuestions() {
        assertThat(StudySourceSupport.normalizeQuizQuestionCount(100)).isEqualTo(100);
        assertThat(StudySourceSupport.normalizeQuizQuestionCount(101)).isEqualTo(100);
    }

    @Test
    void normalizeQuestionCount_KeepsExamLimitAtTwentyQuestions() {
        assertThat(StudySourceSupport.normalizeQuestionCount(20)).isEqualTo(20);
        assertThat(StudySourceSupport.normalizeQuestionCount(21)).isEqualTo(20);
    }
}
