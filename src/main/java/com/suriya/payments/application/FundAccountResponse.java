package com.suriya.payments.application;

import java.util.UUID;

public record FundAccountResponse(
    UUID transactionId,
    UUID accountId,
    long creditedAmount,
    long updatedBalance) {}
