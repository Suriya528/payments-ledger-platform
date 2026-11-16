package com.suriya.payments.infrastructure.psp;

public class PspTimeoutException extends RuntimeException {
  public PspTimeoutException(String message) {
    super(message);
  }
}
