package com.suriya.payments.infrastructure.psp;

/** What the PSP did with an authorization. */
public enum AuthorizationOutcome {
  /** Authorized and captured synchronously — settle now. */
  CAPTURED,
  /** Authorized; capture will be confirmed asynchronously via a webhook. */
  PENDING,
  /** Refused — reverse the hold. */
  DECLINED
}
