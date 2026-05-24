#!/bin/bash
# Quick Start Guide for Local Service Platform Security

echo "================================"
echo "LSP Security Quick Start"
echo "================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 1. Start the application
echo -e "${BLUE}[1] Starting the Spring Boot application...${NC}"
echo "Run from project root:"
echo "  cd \"d:\\Local service provider - dev\\lsp\\lsp\""
echo "  .\\mvnw.cmd spring-boot:run"
echo ""
echo "Expected output: 'Started LspApplication' on port 5000"
echo ""

# 2. Login and get JWT token
echo -e "${BLUE}[2] Login to get JWT token${NC}"
echo "Run this command:"
cat << 'EOF'
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "customer123@example.com",
    "password": "secure_password_123"
  }'
EOF
echo ""
echo "You should get a response like:"
cat << 'EOF'
{
  "token": "eyJhbGciOiJIUzUxMi...",
  "refreshToken": "eyJhbGciOiJIUzUxMi...",
  "type": "Bearer",
  "username": "John Doe",
  "email": "customer123@example.com"
}
EOF
echo ""
echo "Save the token value for the next steps."
echo ""

# 3. Use token in protected endpoint
echo -e "${BLUE}[3] Use token in protected endpoint${NC}"
echo "Replace TOKEN with the token from step 2:"
echo ""
cat << 'EOF'
curl -X GET http://localhost:5000/api/customers/profile \
  -H "Authorization: Bearer TOKEN"
EOF
echo ""

# 4. Test validation
echo -e "${BLUE}[4] Test input validation${NC}"
echo "This request will fail validation (invalid email):"
echo ""
cat << 'EOF'
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "not-an-email",
    "password": "short"
  }'
EOF
echo ""
echo "You should get a 400 error with validation messages."
echo ""

# 5. Test rate limiting
echo -e "${BLUE}[5] Test rate limiting${NC}"
echo "Send 150 rapid requests to test rate limiting:"
echo ""
cat << 'EOF'
for i in {1..150}; do
  curl http://localhost:5000/api/otp/send
done
EOF
echo ""
echo "Requests 101-150 should return 429 (Too Many Requests)"
echo ""

# 6. Test admin protection
echo -e "${BLUE}[6] Test admin endpoint protection${NC}"
echo "This endpoint requires ROLE_ADMIN:"
echo ""
cat << 'EOF'
curl -X POST http://localhost:5000/api/admin/commission/calculate \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "billingMonth": "2026-03",
    "forceRecalculate": false
  }'
EOF
echo ""
echo "Users without ROLE_ADMIN will get 403 (Access Denied)"
echo ""

# 7. Refresh token
echo -e "${BLUE}[7] Refresh expired token${NC}"
cat << 'EOF'
curl -X POST http://localhost:5000/api/auth/refresh \
  -H "Authorization: Bearer REFRESH_TOKEN"
EOF
echo ""

echo -e "${GREEN}✓ Security implementation is complete!${NC}"
echo ""
echo "Key Features Implemented:"
echo "  ✓ JWT Authentication with 24-hour expiration"
echo "  ✓ Input validation on all fields"
echo "  ✓ Rate limiting (100 requests/minute)"
echo "  ✓ Admin endpoint protection with @PreAuthorize"
echo "  ✓ Password hashing with BCrypt"
echo "  ✓ File upload validation"
echo "  ✓ CORS restrictions"
echo "  ✓ Global exception handling"
echo ""
echo "See SECURITY.md for comprehensive documentation"
echo "See IMPLEMENTATION_SUMMARY.md for detailed guide"
echo ""
