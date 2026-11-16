package com.suriya.payments.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final ApiKeyAuthenticationFilter apiKeyFilter;
  private final ObjectMapper objectMapper;

  public SecurityConfig(ApiKeyAuthenticationFilter apiKeyFilter, ObjectMapper objectMapper) {
    this.apiKeyFilter = apiKeyFilter;
    this.objectMapper = objectMapper;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public infrastructure & docs
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/prometheus",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**")
                    .permitAll()
                    // Public signed webhook (cryptographically authenticated via HMAC-SHA256)
                    .requestMatchers(HttpMethod.POST, "/v1/webhooks/psp")
                    .permitAll()
                    // Administrative operations
                    .requestMatchers(HttpMethod.POST, "/v1/accounts")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/v1/accounts/*/fund")
                    .hasRole("ADMIN")
                    .requestMatchers("/v1/reconciliation/**", "/v1/ledger/**")
                    .hasRole("ADMIN")
                    // Authenticated operations (Merchant or Admin)
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    (request, response, authException) -> {
                      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                      Map<String, Object> error =
                          Map.of(
                              "type", "about:blank",
                              "title", "Unauthorized",
                              "status", 401,
                              "detail", "Authentication required. Provide a valid 'X-API-Key' header.");
                      response.getWriter().write(objectMapper.writeValueAsString(error));
                    }))
        .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
