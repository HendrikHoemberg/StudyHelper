package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.config.AppDefaults;
import com.HendrikHoemberg.StudyHelper.dto.SavedSessionSummary;
import com.HendrikHoemberg.StudyHelper.dto.SidebarFolderNode;
import com.HendrikHoemberg.StudyHelper.dto.UserQuotaSummary;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.entity.UserRole;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final FolderService folderService;
    private final DeckService deckService;
    private final UserService userService;
    private final AiRequestQuotaService aiRequestQuotaService;
    private final StorageQuotaService storageQuotaService;
    private final SavedSessionService savedSessionService;
    private final HttpServletRequest request;
    private final MessageSource messageSource;

    public GlobalControllerAdvice(FolderService folderService,
                                   DeckService deckService,
                                   UserService userService,
                                   AiRequestQuotaService aiRequestQuotaService,
                                   StorageQuotaService storageQuotaService,
                                   SavedSessionService savedSessionService,
                                   HttpServletRequest request,
                                   MessageSource messageSource) {
        this.folderService = folderService;
        this.deckService = deckService;
        this.userService = userService;
        this.aiRequestQuotaService = aiRequestQuotaService;
        this.storageQuotaService = storageQuotaService;
        this.savedSessionService = savedSessionService;
        this.request = request;
        this.messageSource = messageSource;
    }

    @ModelAttribute("sidebarTree")
    public List<SidebarFolderNode> addSidebarTree(Principal principal) {
        if (principal == null) {
            return null;
        }
        User user = userService.getByUsername(principal.getName());
        
        Long activeFolderId = null;
        String uri = request.getRequestURI();
        
        // Folders: /folders/{id}
        Pattern folderPattern = Pattern.compile("/folders/(\\d+)");
        Matcher folderMatcher = folderPattern.matcher(uri);
        if (folderMatcher.find()) {
            activeFolderId = Long.parseLong(folderMatcher.group(1));
        } else {
            // Decks: /decks/{id}
            Pattern deckPattern = Pattern.compile("/decks/(\\d+)");
            Matcher deckMatcher = deckPattern.matcher(uri);
            if (deckMatcher.find()) {
                Long deckId = Long.parseLong(deckMatcher.group(1));
                activeFolderId = deckService.findFolderIdByDeckId(deckId, user);
            }
        }

        return folderService.getSidebarTree(user, activeFolderId);
    }

    @ModelAttribute("isAdmin")
    public boolean addIsAdmin(Principal principal) {
        if (principal == null) {
            return false;
        }
        User user = userService.getByUsername(principal.getName());
        return user.getRole() == UserRole.ADMIN;
    }

    @ModelAttribute("defaultColorHex")
    public String addDefaultColorHex() {
        return AppDefaults.DEFAULT_COLOR_HEX;
    }

    @ModelAttribute("currentLocale")
    public String addCurrentLocale() {
        return LocaleContextHolder.getLocale().getLanguage();
    }

    @ModelAttribute("userQuota")
    public UserQuotaSummary addUserQuota(Principal principal) {
        if (principal == null) {
            return null;
        }
        User user = userService.getByUsername(principal.getName());
        return new UserQuotaSummary(
            storageQuotaService.usedBytes(user),
            user.getStorageQuotaBytes(),
            aiRequestQuotaService.todayUsed(user),
            user.getDailyAiRequestLimit()
        );
    }

    @ModelAttribute("savedSession")
    public SavedSessionSummary addSavedSession(Principal principal) {
        if (principal == null) return null;
        User user = userService.getByUsername(principal.getName());
        return savedSessionService.findForUser(user).orElse(null);
    }

    @ModelAttribute("i18nJs")
    public Map<String, String> addI18nJs() {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, String> result = new HashMap<>();
        addJsKeysForLocale("messages", Locale.ENGLISH, result);
        if (!Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
            addJsKeysForLocale("messages", locale, result);
        }
        return result;
    }

    private void addJsKeysForLocale(String basename, Locale locale, Map<String, String> target) {
        if (!(messageSource instanceof org.springframework.context.support.ReloadableResourceBundleMessageSource)) {
            return;
        }
        org.springframework.context.support.ReloadableResourceBundleMessageSource rms =
            (org.springframework.context.support.ReloadableResourceBundleMessageSource) messageSource;
        try {
            java.lang.reflect.Method m = org.springframework.context.support.ReloadableResourceBundleMessageSource.class
                .getDeclaredMethod("getMergedProperties", Locale.class);
            m.setAccessible(true);
            Object holder = m.invoke(rms, locale);
            java.lang.reflect.Method propsMethod = holder.getClass().getMethod("getProperties");
            propsMethod.setAccessible(true);
            java.util.Properties loaded = (java.util.Properties) propsMethod.invoke(holder);
            if (loaded != null) {
                loaded.stringPropertyNames().forEach(key -> {
                    if (key.startsWith("js.")) {
                        target.put(key.substring("js.".length()), loaded.getProperty(key));
                    }
                });
            }
        } catch (org.springframework.context.NoSuchMessageException ignored) {
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load messages for " + locale, ex);
        }
    }
}
