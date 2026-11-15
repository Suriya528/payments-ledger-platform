package com.suriya.payments.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Payments & Double-Entry Ledger Platform")
                .version("1.0.0")
                .description(
                    "Production-grade financial transaction platform built with Spring Boot 3.4 & Java 21.\n\n"
                        + "### Architectural Highlights:\n"
                        + "- **Double-Entry Accounting:** Net-zero balance invariant (sum of debits == sum of credits).\n"
                        + "- **Strict Idempotency:** Header `Idempotency-Key` prevents double-charging and concurrent duplicate processing.\n"
                        + "- **Hold -> Settle Saga:** Decouples external PSP calls from database transactions.\n"
                        + "- **Transactional Outbox:** Guaranteed at-least-once event delivery without distributed 2PC dual-writes.\n"
                        + "- **Continuous Reconciliation:** Real-time auditing for drifted balances and stale hold expiration.")
                .contact(
                    new Contact()
                        .name("Engineering Team")
                        .email("engineering@payments.internal"))
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
  }
}
