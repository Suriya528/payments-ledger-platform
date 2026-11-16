package com.suriya.payments.infrastructure.psp;

import com.suriya.payments.domain.Payment;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Stand-in payment provider with queryStatus support and test failure injection.
 */
@Component
public class MockPspClient implements PaymentProvider {

  private final Map<UUID, AuthorizationResult> mockState = new ConcurrentHashMap<>();
  private volatile boolean simulateTimeout = false;

  @Override
  public AuthorizationResult authorize(Payment payment) {
    if (simulateTimeout) {
      throw new PspTimeoutException("Simulated gateway timeout during authorization of payment " + payment.getId());
    }
    AuthorizationResult result = AuthorizationResult.captured("psp_" + UUID.randomUUID());
    mockState.put(payment.getId(), result);
    return result;
  }

  @Override
  public Optional<AuthorizationResult> queryStatus(UUID paymentId) {
    return Optional.ofNullable(mockState.get(paymentId));
  }

  public void recordOutcome(UUID paymentId, AuthorizationResult result) {
    mockState.put(paymentId, result);
  }

  public void setSimulateTimeout(boolean simulateTimeout) {
    this.simulateTimeout = simulateTimeout;
  }
}
