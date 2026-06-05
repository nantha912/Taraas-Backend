package com.LocalService.lsp.config;

import com.LocalService.lsp.security.JwtAuthenticationFilter;
import com.LocalService.lsp.security.RateLimitingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(
        securedEnabled = true,
        jsr250Enabled = true,
        prePostEnabled = true
)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter) throws Exception {
        http
                // 1. Apply CORS configuration first in the chain
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Disable CSRF for stateless REST APIs to prevent 403 on POST/PUT
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Configure session management to be stateless
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. Add security headers for HTTPS/SSL (basic configuration)
                .headers(headers -> headers.frameOptions().deny())

                // 5. Add JWT and rate limiting filters
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 6. Set up authorization rules
                .authorizeHttpRequests(auth -> auth

                        // Public endpoints - no authentication required
                        .requestMatchers("/api/auth/otp/**").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/verify-otp").permitAll()
                        .requestMatchers("/api/customers/register").permitAll()
                        .requestMatchers("/api/customers/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/providers/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/providers/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/customers/customer/**").permitAll()
                        .requestMatchers("/api/customers/health").permitAll()
                        .requestMatchers("/api/customers/password/reset").permitAll()

                        // Admin endpoints - manual role check in controller
                        .requestMatchers("/api/admin/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/offers/**").permitAll()

                        // Protected endpoints - any authenticated user
                        .requestMatchers(HttpMethod.POST, "/api/offers/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/offers/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/offers/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/customers/*/profile-photo").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/customers/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/customers/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/providers/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/providers/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/providers/**").authenticated()
                        .requestMatchers("/api/transactions/**").permitAll()
                        .requestMatchers("/api/payments/**").authenticated()
                        .requestMatchers("/api/reviews/**").authenticated()
                        .requestMatchers("/api/statements/**").authenticated()
                        .requestMatchers("/api/insights/**").authenticated()

                        // Any other requests must be authenticated
                        .anyRequest().authenticated()
                )
                // 7. Handle unauthorized requests with 401 and access denied with 403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"Full authentication is required to access this resource\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"Access denied. You do not have permission to access this resource.\"}");
                        })
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed Origins
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://taraas.com",
                "https://www.taraas.com",
                "https://api.taraas.com",
                "https://qa-api.taraas.com",
                "https://qa.taraas.com"
        ));

        // Explicitly define allowed methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Headers to support Authorization and custom headers
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Cache-Control",
                "X-Requested-With",
                "Accept",
                "Origin",
                "X-API-KEY"
        ));

        configuration.setAllowCredentials(true);

        // Apply to ALL paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}