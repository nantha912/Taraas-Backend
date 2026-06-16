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
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private StatementRepository statementRepository;

    /**
     * STEP 1: Create Order
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

            Optional<Statement> stmtOpt = statementRepository.findById(statementId);
            if (stmtOpt.isEmpty()) {
                logger.warn("Order creation failed: Statement ID {} not found in database", statementId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Statement record not found."));
            }

            Statement stmt = stmtOpt.get();

            if ("PAID".equals(stmt.getStatus())) {
                return ResponseEntity.badRequest().body("This statement has already been paid.");
            }

            Double authenticAmount = stmt.getCommissionAmount();

            logger.info("Initiating secure payment order for Statement: {} Amount: ₹{}", statementId, authenticAmount);

            String razorpayOrderId = paymentService.createRazorpayOrder(authenticAmount, statementId);

            // Precision Risk Fix: Math.round handles floating point issues cleanly when shifting to paise
            long amountInPaise = Math.round(authenticAmount * 100);

            Map<String, Object> response = new HashMap<>();
            response.put("razorpayOrderId", razorpayOrderId);
            response.put("razorpayKeyId", paymentService.getKeyId());
            response.put("amount", amountInPaise); 
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Order creation failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Could not initialize payment with Razorpay."));
        }
    }

    /**
     * STEP 2: Verify Payment
     * Hardened: Captures razorpay_payment_id and records an audit trail in MongoDB.
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
            // 2. Update the statement status and log audit fields in MongoDB
            Optional<Statement> stmtOpt = statementRepository.findById(statementId);
            if (stmtOpt.isPresent()) {
                Statement stmt = stmtOpt.get();
                
                // --- AUDIT TRAIL ENHANCEMENT ---
                String razorpayPaymentId = payload.get("razorpay_payment_id");
                stmt.setRazorpayPaymentId(razorpayPaymentId); 
                
                stmt.setStatus("PAID");
                stmt.setPaidAt(LocalDateTime.now());
                statementRepository.save(stmt);

                logger.info("Payment verified. Statement {} marked PAID with Transaction ID: {}", statementId, razorpayPaymentId);
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

        /**
    * STEP 3: Razorpay Webhook Gateway
    * Asynchronously processes automated payment.captured alerts directly from Razorpay's servers.
    */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleRazorpayWebhook(
           @RequestBody String rawPayload,
           @RequestHeader("X-Razorpay-Signature") String signature) {
    
        if (rawPayload == null || signature == null || signature.isBlank()) {
            logger.warn("Webhook dropped: Missing signature header or raw payload body structure.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // 1. Verify that this event actually came from Razorpay's secure servers
        boolean isGenuine = paymentService.verifyWebhookSignature(rawPayload, signature);
        if (!isGenuine) {
            logger.warn("Security Alert: Unauthorized or malicious Webhook attempt rejected!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 2. Map the raw payload text to a JSON object wrapper
            org.json.JSONObject webhookData = new org.json.JSONObject(rawPayload);
            String eventType = webhookData.optString("event");

            // We only care about successfully captured transactions
            if ("payment.captured".equals(eventType)) {
                org.json.JSONObject paymentEntity = webhookData
                        .getJSONObject("payload")
                        .getJSONObject("payment")
                        .getJSONObject("entity");

                // Extract the original Razorpay Order ID generated in STEP 1
                String razorpayOrderId = paymentEntity.optString("order_id");
                String razorpayPaymentId = paymentEntity.optString("id");

                logger.info("Webhook processing payment.captured event for Order: {} (Payment ID: {})", 
                        razorpayOrderId, razorpayPaymentId);

                // 3. Find the target record in MongoDB by checking the order mapping
                // Note: Since receipt parameter tracks statement configurations, we can fetch via notes or repositories
                Optional<Statement> stmtOpt = statementRepository.findAll().stream()
                        .filter(s -> "PAID".equals(s.getStatus()) == false) // Performance opt: filter open targets
                        .filter(s -> {
                            // Locate the record whose receipt or tracking matches your statement sequence
                            return ("stmt_" + s.getId()).equals(paymentEntity.optString("receipt"));
                        })
                        .findFirst();

                if (stmtOpt.isPresent()) {
                    Statement stmt = stmtOpt.get();
                    
                    // Idempotency check: If the user's browser already cleared this bill via STEP 2, exit cleanly
                    if ("PAID".equals(stmt.getStatus())) {
                        logger.info("Webhook execution skipped: Statement {} already settled via browser workflow.", stmt.getId());
                        return ResponseEntity.ok().build();
                    }

                    // Update database state variables safely
                    stmt.setStatus("PAID");
                    stmt.setPaidAt(LocalDateTime.now());
                    stmt.setRazorpayPaymentId(razorpayPaymentId);
                    statementRepository.save(stmt);

                    logger.info("Webhook successfully processed! Statement {} updated to PAID asynchronously.", stmt.getId());
                } else {
                    logger.warn("Webhook Warning: Payment captured but no matching un-settled statement sequence located.");
                }
            }

            // Always return a clean 200 OK to Razorpay quickly so their service stops retrying the alert queue
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            logger.error("Critical failure during Webhook parsing matrix: ", e);
            // Return 500 error if execution fails so Razorpay adds the event back into their processing pool retry schedule
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}