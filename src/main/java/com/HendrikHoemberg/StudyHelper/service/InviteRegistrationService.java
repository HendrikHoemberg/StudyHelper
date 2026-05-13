package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteRegistrationService {

    private final UserService userService;
    private final RegistrationCodeService registrationCodeService;

    public InviteRegistrationService(UserService userService, RegistrationCodeService registrationCodeService) {
        this.userService = userService;
        this.registrationCodeService = registrationCodeService;
    }

    @Transactional
    public User register(String code, String username, String password) {
        User user = userService.registerUser(username, password);
        registrationCodeService.consume(code, user);
        return user;
    }
}
