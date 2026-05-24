package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardChunk;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationDestination;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationPlan;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationRisk;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJob;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJobStatus;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardGenerationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlashcardGenerationJobServiceTests {

    private FlashcardGenerationJobRepository jobRepository;
    private AiRequestQuotaService aiRequestQuotaService;
    private TaskExecutor taskExecutor;
    private FileEntryService fileEntryService;
    private FlashcardGenerationPlanService planService;
    private AiFlashcardService aiFlashcardService;
    private FlashcardGenerationPersistenceService persistenceService;
    private FlashcardGenerationJobService service;
    private User user;
    private FlashcardGenerationPlan plan;

    @BeforeEach
    void setUp() {
        jobRepository = mock(FlashcardGenerationJobRepository.class);
        aiRequestQuotaService = mock(AiRequestQuotaService.class);
        taskExecutor = mock(TaskExecutor.class);
        fileEntryService = mock(FileEntryService.class);
        planService = mock(FlashcardGenerationPlanService.class);
        aiFlashcardService = mock(AiFlashcardService.class);
        persistenceService = mock(FlashcardGenerationPersistenceService.class);
        service = new FlashcardGenerationJobService(
            jobRepository, aiRequestQuotaService, taskExecutor,
            fileEntryService, planService, aiFlashcardService, persistenceService
        );

        user = new User();
        user.setId(1L);
        user.setUsername("alice");

        plan = new FlashcardGenerationPlan(
            List.of(new FlashcardChunk("lecture.pdf", 1, 1, 2, "text", null)),
            3, 24, 36, 15, 45, FlashcardGenerationRisk.NORMAL
        );
    }

    @Test
    void acceptJob_chargesQuotaAndPersistsQueuedJob() {
        when(jobRepository.save(any(FlashcardGenerationJob.class))).thenAnswer(inv -> {
            FlashcardGenerationJob job = inv.getArgument(0);
            job.setId(44L);
            return job;
        });

        FlashcardGenerationJob job = service.acceptJob(
            user,
            List.of(99L),
            DocumentMode.TEXT,
            FlashcardGenerationDestination.NEW_DECK,
            null,
            10L,
            "Generated",
            "focus",
            plan
        );

        assertThat(job.getId()).isEqualTo(44L);
        assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.QUEUED);
        assertThat(job.getEstimatedRequestCost()).isEqualTo(3);
        assertThat(job.getChargedRequestCost()).isEqualTo(3);
        assertThat(job.getChunkCount()).isEqualTo(1);
        verify(aiRequestQuotaService).checkAndRecord(user, 3);
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void getJob_found_returnsJob() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(44L);
        when(jobRepository.findByIdAndUser(44L, user)).thenReturn(Optional.of(job));

        assertThat(service.getJob(44L, user).getId()).isEqualTo(44L);
    }

    @Test
    void getJob_notFound_throwsResourceNotFound() {
        when(jobRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(99L, user))
            .isInstanceOf(com.HendrikHoemberg.StudyHelper.exception.ResourceNotFoundException.class);
    }

    @Test
    void markRunning_updatesStatusAndStartedAt() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        FlashcardGenerationJob result = service.markRunning(1L);

        assertThat(result.getStatus()).isEqualTo(FlashcardGenerationJobStatus.RUNNING);
        assertThat(result.getStartedAt()).isNotNull();
    }

    @Test
    void markSucceeded_updatesStatusAndCounts() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        job.setChunkCount(5);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        service.markSucceeded(1L, 20L, 42);

        assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.SUCCEEDED);
        assertThat(job.getSavedDeckId()).isEqualTo(20L);
        assertThat(job.getGeneratedCardCount()).isEqualTo(42);
        assertThat(job.getCompletedChunkCount()).isEqualTo(5);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void markFailed_updatesStatusAndMessage() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        service.markFailed(1L, "Something went wrong");

        assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.FAILED);
        assertThat(job.getFailureMessage()).isEqualTo("Something went wrong");
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void markFailed_truncatesLongMessage() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        String longMessage = "x".repeat(2000);
        service.markFailed(1L, longMessage);

        assertThat(job.getFailureMessage()).hasSize(1000);
    }

    @Test
    void failTimedOutJobs_marksStaleJobsFailed() {
        Instant cutoff = Instant.now();
        FlashcardGenerationJob stale = new FlashcardGenerationJob();
        stale.setId(1L);
        stale.setStatus(FlashcardGenerationJobStatus.RUNNING);
        when(jobRepository.findByStatusAndStartedAtBefore(FlashcardGenerationJobStatus.RUNNING, cutoff))
            .thenReturn(List.of(stale));

        int failed = service.failTimedOutJobs(cutoff);

        assertThat(failed).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(FlashcardGenerationJobStatus.FAILED);
        assertThat(stale.getFailureMessage()).isEqualTo("Flashcard generation timed out.");
        assertThat(stale.getFinishedAt()).isNotNull();
    }

    @Test
    void failTimedOutJobs_noStale_returnsZero() {
        Instant cutoff = Instant.now();
        when(jobRepository.findByStatusAndStartedAtBefore(FlashcardGenerationJobStatus.RUNNING, cutoff))
            .thenReturn(List.of());

        int failed = service.failTimedOutJobs(cutoff);

        assertThat(failed).isZero();
    }
}
