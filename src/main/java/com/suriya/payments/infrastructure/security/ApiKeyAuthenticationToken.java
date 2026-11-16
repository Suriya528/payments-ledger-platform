package com.suriya.payments.infrastructure.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

  private final Object principal;
  private final String apiKey;

  public ApiKeyAuthenticationToken(
      Object principal, String apiKey, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = principal;
    this.apiKey = apiKey;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return apiKey;
  }

  @Override
  public Object getPrincipal() {
    return principal;
  }
}
