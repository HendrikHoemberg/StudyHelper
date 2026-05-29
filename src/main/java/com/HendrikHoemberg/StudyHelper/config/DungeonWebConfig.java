package com.HendrikHoemberg.StudyHelper.config;

import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.HttpSessionMutexListener;

/**
 * Registers {@link HttpSessionMutexListener} so {@code WebUtils.getSessionMutex(session)}
 * returns a stable, dedicated lock object for each session's lifetime. The dungeon
 * controllers synchronize on that mutex to serialize per-session state mutations.
 */
@Configuration
public class DungeonWebConfig {

    @Bean
    public ServletListenerRegistrationBean<HttpSessionMutexListener> sessionMutexListener() {
        return new ServletListenerRegistrationBean<>(new HttpSessionMutexListener());
    }
}
