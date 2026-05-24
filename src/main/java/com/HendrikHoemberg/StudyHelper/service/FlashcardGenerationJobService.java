package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationDestination;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationPlan;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJob;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJobStatus;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.exception.ResourceNotFoundException;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardGenerationJobRepository;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FlashcardGenerationJobService {

    private final FlashcardGenerationJobRepository jobRepository;
    private final AiRequestQuotaService aiRequestQuotaService;
    private final TaskExecutor flashcardGenerationTaskExecutor;
    private final FileEntryService fileEntryService;
    private final FlashcardGenerationPlanService planService;
    private final AiFlashcardService aiFlashcardService;
    private final FlashcardGenerationPersistenceService persistenceService;

    public FlashcardGenerationJobService(FlashcardGenerationJobRepository jobRepository,
                                         AiRequestQuotaService aiRequestQuotaService,
                                         TaskExecutor flashcardGenerationTaskExecutor,
                                         FileEntryService fileEntryService,
                                         FlashcardGenerationPlanService planService,
                                         AiFlashcardService aiFlashcardService,
                                         FlashcardGenerationPersistenceService persistenceService) {
        this.jobRepository = jobRepository;
        this.aiRequestQuotaService = aiRequestQuotaService;
        this.flashcardGenerationTaskExecutor = flashcardGenerationTaskExecutor;
        this.fileEntryService = fileEntryService;
        this.planService = planService;
        this.aiFlashcardService = aiFlashcardService;
        this.persistenceService = persistenceService;
    }

    @Transactional
    public FlashcardGenerationJob acceptJob(User user,
                                            List<Long> sourceFileIds,
                                            DocumentMode documentMode,
                                            FlashcardGenerationDestination destination,
                                            Long existingDeckId,
                                            Long newDeckFolderId,
                                            String newDeckName,
                                            String additionalInstructions,
                                            FlashcardGenerationPlan plan) {
        aiRequestQuotaService.checkAndRecord(user, plan.requestCost());
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setUser(user);
        job.setSourceFileIdsCsv(sourceFileIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        job.setDocumentMode(documentMode);
        job.setDestination(destination);
        job.setExistingDeckId(existingDeckId);
        job.setNewDeckFolderId(newDeckFolderId);
        job.setNewDeckName(newDeckName);
        job.setAdditionalInstructions(additionalInstructions == null || additionalInstructions.isBlank() ? null : additionalInstructions.strip());
        job.setEstimatedRequestCost(plan.requestCost());
        job.setChargedRequestCost(plan.requestCost());
        job.setChunkCount(plan.chunks().size());
        FlashcardGenerationJob saved = jobRepository.save(job);
        flashcardGenerationTaskExecutor.execute(() -> runJob(saved.getId()));
        return saved;
    }

    @Transactional
    public FlashcardGenerationJob getJob(Long jobId, User user) {
        return jobRepository.findByIdAndUser(jobId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Generation job not found"));
    }

    void runJob(Long jobId) {
        FlashcardGenerationJob job = markRunning(jobId);
        try {
            User user = job.getUser();
            List<FileEntry> files = parseFileIds(job.getSourceFileIdsCsv()).stream()
                .map(id -> fileEntryService.getByIdAndUser(id, user))
                .toList();
            FlashcardGenerationPlan plan = planService.plan(files, job.getDocumentMode());
            List<GeneratedFlashcard> generated = aiFlashcardService.generateChunks(plan.chunks(), job.getAdditionalInstructions());
            var deck = persistenceService.saveGeneratedCards(job.getDestination(), job.getExistingDeckId(), job.getNewDeckFolderId(), job.getNewDeckName(), user, generated);
            markSucceeded(jobId, deck.getId(), generated.size());
        } catch (Exception ex) {
            markFailed(jobId, ex.getMessage() == null ? "Flashcard generation failed." : ex.getMessage());
        }
    }

    @Transactional
    FlashcardGenerationJob markRunning(Long jobId) {
        FlashcardGenerationJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(FlashcardGenerationJobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        return job;
    }

    @Transactional
    void markSucceeded(Long jobId, Long deckId, int generatedCardCount) {
        FlashcardGenerationJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(FlashcardGenerationJobStatus.SUCCEEDED);
        job.setSavedDeckId(deckId);
        job.setGeneratedCardCount(generatedCardCount);
        job.setCompletedChunkCount(job.getChunkCount());
        job.setFinishedAt(Instant.now());
    }

    @Transactional
    void markFailed(Long jobId, String message) {
        FlashcardGenerationJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(FlashcardGenerationJobStatus.FAILED);
        job.setFailureMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
        job.setFinishedAt(Instant.now());
    }

    @Transactional
    public int failTimedOutJobs(Instant olderThan) {
        List<FlashcardGenerationJob> stale = jobRepository.findByStatusAndStartedAtBefore(FlashcardGenerationJobStatus.RUNNING, olderThan);
        for (FlashcardGenerationJob job : stale) {
            job.setStatus(FlashcardGenerationJobStatus.FAILED);
            job.setFailureMessage("Flashcard generation timed out.");
            job.setFinishedAt(Instant.now());
        }
        return stale.size();
    }

    private List<Long> parseFileIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(Long::valueOf).toList();
    }
}
