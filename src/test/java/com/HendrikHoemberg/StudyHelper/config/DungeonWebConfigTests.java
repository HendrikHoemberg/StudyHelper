package com.HendrikHoemberg.StudyHelper.config;

import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.util.HttpSessionMutexListener;
import org.springframework.web.util.WebUtils;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonWebConfigTests {

    @Test
    void registersHttpSessionMutexListener() {
        ServletListenerRegistrationBean<HttpSessionMutexListener> bean =
            new DungeonWebConfig().sessionMutexListener();
        assertThat(bean.getListener()).isInstanceOf(HttpSessionMutexListener.class);
    }

    @Test
    void mutexListenerYieldsStableDedicatedMutex() {
        MockHttpSession session = new MockHttpSession();
        HttpSessionMutexListener listener = new HttpSessionMutexListener();

        assertThat(WebUtils.getSessionMutex(session)).isSameAs(session);

        listener.sessionCreated(new HttpSessionEvent(session));

        Object mutex1 = WebUtils.getSessionMutex(session);
        Object mutex2 = WebUtils.getSessionMutex(session);
        assertThat(mutex1).isNotNull();
        assertThat(mutex1).isSameAs(mutex2);
        assertThat(mutex1).isNotSameAs(session);
    }
}
