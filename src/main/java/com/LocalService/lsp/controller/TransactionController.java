package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.Transaction;
import com.LocalService.lsp.model.Customer;
import com.LocalService.lsp.model.Provider;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.ProviderRepository;
import com.LocalService.lsp.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/customer/{customerId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<?> streamToCustomer(@PathVariable String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body("Customer ID is required");
        }
        //logger.info("Stream req received from customer");
        SseEmitter emitter = new SseEmitter(1800_000L); 
        addEmitter(customerId, emitter);
        emitter.onCompletion(() -> removeEmitter(customerId, emitter));
        emitter.onTimeout(() -> removeEmitter(customerId, emitter));
        return ResponseEntity.ok(emitter);
    }

    @GetMapping(value = "/provider/{providerId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<?> streamToProvider(@PathVariable String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        //logger.info("Stream req received from provider");
        SseEmitter emitter = new SseEmitter(1800_000L);
        addEmitter(providerId, emitter);
        emitter.onCompletion(() -> removeEmitter(providerId, emitter));
        emitter.onTimeout(() -> removeEmitter(providerId, emitter));
        return ResponseEntity.ok(emitter);
    }

    private void addEmitter(String id, SseEmitter emitter) {
        if (id == null) return;
        emitters.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(emitter);
        //logger.info("SSE Session opened for: {}. Total Active: {}", id, emitters.get(id).size());
    }

    private void removeEmitter(String id, SseEmitter emitter) {
        if (id == null) return;
        List<SseEmitter> userEmitters = emitters.get(id);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) emitters.remove(id);
        }
    }

    private void broadcast(Transaction tx) {
        if (tx == null) return;
        String providerId = tx.getProviderId();
        String customerId = tx.getCustomerId();
        if (providerId != null) sendUpdate(providerId, tx);
        if (customerId != null) sendUpdate(customerId, tx);
    }

    private void sendUpdate(String id, Transaction tx) {
        List<SseEmitter> userEmitters = emitters.get(id);
        if (userEmitters != null) {
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().name("PAYMENT_UPDATE").data(tx));
                } catch (IOException e) {
                    removeEmitter(id, emitter);
                }
            }
        }
    }

    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@RequestBody Transaction transaction) {
        if (transaction == null) {
            return ResponseEntity.badRequest().body("Transaction data is required");
        }
        //logger.info("payment initiate request received");

        Double amount = transaction.getAmount();
        if (amount == null || amount < 20.0) {
            return ResponseEntity.badRequest().body(Map.of("amount", "Amount must be at least 20"));
        }
        
        String providerId = transaction.getProviderId();
        String customerId = transaction.getCustomerId();
        if (providerId == null || customerId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provider and Customer IDs are required"));
        }
        
        if (transaction.getProviderName() == null || transaction.getProviderName().isBlank()) {
            return ResponseEntity.badRequest().body("Provider name is required");
        }
        if (transaction.getCustomerName() == null || transaction.getCustomerName().isBlank()) {
            return ResponseEntity.badRequest().body("Customer name is required");
        }

        if (customerId.equals(providerId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "You cannot initiate a transaction with yourself."));
        }

        transaction.setStatus("INITIATED");
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setBilled(false);
        Transaction saved = transactionRepository.save(transaction);
        
        // Atomically increments totalOrdersCount (+1)
        updateProviderOrderCounters(saved.getProviderId(), "totalOrdersCount", 1);
        
        broadcast(saved);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/confirm-payment")
    public ResponseEntity<?> confirmPayment(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Transaction ID is required");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) ? auth.getName() : null;
        
        final String finalUserEmail = userEmail;
        return transactionRepository.findById(id).map(t -> {
            if (finalUserEmail != null) {
                Optional<Customer> requesterOpt = customerRepository.findByEmail(finalUserEmail);
                if (requesterOpt.isPresent() && !t.getCustomerId().equals(requesterOpt.get().getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the customer who initiated the transaction can confirm payment."));
                }
            }
            
            t.setStatus("CUSTOMER_CONFIRMED");
            Transaction saved = transactionRepository.save(t);
            broadcast(saved); 
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/verify")
    public ResponseEntity<?> verifyTransaction(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Transaction ID is required");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        Optional<Customer> requesterOpt = customerRepository.findByEmail(userEmail);
        
        if (requesterOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Customer currentCustomer = requesterOpt.get();

        return transactionRepository.findById(id).map(t -> {
            if (currentCustomer.getId().equals(t.getCustomerId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "As the initiator, you cannot verify your own payment. Only the provider can verify."));
            }

            if (!currentCustomer.getId().equals(t.getProviderId())) { 
                Optional<Provider> providerProfile = providerRepository.findByCustomerId(currentCustomer.getId());
                if (providerProfile.isEmpty() || !providerProfile.get().getId().equals(t.getProviderId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the provider for this transaction can verify it."));
                }
            }
            
            String previousStatus = t.getStatus();
            
            t.setStatus("COMPLETED");
            Transaction saved = transactionRepository.save(t);
            
            // Increment completedOrdersCount (+1) only if transitioning to completed for the first time
            if (!"COMPLETED".equalsIgnoreCase(previousStatus)) {
                updateProviderOrderCounters(saved.getProviderId(), "completedOrdersCount", 1);
            }
            
            broadcast(saved); 
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectTransaction(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Transaction ID is required");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        Optional<Customer> requesterOpt = customerRepository.findByEmail(userEmail);
        
        if (requesterOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Customer currentCustomer = requesterOpt.get();

        return transactionRepository.findById(id).map(t -> {
            Optional<Provider> providerProfile = providerRepository.findByCustomerId(currentCustomer.getId());
            if (providerProfile.isEmpty() || !providerProfile.get().getId().equals(t.getProviderId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the provider for this transaction can reject it."));
            }

            t.setStatus("REJECTED");

            if (payload != null && payload.containsKey("reason")) {
                t.setRejectionReason(payload.get("reason"));
            }

            Transaction saved = transactionRepository.save(t);
            
            // 👑 THE CRITICAL FRAUD PROTECTION SECURITY FIX:
            // We NO LONGER update totalOrdersCount with a -1 penalty here.
            // Leaving totalOrdersCount intact widens the gap against completedOrdersCount,
            // dropping the provider's overall fulfillment velocity scores to flag off-platform cheating.
            //logger.info("Transaction rejected by provider: {}. Total orders count preserved for fraud tracking loop metrics.", saved.getProviderId());

            broadcast(saved); 
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Transaction ID is required");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can delete transactions."));
        }

        //logger.info("Deleting Transaction ID: {}", id);
        return transactionRepository.findById(id).map(transaction -> {
            String previousStatus = transaction.getStatus();
            String providerId = transaction.getProviderId();
            
            transactionRepository.delete(transaction);
            
            // Admin manual ledger adjustments
            if ("COMPLETED".equalsIgnoreCase(previousStatus)) {
                updateProviderOrderCounters(providerId, "completedOrdersCount", -1);
                updateProviderOrderCounters(providerId, "totalOrdersCount", -1);
            } else {
                updateProviderOrderCounters(providerId, "totalOrdersCount", -1);
            }
            
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getByCustomer(
        @PathVariable String customerId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
        
    if (customerId == null || customerId.isBlank()) {
        return ResponseEntity.badRequest().body("Customer ID is required");
    }
    
    // Sort transactions by newest first so the user sees fresh history
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    
    List<Transaction> records = transactionRepository.findByCustomerId(customerId, pageable);
    return ResponseEntity.ok(records);
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getByProvider(
            @PathVariable String providerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
            
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        
        //logger.info("Fetching paginated transactions for providerId: {}, page: {}, size: {}", providerId, page, size);
        
        // 👑 HIGH-SCALE PRODUCTION FIX: Sort by newest first and apply data boundaries
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        List<Transaction> records = transactionRepository.findByProviderId(providerId, pageable);
        return ResponseEntity.ok(records);
    }

    private void updateProviderOrderCounters(String providerId, String targetFieldName, int incrementalValue) {
        if (providerId == null || providerId.isBlank() || targetFieldName == null) return;

        try {
            Query query = new Query(Criteria.where("_id").is(providerId));
            Update updateOp = new Update().inc(targetFieldName, incrementalValue);
            mongoTemplate.updateFirst(query, updateOp, "providers");
            
            //logger.info("Atomic Incremental Order Sync -> Provider: {}, Field: {}, Adjustment: {}", 
            //        providerId, targetFieldName, incrementalValue);
            
        } catch (Exception e) {
            logger.error("Failed executing incremental transaction calculation matrices for Provider {}: {}", 
                    providerId, e.getMessage());
        }
    }
}