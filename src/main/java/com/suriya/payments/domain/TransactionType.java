package com.suriya.payments.domain;

/** The kind of ledger transaction. */
public enum TransactionType {
  PAYMENT,
  REFUND,
  REVERSAL,
  FEE,
  DEPOSIT
}
