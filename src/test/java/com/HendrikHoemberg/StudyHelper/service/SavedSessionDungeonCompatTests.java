package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.SavedSession;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.SavedSessionRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SavedSessionDungeonCompatTests {

    @Test
    void loadDungeon_withIncompatiblePayload_returnsEmptyAndSetsFlag() {
        SavedSessionRepository repo = mock(SavedSessionRepository.class);
        SavedSession session = new SavedSession();
        session.setType(SavedSessionType.DUNGEON);
        session.setPayload("{\"this\":\"is not a DungeonSessionState\"}");
        User user = new User();
        user.setId(7L);
        when(repo.findByUser(user)).thenReturn(Optional.of(session));

        SavedSessionService svc = new SavedSessionService(repo, null, new ObjectMapper(), null, null);
        Optional<DungeonSessionState> loaded = svc.loadDungeon(user);

        assertThat(loaded).isEmpty();
        assertThat(svc.consumeIncompatibleDiscardFlag(user)).isTrue();
        verify(repo).deleteByUser(user);
    }
}
