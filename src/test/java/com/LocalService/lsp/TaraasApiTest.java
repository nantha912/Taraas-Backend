package com.LocalService.lsp;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TaraasApiTest extends BaseApiTest {

    private static String createdCustomerId;
    private static String createdProviderId;
    private static String createdOfferId;
    private static String createdTransactionId;
    private static String createdReviewId;
    private static String refreshToken;
    private static String accessToken;
    private static String testEmail = "automation_" + System.currentTimeMillis() + "@example.com";
    private static String testPassword = "SecurePassword123!";

    @AfterAll
    public static void globalCleanup() {
        // Robust cleanup: Attempt to delete created resources if they exist.
        System.out.println("Cleaning up test resources...");

        // 1. Delete Review
        if (createdReviewId != null) {
            given().header("Authorization", "Bearer " + accessToken)
                    .when().delete("/api/reviews/" + createdReviewId)
                    .then().log().ifValidationFails();
        }

        // 2. Delete Transaction
        if (createdTransactionId != null) {
            given().when().delete("/api/transactions/" + createdTransactionId)
                    .then().log().ifValidationFails();
        }

        // 3. Delete Offer (if not already deleted by test99)
        if (createdOfferId != null) {
            given().header("Authorization", "Bearer " + accessToken)
                    .when().delete("/api/offers/" + createdOfferId)
                    .then().log().ifValidationFails();
        }

        // 4. Delete Provider
        if (createdProviderId != null) {
            given().header("Authorization", "Bearer " + accessToken)
                    .when().delete("/api/providers/" + createdProviderId)
                    .then().log().ifValidationFails();
        }

        // 5. Delete Customer
        if (createdCustomerId != null) {
            given().header("Authorization", "Bearer " + accessToken)
                    .when().delete("/api/customers/customer/" + createdCustomerId)
                    .then().log().ifValidationFails();
        }
    }

    // --- 1. AUTHENTICATION & SECURITY ---

    @Test
    @Order(1)
    @DisplayName("Customer: Register a new user for subsequent tests")
    public void test01_RegisterCustomer() {
        // 1. Request OTP
        Map<String, String> otpRequest = new HashMap<>();
        otpRequest.put("email", testEmail);
        given()
                .spec(requestSpec)
                .body(otpRequest)
                .when()
                .post("/api/auth/otp/send")
                .then()
                .statusCode(200);

        // 2. Intercept and mock OTP in DB (bypass real email)
        com.LocalService.lsp.model.OtpRecord otpRecord = otpRepository.findByEmail(testEmail)
                .orElseThrow(() -> new RuntimeException("OTP record not found for " + testEmail));
        
        // We set a known OTP "123456" by hashing it and updating the DB record
        otpRecord.setHashedOtp(passwordEncoder.encode("123456"));
        otpRecord.setExpiryTime(java.time.LocalDateTime.now().plusMinutes(5));
        otpRepository.save(otpRecord);

        // 3. Verify OTP via API
        Map<String, String> verifyRequest = new HashMap<>();
        verifyRequest.put("email", testEmail);
        verifyRequest.put("otp", "123456");
        given()
                .spec(requestSpec)
                .body(verifyRequest)
                .when()
                .post("/api/auth/otp/verify")
                .then()
                .statusCode(200);

        // 4. Register Customer
        Map<String, String> customer = new HashMap<>();
        customer.put("name", "Automation User");
        customer.put("email", testEmail);
        customer.put("password", testPassword);
        customer.put("city", "Mumbai");

        createdCustomerId = given()
                .spec(requestSpec)
                .body(customer)
                .when()
                .post("/api/customers/register")
                .then()
                .statusCode(201)
                .extract().path("id");
        
        Assertions.assertNotNull(createdCustomerId);
    }

    @Test
    @Order(2)
    @DisplayName("Auth: Login Success")
    public void test02_LoginSuccess() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", testEmail);
        credentials.put("password", testPassword);

        Response response = given()
                .spec(requestSpec)
                .body(credentials)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("refreshToken", notNullValue())
                .extract().response();

        accessToken = response.path("token");
        refreshToken = response.path("refreshToken");
    }

    @Test
    @Order(3)
    @DisplayName("Auth: Refresh Token Success")
    public void test03_RefreshToken() {
        given()
                .header("Authorization", "Bearer " + refreshToken)
                .when()
                .post("/api/auth/refresh")
                .then()
                .statusCode(200)
                .body("token", notNullValue());
    }

    @Test
    @Order(4)
    @DisplayName("Auth: Negative - Invalid Login")
    public void test05_InvalidLogin() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", testEmail);
        credentials.put("password", "wrongpassword");

        given()
                .spec(requestSpec)
                .body(credentials)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401);
    }

    // --- 2. CUSTOMER MANAGEMENT ---

    @Test
    @Order(5)
    @DisplayName("Customer: Check Existence")
    public void test06_CheckExistence() {
        given()
                .queryParam("email", testEmail)
                .when()
                .get("/api/customers/customer/exists")
                .then()
                .statusCode(200)
                .body(equalTo("true"));
    }

    @Test
    @Order(6)
    @DisplayName("Customer: Fetch Profile")
    public void test07_FetchProfile() {
        given()
                .when()
                .get("/api/customers/customer/" + createdCustomerId)
                .then()
                .statusCode(200)
                .body("id", equalTo(createdCustomerId))
                .body("email", equalTo(testEmail))
                .body("name", equalTo("Automation User"))
                .body("profilePhotoUrl", notNullValue())
                .body("buyerCategory", notNullValue())
                .body("totalSpent", notNullValue());
    }

    @Test
    @Order(7)
    @DisplayName("Customer: Update Profile")
    public void test08_UpdateProfile() {
        Map<String, String> updates = new HashMap<>();
        updates.put("name", "Updated Automation Name");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(updates)
                .when()
                .put("/api/customers/customer/" + createdCustomerId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Updated Automation Name"));
    }

    @Test
    @Order(8)
    @DisplayName("Customer: Upload Photo")
    public void test09_UploadPhoto() {
        File testImage = new File("src/test/resources/test_avatar.jpg");
        
        given()
                .header("Authorization", "Bearer " + accessToken)
                .multiPart("file", testImage)
                .when()
                .post("/api/customers/customer/" + createdCustomerId + "/profile-photo")
                .then()
                .statusCode(200)
                .body("profilePhotoUrl", notNullValue());
    }

    // --- 3. PROVIDER MANAGEMENT ---

    @Test
    @Order(9)
    @DisplayName("Provider: Become Provider")
    public void test10_BecomeProvider() {
        Map<String, Object> provider = new HashMap<>();
        provider.put("customerId", createdCustomerId);
        provider.put("name", "Automation Pro Service");
        provider.put("serviceCategory", List.of("Plumbing"));
        provider.put("price", 500.0);
        provider.put("city", "Mumbai");
        provider.put("serviceDeliveryType", "LOCAL");

        createdProviderId = given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(provider)
                .when()
                .post("/api/providers")
                .then()
                .statusCode(200)
                .extract().path("id");
        
        Assertions.assertNotNull(createdProviderId);
    }

    @Test
    @Order(10)
    @DisplayName("Provider: Search Providers")
    public void test11_SearchProviders() {
        given()
                .queryParam("service", "Plumbing")
                .queryParam("city", "Mumbai")
                .queryParam("mode", "NEARBY")
                .when()
                .get("/api/providers/search")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(0));
    }

    // --- 4. OFFERS & PROMOTIONS ---

    @Test
    @Order(11)
    @DisplayName("Offer: Create Offer")
    public void test12_CreateOffer() {
        Map<String, Object> offer = new HashMap<>();
        offer.put("providerId", createdProviderId);
        offer.put("title", "10% Automation Discount");
        offer.put("type", "PERCENTAGE");
        offer.put("value", "10");
        offer.put("minCategory", "VERIFIED");

        createdOfferId = given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(offer)
                .when()
                .post("/api/offers")
                .then()
                .statusCode(200)
                .extract().path("id");
    }

    @Test
    @Order(12)
    @DisplayName("Offer: Fetch Active Offers")
    public void test13_FetchActiveOffers() {
        given()
                .queryParam("category", "Plumbing")
                .when()
                .get("/api/offers/active")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    // --- 5. TRANSACTIONS ---

    @Test
    @Order(13)
    @DisplayName("Transaction: Initiate Transaction")
    public void test14_InitiateTransaction() {
        Map<String, Object> tx = new HashMap<>();
        tx.put("providerId", createdProviderId);
        tx.put("providerName", "Automation Pro Service");
        tx.put("customerId", createdCustomerId);
        tx.put("customerName", "Automation User");
        tx.put("amount", 500.0);

        createdTransactionId = given()
                .spec(requestSpec)
                .body(tx)
                .when()
                .post("/api/transactions/initiate")
                .then()
                .statusCode(200)
                .body("status", equalTo("INITIATED"))
                .extract().path("id");
    }

    @Test
    @Order(14)
    @DisplayName("Transaction: Confirm Payment")
    public void test15_ConfirmPayment() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .put("/api/transactions/" + createdTransactionId + "/confirm-payment")
                .then()
                .statusCode(200)
                .body("status", equalTo("CUSTOMER_CONFIRMED"));
    }

    // --- 7. REVIEWS ---

    @Test
    @Order(15)
    @DisplayName("Review: Submit Review")
    public void test16_SubmitReview() {
        Map<String, Object> review = new HashMap<>();
        review.put("providerId", createdProviderId);
        review.put("providerName", "Automation Pro Service");
        review.put("customerId", createdCustomerId);
        review.put("customerName", "Automation User");
        review.put("rating", 5);
        review.put("text", "Excellent automation test review!");

        createdReviewId = given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(review)
                .when()
                .post("/api/reviews")
                .then()
                .statusCode(200)
                .extract().path("id");
    }

    // --- 8. BUSINESS INTELLIGENCE ---

    @Test
    @Order(16)
    @DisplayName("Insights: Record Lead")
    public void test17_RecordLead() {
        Map<String, String> lead = new HashMap<>();
        lead.put("providerId", createdProviderId);
        lead.put("customerId", createdCustomerId);
        lead.put("customerName", "Automation User");
        lead.put("contactMethod", "WHATSAPP");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(lead)
                .when()
                .post("/api/insights/lead")
                .then()
                .statusCode(400);
    }

    // --- 9. ADMINISTRATIVE OPERATIONS ---

    @Test
    @Order(17)
    @DisplayName("Admin: Calculate Commission (Unauthorized)")
    public void test18_AdminCalculateCommissionUnauthorized() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("billingMonth", "2026-03");

        // Use a standard user token, which should be forbidden for admin endpoints
        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(payload)
                .when()
                .post("/api/admin/commission/calculate")
                .then()
                .statusCode(403);
    }

    // ====================================================================
    // --- HARDENING & SECURITY SCENARIOS (CUSTOMER & PROVIDER) ---
    // ====================================================================

    // --- Data Providers for Parameterized Tests ---

    static Stream<Arguments> provideInvalidCustomerPayloads() {
    return Stream.of(
        Arguments.of("Missing Name", Map.of(
            "email", "err_name_" + UUID.randomUUID() + "@test.com", 
            "password", "password123"
        )),
        Arguments.of("Missing Password", Map.of(
            "name", "Test User", 
            "email", "err_pass_" + UUID.randomUUID() + "@test.com"
        )),
        Arguments.of("Invalid Email", Map.of(
            "name", "Test User", 
            "email", "not-an-email", // This should always trigger 400
            "password", "password123"
        )),
        Arguments.of("Weak Password", Map.of(
            "name", "Test User", 
            "email", "err_weak_" + UUID.randomUUID() + "@test.com", 
            "password", "123"
        ))
        );
    }

    static Stream<Arguments> provideInvalidProviderPayloads() {
        return Stream.of(
            Arguments.of("Missing Category", Map.of("name", "Test Pro", "serviceDeliveryType", "LOCAL")),
            Arguments.of("Missing Delivery Type", Map.of("name", "Test Pro", "serviceCategory", List.of("Plumbing"))),
            Arguments.of("Missing Name", Map.of("serviceCategory", List.of("Plumbing"), "serviceDeliveryType", "LOCAL"))
        );
    }
    static Stream<Arguments> provideInvalidOfferPayloads() {
        return Stream.of(
            Arguments.of("Missing Title", Map.of("type", "PERCENTAGE", "value", "10", "minCategory", "VERIFIED")),
            Arguments.of("Missing Value", Map.of("title", "Promo", "type", "PERCENTAGE", "minCategory", "VERIFIED"))
        );
    }

    static Stream<Arguments> provideInvalidTransactionPayloads() {
        return Stream.of(
            Arguments.of("Amount Less Than 20", Map.of("providerName", "Pro", "customerName", "Cust", "amount", 10.0)),
            Arguments.of("Negative Amount", Map.of("providerName", "Pro", "customerName", "Cust", "amount", -50.0)),
            Arguments.of("Missing Amount", Map.of("providerName", "Pro", "customerName", "Cust"))
        );
    }

    static Stream<Arguments> provideInvalidReviewPayloads() {
        return Stream.of(
            Arguments.of("Rating > 5", Map.of("providerName", "Pro", "customerName", "Cust", "rating", 6, "text", "Great")),
            Arguments.of("Rating < 1", Map.of("providerName", "Pro", "customerName", "Cust", "rating", 0, "text", "Bad")),
            Arguments.of("Missing Text", Map.of("providerName", "Pro", "customerName", "Cust", "rating", 4)),
            Arguments.of("SQL Injection in Text", Map.of("rating", 5, "text", "DROP TABLE reviews;"))
        );
    }
    static Stream<Arguments> provideInvalidInsightPayloads() {
        return Stream.of(
            Arguments.of("Invalid Contact Method", Map.of("contactMethod", "EMAIL")), // Must be PHONE or WHATSAPP
            Arguments.of("Missing Contact Method", Map.of("dummy", "data"))
        );
    }

    // --- Customer Hardening ---

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideInvalidCustomerPayloads")
    @Order(20)
    @DisplayName("Customer Negative: Registration Validation Failures")
    public void test20_CustomerRegistration_ValidationFailures(String scenario, Map<String, String> payload) {
        given()
                .spec(requestSpec)
                .body(payload)
                .when()
                .post("/api/customers/register")
                .then()
                .statusCode(400); // Expect Bad Request
    }

    @Test
    @Order(21)
    @DisplayName("Customer Negative: Duplicate Registration")
    public void test21_CustomerRegistration_Duplicate() {
        // Must verify OTP first to reach the registration logic
        given().spec(requestSpec).body(Map.of("email", testEmail)).when().post("/api/auth/otp/send");
        com.LocalService.lsp.model.OtpRecord otpRecord = otpRepository.findByEmail(testEmail).get();
        otpRecord.setHashedOtp(passwordEncoder.encode("123456"));
        otpRecord.setExpiryTime(java.time.LocalDateTime.now().plusMinutes(5));
        otpRepository.save(otpRecord);
        given().spec(requestSpec).body(Map.of("email", testEmail, "otp", "123456")).when().post("/api/auth/otp/verify");

        Map<String, String> duplicateCustomer = new HashMap<>();
        duplicateCustomer.put("name", "Duplicate User");
        duplicateCustomer.put("email", testEmail); // Using the same email from test01
        duplicateCustomer.put("password", testPassword);
        duplicateCustomer.put("city", "Mumbai");

        given()
                .spec(requestSpec)
                .body(duplicateCustomer)
                .when()
                .post("/api/customers/register")
                .then()
                .statusCode(409); // Expect Conflict
    }

    @Test
    @Order(22)
    @DisplayName("Customer Auth: Forbidden Profile Update (Identity Spoofing)")
    public void test22_CustomerUpdate_Forbidden() {
        Map<String, String> updates = new HashMap<>();
        updates.put("name", "Hacked Name");

        // Attempting to update a different user's ID (appending "999" to fake another ID)
        String spoofedCustomerId = createdCustomerId + "999"; 

        given()
                .header("Authorization", "Bearer " + accessToken) // Logged in as original user
                .spec(requestSpec)
                .body(updates)
                .when()
                .put("/api/customers/customer/" + spoofedCustomerId)
                .then()
                .statusCode(403); // Expect Forbidden
    }

    // --- Provider Hardening ---

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideInvalidProviderPayloads")
    @Order(23)
    @DisplayName("Provider Negative: Registration Validation Failures")
    public void test23_ProviderRegistration_ValidationFailures(String scenario, Map<String, Object> payload) {
        // Automatically inject the valid customer ID so only the parameterized fields fail
        Map<String, Object> finalPayload = new HashMap<>(payload);
        finalPayload.put("customerId", createdCustomerId);

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(finalPayload)
                .when()
                .post("/api/providers")
                .then()
                .statusCode(400); // Expect Bad Request
    }

    @Test
    @Order(24)
    @DisplayName("Provider Negative: Duplicate Provider Profile")
    public void test24_ProviderRegistration_Duplicate() {
        Map<String, Object> provider = new HashMap<>();
        provider.put("customerId", createdCustomerId); // Already used in test10
        provider.put("name", "Second Pro Service");
        provider.put("serviceCategory", List.of("Plumbing"));
        provider.put("serviceDeliveryType", "LOCAL");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(provider)
                .when()
                .post("/api/providers")
                .then()
                .statusCode(409); // Expect Conflict
    }

    @Test
    @Order(25)
    @DisplayName("Provider Auth: Identity Spoofing during Creation")
    public void test25_Provider_AuthSpoofing() {
        Map<String, Object> provider = new HashMap<>();
        provider.put("customerId", "some-other-customer-id-12345"); // Not matching the token
        provider.put("name", "Spoofed Pro Service");
        provider.put("serviceCategory", List.of("Cleaning"));
        provider.put("serviceDeliveryType", "LOCAL");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(provider)
                .when()
                .post("/api/providers")
                .then()
                .statusCode(403); // Expect Forbidden because token ID doesn't match payload ID
    }
    // ====================================================================
    // --- HARDENING: OFFERS, TRANSACTIONS, & REVIEWS ---
    // ====================================================================

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideInvalidOfferPayloads")
    @Order(26)
    @DisplayName("Offer Negative: Creation Validation Failures")
    public void test26_Offer_ValidationFailures(String scenario, Map<String, Object> payload) {
        Map<String, Object> finalPayload = new HashMap<>(payload);
        finalPayload.put("providerId", createdProviderId);

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(finalPayload)
                .when()
                .post("/api/offers")
                .then()
                .statusCode(400); 
    }

    @Test
    @Order(27)
    @DisplayName("Offer Auth: Spoofing Provider ID")
    public void test27_Offer_Spoofing() {
        Map<String, Object> offer = new HashMap<>();
        offer.put("providerId", "different-provider-id-999"); // Trying to create an offer for someone else
        offer.put("title", "Hacked Discount");
        offer.put("type", "PERCENTAGE");
        offer.put("value", "50");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(offer)
                .when()
                .post("/api/offers")
                .then()
                .statusCode(403); 
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideInvalidTransactionPayloads")
    @Order(28)
    @DisplayName("Transaction Negative: Amount & Field Validation")
    public void test28_Transaction_ValidationFailures(String scenario, Map<String, Object> payload) {
        Map<String, Object> finalPayload = new HashMap<>(payload);
        finalPayload.put("providerId", createdProviderId);
        finalPayload.put("customerId", createdCustomerId);

        given()
                .spec(requestSpec)
                .body(finalPayload)
                .when()
                .post("/api/transactions/initiate")
                .then()
                .statusCode(400); 
    }

    @Test
    @Order(29)
    @DisplayName("Transaction Logic: Customer & Provider are the same ID")
    public void test29_Transaction_SameUserLogicError() {
        Map<String, Object> tx = new HashMap<>();
        tx.put("providerId", createdCustomerId); // Deliberately using Customer ID for Provider
        tx.put("customerId", createdCustomerId);
        tx.put("amount", 500.0);

        given()
                .spec(requestSpec)
                .body(tx)
                .when()
                .post("/api/transactions/initiate")
                .then()
                .statusCode(400); 
    }

    @Test
    @Order(30)
    @DisplayName("Transaction Role Auth: Customer tries to Verify Payment")
    public void test30_Transaction_RoleViolation() {
        // Customer initiated it in test14. Only Provider should verify.
        // We are using the Customer's token here, which should fail.
        given()
                .header("Authorization", "Bearer " + accessToken) 
                .when()
                .put("/api/transactions/" + createdTransactionId + "/verify")
                .then()
                .statusCode(403); 
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideInvalidReviewPayloads")
    @Order(31)
    @DisplayName("Review Negative: Rating & Text Validation")
    public void test31_Review_ValidationFailures(String scenario, Map<String, Object> payload) {
        Map<String, Object> finalPayload = new HashMap<>(payload);
        finalPayload.put("providerId", createdProviderId);
        finalPayload.put("customerId", createdCustomerId);

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(finalPayload)
                .when()
                .post("/api/reviews")
                .then()
                .statusCode(400); 
    }

    @Test
    @Order(32)
    @DisplayName("Review Auth: Editing Someone Else's Review")
    public void test32_Review_SpoofingEdit() {
        Map<String, Object> reviewUpdate = new HashMap<>();
        reviewUpdate.put("customerId", "different-customer-id"); // Trying to swap the owner
        reviewUpdate.put("rating", 1);
        reviewUpdate.put("text", "I am editing your review!");
        reviewUpdate.put("providerId", createdProviderId);
        reviewUpdate.put("providerName", "Automation Pro Service");
        reviewUpdate.put("customerName", "Automation User");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(reviewUpdate)
                .when()
                .put("/api/reviews/" + createdReviewId)
                .then()
                .statusCode(403); // Expect Forbidden or Bad Request
    }
    // ====================================================================
    // --- HARDENING: INSIGHTS, ADMIN, & DELETE SECURITY ---
    // ====================================================================

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideInvalidInsightPayloads")
    @Order(33)
    @DisplayName("Insights Negative: Validation Failures")
    public void test33_Insight_ValidationFailures(String scenario, Map<String, Object> payload) {
        Map<String, Object> finalPayload = new HashMap<>(payload);
        finalPayload.put("providerId", createdProviderId);
        finalPayload.put("customerId", createdCustomerId);

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(finalPayload)
                .when()
                .post("/api/insights/lead")
                .then()
                .statusCode(400); 
    }

    @Test
    @Order(34)
    @DisplayName("Insights Logic: Provider logs lead on their own profile")
    public void test34_Insight_OwnProfileError() {
        Map<String, Object> lead = new HashMap<>();
        lead.put("providerId", createdProviderId);
        lead.put("customerId", createdCustomerId); // Using the same linked ID
        lead.put("contactMethod", "PHONE");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .spec(requestSpec)
                .body(lead)
                .when()
                .post("/api/insights/lead")
                .then()
                .statusCode(400); // Expect bad request for self-interaction
    }

    @Test
    @Order(35)
    @DisplayName("Insights Auth: Privacy - View another provider's stats")
    public void test35_Insight_Privacy_Forbidden() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/insights/different-provider-id-999?year=2026&month=3")
                .then()
                .statusCode(403); 
    }

    /* * Note on Admin Validation: We cannot test "Invalid Month Format" (400) 
     * here because we are using a standard User token. Spring Security will 
     * intercept it and return 403 Forbidden before the 400 validation even triggers.
     * We proved the 403 happens back in test18!
     */

    @Test
    @Order(36)
    @DisplayName("Delete Security: No Token Provided")
    public void test36_Delete_Unauthorized() {
        given()
                // Notice: No Authorization header here
                .when()
                .delete("/api/offers/" + createdOfferId)
                .then()
                .statusCode(401); 
    }

    @Test
    @Order(37)
    @DisplayName("Delete Security: Identity Spoofing (Forbidden)")
    public void test37_Delete_ForbiddenOtherUser() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                // Try to delete a customer ID that does not match our token
                .delete("/api/customers/customer/some-other-id-999")
                .then()
                .statusCode(403); 
    }

    @Test
    @Order(38)
    @DisplayName("Delete Security: Role Violation (Customer deleting Transaction)")
    public void test38_DeleteTransaction_ForbiddenRole() {
        given()
                .header("Authorization", "Bearer " + accessToken) 
                .when()
                .delete("/api/transactions/" + createdTransactionId)
                .then()
                .statusCode(403); // Only Admin should be able to do this
    }

    // --- CLEANUP ---

    @Test
    @Order(99)
    @DisplayName("Cleanup: Delete Offer")
    public void test99_DeleteOffer() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .delete("/api/offers/" + createdOfferId)
                .then()
                .statusCode(200);
    }
}
