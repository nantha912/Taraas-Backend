package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.OnboardingTracker;
import com.LocalService.lsp.model.SystemSettings;
import com.LocalService.lsp.repository.OnboardingTrackerRepository;
import com.LocalService.lsp.repository.SystemSettingsRepository;
import com.LocalService.lsp.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payments/subscription")
public class SubscriptionOnboardingController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionOnboardingController.class);

    @Autowired
    private SystemSettingsRepository systemSettingsRepository;

    @Autowired
    private OnboardingTrackerRepository onboardingTrackerRepository;

    @Autowired
    private PaymentService paymentService;

    /**
     * ENDPOINT A: Get Active Pricing Configuration
     * Publicly fetched by React form page to display offer text, base price, and promo deal.
     */
    @GetMapping("/config")
    public ResponseEntity<?> getSubscriptionConfig() {
        Optional<SystemSettings> configOpt = systemSettingsRepository.findById("subscription_config");
        if (configOpt.isPresent()) {
            return ResponseEntity.ok(configOpt.get());
        }
        
        // Seed fallback data context seamlessly if collection is completely clean
        SystemSettings fallback = new SystemSettings();
        fallback.setBaseAmount(5.0);
        fallback.setDiscountedAmount(1999.0);
        fallback.setOfferName("Early Bird Offer");
        return ResponseEntity.ok(fallback);
    }

    /**
     * ENDPOINT B: Pre-Onboard Validation & State Caching
     * Places the raw registration data form securely inside a temp tracking record document.
     */
    @PostMapping("/pre-onboard")
    public ResponseEntity<?> preOnboardProvider(@RequestBody Map<String, Object> formData) {
        if (formData == null || formData.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Form layout records required."));
        }

        try {
            OnboardingTracker tracker = new OnboardingTracker();
            tracker.setProviderFormData(formData);
            tracker.setStatus("PENDING_PAYMENT");
            
            OnboardingTracker savedTracker = onboardingTrackerRepository.save(tracker);
            
            //logger.info("Temporary onboarding cache generated with token ID: {}", savedTracker.getId());
            return ResponseEntity.ok(Map.of("trackerId", savedTracker.getId()));
        } catch (Exception e) {
            logger.error("Failed executing state preservation cache allocation trace:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ENDPOINT C: Create Secure Subscription Order
     * Takes the trackerId, checks the dynamic DB configuration price, and constructs a Razorpay transaction token.
     */
    @PostMapping("/create-order")
    public ResponseEntity<?> createSubscriptionOrder(@RequestBody Map<String, String> payload) {
        String trackerId = payload.get("trackerId");
        if (trackerId == null || trackerId.isBlank()) {
            return ResponseEntity.badRequest().body("trackerId parameter token is mandatory.");
        }

        Optional<OnboardingTracker> trackerOpt = onboardingTrackerRepository.findById(trackerId);
        if (trackerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Invalid onboarding checkpoint context.");
        }

        try {
            // Pull the authentic active discount price directly out of system setting collections
            SystemSettings config = systemSettingsRepository.findById("subscription_config")
                    .orElseGet(() -> {
                        SystemSettings s = new SystemSettings();
                        s.setDiscountedAmount(1999.0);
                        return s;
                    });

            Double secureAmount = config.getDiscountedAmount();
            
            // Execute order wrapper construction using your existing PaymentService logic hooks
            String razorpayOrderId = paymentService.createRazorpayOrder(secureAmount, "sub_track_" + trackerId);

            // Sync the tracking collection to know which Razorpay transaction goes with this form entry
            OnboardingTracker tracker = trackerOpt.get();
            tracker.setRazorpayOrderId(razorpayOrderId);
            onboardingTrackerRepository.save(tracker);

            Map<String, Object> response = new HashMap<>();
            response.put("razorpayOrderId", razorpayOrderId);
            response.put("razorpayKeyId", paymentService.getKeyId());
            response.put("amount", Math.round(secureAmount * 100)); // paise conversion

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed compiling onboarding verification signature tokens:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
        @Autowired
    private com.LocalService.lsp.repository.ProviderRepository providerRepository; // Inject your active provider repository

    /**
        * STEP 4: Cryptographic Verification & Provider Account Promotion
    * Validates the transaction signature and transforms the temporary tracker state 
    * into a fully active service provider profile.
    */
    @PostMapping("/verify-and-register")
    public ResponseEntity<?> verifyAndRegister(@RequestBody Map<String, String> payload) {
        if (payload == null) return ResponseEntity.badRequest().body("Payload required");

        String trackerId = payload.get("trackerId");
        if (trackerId == null || trackerId.isBlank()) {
            return ResponseEntity.badRequest().body("trackerId is required");
        }

        // 1. Fetch the temporary cached form data structure
        Optional<OnboardingTracker> trackerOpt = onboardingTrackerRepository.findById(trackerId);
        if (trackerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Onboarding tracker token not found.");
        }

        OnboardingTracker tracker = trackerOpt.get();
        
        // Idempotency check: Prevent duplicate registration attempts if a user spams the button
        if ("COMPLETED".equals(tracker.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "This registration has already been finalized."));
        }

        // 2. Validate the payment signature cryptographically using your existing PaymentService logic
        boolean isValid = paymentService.verifySignature(payload);
        if (!isValid) {
            logger.warn("Security Alert: Fraudulent subscription signature rejected for Tracker ID: {}", trackerId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "failed", "message", "Invalid payment signature transaction."));
        }

        try {
            // 3. Map the cached data into your live Provider domain class structures
            Map<String, Object> formMap = tracker.getProviderFormData();
            
            com.LocalService.lsp.model.Provider provider = new com.LocalService.lsp.model.Provider();
            
            // Populate standard properties systematically from your raw form context
            provider.setCustomerId(formMap.get("customerId").toString());
            provider.setName(formMap.get("name").toString());
            provider.setEmail(formMap.get("email").toString());
            provider.setPhoneNumber(formMap.get("phoneNumber").toString());
            provider.setWhatsappNumber(formMap.get("whatsappNumber").toString());
            provider.setUpiId(formMap.get("upiId").toString());
            provider.setDescription(formMap.get("description").toString());
            provider.setCity(formMap.get("city").toString());
            provider.setLocation(formMap.get("location").toString());
            provider.setServiceDeliveryType(formMap.get("serviceDeliveryType").toString());
            
            
            if (formMap.get("price") != null) {
                provider.setPrice(Double.valueOf(formMap.get("price").toString()));
            }
            
            if (formMap.get("serviceCategory") != null) {
                provider.setServiceCategory((java.util.List<String>) formMap.get("serviceCategory"));
            }
            
            if (formMap.get("latitude") != null && formMap.get("longitude") != null) {
                Double[] coords = new Double[]{
                    Double.valueOf(formMap.get("longitude").toString()),
                    Double.valueOf(formMap.get("latitude").toString())
                };
                provider.setCoordinates(coords);
            }

            // Map social media links securely
            provider.setInstagramLink(formMap.getOrDefault("instagramLink", "").toString());
            provider.setFacebookLink(formMap.getOrDefault("facebookLink", "").toString());
            provider.setYoutubeLink(formMap.getOrDefault("youtubeLink", "").toString());
            provider.setTwitterLink(formMap.getOrDefault("twitterLink", "").toString());
            provider.setWebsiteLink(formMap.getOrDefault("websiteLink", "").toString());

            // 4. Set up the dynamic Subscription tracking block
            java.time.LocalDateTime paymentTime = java.time.LocalDateTime.now();
            provider.setRole("PROVIDER");
            provider.setSubscriptionStatus("ACTIVE");
            provider.setSubscriptionPaidAmount(configAmountLookup());
            provider.setLastPaymentDate(paymentTime);
            
            // --- PRECISE YEARLY RENEWAL TIMESTAMP ---
            provider.setNextRenewalDate(paymentTime.plusYears(1)); // Stamped exactly 365 days out!
            provider.setRazorpayPaymentId(payload.get("razorpay_payment_id"));

            // 5. Commit the fully verified entity to MongoDB production collections
            com.LocalService.lsp.model.Provider activeProvider = providerRepository.save(provider);
            
            // 6. Mark the onboarding tracker as finalized to clear the staging loop
            tracker.setStatus("COMPLETED");
            onboardingTrackerRepository.save(tracker);

            //logger.info("Premium Onboarding complete! Account dynamically launched for Provider: {} (ID: {})", 
            //        activeProvider.getName(), activeProvider.getId());
            
            // Return details back to React so it can finish profile redirect tasks smoothly
            return ResponseEntity.ok(activeProvider);

        } catch (Exception e) {
            logger.error("Critical error mapping and finalizing premium registration parameters:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Payment verified but registration task crashed. Contact support."));
        }
    }

    /**
     * Lightweight secure utility helper to query configuration amounts internally for telemetry stamping
     */
    private Double configAmountLookup() {
        return systemSettingsRepository.findById("subscription_config")
                .map(SystemSettings::getDiscountedAmount)
                .orElse(1999.0);
    }

}