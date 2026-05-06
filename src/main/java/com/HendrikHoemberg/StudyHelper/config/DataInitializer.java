package com.HendrikHoemberg.StudyHelper.config;

import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;

    @Value("${app.user1.username}")
    private String user1Name;

    @Value("${app.user1.password}")
    private String user1Password;

    @Value("${app.user2.username}")
    private String user2Name;

    @Value("${app.user2.password}")
    private String user2Password;

    public DataInitializer(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(String... args) throws Exception {
        userService.createIfNotExists(user1Name, user1Password);
        userService.createIfNotExists(user2Name, user2Password);
    }
}
