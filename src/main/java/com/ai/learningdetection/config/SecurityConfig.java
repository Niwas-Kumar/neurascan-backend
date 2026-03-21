package com.ai.learningdetection.config;

import com.ai.learningdetection.security.AppUserDetailsService;
import com.ai.learningdetection.security.JwtAuthenticationEntryPoint;
import com.ai.learningdetection.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final AppUserDetailsService userDetailsService;

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Public health check endpoints (for deployment platforms like Render)
                .requestMatchers("/actuator/health", "/health", "/ping").permitAll()

                // Public auth endpoints
                .requestMatchers("/api/auth/**", "/auth/**").permitAll()

                // Public quiz attempt endpoints (accessed via email link token)
                .requestMatchers("/api/quizzes/public/**", "/quizzes/public/**").permitAll()
                .requestMatchers("/api/quiz-attempt/**", "/quiz-attempt/**").permitAll()

                // Student management — TEACHER only
                .requestMatchers(HttpMethod.GET,    "/api/students/**", "/students/**").hasRole("TEACHER")
                .requestMatchers(HttpMethod.POST,   "/api/students/**", "/students/**").hasRole("TEACHER")
                .requestMatchers(HttpMethod.PUT,    "/api/students/**", "/students/**").hasRole("TEACHER")
                .requestMatchers(HttpMethod.DELETE, "/api/students/**", "/students/**").hasRole("TEACHER")

                // Upload & teacher dashboard — TEACHER only
                .requestMatchers("/api/analysis/upload", "/analysis/upload").hasRole("TEACHER")
                .requestMatchers("/api/analysis/reports", "/analysis/reports").hasRole("TEACHER")
                .requestMatchers("/api/analysis/dashboard", "/analysis/dashboard").hasRole("TEACHER")

                // Parent quiz access - MUST be before the general /api/quizzes/** rule
                .requestMatchers("/api/quizzes/student/*/responses", "/quizzes/student/*/responses").hasAnyRole("PARENT", "TEACHER")
                .requestMatchers("/api/quizzes/student/*/attempts", "/quizzes/student/*/attempts").hasAnyRole("PARENT", "TEACHER")
                .requestMatchers("/api/quizzes/student/*/all-attempts", "/quizzes/student/*/all-attempts").hasAnyRole("PARENT", "TEACHER")
                .requestMatchers("/api/quiz-attempts/student/**", "/quiz-attempts/student/**").hasAnyRole("PARENT", "TEACHER")

                // Quiz management — TEACHER only (general rule after specific parent endpoints)
                .requestMatchers("/api/quizzes/**", "/quizzes/**").hasRole("TEACHER")

                // Parent report access — PARENT only
                .requestMatchers("/api/analysis/student-report/**", "/analysis/student-report/**").hasRole("PARENT")
                .requestMatchers("/api/analysis/progress/**", "/analysis/progress/**").hasRole("PARENT")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration =
                new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(java.util.Arrays.asList(corsAllowedOrigins.split(",")));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source =
                new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
