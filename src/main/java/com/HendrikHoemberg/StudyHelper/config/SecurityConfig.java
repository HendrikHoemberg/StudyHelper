package com.HendrikHoemberg.StudyHelper.config;

import com.HendrikHoemberg.StudyHelper.security.DisabledUserFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectProvider<DisabledUserFilter> disabledUserFilterProvider;

    public SecurityConfig(ObjectProvider<DisabledUserFilter> disabledUserFilterProvider) {
        this.disabledUserFilterProvider = disabledUserFilterProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**", "/js/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );
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
