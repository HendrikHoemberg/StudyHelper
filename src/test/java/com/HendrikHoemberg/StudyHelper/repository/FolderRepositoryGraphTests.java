package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
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
    void rootsWithSubtree_fetchesInBoundedQueries() {
        User user = new User();
        user.setUsername("graph-user");
        user.setPassword("password");
        entityManager.persist(user);

        for (int rootIndex = 0; rootIndex < 5; rootIndex++) {
            Folder root = folder("Root " + rootIndex, user, null);
            entityManager.persist(root);

            for (int subIndex = 0; subIndex < 3; subIndex++) {
                Folder subFolder = folder("Sub " + rootIndex + "-" + subIndex, user, root);
                entityManager.persist(subFolder);

                Deck deck = new Deck();
                deck.setName("Deck " + rootIndex + "-" + subIndex);
                deck.setUser(user);
                deck.setFolder(subFolder);
                entityManager.persist(deck);
            }
        }

        entityManager.flush();
        entityManager.clear();

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        var roots = folderRepository.findRootsWithSubtreeByUser(user);

        assertThat(roots).hasSize(5);
        assertThat(roots)
            .allSatisfy(root -> assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(root, "subFolders"))
                .isTrue());

        int deckCount = roots.stream()
            .flatMap(root -> root.getSubFolders().stream())
            .mapToInt(subFolder -> subFolder.getDecks().size())
            .sum();

        assertThat(deckCount).isEqualTo(15);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    @Test
    void findByIdAndUserWithSubtree_loadsSubFoldersImmediately() {
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

        Folder found = folderRepository.findByIdAndUserWithSubtree(root.getId(), user).orElseThrow();

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
}
