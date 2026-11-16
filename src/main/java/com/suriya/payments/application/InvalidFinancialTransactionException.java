package com.suriya.payments.application;

public class InvalidFinancialTransactionException extends RuntimeException {
  public InvalidFinancialTransactionException(String message) {
    super(message);
  }
}
