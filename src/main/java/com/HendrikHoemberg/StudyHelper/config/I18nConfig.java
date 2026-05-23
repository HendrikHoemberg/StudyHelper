package com.HendrikHoemberg.StudyHelper.config;

import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        ms.setDefaultLocale(Locale.ENGLISH);
        ms.setUseCodeAsDefaultMessage(true);
        return ms;
    }

    @Bean
    public LocaleResolver localeResolver(UserRepository userRepository) {
        return new UserLocaleResolver(userRepository);
    }
}
