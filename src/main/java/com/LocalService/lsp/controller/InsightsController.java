package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.*;
import com.LocalService.lsp.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private static final Logger logger = LoggerFactory.getLogger(InsightsController.class);

    @Autowired private ProfileViewRepository viewRepo;
    @Autowired private LeadEventRepository leadRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private CustomerRepository customerRepo;
    @Autowired private ProviderRepository providerRepo;

    @PostMapping("/view")
    public ResponseEntity<?> recordView(@RequestBody Map<String, String> payload) {
        if (payload == null) return ResponseEntity.badRequest().body("Payload required");
        String providerId = payload.get("providerId");
        String sessionId = payload.get("sessionId");
        
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body("Session ID is required");
        }

        // Logic check: Prevent provider viewing own profile from recording a view (optional refinement)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String userEmail = auth.getName();
            customerRepo.findByEmail(userEmail).ifPresent(c -> {
                providerRepo.findByCustomerId(c.getId()).ifPresent(p -> {
                    if (p.getId().equals(providerId)) {
                        logger.info("Self-view ignored for provider: {}", providerId);
                    }
                });
            });
        }

        viewRepo.save(new ProfileView(providerId, sessionId));
        return ResponseEntity.ok().build();
    }

    /**
     * Record Lead - DEDUPLICATION LOGIC
     * 1. Checks if a lead from the same customer/method/provider exists within the last hour.
     * 2. If duplicate found, returns 200 OK without saving to keep frontend silent.
     */
    @PostMapping("/lead")
    public ResponseEntity<?> recordLead(@RequestBody LeadEvent lead) {
        if (lead == null) return ResponseEntity.badRequest().body("Lead data required");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        
        Optional<Customer> customerOpt = customerRepo.findByEmail(userEmail);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }
        
        Customer currentCustomer = customerOpt.get();
        if (!currentCustomer.getId().equals(lead.getCustomerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only record leads as yourself."));
        }

        String providerId = lead.getProviderId();
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        
        // Logic Check: Cannot record lead on own profile
        Optional<Provider> ownProfile = providerRepo.findByCustomerId(currentCustomer.getId());
        if (ownProfile.isPresent() && ownProfile.get().getId().equals(providerId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "You cannot record a lead on your own profile."));
        }

        String method = lead.getContactMethod();
        if (method == null || (!method.equals("PHONE") && !method.equals("WHATSAPP"))) {
            return ResponseEntity.badRequest().body("Valid contact method (PHONE/WHATSAPP) is required");
        }

        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        // Fetch recent leads for this provider to check for duplicates
        List<LeadEvent> recentLeads = leadRepo.findAllByProviderIdAndTimestampBetween(
                providerId, oneHourAgo, LocalDateTime.now());

        boolean isDuplicate = recentLeads.stream().anyMatch(e ->
                Objects.equals(e.getContactMethod(), method) &&
                        Objects.equals(e.getCustomerId(), lead.getCustomerId())
        );

        if (isDuplicate) {
            logger.info("Duplicate lead ignored for provider: {} (Method: {})", providerId, method);
            return ResponseEntity.ok().build();
        }

        if (lead.getTimestamp() == null) {
            lead.setTimestamp(LocalDateTime.now());
        }

        leadRepo.save(lead);
        logger.info("New unique lead recorded for provider: {} via {}", providerId, method);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{providerId}")
    public ResponseEntity<?> getProviderInsights(
            @PathVariable String providerId,
            @RequestParam int year,
            @RequestParam int month) {

        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        
        Optional<Customer> customerOpt = customerRepo.findByEmail(userEmail);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }
        
        Customer currentCustomer = customerOpt.get();
        
        // Ownership Check: Only the owner of the provider profile can view its insights.
        Optional<Provider> targetProvider = providerRepo.findById(providerId);
        if (targetProvider.isPresent()) {
            if (!targetProvider.get().getCustomerId().equals(currentCustomer.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only view insights for your own profile."));
            }
        } else {
            // If the provider doesn't exist, we still return 403 if the user is not the owner
            Optional<Provider> requesterProvider = providerRepo.findByCustomerId(currentCustomer.getId());
            if (requesterProvider.isPresent() && !requesterProvider.get().getId().equals(providerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
            }
            return ResponseEntity.notFound().build();
        }

        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end = start.plusMonths(1);

        logger.info("Fetching insights for {} between {} and {}", providerId, start, end);

        // 1. Unique Profile Views
        long views = viewRepo.findAllByProviderIdAndTimestampBetween(providerId, start, end)
                .stream()
                .filter(pv -> pv != null && pv.getSessionId() != null)
                .map(ProfileView::getSessionId)
                .distinct()
                .count();

        // 2. Lead Activity (Sorted: Most Recent First)
        List<LeadEvent> leads = leadRepo.findAllByProviderIdAndTimestampBetween(providerId, start, end)
                .stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LeadEvent::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        // 3. Financials
        List<Transaction> completedTxs = txRepo.findAllByProviderIdAndStatusAndCreatedAtBetween(
                providerId, "COMPLETED", start, end);

        double turnover = completedTxs.stream()
                .filter(Objects::nonNull)
                .mapToDouble(t -> Optional.ofNullable(t.getAmount()).orElse(0.0))
                .sum();

        Map<String, Object> response = new HashMap<>();
        response.put("views", views);
        response.put("leadsCount", leads.size());
        response.put("leadsHistory", leads);
        response.put("totalOrders", completedTxs.size());
        response.put("turnover", turnover);

        return ResponseEntity.ok(response);
    }
}
