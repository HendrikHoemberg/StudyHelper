package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record RelicDefinition(
    RelicId id,
    String nameKey,
    String descKey,
    RelicPool pool
) implements Serializable {}
