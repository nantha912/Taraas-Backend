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
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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

    private final CardScraperFilter cardScraperFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public SecurityConfig(CardScraperFilter cardScraperFilter) {
        this.cardScraperFilter = cardScraperFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter) throws Exception {
        http
                // 1. Force Spring Security to use your centralized global dynamic WebMvcConfigurer settings
                .cors(cors -> cors.configure(http))

                // 2. Disable CSRF for stateless REST APIs to prevent 403 on POST/PUT
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Configure session management to be stateless
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. Add security headers for HTTPS/SSL (basic configuration)
                .headers(headers -> headers.frameOptions(frame -> frame.deny()))

                // 5. Add JWT and rate limiting filters
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(cardScraperFilter, AuthorizationFilter.class)

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
                        .requestMatchers("/provider/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/providers/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/customers/customer/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/**").permitAll()
                        .requestMatchers("/api/customers/health").permitAll()
                        .requestMatchers("/api/customers/password/reset").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/promoters/admin/export-payouts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/promoters/admin/settle/*").permitAll()
                        .requestMatchers("/api/promoters/config").permitAll()
                        .requestMatchers("/api/promoters/validate").permitAll()
                        .requestMatchers("/api/promoters/test-list").permitAll()
                        

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
                        .requestMatchers("/api/promoters/dashboard/summary").authenticated()
                        .requestMatchers("/api/promoters/signup").authenticated()

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
}