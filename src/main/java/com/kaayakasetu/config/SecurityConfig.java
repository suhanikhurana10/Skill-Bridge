package com.kaayakasetu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaayakasetu.auth.JwtAuthenticationFilter;
import com.kaayakasetu.auth.JwtService;
import com.kaayakasetu.auth.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Which URLs are public, which need a login, and which need a particular role.
 * The rules are checked top to bottom; the first match wins.
 * Rules about WHOSE data you may touch (a worker edits only their own profile, an employer only their own jobs)
 * are enforced in the controllers/services, because they depend on the row being accessed.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, UserRepository users,
                                                   ObjectMapper objectMapper) throws Exception {
        http
            // CSRF protects cookie-based sessions. We use no cookies (token goes in a header), so it is switched off.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            // STATELESS: the server remembers nobody between requests; every request must carry its token
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setHeader("WWW-Authenticate", "Bearer");
                    writeJson(res, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                            "Authentication required: send 'Authorization: Bearer <token>' (token missing, invalid or expired)");
                })
                .accessDeniedHandler((req, res, ex) -> writeJson(res, objectMapper,
                        HttpServletResponse.SC_FORBIDDEN, "You do not have permission to do this")))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // dashboard files, API docs, health check
                .requestMatchers("/", "/index.html", "/static/**", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/health", "/docs", "/docs/**", "/openapi.json", "/openapi.json/**",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                // register / login are public, everything else under /auth needs a token
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/**").authenticated()
                // role-protected writes (must come BEFORE the public GET rules below)
                .requestMatchers(HttpMethod.GET, "/api/v1/jobs/mine").hasRole("EMPLOYER")
                .requestMatchers(HttpMethod.POST, "/api/v1/workers", "/api/v1/workers/").hasRole("WORKER")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/workers/**").hasRole("WORKER")
                .requestMatchers(HttpMethod.POST, "/api/v1/jobs", "/api/v1/jobs/").hasRole("EMPLOYER")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/jobs/**").hasRole("EMPLOYER")
                // public browsing: job board, worker directory, feeds, calculators
                .requestMatchers(HttpMethod.GET, "/api/v1/workers/**", "/api/v1/jobs/**", "/api/v1/feeds/**").permitAll()
                .requestMatchers("/api/v1/location/**", "/api/v1/wage/**").permitAll()
                // USSD/IVR webhooks are called by the telephone network, which cannot send our JWT.
                // In production protect them with the provider's request signature or an IP allow-list.
                .requestMatchers("/api/v1/telecom/**").permitAll()
                // matching and contacts need a logged-in user (controllers then check ownership)
                .requestMatchers("/api/v1/match/**", "/api/v1/contact/**").authenticated()
                // anything not listed above is closed by default
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeJson(HttpServletResponse res, ObjectMapper om, int status, String detail) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        om.writeValue(res.getWriter(), Map.of("detail", detail));
    }
}
