package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.entity.UserRole;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTests {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void createIfNotExists_AppliesDefaults() {
        when(userRepository.findByUsername("new-user")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.createIfNotExists("new-user", "password123");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("new-user");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(savedUser.getStorageQuotaBytes()).isEqualTo(User.DEFAULT_STORAGE_QUOTA_BYTES);
        assertThat(savedUser.getDailyAiRequestLimit()).isEqualTo(User.DEFAULT_DAILY_AI_REQUEST_LIMIT);
    }

    @Test
    void createIfNotExists_TrimsUsernameBeforeLookupAndSave() {
        when(userRepository.findByUsername("new-user")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        userService.createIfNotExists("  new-user  ", "password123");

        verify(userRepository, times(1)).findByUsername("new-user");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("new-user");
    }

    @Test
    void createIfNotExists_TooShortPassword_IsIgnored() {
        userService.createIfNotExists("new-user", "short");

        verify(userRepository, never()).findByUsername(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_HappyPath_TrimsValidatesEncodesAndSaves() {
        when(userRepository.existsByUsername("new-user")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.registerUser("  new-user  ", "password123");

        verify(userRepository, times(1)).existsByUsername("new-user");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("new-user");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(savedUser.getStorageQuotaBytes()).isEqualTo(User.DEFAULT_STORAGE_QUOTA_BYTES);
        assertThat(savedUser.getDailyAiRequestLimit()).isEqualTo(User.DEFAULT_DAILY_AI_REQUEST_LIMIT);
        assertThat(result).isSameAs(savedUser);
    }

    @Test
    void registerUser_DuplicateUsername_ThrowsAndDoesNotSave() {
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser("existing", "password123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Username already exists");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_WhitespaceOnlyPassword_ThrowsAndDoesNotSave() {
        assertThatThrownBy(() -> userService.registerUser("new-user", "        "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Password must be at least");

        verify(userRepository, never()).existsByUsername(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void promoteToAdminIfPresent_UpdatesRoleAndSaves() {
        User existing = new User();
        existing.setUsername("alice");
        existing.setRole(UserRole.USER);
        existing.setEnabled(false);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));

        userService.promoteToAdminIfPresent("alice");

        verify(userRepository, times(1)).save(existing);
        assertThat(existing.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(existing.isEnabled()).isTrue();
    }
}
