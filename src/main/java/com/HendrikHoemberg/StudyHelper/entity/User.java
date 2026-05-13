package com.HendrikHoemberg.StudyHelper.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    public static final long DEFAULT_STORAGE_QUOTA_BYTES = 1_073_741_824L;
    public static final int DEFAULT_DAILY_AI_REQUEST_LIMIT = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private long storageQuotaBytes = DEFAULT_STORAGE_QUOTA_BYTES;

    @Column(nullable = false)
    private int dailyAiRequestLimit = DEFAULT_DAILY_AI_REQUEST_LIMIT;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Folder> folders = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (role == null) {
            role = UserRole.USER;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
