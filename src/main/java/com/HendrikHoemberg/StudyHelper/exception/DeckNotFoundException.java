package com.HendrikHoemberg.StudyHelper.exception;

public class DeckNotFoundException extends RuntimeException {
    private final String deckName;
    public DeckNotFoundException(String deckName) {
        super("Deck not found: " + deckName);
        this.deckName = deckName;
    }
    public String getDeckName() { return deckName; }
}
