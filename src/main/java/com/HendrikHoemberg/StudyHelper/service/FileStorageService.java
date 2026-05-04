package com.HendrikHoemberg.StudyHelper.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${file.upload.dir}") String uploadDir) throws IOException {
        this.uploadDir = Paths.get(uploadDir);
        Files.createDirectories(this.uploadDir);
    }

    public String store(MultipartFile file) throws IOException {
        String storedFilename = UUID.randomUUID() + extension(file.getOriginalFilename());
        Files.copy(file.getInputStream(), uploadDir.resolve(storedFilename), StandardCopyOption.REPLACE_EXISTING);
        return storedFilename;
    }

    public Resource load(String storedFilename) {
        try {
            Path path = uploadDir.resolve(storedFilename).normalize();
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists() && resource.isReadable()) return resource;
            throw new RuntimeException("File not readable: " + storedFilename);
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found: " + storedFilename, e);
        }
    }

    public Path resolvePath(String storedFilename) {
        return uploadDir.resolve(storedFilename).normalize();
    }

    public void replaceContents(String storedFilename, MultipartFile file) throws IOException {
        Files.copy(file.getInputStream(), uploadDir.resolve(storedFilename), StandardCopyOption.REPLACE_EXISTING);
    }

    public void delete(String storedFilename) throws IOException {
        Files.deleteIfExists(uploadDir.resolve(storedFilename));
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
