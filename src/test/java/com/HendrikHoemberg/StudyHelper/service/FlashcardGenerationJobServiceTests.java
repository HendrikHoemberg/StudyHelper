package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardChunk;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationDestination;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationPlan;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationRisk;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJob;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJobStatus;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardGenerationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlashcardGenerationJobServiceTests {

    private FlashcardGenerationJobRepository jobRepository;
    private AiRequestQuotaService aiRequestQuotaService;
    private TaskExecutor taskExecutor;
    private FileEntryService fileEntryService;
    private DocumentExtractionService documentExtractionService;
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
        documentExtractionService = mock(DocumentExtractionService.class);
        planService = mock(FlashcardGenerationPlanService.class);
        aiFlashcardService = mock(AiFlashcardService.class);
        persistenceService = mock(FlashcardGenerationPersistenceService.class);
        service = new FlashcardGenerationJobService(
            jobRepository, aiRequestQuotaService, taskExecutor, testTransactionTemplate(),
            fileEntryService, documentExtractionService, planService, aiFlashcardService, persistenceService
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
        assertThat(job.getChunkPlanSnapshot()).isNotBlank();
        assertThat(job.getChunkPlanSnapshot()).contains("lecture.pdf");
        verify(aiRequestQuotaService).checkAndRecord(user, 3);
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void runJob_usesAcceptedChunkPlanSnapshot() throws Exception {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        job.setUser(user);
        job.setSourceFileIdsCsv("99");
        job.setDocumentMode(DocumentMode.TEXT);
        job.setDestination(FlashcardGenerationDestination.NEW_DECK);
        job.setNewDeckFolderId(10L);
        job.setNewDeckName("Generated");
        job.setChunkCount(plan.chunks().size());
        job.setChunkPlanSnapshot(FlashcardGenerationJobService.serializePlanSnapshot(plan));
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(aiFlashcardService.generateChunks(eq(plan.chunks()), eq(null), any(IntConsumer.class)))
            .thenReturn(List.of(new GeneratedFlashcard("Q", "A")));
        Deck deck = new Deck();
        deck.setId(20L);
        when(persistenceService.saveGeneratedCards(eq(FlashcardGenerationDestination.NEW_DECK), eq(null), eq(10L), eq("Generated"), eq(user), anyList()))
            .thenReturn(deck);

        service.runJob(1L);

        verify(planService, never()).plan(anyList(), any());
        verify(fileEntryService, never()).getByIdAndUser(any(), any());
        verify(aiFlashcardService).generateChunks(eq(plan.chunks()), eq(null), any(IntConsumer.class));
        assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.SUCCEEDED);
    }

    @Test
    void runJob_updatesCompletedChunksFromAiCallback() {
        FlashcardGenerationPlan twoChunkPlan = new FlashcardGenerationPlan(
            List.of(
                new FlashcardChunk("lecture.pdf", 1, 1, 2, "one", null),
                new FlashcardChunk("lecture.pdf", 2, 3, 4, "two", null)
            ),
            2, 16, 24, 10, 30, FlashcardGenerationRisk.NORMAL
        );
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        job.setUser(user);
        job.setSourceFileIdsCsv("99");
        job.setDocumentMode(DocumentMode.TEXT);
        job.setDestination(FlashcardGenerationDestination.NEW_DECK);
        job.setNewDeckFolderId(10L);
        job.setNewDeckName("Generated");
        job.setChunkCount(twoChunkPlan.chunks().size());
        job.setChunkPlanSnapshot(FlashcardGenerationJobService.serializePlanSnapshot(twoChunkPlan));
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(aiFlashcardService.generateChunks(eq(twoChunkPlan.chunks()), eq(null), any(IntConsumer.class)))
            .thenAnswer(invocation -> {
                IntConsumer progress = invocation.getArgument(2);
                progress.accept(1);
                assertThat(job.getCompletedChunkCount()).isEqualTo(1);
                progress.accept(2);
                assertThat(job.getCompletedChunkCount()).isEqualTo(2);
                return List.of(new GeneratedFlashcard("Q", "A"));
            });
        Deck deck = new Deck();
        deck.setId(20L);
        when(persistenceService.saveGeneratedCards(eq(FlashcardGenerationDestination.NEW_DECK), eq(null), eq(10L), eq("Generated"), eq(user), anyList()))
            .thenReturn(deck);

        service.runJob(1L);

        assertThat(job.getCompletedChunkCount()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.SUCCEEDED);
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
        Instant now = Instant.parse("2026-05-24T12:00:00Z");
        FlashcardGenerationJob stale = new FlashcardGenerationJob();
        stale.setId(1L);
        stale.setStatus(FlashcardGenerationJobStatus.RUNNING);
        stale.setStartedAt(now.minusSeconds(11 * 60));
        stale.setChunkCount(1);
        when(jobRepository.findByStatus(FlashcardGenerationJobStatus.RUNNING))
            .thenReturn(List.of(stale));

        int failed = service.failTimedOutJobs(now);

        assertThat(failed).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(FlashcardGenerationJobStatus.FAILED);
        assertThat(stale.getFailureMessage()).isEqualTo("Flashcard generation timed out.");
        assertThat(stale.getFinishedAt()).isNotNull();
    }

    @Test
    void failTimedOutJobs_noStale_returnsZero() {
        Instant now = Instant.parse("2026-05-24T12:00:00Z");
        when(jobRepository.findByStatus(FlashcardGenerationJobStatus.RUNNING))
            .thenReturn(List.of());

        int failed = service.failTimedOutJobs(now);

        assertThat(failed).isZero();
    }

    @Test
    void failTimedOutJobs_usesPerJobRuntimeDeadline() {
        Instant now = Instant.parse("2026-05-24T12:00:00Z");
        FlashcardGenerationJob timedOut = new FlashcardGenerationJob();
        timedOut.setId(1L);
        timedOut.setStatus(FlashcardGenerationJobStatus.RUNNING);
        timedOut.setStartedAt(now.minusSeconds(11 * 60));
        timedOut.setChunkCount(1);

        FlashcardGenerationJob stillAllowed = new FlashcardGenerationJob();
        stillAllowed.setId(2L);
        stillAllowed.setStatus(FlashcardGenerationJobStatus.RUNNING);
        stillAllowed.setStartedAt(now.minusSeconds(12 * 60));
        stillAllowed.setChunkCount(5);

        when(jobRepository.findByStatus(FlashcardGenerationJobStatus.RUNNING))
            .thenReturn(List.of(timedOut, stillAllowed));

        int failed = service.failTimedOutJobs(now);

        assertThat(failed).isEqualTo(1);
        assertThat(timedOut.getStatus()).isEqualTo(FlashcardGenerationJobStatus.FAILED);
        assertThat(timedOut.getFailureMessage()).isEqualTo("Flashcard generation timed out.");
        assertThat(stillAllowed.getStatus()).isEqualTo(FlashcardGenerationJobStatus.RUNNING);
    }

    @Test
    void cancelJob_updatesStatusToCancelledAndRefundsQuota() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        job.setUser(user);
        job.setStatus(FlashcardGenerationJobStatus.RUNNING);
        job.setChargedRequestCost(5);
        job.setCompletedChunkCount(2);
        when(jobRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(job));

        service.cancelJob(1L, user);

        assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.CANCELLED);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getChargedRequestCost()).isEqualTo(2);
        verify(aiRequestQuotaService).refund(user, 3);
    }

    @Test
    void markRunning_throwsJobCancelledException_ifJobCancelled() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        job.setStatus(FlashcardGenerationJobStatus.CANCELLED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.markRunning(1L))
            .isInstanceOf(FlashcardGenerationJobService.JobCancelledException.class);
    }

    @Test
    void updateCompletedChunks_throwsJobCancelledException_ifJobCancelled() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        job.setStatus(FlashcardGenerationJobStatus.CANCELLED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.updateCompletedChunks(1L, 3))
            .isInstanceOf(FlashcardGenerationJobService.JobCancelledException.class);
    }

    @Test
    void runJob_terminatesGracefully_onJobCancelledException() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        job.setUser(user);
        job.setStatus(FlashcardGenerationJobStatus.CANCELLED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        service.runJob(1L);

        // Should not fail and should not mark job as failed
        assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.CANCELLED);
    }

    @Test
    void markFailed_refundsUnusedQuota() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        job.setUser(user);
        job.setChargedRequestCost(5);
        job.setCompletedChunkCount(2);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        service.markFailed(1L, "Something went wrong");

        assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.FAILED);
        assertThat(job.getFailureMessage()).isEqualTo("Something went wrong");
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getChargedRequestCost()).isEqualTo(2);
        verify(aiRequestQuotaService).refund(user, 3);
    }

    @Test
    void setJobThrottled_updatesThrottledField() {
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setId(1L);
        job.setThrottled(false);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        service.setJobThrottled(1L, true);

        assertThat(job.isThrottled()).isTrue();
    }

    private TransactionTemplate testTransactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
