package com.LocalService.lsp.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;

/**
 * PaymentService - Razorpay Logic Implementation
 * Handles order creation and cryptographic signature verification.
 */
@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    private RazorpayClient client;

    @PostConstruct
    public void init() {
        try {
            this.client = new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            logger.error("Failed to initialize Razorpay Client: {}", e.getMessage());
        }
    }

    /**
     * Creates a Razorpay Order.
     * @param amount The amount in INR (will be converted to paise).
     * @param statementId Reference ID for the transaction.
     * @return The Razorpay Order ID.
     */
    public String createRazorpayOrder(Double amount, String statementId) throws RazorpayException {
        JSONObject orderRequest = new JSONObject();
        // Razorpay expects amount in the smallest currency unit (paise for INR)
        long amountInPaise = Math.round(amount * 100);

        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "stmt_" + statementId);

        Order order = client.orders.create(orderRequest);
        return order.get("id");
    }

    /**
     * Verifies the Razorpay payment signature for security.
     */
    public boolean verifySignature(Map<String, String> payload) {
        try {
            // Internal Razorpay utility to verify the HMAC hex digest
            return Utils.verifyPaymentSignature(new JSONObject(payload), keySecret);
        } catch (Exception e) {
            logger.error("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    public String getKeyId() {
        return keyId;
    }

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

/**
 * Validates that the incoming Webhook call genuinely originated from Razorpay's servers.
 * Razorpay passes the verification signature package in the X-Razorpay-Signature header.
 */
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            // The SDK requires the raw text request body string, the signature header, and your webhook secret
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            logger.error("Webhook cryptographic validation crashed: {}", e.getMessage());
            return false;
        }
    }


}