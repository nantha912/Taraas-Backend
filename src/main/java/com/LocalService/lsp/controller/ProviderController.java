package com.LocalService.lsp.controller;

import com.LocalService.lsp.dto.ProviderSearchResultDTO;
import com.LocalService.lsp.model.Provider;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.ProviderRepository;
import com.LocalService.lsp.service.ProviderService;
import com.LocalService.lsp.service.S3Service;
import com.LocalService.lsp.model.Customer;
import com.LocalService.lsp.util.FileUploadValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ProviderController - City-Centric Marketplace Edition
 * Handles professional profile management and the Mode-Aware Search Engine.
 */
@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private static final Logger logger = LoggerFactory.getLogger(ProviderController.class);

    @Autowired
    private ProviderRepository repository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private S3Service s3Service;

    /**
     * SEARCH: Mode-Aware Weighted Ranking
     */
    @GetMapping("/search")
    public List<ProviderSearchResultDTO> searchProviders(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "NEARBY") String mode) {

        //logger.info("Marketplace Search Triggered -> Mode: {}, Service: {}, City: {}, Lat: {}, Lon: {}", mode, service, city);
        return providerService.searchWithRanking(service, lat, lon, city, mode);
    }

    /**
     * FETCH BY ID: Retrieves profile data.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProviderById(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        return repository.findById(id)
                .map(provider -> ResponseEntity.ok(provider))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PROFILE PHOTO UPLOAD: Dedicated endpoint for the main provider avatar.
     */
    @PostMapping("/{id}/profile-photo")
    public ResponseEntity<?> uploadProfilePhoto(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file) {

        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }

        FileUploadValidator.validateImageFile(file);

        Optional<Provider> providerOpt = repository.findById(id);
        if (providerOpt.isEmpty()) return ResponseEntity.notFound().build();

        Provider provider = providerOpt.get();

        try {
            // 1. Delete old photo if it exists
            String oldUrl = Optional.ofNullable(provider.getProfilePhotoUrl()).orElse("");
            if (!oldUrl.isEmpty()) {
                s3Service.deleteFile(oldUrl);
            }

            // 2. Upload to S3
            String photoUrl = s3Service.uploadFile(file, id, "provider_profile");
            provider.setProfilePhotoUrl(photoUrl);

            // 3. Save the updated provider record
            Provider updated = repository.save(provider);
            //logger.info("Profile photo updated for provider: {}", id);
            return ResponseEntity.ok(updated);
        } catch (IOException e) {
            logger.error("Photo upload failed for provider {}: ", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Photo upload failed.");
        }
    }

    /**
     * PORTFOLIO PHOTO UPLOAD: Adds a new photo to the provider's gallery.
     * Maps to POST /api/providers/{id}/photos
     */
    @PostMapping("/{id}/photos")
    public ResponseEntity<?> uploadPortfolioPhoto(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file) {

        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }

        FileUploadValidator.validateImageFile(file);

        Optional<Provider> providerOpt = repository.findById(id);
        if (providerOpt.isEmpty()) return ResponseEntity.notFound().build();

        Provider provider = providerOpt.get();

        try {
            // Upload to S3 under 'portfolio' subfolder
            String photoUrl = s3Service.uploadFile(file, id, "portfolio");

            // Add to the list
            List<String> portfolioPhotos = Optional.ofNullable(provider.getPortfolioPhotos()).orElseGet(() -> {
                provider.setPortfolioPhotos(new ArrayList<>());
                return provider.getPortfolioPhotos();
            });
            portfolioPhotos.add(photoUrl);

            Provider updated = repository.save(provider);
            //logger.info("Portfolio photo added to provider: {}", id);
            return ResponseEntity.ok(updated);
        } catch (IOException e) {
            logger.error("Portfolio upload failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * PORTFOLIO PHOTO DELETE: Removes a photo from S3 and the provider's list.
     * Maps to DELETE /api/providers/{id}/photos?url=...
     */
    @DeleteMapping("/{id}/photos")
    public ResponseEntity<?> deletePortfolioPhoto(
            @PathVariable String id,
            @RequestParam("url") String url) {

        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body("Photo URL is required");
        }

        Optional<Provider> providerOpt = repository.findById(id);
        if (providerOpt.isEmpty()) return ResponseEntity.notFound().build();

        Provider provider = providerOpt.get();

        try {
            // 1. Delete from S3 storage
            s3Service.deleteFile(url);

            // 2. Remove from the local list in MongoDB
            List<String> portfolioPhotos = Optional.ofNullable(provider.getPortfolioPhotos()).orElseGet(() -> {
                provider.setPortfolioPhotos(new ArrayList<>());
                return provider.getPortfolioPhotos();
            });
            portfolioPhotos.remove(url);

            Provider updated = repository.save(provider);
            //logger.info("Portfolio photo deleted for provider: {}", id);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logger.error("Failed to delete portfolio photo: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProvider(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        //logger.info("Deleting Provider ID: {}", id);
        Optional<Provider> providerOpt = repository.findById(id);
        if (providerOpt.isPresent()) {
            repository.delete(providerOpt.get());
            return ResponseEntity.ok(Map.of("message", "Provider deleted successfully"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getProviderByCustomerId(@PathVariable String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body("Customer ID is required");
        }
        return repository.findByCustomerId(customerId)
                .map(provider -> ResponseEntity.ok(provider))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> saveProvider(@RequestBody Provider provider) {
        if (provider == null) {
            return ResponseEntity.badRequest().body("Provider data is required");
        }

        // Manual validation BEFORE idempotency check
        String name = provider.getName();
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body("Provider Name is required");
        }
        
        String customerId = provider.getCustomerId();
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body("Customer ID is required");
        }

        if (provider.getServiceCategory() == null || provider.getServiceCategory().isEmpty()) {
            return ResponseEntity.badRequest().body("Service category is required");
        }
        
        String serviceDeliveryType = provider.getServiceDeliveryType();
        if (serviceDeliveryType == null || serviceDeliveryType.isBlank()) {
            return ResponseEntity.badRequest().body("Service delivery type is required");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null) ? auth.getName() : null;
        
        if (userEmail == null || userEmail.equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Optional<Customer> customerOpt = customerRepository.findByEmail(userEmail);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }
        
        Customer currentCustomer = customerOpt.get();

        if (!currentCustomer.getId().equals(customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only create a provider profile for yourself."));
        }

        // Idempotency: Check if provider already exists for this customer
        Optional<Provider> existingProvider = repository.findByCustomerId(currentCustomer.getId());
        if (existingProvider.isPresent() && provider.getId() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Provider profile already exists for this customer."));
        }

        return ResponseEntity.ok(repository.save(provider));
    }
}
