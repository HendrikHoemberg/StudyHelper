package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonShopOfferCompatTests {

    // Mirrors SavedSessionService's mapper: tolerate fields that no longer exist on the record.
    private final ObjectMapper mapper = new ObjectMapper().rebuild()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    @Test
    void legacyShopOfferJson_withRemovedConsumable_deserializes() {
        String legacyJson = """
            {"entries":[{"relic":"LUCKY_CHARM","price":75},{"relic":"MAP_SENSE","price":150}],
             "consumable":{"i18nKey":"dungeon.shop.consumable.heal","price":30,"healAmount":2}}
            """;

        ShopOffer offer = mapper.readValue(legacyJson, ShopOffer.class);

        assertThat(offer.entries()).hasSize(2);
        assertThat(offer.entries().get(0).relic()).isEqualTo(RelicId.LUCKY_CHARM);
        assertThat(offer.entries().get(1).price()).isEqualTo(150);
    }
}
