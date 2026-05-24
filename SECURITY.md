# Security Implementation Guide - Local Service Platform (LSP)

## Overview
This document outlines all security features implemented in the Local Service Platform backend. The implementation follows industry best practices and OWASP guidelines.

---

## Table of Contents
1. [JWT Authentication](#jwt-authentication)
2. [Password Security](#password-security)
3. [Input Validation](#input-validation)
4. [Endpoint Authorization](#endpoint-authorization)
5. [CORS Configuration](#cors-configuration)
6. [Rate Limiting](#rate-limiting)
7. [HTTPS/SSL Setup](#httpssl-setup)
8. [Sensitive Data Protection](#sensitive-data-protection)
9. [File Upload Validation](#file-upload-validation)
10. [Security Headers](#security-headers)

---

## JWT Authentication

### Overview
JWT (JSON Web Token) authentication provides stateless, token-based security for REST APIs.

### Key Classes
- **JwtTokenProvider** (`security/JwtTokenProvider.java`): Generates and validates JWT tokens
- **JwtAuthenticationFilter** (`security/JwtAuthenticationFilter.java`): Validates tokens on each request
- **AuthController** (`controller/AuthController.java`): Handles login and token refresh

### Configuration
**Location**: `application.properties`
```properties
jwt.expiration=86400000           # Token expires in 24 hours
jwt.refreshExpiration=604800000   # Refresh token expires in 7 days
jwt.secret=your-secret-key...     # CHANGE IN PRODUCTION
```

### How to Use

#### 1. Login and Get Token
```bash
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "type": "Bearer",
  "username": "John Doe",
  "email": "user@example.com"
}
```

#### 2. Use Token in Requests
```bash
curl -X GET http://localhost:5000/api/customers/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

#### 3. Refresh Expired Token
```bash
curl -X POST http://localhost:5000/api/auth/refresh \
  -H "Authorization: Bearer <refresh-token>"
```

### Token Validation
- Tokens are validated on every protected endpoint
- Invalid tokens return HTTP 403 Forbidden
- Expired tokens must be refreshed

---

## Password Security

### Overview
Passwords are hashed using BCrypt with a salt factor of 10.

### Implementation
```java
@Autowired
private PasswordEncoder passwordEncoder;

// Hashing password during registration
String hashedPassword = passwordEncoder.encode(plainTextPassword);

// Verifying password during login
if (passwordEncoder.matches(loginPassword, storedHash)) {
    // Authentication successful
}
```

### Security Measures
- ✅ Passwords are never stored in plain text
- ✅ BCrypt automatically generates random salt
- ✅ Rainbow table attacks are prevented
- ✅ Password complexity is validated (min 8 characters)

### Best Practices
1. Never expose password hashes in logs
2. Use HTTPS to prevent password interception
3. Implement password reset with secure token
4. Add rate limiting to login attempts

---

## Input Validation

### Overview
All API input is validated using Jakarta Validation annotations to prevent injection attacks and data integrity issues.

### Validation Annotations Used

#### Customer Model
```java
@NotBlank(message = "Name cannot be empty")
@Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
private String name;

@NotBlank(message = "Email cannot be empty")
@Email(message = "Email must be valid")
private String email;
```

#### Provider Model
```java
@Pattern(regexp = "^(\\+91)?[6-9]\\d{9}$|^$", message = "Invalid phone number")
private String phoneNumber;

@Min(value = 0, message = "Price cannot be negative")
private Double price;
```

#### Review Model
```java
@NotNull(message = "Rating cannot be null")
@Min(value = 1, message = "Rating must be at least 1")
@Max(value = 5, message = "Rating must not exceed 5")
private Integer rating;
```

### Using @Valid in Controllers
```java
@PostMapping("/create")
public ResponseEntity<?> createOffer(@Valid @RequestBody Offer offer) {
    // Validation automatically checks all @NotNull, @Email, etc.
    // Invalid requests return 400 Bad Request with error details
}
```

### Error Response Format
```json
{
  "email": "Email must be valid",
  "name": "Name must be between 2 and 100 characters",
  "rating": "Rating must be at least 1"
}
```

### Supported Validators
- `@NotNull`, `@NotBlank`, `@NotEmpty` - Field not empty
- `@Size(min, max)` - String length validation
- `@Email` - Email format validation
- `@Min`, `@Max` - Numeric range validation
- `@DecimalMin`, `@DecimalMax` - Decimal number range
- `@Pattern(regexp)` - Regex pattern matching

---

## Endpoint Authorization

### Overview
Role-based access control (RBAC) is implemented using `@PreAuthorize` annotations.

### Public Endpoints (No Authentication Required)
```
POST   /api/otp/**              - OTP generation and verification
POST   /api/auth/login          - Customer login
POST   /api/auth/refresh        - Token refresh
POST   /api/auth/verify-otp     - OTP verification
```

### Protected Endpoints (Authentication Required)
```
GET    /api/customers/**        - Customer operations
GET    /api/providers/**        - Provider operations
POST   /api/offers/**           - Offer creation
POST   /api/transactions/**     - Transaction management
POST   /api/reviews/**          - Review submission
```

### Admin-Only Endpoints
```
POST   /api/admin/commission/calculate - Commission calculation
```

**Authentication:**
```java
@PostMapping("/calculate")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> triggerCalculation(@RequestBody Map<String, Object> payload) {
    // Only users with ROLE_ADMIN can access this endpoint
}
```

### Custom Role Checking
```java
// Check single role
@PreAuthorize("hasRole('ADMIN')")

// Check multiple roles
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERUSER')")

// Check custom authority
@PreAuthorize("hasAuthority('WRITE_OFFERS')")

// Method-level security
@Secured("ROLE_ADMIN")
```

---

## CORS Configuration

### Overview
CORS (Cross-Origin Resource Sharing) is configured to allow requests only from trusted origins.

### Allowed Origins
```properties
- http://localhost:5173        (Development)
- http://127.0.0.1:5173       (Development alternate)
- https://taraas.com          (Production)
- https://www.taraas.com      (Production with www)
- https://api.taraas.com      (API subdomain)
```

### Allowed Methods
```
GET, POST, PUT, DELETE, PATCH, OPTIONS
```

### Allowed Headers
```
Authorization
Content-Type
Cache-Control
X-Requested-With
Accept
Origin
X-API-KEY
```

### Update CORS Origins
Edit `SecurityConfig.corsConfigurationSource()`:
```java
configuration.setAllowedOrigins(Arrays.asList(
    "https://yourdomain.com",
    "https://www.yourdomain.com"
));
```

---

## Rate Limiting

### Overview
Global rate limiting prevents brute force attacks and DDoS attempts.

### Configuration
- **Limit**: 100 requests per minute per client IP
- **Enforcement**: Applied before JWT authentication filter
- **Response**: HTTP 429 Too Many Requests

### Implementation
```java
@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final int REQUESTS_PER_MINUTE = 100;
    // Bucket4j library manages token bucket algorithm
}
```

### Error Response
```json
{
  "error": "Rate limit exceeded. Max 100 requests per minute."
}
```

### Adjust Rate Limit
Edit `RateLimitingFilter.createNewBucket()`:
```java
Bandwidth limit = Bandwidth.classic(200, Refill.intervally(200, Duration.ofMinutes(1)));
```

---

## HTTPS/SSL Setup

### Development (HTTP)
Currently runs on HTTP for local development:
```properties
server.port=5000
```

### Production (HTTPS)

#### Option 1: Using Self-Signed Certificate
```bash
# Generate keystore
keytool -genkey -alias tomcat -storetype PKCS12 -keyalg RSA \
  -keysize 2048 -keystore keystore.p12 -validity 365

# Copy to src/main/resources/
cp keystore.p12 src/main/resources/
```

#### Option 2: Using Let's Encrypt (Recommended)
```bash
# Obtain certificate
certbot certonly --standalone -d api.taraas.com

# Convert to PKCS12
openssl pkcs12 -export -in /etc/letsencrypt/live/api.taraas.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/api.taraas.com/privkey.pem \
  -out keystore.p12 -name tomcat
```

#### Enable HTTPS in application.properties
```properties
# SSL Configuration
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=your-keystore-password
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=tomcat

# HTTP to HTTPS Redirect
server.http2.enabled=true
server.port=8443

# Security Headers (automatically applied)
# HSTS, CSP, X-Frame-Options enabled in SecurityConfig
```

---

## Sensitive Data Protection

### Overview
Sensitive fields (passwords, API keys, customer IDs) are excluded from API responses.

### DTOs for Safe Responses

#### CustomerResponse
- ✅ Includes: id, name, email, profilePhotoUrl, city, totalSpent, buyerCategory
- ❌ Excludes: password

#### ProviderResponse
- ✅ Includes: All public provider details
- ❌ Excludes: None (customerId write-only)

### Using @JsonProperty
```java
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String password;  // Not included in JSON responses
```

### API Response Example
```json
{
  "id": "customer123",
  "name": "John Doe",
  "email": "john@example.com",
  "city": "Delhi",
  "totalSpent": 5000.00,
  "buyerCategory": "VERIFIED"
  // Password NOT included
}
```

---

## File Upload Validation

### Overview
File uploads are validated for type, size, and content to prevent malicious files.

### Validation Rules
- **Allowed Types**: JPEG, PNG, GIF, WebP
- **Maximum Size**: 5MB
- **Magic Number Check**: Verifies file signature

### Implementation
```java
@PostMapping("/upload-profile-photo")
public ResponseEntity<?> uploadProfilePhoto(@RequestParam("file") MultipartFile file) {
    FileUploadValidator.validateImageFile(file);
    // Safe to process file
    s3Service.uploadFile(file);
}
```

### Validation Details
```java
// Check file size
if (file.getSize() > 5 * 1024 * 1024) {
    throw new IllegalArgumentException("File size must not exceed 5MB");
}

// Check MIME type
Set<String> allowedTypes = {"image/jpeg", "image/png", "image/gif", "image/webp"};
if (!allowedTypes.contains(file.getContentType())) {
    throw new IllegalArgumentException("Invalid file type");
}

// Verify file signature (magic number)
validateFileMagicNumber(header, extension);
```

### Error Response
```json
{
  "error": "File size must not exceed 5MB. Actual size: 7.5 MB"
}
```

---

## Security Headers

### HSTS (HTTP Strict Transport Security)
```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
```
Forces HTTPS-only connections for 1 year, including subdomains.

### CSP (Content Security Policy)
```
Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'
```
Prevents XSS attacks and restricts resource loading.

### X-Frame-Options
```
X-Frame-Options: DENY
```
Prevents clickjacking by disallowing the site from being embedded in iframes.

### X-XSS-Protection
```
X-XSS-Protection: 1; mode=block
```
Enables browser XSS protection filters.

---

## Testing Security

### Test JWT Token Generation
```bash
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

### Test Protected Endpoint
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:5000/api/customers/profile
```

### Test Invalid Token
```bash
curl -H "Authorization: Bearer invalid-token" \
  http://localhost:5000/api/customers/profile
# Should return 403 Forbidden
```

### Test Rate Limiting
```bash
# Send 101+ requests in 1 minute - should fail
for i in {1..101}; do
  curl http://localhost:5000/api/otp/send
done
# Last requests should return 429 Too Many Requests
```

### Test Input Validation
```bash
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"invalid-email","password":"short"}'
# Should return 400 with validation errors
```

---

## Migration Checklist

- [ ] Change `jwt.secret` in application.properties to a strong random string (256+ bits)
- [ ] Update database with customer roles (ROLE_ADMIN, ROLE_USER, etc.)
- [ ] Test JWT login endpoint with real credentials
- [ ] Configure HTTPS certificate for production
- [ ] Update CORS allowed origins for your domain
- [ ] Set up SMTP credentials in application-secrets.properties
- [ ] Enable rate limiting limits as per requirements
- [ ] Document API endpoints for frontend team
- [ ] Set up CI/CD pipeline for automated security scanning
- [ ] Conduct security audit before production deployment

---

## Additional Resources

- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [JWT.io](https://jwt.io/) - JWT documentation
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Validation](https://spring.io/guides/gs/validating-form-input/)

---

## Support

For security-related questions or vulnerabilities, contact the security team or log an issue tagged with `security`.
