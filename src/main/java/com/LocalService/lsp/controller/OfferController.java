package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.Customer;
import com.LocalService.lsp.model.Offer;
import com.LocalService.lsp.model.Provider;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.OfferRepository;
import com.LocalService.lsp.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private static final Logger logger = LoggerFactory.getLogger(OfferController.class);

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private CustomerRepository customerRepository;


    /**
     * Provider: Create or Update an offer.
     * Snapshot provider details for fast read (NO joins).
     */
    @PostMapping
    public ResponseEntity<?> saveOffer(@RequestBody Offer offer) {
        if (offer == null) {
            return ResponseEntity.badRequest().body("Offer data is required");
        }

        String providerId = offer.getProviderId();
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        
        String userEmail = auth.getName();
        Optional<Customer> customerOpt = customerRepository.findByEmail(userEmail);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }
        
        Customer currentCustomer = customerOpt.get();
        Optional<Provider> providerOpt = providerRepository.findByCustomerId(currentCustomer.getId());
        
        // Ownership Check: Current user must have a provider profile AND match the offer's providerId
        if (providerOpt.isEmpty() || !providerOpt.get().getId().equals(providerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only create offers for your own provider profile."));
        }

        // Manual validation for the fields we moved out of JSR-303
        if (offer.getTitle() == null || offer.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("title", "Title cannot be empty"));
        }
        if (offer.getType() == null) {
            return ResponseEntity.badRequest().body(Map.of("type", "Offer type cannot be null"));
        }
        if (offer.getValue() == null || offer.getValue().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("value", "Value cannot be empty"));
        }
        if (offer.getMinCategory() == null) {
            return ResponseEntity.badRequest().body(Map.of("minCategory", "Minimum category cannot be null"));
        }

        Provider provider = providerOpt.get();

        if (offer.getCreatedAt() == null) {
            offer.setCreatedAt(LocalDateTime.now());
        }

        // 🔹 Snapshot provider data (IMPORTANT)
        offer.setProviderName(provider.getName());
        offer.setProviderProfilePhoto(provider.getProfilePhotoUrl());
        offer.setServiceCategory(provider.getServiceCategory()); // List<String>

        // ✅ NEW (safe additions)
        offer.setLocation(provider.getCity()); // auto attach provider city
        offer.setServiceDeliveryType(provider.getServiceDeliveryType()); // LOCAL / REMOTE / HYBRID

        return ResponseEntity.ok(offerRepository.save(offer));
    }

    /**
     * Provider: Get all offers created by this provider.
     */
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getOffersByProvider(@PathVariable String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        return ResponseEntity.ok(offerRepository.findByProviderId(providerId));
    }

    /**
     * Buyer: Fetch eligible offers for a provider profile.
     * Rule: buyerCategory.rank >= offer.minCategory.rank
     */
    @GetMapping("/provider/{providerId}/eligible")
    public ResponseEntity<?> getEligibleOffers(
            @PathVariable String providerId,
            @RequestParam Offer.BuyerCategory category) {

        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        if (category == null) {
            return ResponseEntity.badRequest().body("Buyer category is required");
        }

        LocalDateTime now = LocalDateTime.now();

        List<Offer> eligible = offerRepository.findByProviderId(providerId)
                .stream()
                .filter(o -> o.getMinCategory() != null && category.getRank() >= o.getMinCategory().getRank())
                .filter(o -> o.getStartDate() == null || o.getStartDate().isBefore(now))
                .filter(o -> o.getEndDate() == null || o.getEndDate().isAfter(now))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(eligible);
    }

    /**
     * Public: Fetch all active offers (View Offers page)
     * Supports optional category & location filters
     */
    /**
     * Public: Fetch all active offers (View Offers page)
     * Supports optional partial relative category filtering
     */
    @GetMapping("/active")
    public ResponseEntity<?> getActiveOffers(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        logger.info("Fetching simplified paginated regex offers -> Category: {}, Page: {}, Size: {}", category, page, size);

        // Sort by stable secondary IDs as established in our previous optimization step
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("id")));

        List<Offer> paginatedOffers;

        if (category == null || category.isBlank()) {
            // If no category filter is applied, return a clean page frame of all records
            Page<Offer> offerPage = offerRepository.findAll(pageable);
            paginatedOffers = offerPage.getContent();
        } else {
            // Otherwise, execute the regex partial search lookups
            Page<Offer> offerPage = offerRepository.findByServiceCategoryContainingRegex(category.trim(), pageable);
            paginatedOffers = offerPage.getContent();
        }

        return ResponseEntity.ok(paginatedOffers);
    }

    /**
     * Provider: Delete an offer
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOffer(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Offer ID is required");
        }
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated() || authentication.getName().equals("anonymousUser")) {
                return ResponseEntity.status(401).body("Authentication required");
            }

            String subject = authentication.getName();

            // subject may be email or user id depending on auth context
            Optional<Customer> customerOpt = customerRepository.findByEmail(subject);
            Provider provider = null;

            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();
                provider = providerRepository.findByCustomerId(customer.getId()).orElse(null);
            }

            if (provider == null) {
                // Try subject as provider id directly
                provider = providerRepository.findById(subject).orElse(null);
            }

            if (provider == null) {
                logger.warn("Delete offer denied: no provider profile for auth subject {}", subject);
                return ResponseEntity.status(403).body("Only providers can delete offers");
            }

            Optional<Offer> offerOpt = offerRepository.findById(id);
            if (offerOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Offer offer = offerOpt.get();
            if (!provider.getId().equals(offer.getProviderId())) {
                logger.warn("Delete offer denied: provider {} tried to delete non-owned offer {}", provider.getId(), id);
                return ResponseEntity.status(403).body("You can only delete your own offers");
            }

            offerRepository.deleteById(id);
            logger.info("Offer {} deleted by provider {}", id, provider.getId());
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            logger.error("Error deleting offer {}", id, ex);
            return ResponseEntity.status(500).body("Failed to delete offer: " + ex.getMessage());
        }
    }
}
