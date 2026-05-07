package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.FolderView;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;

@Controller
public class DeckController {

    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final FolderService folderService;
    private final UserService userService;

    public DeckController(DeckService deckService, FlashcardService flashcardService,
                          FolderService folderService, UserService userService) {
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.folderService = folderService;
        this.userService = userService;
    }

    @GetMapping("/folders/{folderId}/decks/new")
    public String newDeckModal(@PathVariable Long folderId, Model model) {
        model.addAttribute("action", "/folders/" + folderId + "/decks");
        model.addAttribute("title", "New Deck");
        return "fragments/deck-form :: deckModal";
    }

    @PostMapping("/folders/{folderId}/decks")
    public String createDeck(@PathVariable Long folderId,
                             @RequestParam String name,
                             Principal principal,
                             Model model,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                             HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());
        Deck deck = deckService.createDeck(name, folderId, user);

        if (hxRequest != null) {
            response.setHeader("HX-Push-Url", "/decks/" + deck.getId());
            model.addAttribute("deck", deck);
            model.addAttribute("flashcards", deck.getFlashcards());
            model.addAttribute("username", principal.getName());
            model.addAttribute("refreshSidebar", true);
            model.addAttribute("sidebarTree", folderService.getSidebarTree(user, folderId));
            return "fragments/deck :: deckDetail";
        }
        return "redirect:/decks/" + deck.getId();
    }

    @GetMapping("/decks/{id}")
    public String viewDeck(@PathVariable Long id, Model model, Principal principal,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        Deck deck = deckService.getDeck(id, user);
        model.addAttribute("deck", deck);
        model.addAttribute("flashcards", deck.getFlashcards());
        model.addAttribute("username", principal.getName());

        if (hxRequest != null) {
            model.addAttribute("refreshSidebar", true);
            return "fragments/deck :: deckDetail";
        }
        return "deck-page";
    }

    @GetMapping("/decks/{id}/rename")
    public String renameDeckModal(@PathVariable Long id, Model model, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        Deck deck = deckService.getDeck(id, user);
        model.addAttribute("deck", deck);
        model.addAttribute("action", "/decks/" + id + "/rename");
        model.addAttribute("title", "Rename Deck");
        return "fragments/deck-form :: deckModal";
    }

    @PostMapping("/decks/{id}/rename")
    public String renameDeck(@PathVariable Long id,
                             @RequestParam String name,
                             Principal principal,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                             @RequestHeader(value = "HX-Current-URL", required = false) String currentUrl) {
        User user = userService.getByUsername(principal.getName());
        Deck deck = deckService.renameDeck(id, name, user);

        if (hxRequest != null && currentUrl != null) {
            return "redirect:" + currentUrl;
        }
        if (hxRequest != null) {
            return "redirect:/decks/" + id;
        }
        return "redirect:/decks/" + id;
    }

    @PostMapping("/decks/{id}/delete")
    public String deleteDeck(@PathVariable Long id,
                             Principal principal,
                             Model model,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                             HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());
        Long folderId = deckService.deleteDeck(id, user);

        if (hxRequest != null) {
            response.setHeader("HX-Push-Url", "/folders/" + folderId);
            FolderView view = folderService.getFolderView(folderId, user, null, "asc");
            model.addAttribute("view", view);
            model.addAttribute("username", principal.getName());
            model.addAttribute("refreshSidebar", true);
            model.addAttribute("sidebarTree", folderService.getSidebarTree(user, folderId));
            return "fragments/folder-detail :: folderDetail";
        }
        return "redirect:/folders/" + folderId;
    }
}
