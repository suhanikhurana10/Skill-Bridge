package com.kaayakasetu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Kept separate from SecurityConfig so other beans (DatabaseInitializer, AuthService) can use it without a cycle. */
@Configuration
public class PasswordConfig {

    /** BCrypt = slow, salted hash. The same password gives a different hash each time, so leaked hashes are hard to crack. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
