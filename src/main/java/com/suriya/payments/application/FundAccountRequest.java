package com.suriya.payments.application;

import jakarta.validation.constraints.Positive;

public record FundAccountRequest(
    @Positive(message = "amount must be greater than zero") long amount) {}
