package com.suriya.payments.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  public static final String HEADER_API_KEY = "X-API-Key";

  private final String adminApiKey;
  private final Map<String, String> merchantApiKeys;

  public ApiKeyAuthenticationFilter(
      @Value("${security.api-keys.admin:admin-live-key-secret-999}") String adminApiKey) {
    this.adminApiKey = adminApiKey;
    this.merchantApiKeys =
        Map.of(
            "merchant-acme-key-123", "acme-store",
            "merchant-m1-key-456", "m1",
            "merchant-test-key", "test-merchant");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String apiKey = request.getHeader(HEADER_API_KEY);

    if (apiKey != null && !apiKey.isBlank()) {
      if (adminApiKey.equals(apiKey)) {
        var auth =
            new ApiKeyAuthenticationToken(
                "admin",
                apiKey,
                List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_MERCHANT")));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } else if (merchantApiKeys.containsKey(apiKey)) {
        String merchantId = merchantApiKeys.get(apiKey);
        var auth =
            new ApiKeyAuthenticationToken(
                new MerchantPrincipal(merchantId, "ROLE_MERCHANT"),
                apiKey,
                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")));
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    }

    filterChain.doFilter(request, response);
  }
}
