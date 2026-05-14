package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class FolderRepositoryGraphTests {

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void rootsWithSubFoldersAndBatchFetchedCollections_traversesTreeInBoundedQueries() {
        User user = new User();
        user.setUsername("graph-user");
        user.setPassword("password");
        entityManager.persist(user);

        for (int rootIndex = 0; rootIndex < 5; rootIndex++) {
            Folder root = folder("Root " + rootIndex, user, null);
            entityManager.persist(root);
            persistFile("root-" + rootIndex + ".pdf", root, user);

            for (int subIndex = 0; subIndex < 3; subIndex++) {
                Folder subFolder = folder("Sub " + rootIndex + "-" + subIndex, user, root);
                entityManager.persist(subFolder);
                persistFile("sub-" + rootIndex + "-" + subIndex + ".pdf", subFolder, user);

                Deck deck = new Deck();
                deck.setName("Deck " + rootIndex + "-" + subIndex);
                deck.setUser(user);
                deck.setFolder(subFolder);
                entityManager.persist(deck);
                persistFlashcard(deck, "front " + rootIndex + "-" + subIndex, "back");
            }
        }

        entityManager.flush();
        entityManager.clear();

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        var roots = folderRepository.findRootsWithSubFoldersByUser(user);

        assertThat(roots).hasSize(5);
        assertThat(roots)
            .allSatisfy(root -> assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(root, "subFolders"))
                .isTrue());

        int deckCount = 0;
        int fileCount = 0;
        int flashcardCount = 0;
        for (Folder root : roots) {
            deckCount += root.getDecks().size();
            fileCount += root.getFiles().size();
            for (Folder subFolder : root.getSubFolders()) {
                deckCount += subFolder.getDecks().size();
                fileCount += subFolder.getFiles().size();
                for (Deck deck : subFolder.getDecks()) {
                    flashcardCount += deck.getFlashcards().size();
                }
            }
        }

        assertThat(deckCount).isEqualTo(15);
        assertThat(fileCount).isEqualTo(20);
        assertThat(flashcardCount).isEqualTo(15);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(6);
    }

    @Test
    void findByIdAndUserWithSubFolders_loadsSubFoldersImmediately() {
        User user = new User();
        user.setUsername("single-folder-user");
        user.setPassword("password");
        entityManager.persist(user);

        Folder root = folder("Root", user, null);
        entityManager.persist(root);

        Folder subFolder = folder("Sub", user, root);
        entityManager.persist(subFolder);

        Deck deck = new Deck();
        deck.setName("Deck");
        deck.setUser(user);
        deck.setFolder(subFolder);
        entityManager.persist(deck);

        entityManager.flush();
        entityManager.clear();

        Folder found = folderRepository.findByIdAndUserWithSubFolders(root.getId(), user).orElseThrow();

        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(found, "subFolders"))
            .isTrue();
        assertThat(found.getSubFolders()).hasSize(1);
        assertThat(found.getSubFolders().getFirst().getDecks()).hasSize(1);
    }

    private Folder folder(String name, User user, Folder parent) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setUser(user);
        folder.setParentFolder(parent);
        return folder;
    }

    private void persistFile(String name, Folder folder, User user) {
        FileEntry file = new FileEntry();
        file.setOriginalFilename(name);
        file.setStoredFilename("stored-" + name);
        file.setMimeType("application/pdf");
        file.setFileSizeBytes(128L);
        file.setFolder(folder);
        file.setUser(user);
        entityManager.persist(file);
    }

    private void persistFlashcard(Deck deck, String front, String back) {
        Flashcard flashcard = new Flashcard();
        flashcard.setDeck(deck);
        flashcard.setFrontText(front);
        flashcard.setBackText(back);
        entityManager.persist(flashcard);
    }
}
