# API Test Scenarios: TaraasBackend

API Test Scenarios: TaraasBackend (Hardening & Automation Suite)
This document outlines the human-readable test scenarios for the Taraas Local Service Platform API. Scenarios are categorized by functional module and include Positive, Negative, and Authorization cases.

Roles Definition:

Visitor: Unauthenticated user.

User: Authenticated customer or service provider.

Admin: Authenticated user with ROLE_ADMIN.

1. Authentication & Security (/api/auth, /api/auth/otp)
Positive Cases (Already Automated)
Login Success: Visitor logs in with valid email and password. Receives JWT and refresh token.

Refresh Token Success: User requests a new access token using a valid refresh token header.

Send OTP: Visitor requests an OTP for a valid email address.

Verify OTP: Visitor provides the correct 6-digit OTP received via email.

Negative Cases (Hardening)
Invalid Login (No User): Visitor attempts login with an unregistered email -> Assert 401/404

Invalid Login (Wrong Pass): Visitor attempts login with a registered email but incorrect password -> Assert 401

Expired Refresh Token: User attempts to refresh with an expired token -> Assert 400

OTP Rate Limiting: Visitor requests more than 1 OTP within 60 seconds -> Assert 400/IllegalState

Invalid OTP: Visitor provides a wrong OTP code -> Assert 401

Authorization (RBAC)
Visitor: Can access all login, refresh, and OTP endpoints.

2. Customer Management (/api/customers)
Positive Cases (Already Automated)
Register Customer: Visitor creates a new account with valid details.

Cleanup: Delete the customer record from the database.

Check Existence: Visitor checks if an email is already registered.

Fetch Profile: Visitor/User views a public customer profile by ID (Tier info included).

Update Profile: User updates their own name or password.

Upload Photo: User uploads a valid profile image (JPEG/PNG, <5MB).

Cleanup: Revert to default or delete uploaded file from S3.

Negative Cases (Hardening)
[Parameterized] Validation Failures: Visitor tries to register with invalid payloads (Missing name, Missing password, Invalid email format) -> Assert 400

Duplicate Registration: Visitor tries to register with an existing email -> Assert 409 Conflict

Weak Password: Visitor tries to register with a password < 8 characters -> Assert 400

Invalid Photo Type: User uploads a .pdf or .exe file -> Assert 400

Authorization (RBAC)
Fetch Profile: Not logged-in user attempts to fetch private customer profile -> Assert 401

Update Profile: Logged-in user attempts to update a different user's profile/password -> Assert 403 Forbidden

Upload Photo: Logged-in user attempts to upload a photo for a different user -> Assert 403

Endpoint Security: Attempt to DELETE customer without logged-in user or using a different user's ID -> Assert 401/403

3. Provider Management (/api/providers)
Positive Cases (Already Automated)
Search Providers: Visitor searches for providers (Nearby via GPS / Remote mode).

Become Provider: User creates a professional profile linked to their customer ID.

Cleanup: Delete the provider profile.

Portfolio Management: User adds/deletes photos to their professional gallery.

Cleanup: Ensure S3 objects are removed upon deletion.

Fetch Provider: Logged-in user successfully fetches provider by ID/Customer ID.

Negative Cases (Hardening)
[Parameterized] Validation Failures: User tries to register provider missing required fields (Service Delivery Type, Name, Email, Customer ID, Service Category, Phone, WhatsApp, City, Location, Coordinates, Description, Upi Id) -> Assert 400

Duplicate Provider: Existing provider attempts to register as a provider again -> Assert 409 Conflict

Invalid Customer ID: Provider registration with an invalid/non-existent Customer ID -> Assert 404/400

Invalid Phone Format: User provides a non-10-digit phone number -> Assert 400

Portfolio Limit: Provider attempts to upload more than 10 portfolio photos -> Assert 400 Boundary Error

Authorization (RBAC)
Identity Spoofing: Provider registration using a valid Customer ID that belongs to a different user -> Assert 403

Fetch Provider: Not logged-in user attempts to fetch provider by ID or Customer ID -> Assert 401

Upload Assets: Not logged-in or different user attempts to upload provider profile/portfolio photos -> Assert 401/403

Endpoint Security: Attempt to DELETE provider or portfolio photos without logged-in user or using a different user's ID -> Assert 401/403

4. Offers & Promotions (/api/offers)
Positive Cases (Already Automated)
Create Offer: Provider creates a "10% Discount" offer for ELITE tier customers.

Cleanup: Delete the offer by ID.

Fetch Active Offers: Visitor views all currently active offers filtered by category.

Eligible Offers: User views offers they are eligible for based on their current Buyer Category.

Negative Cases (Hardening)
[Parameterized] Validation Failures: Create offer missing required fields (title, description, type, value, minCategory) -> Assert 400

Invalid Provider: Create offer with invalid/non-existent provider ID -> Assert 404/400

Expired Offer Visibility: Visitor should not see offers where endDate is in the past -> Assert 404/Empty List

Authorization (RBAC)
Creation Access: Create offer without logged-in user -> Assert 401

Role Access: Create offer by a logged-in user who is not a provider -> Assert 403

Identity Spoofing: Create offer using a Provider ID that belongs to a different user -> Assert 403

Endpoint Security: Provider A tries to DELETE Provider B's offer -> Assert 403

5. Transactions & Real-time Updates (/api/transactions)
Positive Cases (Already Automated)
Initiate Transaction: User (Customer) starts a payment for a service.

Cleanup: Delete the transaction record.

Confirm Payment: User (Customer) marks payment as sent.

Verify Transaction: User (Provider) confirms receipt, moving status to COMPLETED.

Live Stream (SSE): User opens an EventSource connection to receive real-time status updates.

Negative Cases (Hardening)
[Parameterized] Amount Validation: Initiate Transaction with invalid amounts (Missing amount, Not a number, Less than 20, Negative amount, Exceeds system max) -> Assert 400

Missing Data: Initiate Transaction with missing/invalid IDs or missing transaction note -> Assert 400

Logic Error: Initiate Transaction where Provider ID and Customer ID belong to the exact same person -> Assert 400

Invalid Status Transition: Visitor tries to move a REJECTED transaction to COMPLETED -> Assert 400

Transaction Not Found: User tries to confirm/verify/reject a non-existent transaction ID -> Assert 404

Authorization (RBAC)
Initiation Access: Initiate Transaction without logged-in user or using a Customer ID belonging to a different user -> Assert 401/403

Action Access: Confirm/Verify/Reject payment without logged-in user -> Assert 401

Role Violation: Confirm/Verify/Reject payment by the transaction-initiated customer (Verifying should be Provider only) -> Assert 403

Identity Spoofing: Confirm/Verify/Reject payment by a Provider who is different from the one in the Transaction object -> Assert 403

Visibility: Get transaction by customer/provider using a different logged-in user -> Assert 403

Endpoint Security: Attempt to DELETE Transaction accessed by Customer/Provider user -> Assert 403 (Only Admin can delete).

6. Payments & Billing (/api/payments, /api/statements)
Positive Cases (Already Automated)
Create Razorpay Order: Provider initiates a payment for their monthly commission statement.

Verify Payment Signature: System validates the cryptographic hash from Razorpay.

View Statements: Provider fetches their monthly billing history.

Negative Cases (Hardening)
Signature Mismatch: User sends a faked Razorpay signature -> Assert 400

Statement Already Paid: Provider tries to pay a statement that is already in PAID status -> Assert 400 Logic Error

Invalid Date Range: Get statement by provider with invalid date range (Start date after End date) -> Assert 400

Authorization (RBAC)
Statement Access: Get statement without logged-in user -> Assert 401

Identity Spoofing: Get statement where requested Provider ID is different from logged-in Provider ID -> Assert 403

7. Reviews & Feedback (/api/reviews)
Positive Cases (Already Automated)
Submit Review: User (Customer) leaves a 5-star review for a completed service.

Cleanup: Delete the review.

Update Review: User edits the text of their previous review.

View Provider Reviews: Visitor reads all reviews for a specific provider.

Negative Cases (Hardening)
[Parameterized] Validation Failures: Submit/Update review missing fields (Provider name, Customer name, Text) -> Assert 400

[Parameterized] Rating Boundaries: Submit/Update review with invalid ratings (Missing, Not a number, Less than 1, More than 5) -> Assert 400

Missing IDs: Submit review with invalid/missing Provider ID or Customer ID -> Assert 404/400

Security Injection: Submit review containing SQL Injection or XSS payload in the text field -> Assert 400 / Verify Sanitization

Authorization (RBAC)
Creation Access: Submit/Update review without logged-in user -> Assert 401

Identity Spoofing: Submit/Update review using a Customer ID that belongs to a different user -> Assert 403

Immutable Data: Update review attempting to change the Provider ID or Customer ID in the existing review object -> Assert 403/400

Endpoint Security: Delete review without logged-in user or using a customer ID not belonging to the current user -> Assert 401/403

8. Business Intelligence & Insights (/api/insights)
Positive Cases (Already Automated)
Record Profile View: Visitor/User views a profile (deduplicated by Session ID).

Record Lead: Visitor/User clicks "Contact" (deduplicated within 1 hour).

Monthly Insights: Provider views their turnover, view count, and lead history for a specific month.

Negative Cases (Hardening)
Logic Error: Record lead/view where the current logged-in Provider is logging a view/lead on their own profile -> Assert 400

Invalid Contact: Record lead missing contact Method or invalid contact method (Must be 'PHONE' or 'WHATSAPP') -> Assert 400

Missing IDs: Record lead/view with invalid/missing IDs -> Assert 404/400

Authorization (RBAC)
Event Access: Record lead/view without logged-in user -> Assert 401

Identity Spoofing: Record lead/view using a Customer ID that belongs to a different user -> Assert 403

Insight Privacy: User A tries to view the private turnover/insights data of Provider B -> Assert 403

9. Administrative Operations (/api/admin)
Positive Cases (Already Automated)
Calculate Commissions: Admin triggers the billing engine for a specific month (e.g., "2026-03").

Cleanup: Delete the generated statements for that month.

System Teardown: Admin user successfully deletes transaction records for system cleanup.

Negative Cases (Hardening)
Invalid Month Format: Admin provides "March-2026" instead of "2026-03" -> Assert 400

Authorization (RBAC)
Unauthorized Access: Non-admin User (Customer or Provider) or unauthenticated user tries to trigger commission calculation -> Assert 403/401
