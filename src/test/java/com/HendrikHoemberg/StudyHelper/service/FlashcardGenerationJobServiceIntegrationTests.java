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
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
class FlashcardGenerationJobServiceIntegrationTests {

    @Autowired
    private FlashcardGenerationJobService service;

    @Autowired
    private FlashcardGenerationJobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AiFlashcardService aiFlashcardService;

    @MockitoBean
    private FlashcardGenerationPersistenceService persistenceService;

    @Test
    void runJob_persistsStatusProgressAndSavedDeckAcrossWorkerTransactions() throws Exception {
        User user = new User();
        user.setUsername("worker-persistence");
        user.setPassword("password");
        user = userRepository.save(user);

        FlashcardGenerationPlan plan = new FlashcardGenerationPlan(
            List.of(new FlashcardChunk("lecture.pdf", 1, 1, 2, "text", null)),
            1, 8, 12, 5, 15, FlashcardGenerationRisk.NORMAL
        );

        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setUser(user);
        job.setSourceFileIdsCsv("99");
        job.setDocumentMode(DocumentMode.TEXT);
        job.setDestination(FlashcardGenerationDestination.NEW_DECK);
        job.setNewDeckFolderId(10L);
        job.setNewDeckName("Generated");
        job.setEstimatedRequestCost(1);
        job.setChargedRequestCost(1);
        job.setChunkCount(1);
        job.setChunkPlanSnapshot(FlashcardGenerationJobService.serializePlanSnapshot(plan));
        job = jobRepository.saveAndFlush(job);

        when(aiFlashcardService.generateChunks(eq(plan.chunks()), eq(null), any(IntConsumer.class)))
            .thenAnswer(invocation -> {
                IntConsumer progress = invocation.getArgument(2);
                progress.accept(1);
                return List.of(new GeneratedFlashcard("Q", "A"));
            });
        Deck deck = new Deck();
        deck.setId(20L);
        when(persistenceService.saveGeneratedCards(eq(FlashcardGenerationDestination.NEW_DECK), eq(null), eq(10L), eq("Generated"), any(User.class), anyList()))
            .thenReturn(deck);

        service.runJob(job.getId());

        FlashcardGenerationJob persisted = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(FlashcardGenerationJobStatus.SUCCEEDED);
        assertThat(persisted.getCompletedChunkCount()).isEqualTo(1);
        assertThat(persisted.getSavedDeckId()).isEqualTo(20L);
        assertThat(persisted.getGeneratedCardCount()).isEqualTo(1);
        assertThat(persisted.getStartedAt()).isNotNull();
        assertThat(persisted.getFinishedAt()).isNotNull();
    }
}
