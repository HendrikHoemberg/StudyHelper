package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;

import java.util.List;

public record FolderView(
    Folder folder,
    List<Folder> subFolders,
    List<Deck> decks,
    List<FileEntry> files,
    List<Folder> breadcrumb,
    int totalCardCount
) {}
