package com.suriya.payments.api;

import com.suriya.payments.application.RefundRequest;
import com.suriya.payments.application.RefundResponse;
import com.suriya.payments.application.RefundResult;
import com.suriya.payments.application.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments/{paymentId}/refunds")
@Tag(name = "Refunds", description = "Full and partial payment refunds with double-entry reversal postings")
public class RefundController {

  private final RefundService refunds;

  public RefundController(RefundService refunds) {
    this.refunds = refunds;
  }

  @PostMapping
  @Operation(
      summary = "Refund a payment (full or partial)",
      description =
          "Initiates an idempotent refund for a captured payment. Reverses funds from payee to payer and guards against over-refunding.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Refund created successfully"),
    @ApiResponse(responseCode = "404", description = "Payment not found"),
    @ApiResponse(responseCode = "422", description = "Payment not refundable or refund amount exceeds remaining amount")
  })
  public ResponseEntity<RefundResponse> create(
      @Parameter(description = "ID of the payment to refund", required = true)
      @PathVariable UUID paymentId,
      @Parameter(description = "Idempotency key for this refund operation", required = true)
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody RefundRequest request) {
    RefundResult result = refunds.refund(idempotencyKey, paymentId, request.amount());
    return ResponseEntity.status(result.statusCode()).body(result.response());
  }
}
