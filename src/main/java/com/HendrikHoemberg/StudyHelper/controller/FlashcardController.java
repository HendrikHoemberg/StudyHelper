package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FileStorageService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@Controller
public class FlashcardController {

    private final FlashcardService flashcardService;
    private final DeckService deckService;
    private final UserService userService;
    private final FileStorageService fileStorageService;

    public FlashcardController(FlashcardService flashcardService, DeckService deckService,
                               UserService userService, FileStorageService fileStorageService) {
        this.flashcardService = flashcardService;
        this.deckService = deckService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/decks/{deckId}/flashcards/new")
    public String newFlashcardModal(@PathVariable Long deckId, Model model) {
        model.addAttribute("deckId", deckId);
        model.addAttribute("action", "/decks/" + deckId + "/flashcards");
        model.addAttribute("title", "New Flashcard");
        return "fragments/flashcard-form :: flashcardModal";
    }

    @PostMapping(value = "/decks/{deckId}/flashcards", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public String createFlashcard(@PathVariable Long deckId,
                                  @RequestParam(defaultValue = "") String frontText,
                                  @RequestParam(defaultValue = "") String backText,
                                  @RequestParam(required = false) MultipartFile frontImage,
                                  @RequestParam(required = false) MultipartFile backImage,
                                  Model model, Principal principal,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        flashcardService.createFlashcard(frontText, backText, deckId, user, frontImage, backImage);

        if (hxRequest != null) {
            Deck deck = deckService.getDeck(deckId, user);
            model.addAttribute("deck", deck);
            model.addAttribute("flashcards", deck.getFlashcards());
            return "fragments/deck :: flashcardList";
        }
        return "redirect:/decks/" + deckId;
    }

    @GetMapping("/flashcards/{id}/edit")
    public String editFlashcardModal(@PathVariable Long id, Model model, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        Flashcard flashcard = flashcardService.getFlashcardForUser(id, user);
        model.addAttribute("flashcard", flashcard);
        model.addAttribute("deckId", flashcard.getDeck().getId());
        model.addAttribute("action", "/flashcards/" + id + "/edit");
        model.addAttribute("title", "Edit Flashcard");
        if (flashcard.getFrontImageFilename() != null) {
            model.addAttribute("frontImageUrl", "/flashcards/" + id + "/images/front");
        }
        if (flashcard.getBackImageFilename() != null) {
            model.addAttribute("backImageUrl", "/flashcards/" + id + "/images/back");
        }
        return "fragments/flashcard-form :: flashcardModal";
    }

    @PostMapping(value = "/flashcards/{id}/edit", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public String editFlashcard(@PathVariable Long id,
                                @RequestParam(defaultValue = "") String frontText,
                                @RequestParam(defaultValue = "") String backText,
                                @RequestParam(required = false) MultipartFile frontImage,
                                @RequestParam(defaultValue = "false") boolean removeFrontImage,
                                @RequestParam(required = false) MultipartFile backImage,
                                @RequestParam(defaultValue = "false") boolean removeBackImage,
                                @RequestParam Long deckId,
                                Model model, Principal principal,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        flashcardService.updateFlashcard(id, frontText, backText, principal.getName(),
                frontImage, removeFrontImage, backImage, removeBackImage);

        if (hxRequest != null) {
            Deck deck = deckService.getDeck(deckId, user);
            model.addAttribute("deck", deck);
            model.addAttribute("flashcards", deck.getFlashcards());
            return "fragments/deck :: flashcardList";
        }
        return "redirect:/decks/" + deckId;
    }

    @PostMapping("/flashcards/{id}/delete")
    public String deleteFlashcard(@PathVariable Long id,
                                  Model model, Principal principal,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        Long deckId = flashcardService.deleteFlashcard(id, principal.getName());

        if (hxRequest != null) {
            Deck deck = deckService.getDeck(deckId, user);
            model.addAttribute("deck", deck);
            model.addAttribute("flashcards", deck.getFlashcards());
            return "fragments/deck :: flashcardList";
        }
        return "redirect:/decks/" + deckId;
    }

    @GetMapping("/flashcards/{id}/images/{side}")
    public ResponseEntity<Resource> serveImage(@PathVariable Long id,
                                               @PathVariable String side,
                                               Principal principal) {
        User user = userService.getByUsername(principal.getName());
        Flashcard card = flashcardService.getFlashcardForUser(id, user);
        String filename = "front".equals(side) ? card.getFrontImageFilename() : card.getBackImageFilename();
        if (filename == null) return ResponseEntity.notFound().build();

        Resource resource = fileStorageService.load(filename);
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "";
        MediaType mediaType = switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }
}
