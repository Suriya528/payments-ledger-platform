package com.suriya.payments.api;

import com.suriya.payments.application.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks/psp")
@Tag(name = "PSP Webhooks", description = "Asynchronous capture and failure notifications from Payment Service Providers")
public class PspWebhookController {

  private final WebhookService webhooks;

  public PspWebhookController(WebhookService webhooks) {
    this.webhooks = webhooks;
  }

  @PostMapping
  @Operation(
      summary = "Handle PSP Webhook Event",
      description =
          "Receives HMAC-SHA256 signed payment events from the payment provider to settle or decline authorized holds.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Webhook verified and processed"),
    @ApiResponse(responseCode = "401", description = "Invalid or missing HMAC signature")
  })
  public ResponseEntity<Void> receive(
      @Parameter(description = "HMAC-SHA256 signature calculated with shared secret", required = true)
      @RequestHeader(value = "X-PSP-Signature", required = false) String signature,
      @RequestBody String rawBody) {
    webhooks.handle(rawBody, signature);
    return ResponseEntity.ok().build();
  }
}
