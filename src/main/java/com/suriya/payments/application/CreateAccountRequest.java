package com.suriya.payments.application;

import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.OwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
    @NotNull(message = "ownerType is required") OwnerType ownerType,
    @NotBlank(message = "ownerId is required") String ownerId,
    @NotNull(message = "type is required") AccountType type,
    @NotBlank(message = "currency is required") @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code") String currency) {}
