package com.suriya.payments.api;

import com.suriya.payments.application.CreatePaymentRequest;
import com.suriya.payments.application.PaymentResponse;
import com.suriya.payments.application.PaymentResult;
import com.suriya.payments.application.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments")
@Tag(name = "Payments", description = "Idempotent payment creation and transaction status checks")
public class PaymentController {

  private final PaymentService payments;

  public PaymentController(PaymentService payments) {
    this.payments = payments;
  }

  @PostMapping
  @Operation(
      summary = "Create an idempotent payment",
      description =
          "Executes a hold-and-settle payment saga. Uses the 'Idempotency-Key' header to guarantee that duplicate requests are replayed safely without re-debiting funds.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Payment processed successfully"),
    @ApiResponse(responseCode = "400", description = "Missing idempotency key or invalid payload"),
    @ApiResponse(responseCode = "409", description = "Concurrent request in progress for the same idempotency key"),
    @ApiResponse(responseCode = "422", description = "Insufficient funds or payload hash mismatch for the idempotency key")
  })
  public ResponseEntity<PaymentResponse> create(
      @Parameter(description = "Client-supplied UUID ensuring idempotency", required = true)
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreatePaymentRequest request) {
    PaymentResult result = payments.create(idempotencyKey, request);
    return ResponseEntity.status(result.statusCode()).body(result.response());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get payment status", description = "Fetches the current state of a payment by ID.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Payment found"),
    @ApiResponse(responseCode = "404", description = "Payment not found")
  })
  public PaymentResponse get(
      @Parameter(description = "Payment UUID", required = true)
      @PathVariable UUID id) {
    return payments.get(id);
  }
}
