package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record Revenant(
    String encounterId,
    String originRoomId,
    String currentRoomId
) implements Serializable {

    public Revenant withCurrentRoomId(String roomId) {
        return new Revenant(encounterId, originRoomId, roomId);
    }
}
