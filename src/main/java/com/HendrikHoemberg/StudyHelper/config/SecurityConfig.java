package com.HendrikHoemberg.StudyHelper.config;

import com.HendrikHoemberg.StudyHelper.security.DisabledUserFilter;
import com.HendrikHoemberg.StudyHelper.security.LoginAttemptService;
import com.HendrikHoemberg.StudyHelper.security.LoginRateLimitingFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectProvider<DisabledUserFilter> disabledUserFilterProvider;
    private final LoginAttemptService loginAttemptService;

    public SecurityConfig(ObjectProvider<DisabledUserFilter> disabledUserFilterProvider,
                          LoginAttemptService loginAttemptService) {
        this.disabledUserFilterProvider = disabledUserFilterProvider;
        this.loginAttemptService = loginAttemptService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
            new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setDefaultTargetUrl("/");
        successHandler.setAlwaysUseDefaultTargetUrl(true);

        SimpleUrlAuthenticationFailureHandler failureHandler =
            new SimpleUrlAuthenticationFailureHandler("/login?error");

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**", "/js/**", "/manifest.webmanifest", "/service-worker.js", "/favicon.ico", "/icons/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
                .successHandler((request, response, authentication) -> {
                    loginAttemptService.loginSucceeded(
                        LoginAttemptService.extractClientIP(request));
                    successHandler.onAuthenticationSuccess(request, response, authentication);
                })
                .failureHandler((request, response, exception) -> {
                    loginAttemptService.loginFailed(
                        LoginAttemptService.extractClientIP(request));
                    failureHandler.onAuthenticationFailure(request, response, exception);
                })
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );
        http.addFilterBefore(new LoginRateLimitingFilter(loginAttemptService),
            UsernamePasswordAuthenticationFilter.class);
        DisabledUserFilter disabledUserFilter = disabledUserFilterProvider.getIfAvailable();
        if (disabledUserFilter != null) {
            http.addFilterAfter(disabledUserFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
