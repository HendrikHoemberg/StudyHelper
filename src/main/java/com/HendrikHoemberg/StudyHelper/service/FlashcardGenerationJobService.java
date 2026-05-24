package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardChunk;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FlashcardGenerationJobService {

    private final FlashcardGenerationJobRepository jobRepository;
    private final AiRequestQuotaService aiRequestQuotaService;
    private final TaskExecutor flashcardGenerationTaskExecutor;
    private final FileEntryService fileEntryService;
    private final DocumentExtractionService documentExtractionService;
    private final FlashcardGenerationPlanService planService;
    private final AiFlashcardService aiFlashcardService;
    private final FlashcardGenerationPersistenceService persistenceService;

    public FlashcardGenerationJobService(FlashcardGenerationJobRepository jobRepository,
                                         AiRequestQuotaService aiRequestQuotaService,
                                         TaskExecutor flashcardGenerationTaskExecutor,
                                         FileEntryService fileEntryService,
                                         DocumentExtractionService documentExtractionService,
                                         FlashcardGenerationPlanService planService,
                                         AiFlashcardService aiFlashcardService,
                                         FlashcardGenerationPersistenceService persistenceService) {
        this.jobRepository = jobRepository;
        this.aiRequestQuotaService = aiRequestQuotaService;
        this.flashcardGenerationTaskExecutor = flashcardGenerationTaskExecutor;
        this.fileEntryService = fileEntryService;
        this.documentExtractionService = documentExtractionService;
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
        job.setChunkPlanSnapshot(serializePlanSnapshot(plan));
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
            List<FlashcardChunk> chunks = chunksForJob(job, user);
            List<GeneratedFlashcard> generated = aiFlashcardService.generateChunks(
                chunks,
                job.getAdditionalInstructions(),
                completedChunks -> updateCompletedChunks(jobId, completedChunks)
            );
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
    void updateCompletedChunks(Long jobId, int completedChunkCount) {
        FlashcardGenerationJob job = jobRepository.findById(jobId).orElseThrow();
        job.setCompletedChunkCount(Math.min(completedChunkCount, job.getChunkCount()));
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

    private List<FlashcardChunk> chunksForJob(FlashcardGenerationJob job, User user) throws Exception {
        if (job.getChunkPlanSnapshot() != null && !job.getChunkPlanSnapshot().isBlank()) {
            return deserializePlanSnapshot(job.getChunkPlanSnapshot(), user);
        }
        List<FileEntry> files = parseFileIds(job.getSourceFileIdsCsv()).stream()
            .map(id -> fileEntryService.getByIdAndUser(id, user))
            .toList();
        return planService.plan(files, job.getDocumentMode()).chunks();
    }

    static String serializePlanSnapshot(FlashcardGenerationPlan plan) {
        return plan.chunks().stream()
            .map(chunk -> String.join("\t",
                chunk.sourceFileId() == null ? "" : chunk.sourceFileId().toString(),
                sanitizeSnapshotField(chunk.sourceFilename()),
                Integer.toString(chunk.chunkIndex()),
                Integer.toString(chunk.startPage()),
                Integer.toString(chunk.endPage()),
                Boolean.toString(chunk.hasPdfResource()),
                encodeSnapshotText(chunk.text())
            ))
            .collect(Collectors.joining("\n"));
    }

    private List<FlashcardChunk> deserializePlanSnapshot(String snapshot, User user) throws Exception {
        Map<Long, FileEntry> filesById = new HashMap<>();
        List<FlashcardChunk> chunks = new ArrayList<>();
        for (String line : snapshot.split("\\R")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\t", -1);
            if (parts.length != 7) {
                throw new IllegalStateException("Stored flashcard generation plan is invalid.");
            }
            Long sourceFileId = parts[0].isBlank() ? null : Long.valueOf(parts[0]);
            String sourceFilename = parts[1];
            int chunkIndex = Integer.parseInt(parts[2]);
            int startPage = Integer.parseInt(parts[3]);
            int endPage = Integer.parseInt(parts[4]);
            boolean hasPdfResource = Boolean.parseBoolean(parts[5]);
            String text = decodeSnapshotText(parts[6]);
            org.springframework.core.io.Resource resource = null;
            if (hasPdfResource) {
                if (sourceFileId == null) {
                    throw new IllegalStateException("Stored PDF chunk is missing its source file id.");
                }
                FileEntry file = filesById.computeIfAbsent(sourceFileId, id -> fileEntryService.getByIdAndUser(id, user));
                resource = documentExtractionService.loadPdfPageRangeResource(file, startPage, endPage);
            }
            chunks.add(new FlashcardChunk(
                sourceFileId, sourceFilename, chunkIndex, startPage, endPage, text, resource
            ));
        }
        return List.copyOf(chunks);
    }

    private static String sanitizeSnapshotField(String value) {
        if (value == null) return "";
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String encodeSnapshotText(String value) {
        if (value == null || value.isEmpty()) return "";
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeSnapshotText(String value) {
        if (value == null || value.isEmpty()) return "";
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
