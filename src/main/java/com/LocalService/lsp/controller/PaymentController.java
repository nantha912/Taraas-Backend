package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.Statement;
import com.LocalService.lsp.repository.StatementRepository;
import com.LocalService.lsp.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * PaymentController - Secure Transaction Gateway
 * Endpoints to handle the Razorpay payment lifecycle.
 */
@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private StatementRepository statementRepository;

    /**
     * STEP 1: Create Order
     * Triggered when user clicks "Pay Now" on the Charges Page.
     */
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> payload) {
        if (payload == null) return ResponseEntity.badRequest().body("Payload required");
        try {
            Object statementIdObj = payload.get("statementId");
            Object amountObj = payload.get("amount");
            
            if (statementIdObj == null) return ResponseEntity.badRequest().body("statementId required");
            if (amountObj == null) return ResponseEntity.badRequest().body("amount required");

            String statementId = statementIdObj.toString();
            Double amount = Double.valueOf(amountObj.toString());

            logger.info("Initiating payment order for Statement: {} Amount: ₹{}", statementId, amount);

            String razorpayOrderId = paymentService.createRazorpayOrder(amount, statementId);

            Map<String, Object> response = new HashMap<>();
            response.put("razorpayOrderId", razorpayOrderId);
            response.put("razorpayKeyId", paymentService.getKeyId());
            response.put("amount", Math.round(amount * 100)); // Returns paise to frontend
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Order creation failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Could not initialize payment with Razorpay."));
        }
    }

    /**
     * STEP 2: Verify Payment
     * Triggered by Razorpay Handler callback on successful payment.
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> payload) {
        if (payload == null) return ResponseEntity.badRequest().body("Payload required");
        
        String statementId = payload.get("statementId");
        if (statementId == null || statementId.isBlank()) {
            return ResponseEntity.badRequest().body("statementId required");
        }

        // 1. Validate the cryptographic signature
        boolean isValid = paymentService.verifySignature(payload);

        if (isValid) {
            // 2. Update the statement status in MongoDB
            Optional<Statement> stmtOpt = statementRepository.findById(statementId);
            if (stmtOpt.isPresent()) {
                Statement stmt = stmtOpt.get();
                stmt.setStatus("PAID");
                stmt.setPaidAt(LocalDateTime.now());
                statementRepository.save(stmt);

                logger.info("Payment verified and statement updated: {}", statementId);
                return ResponseEntity.ok(Map.of("status", "success"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Statement record not found."));
            }
        } else {
            logger.warn("Security Warning: Invalid payment signature received for statement: {}", statementId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "failed", "message", "Invalid payment signature."));
        }
    }
}
