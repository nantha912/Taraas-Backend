package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.*;
import com.LocalService.lsp.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/promoters")
public class PromoterController {

    @Autowired
    private PromoterRepository promoterRepository;

    @Autowired
    private ReferralLedgerRepository ledgerRepository;

    @Autowired
    private SystemSettingsRepository settingsRepository;

    private static final String ADMIN_BYPASS_TOKEN = "TaraasAdminSecret2026Key"; // Simple header security check for MVP

    private static final String CONFIG_ID = "subscription_config";
    private static final double DEFAULT_FALLBACK_RATE = 0.20; // 20% fallback rate

    /**
     * Public: Get dynamic rate definitions for marketing layouts
     */
    @GetMapping("/config")
    public ResponseEntity<?> getGlobalRateConfig() {
        SystemSettings settings = settingsRepository.findById(CONFIG_ID).orElse(null);
        double currentRate = (settings != null) ? settings.getPromoterCommissionRate() : DEFAULT_FALLBACK_RATE;
        return ResponseEntity.ok(Map.of("promoterCommissionPercentage", currentRate * 100));
    }

    /**
     * Public: Register promoter with multiple social tracking references
     */
    @PostMapping("/signup")
    public ResponseEntity<?> signupPromoter(@RequestBody Promoter promoter) {
        if (promoter.getEmail() == null || promoter.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body("Email field is mandatory.");
        }
        if (promoterRepository.findByEmail(promoter.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email identifier is already registered.");
        }

        // Generate clean random coupon suffix uppercase tags
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String generatedCode = "TARAAS" + uniqueSuffix;
        
        promoter.setReferralCode(generatedCode);
        promoter.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.ok(promoterRepository.save(promoter));
    }

    /**
     * Checkout Pass Verification Pipeline
     */
    @GetMapping("/validate")
    public ResponseEntity<?> validateReferralCode(@RequestParam String code) {
        Optional<Promoter> promoterOpt = promoterRepository.findByReferralCodeIgnoreCase(code.trim());
        if (promoterOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("valid", false, "message", "Invalid Referral Code"));
        }
        return ResponseEntity.ok(Map.of("valid", true, "promoterName", promoterOpt.get().getName()));
    }

    /**
     * Private Authenticated Dash Pass Metrics Aggregator
     */
    @GetMapping("/dashboard/summary")
    public ResponseEntity<?> getDashboardSummary() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Fetch corresponding promoter document by auth context email claim
        Promoter promoter = promoterRepository.findByEmail(auth.getName()).orElse(null);
        if (promoter == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Promoter workspace record not found.");
        }

        List<ReferralLedger> logs = ledgerRepository.findByPromoterId(promoter.getId());

        long conversions = logs.stream().filter(l -> "EARNED".equals(l.getStatus())).count();
        
        double totalEarned = logs.stream()
                .filter(l -> "EARNED".equals(l.getStatus()))
                .mapToDouble(ReferralLedger::getCommissionEarned).sum();

        Map<String, Object> response = new HashMap<>();
        response.put("referralCode", promoter.getReferralCode());
        response.put("conversionsPaidCount", conversions);
        response.put("totalEarnings", totalEarned);
        response.put("historyLedger", logs);

        return ResponseEntity.ok(response);
    }
    @GetMapping("/admin/export-payouts")
    public ResponseEntity<?> exportUnpaidPayouts(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (token == null || !token.equals(ADMIN_BYPASS_TOKEN)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access Denied: Invalid Admin Secret.");
        }

        // 1. Fetch all records awaiting processing
        List<ReferralLedger> unpaidRecords = ledgerRepository.findAll().stream()
                .filter(log -> "EARNED".equals(log.getStatus()) || "PENDING_VALIDATION".equals(log.getStatus()))
                .collect(Collectors.toList());

        if (unpaidRecords.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No pending payouts found. All clean!"));
        }

        // 2. Group records by promoterId to construct a clean payout manifest sheet
        Map<String, List<ReferralLedger>> groupedByPromoter = unpaidRecords.stream()
                .collect(Collectors.groupingBy(ReferralLedger::getPromoterId));

        List<Map<String, Object>> payoutManifest = new ArrayList<>();

        for (Map.Entry<String, List<ReferralLedger>> entry : groupedByPromoter.entrySet()) {
            String promoterId = entry.getKey();
            List<ReferralLedger> promoterLogs = entry.getValue();

            Promoter promoter = promoterRepository.findById(promoterId).orElse(null);
            if (promoter == null) continue;

            double totalPayoutAmount = promoterLogs.stream().mapToDouble(ReferralLedger::getCommissionEarned).sum();
            
            Map<String, Object> promoterPayoutRow = new HashMap<>();
            promoterPayoutRow.put("promoterId", promoterId);
            promoterPayoutRow.put("name", promoter.getName());
            promoterPayoutRow.put("email", promoter.getEmail());
            promoterPayoutRow.put("upiId", promoter.getPayoutDetails() != null ? promoter.getPayoutDetails().getUpiId() : "MISSING");
            promoterPayoutRow.put("totalAmountToPay", totalPayoutAmount);
            promoterPayoutRow.put("transactionCount", promoterLogs.size());
            
            payoutManifest.add(promoterPayoutRow);
        }

        return ResponseEntity.ok(payoutManifest);
    }

    /**
     * 💳 ADMIN ONLY: Mark all pending records for a specific promoter as SETTLED
     */
    /**
     * 💳 ADMIN ONLY: Mark all pending records for a specific promoter as SETTLED with dynamic execution timestamps
     */
    @PostMapping("/admin/settle/{promoterId}")
    public ResponseEntity<?> bulkSettlePromoter(@PathVariable String promoterId, 
                                                @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (token == null || !token.equals(ADMIN_BYPASS_TOKEN)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access Denied: Invalid Admin Secret.");
        }

        // 🟢 Capture the single point-in-time snapshot for this settlement batch execution
        LocalDateTime settlementTimestamp = LocalDateTime.now();

        List<ReferralLedger> promoterLogs = ledgerRepository.findByPromoterId(promoterId);
        
        List<ReferralLedger> updatedLogs = promoterLogs.stream()
                .filter(log -> "EARNED".equals(log.getStatus()) || "PENDING_VALIDATION".equals(log.getStatus()))
                .peek(log -> {
                    log.setStatus("SETTLED");
                    log.setProcessedAt(settlementTimestamp); // 🟢 Saves the exact date & time to MongoDB document references
                })
                .collect(Collectors.toList());

        if (updatedLogs.isEmpty()) {
            return ResponseEntity.badRequest().body("No pending or earned transactions found to settle for this promoter.");
        }

        // Save back to MongoDB cluster
        ledgerRepository.saveAll(updatedLogs); 
        
        // 🟢 Return a rich payload response showing exactly when the batch finalized
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("promoterId", promoterId);
        response.put("recordsUpdatedCount", updatedLogs.size());
        response.put("settlementDateTime", settlementTimestamp.toString()); // Format: YYYY-MM-DDTHH:MM:SS.ms
        response.put("message", "Successfully marked records as SETTLED.");

        return ResponseEntity.ok(response);
    }
}