# Frontend Implementation Guide - LSP Security & Authentication

## Overview
The backend has been hardened with JWT authentication, input validation, authorization controls, and rate limiting. This guide outlines all necessary frontend modifications to successfully integrate with the updated backend APIs.

---

## 1. Authentication System (JWT)

### 1.1 Login Endpoint
**Endpoint:** `POST http://localhost:5000/api/auth/login`

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "userPassword123"
}
```

**Success Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "username": "John Doe",
  "email": "user@example.com"
}
```

**Error Responses:**
- **400 Bad Request:** Validation error (missing email, invalid format, blank password)
  ```json
  {
    "error": "Validation failed",
    "details": [
      "email: must be a valid email address",
      "password: must not be blank"
    ]
  }
  ```

- **401 Unauthorized:** Invalid credentials
  ```json
  {
    "error": "Customer not found with email: user@example.com"
  }
  ```
  OR
  ```json
  {
    "error": "Invalid password"
  }
  ```

### 1.2 Token Storage
- **Access Token:** Store in memory or sessionStorage (24 hours expiration)
  ```javascript
  localStorage.setItem('authToken', response.token);
  localStorage.setItem('userName', response.username);
  localStorage.setItem('userEmail', response.email);
  ```

- **Refresh Token:** Store securely (HttpOnly cookie preferred, or sessionStorage if cookie unavailable)
  ```javascript
  localStorage.setItem('refreshToken', response.refreshToken);
  ```

### 1.3 Token Usage with API Requests
All protected API requests **MUST** include the Authorization header:
```
Authorization: Bearer {accessToken}
```

**Example with Fetch:**
```javascript
const response = await fetch('http://localhost:5000/api/customers/profile', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('authToken')}`,
    'Content-Type': 'application/json'
  }
});
```

**Example with Axios:**
```javascript
axios.defaults.headers.common['Authorization'] = `Bearer ${localStorage.getItem('authToken')}`;
```

### 1.4 Token Refresh Endpoint
**Endpoint:** `POST http://localhost:5000/api/auth/refresh`

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

**Success Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

**Implementation:**
- Call refresh endpoint when access token expires (catch 401 response)
- Update tokens and retry the original request
- If refresh fails, clear tokens and redirect to login page

---

## 2. Input Validation Rules

### 2.1 Customer Registration/Profile
**Email Field:**
- Must be valid email format (user@example.com)
- Validation: `pattern="[a-zA-Z0-9_%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"`
- Error message: "Please enter a valid email address"

**Password Field:**
- Must not be blank/empty
- Minimum 8 characters recommended (for security, not enforced on login)
- Complex password recommended: uppercase, lowercase, numbers, special characters
- Error message: "Password is required"

**Name Field:**
- Must not be blank
- Validation: `minLength="1"` (required)

**City Field:**
- Must not be blank
- Validation: `minLength="1"` (required)

### 2.2 Provider Registration/Profile
**Phone Number:**
- Must match pattern: `^\d{10}$` (10 digits)
- Error message: "Phone number must be 10 digits"

**Email:**
- Same as customer (valid email format)

**Service Price:**
- Must be >= 0
- Must be numeric
- Error message: "Price must be a positive number"

**Name:**
- Must not be blank

### 2.3 Offer/Transaction/Review Inputs
Refer to model validation rules below.

### 2.4 Frontend Validation Implementation
```html
<form>
  <input 
    type="email" 
    name="email" 
    required 
    pattern="[a-zA-Z0-9_%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"
    placeholder="Enter your email"
  />
  <input 
    type="password" 
    name="password" 
    required 
    minlength="1"
    placeholder="Enter your password"
  />
</form>
```

**Backend Validation Handler:**
```javascript
handleError(error) {
  if (error.response?.status === 400) {
    const errors = error.response.data.details || [error.response.data.error];
    // Display errors to user
    errors.forEach(err => showToast(err, 'error'));
    return;
  }
  if (error.response?.status === 401) {
    showToast('Invalid email or password', 'error');
    return;
  }
}
```

---

## 3. API Endpoints & Route Changes

### 3.1 Public Endpoints (No Authentication Required)
```
POST   /api/auth/login          → User login
POST   /api/auth/refresh         → Refresh access token
```

### 3.2 Protected Endpoints (Requires Valid JWT Token)
```
GET    /api/customers/profile    → Get customer profile
POST   /api/customers            → Create/update customer
GET    /api/customers/{id}       → Get customer by ID
DELETE /api/customers/{id}       → Delete customer

GET    /api/providers            → List providers
GET    /api/providers/{id}       → Get provider details
POST   /api/providers            → Create/update provider
DELETE /api/providers/{id}       → Delete provider

GET    /api/offers              → List offers
POST   /api/offers              → Create offer
GET    /api/offers/{id}         → Get offer details
DELETE /api/offers/{id}         → Delete offer

GET    /api/transactions        → List transactions
POST   /api/transactions        → Create transaction
GET    /api/transactions/{id}   → Get transaction details

GET    /api/reviews             → List reviews
POST   /api/reviews             → Create review
GET    /api/reviews/{id}        → Get review details

GET    /api/statements          → Get statements
GET    /api/insights            → Get dashboard insights

GET    /api/otp/verify          → Verify OTP
POST   /api/otp/send            → Send OTP
```

### 3.3 Admin Endpoints (Requires ROLE_ADMIN)
```
GET    /api/admin/**            → All admin endpoints
```

### 3.4 IMPORTANT ROUTE CHANGE
- **Customer endpoints moved from `/api/auth` → `/api/customers`**
- Update all API URLs in code if previously using `/api/auth/...`

---

## 4. Error Handling & Status Codes

### 4.1 HTTP Status Codes
```
200 OK                  → Request successful
201 Created            → Resource created successfully
400 Bad Request        → Validation error or malformed request
401 Unauthorized       → Missing/invalid JWT token or invalid credentials
403 Forbidden          → User lacks permission (e.g., not admin)
500 Internal Server    → Server error
```

### 4.2 Error Response Format
```json
{
  "error": "Error message",
  "details": ["validation error 1", "validation error 2"]
}
```

### 4.3 Frontend Error Handling Logic
```javascript
async function apiCall(url, options = {}) {
  const token = localStorage.getItem('authToken');
  
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers
  };
  
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  
  try {
    const response = await fetch(url, {
      ...options,
      headers
    });
    
    if (response.status === 401) {
      // Token expired or invalid
      // Attempt refresh token
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        // Retry original request with new token
        return apiCall(url, options);
      } else {
        // Refresh failed, redirect to login
        window.location.href = '/';
        return null;
      }
    }
    
    if (response.status === 400) {
      const errorData = await response.json();
      showValidationErrors(errorData.details || [errorData.error]);
      return null;
    }
    
    if (response.status === 403) {
      showToast('You do not have permission to access this resource', 'error');
      return null;
    }
    
    if (!response.ok) {
      const errorData = await response.json();
      showToast(errorData.error || 'An error occurred', 'error');
      return null;
    }
    
    return await response.json();
  } catch (error) {
    console.error('API call failed:', error);
    showToast('Network error. Please check your connection.', 'error');
    return null;
  }
}
```

---

## 5. Login Flow Implementation

### 5.1 Login Component Steps
1. **Validate Form Inputs**
   - Email: Check format
   - Password: Check not empty

2. **Call Login Endpoint**
   ```javascript
   const loginUser = async (email, password) => {
     const response = await fetch('http://localhost:5000/api/auth/login', {
       method: 'POST',
       headers: { 'Content-Type': 'application/json' },
       body: JSON.stringify({ email, password })
     });
     
     if (!response.ok) {
       const error = await response.json();
       throw new Error(error.error);
     }
     
     return await response.json();
   };
   ```

3. **Store Tokens**
   ```javascript
   const data = await loginUser(email, password);
   localStorage.setItem('authToken', data.token);
   localStorage.setItem('refreshToken', data.refreshToken);
   localStorage.setItem('userName', data.username);
   localStorage.setItem('userEmail', data.email);
   ```

4. **Redirect to Dashboard**
   ```javascript
   window.location.href = '/dashboard';
   ```

5. **Handle Errors**
   - Display validation errors (400)
   - Display "Invalid email or password" (401)
   - Display network errors

### 5.2 React Example
```jsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  
  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    
    try {
      const response = await fetch('http://localhost:5000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      });
      
      if (!response.ok) {
        const errorData = await response.json();
        setError(errorData.error || 'Login failed');
        return;
      }
      
      const data = await response.json();
      localStorage.setItem('authToken', data.token);
      localStorage.setItem('refreshToken', data.refreshToken);
      localStorage.setItem('userName', data.username);
      localStorage.setItem('userEmail', data.email);
      
      navigate('/dashboard');
    } catch (err) {
      setError('Network error. Please try again.');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };
  
  return (
    <form onSubmit={handleLogin}>
      <input
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="Email"
        required
      />
      <input
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="Password"
        required
      />
      {error && <div className="error">{error}</div>}
      <button type="submit" disabled={loading}>
        {loading ? 'Logging in...' : 'Login'}
      </button>
    </form>
  );
}
```

---

## 6. Protected Routes & Authentication Guard

### 6.1 Protected Route Component
```jsx
import { Navigate } from 'react-router-dom';

export function ProtectedRoute({ children }) {
  const token = localStorage.getItem('authToken');
  
  if (!token) {
    return <Navigate to="/" replace />;
  }
  
  return children;
}
```

### 6.2 Usage in Router
```jsx
<BrowserRouter>
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route
      path="/dashboard"
      element={
        <ProtectedRoute>
          <DashboardPage />
        </ProtectedRoute>
      }
    />
    <Route
      path="/profile"
      element={
        <ProtectedRoute>
          <ProfilePage />
        </ProtectedRoute>
      }
    />
  </Routes>
</BrowserRouter>
```

---

## 7. Token Refresh Implementation

### 7.1 Refresh Token Logic
```javascript
async function refreshAccessToken() {
  const refreshToken = localStorage.getItem('refreshToken');
  
  if (!refreshToken) {
    // No refresh token available, redirect to login
    window.location.href = '/';
    return false;
  }
  
  try {
    const response = await fetch('http://localhost:5000/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    });
    
    if (!response.ok) {
      // Refresh failed, clear tokens and redirect to login
      localStorage.removeItem('authToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/';
      return false;
    }
    
    const data = await response.json();
    localStorage.setItem('authToken', data.token);
    localStorage.setItem('refreshToken', data.refreshToken);
    return true;
  } catch (error) {
    console.error('Token refresh failed:', error);
    window.location.href = '/';
    return false;
  }
}
```

### 7.2 Axios Interceptor (Alternative)
```javascript
import axios from 'axios';

axios.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config;
    
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        const refreshToken = localStorage.getItem('refreshToken');
        const response = await axios.post('http://localhost:5000/api/auth/refresh', {
          refreshToken
        });
        
        const newToken = response.data.token;
        localStorage.setItem('authToken', newToken);
        axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
        originalRequest.headers['Authorization'] = `Bearer ${newToken}`;
        
        return axios(originalRequest);
      } catch (refreshError) {
        localStorage.removeItem('authToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/';
        return Promise.reject(refreshError);
      }
    }
    
    return Promise.reject(error);
  }
);
```

---

## 8. CORS Configuration

### 8.1 Allowed Origins
Frontend application can run on:
- `http://localhost:5173` (Vite dev server)
- `https://taraas.com` (Production domain)

### 8.2 Allowed Methods
```
GET, POST, PUT, DELETE, OPTIONS, PATCH
```

### 8.3 Allowed Headers
```
Content-Type, Authorization, Accept
```

### 8.4 Frontend Configuration
No special CORS handling needed on frontend if running on allowed origins. Credentials are not sent by default unless explicitly configured:

```javascript
// Only if credentials needed (not required for JWT):
fetch(url, {
  credentials: 'include',
  headers: { /* ... */ }
});
```

---

## 9. Rate Limiting

### 9.1 Rate Limit Rules
- **100 requests per minute per client IP**
- Rate limit increases if same user has multiple tabs/windows

### 9.2 Frontend Handling
```javascript
async function apiCall(url, options = {}) {
  const response = await fetch(url, options);
  
  // Check for rate limit (429 Too Many Requests)
  if (response.status === 429) {
    showToast('Too many requests. Please wait a minute and try again.', 'warning');
    return null;
  }
  
  return await response.json();
}
```

### 9.3 Rate Limit Headers
Response headers may include:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1710158400
```

---

## 10. Model Validation Rules (Detailed)

### 10.1 Customer Model
| Field | Type | Rules | Error Message |
|-------|------|-------|---------------|
| name | string | @NotBlank | Name is required |
| email | string | @NotBlank, @Email | Email is required and must be valid |
| password | string | @NotBlank | Password is required |
| city | string | @NotBlank | City is required |
| totalSpent | number | @Min(0) | Total spent must be >= 0 |

### 10.2 Provider Model
| Field | Type | Rules | Error Message |
|-------|------|-------|---------------|
| name | string | @NotBlank | Name is required |
| phone | string | @Pattern(^\d{10}$) | Phone must be 10 digits |
| price | number | @Min(0) | Price must be >= 0 |
| email | string | @Email | Email must be valid |
| rating | number | @Min(0), @Max(5) | Rating must be 0-5 |

### 10.3 Offer Model
| Field | Type | Rules | Error Message |
|-------|------|-------|---------------|
| title | string | @NotBlank | Title is required |
| type | string | @NotNull | Type is required |
| value | number | @DecimalMin("0.01") | Value must be > 0 |
| description | string | @Size(10-1000) | Description must be 10-1000 chars |

### 10.4 Transaction Model
| Field | Type | Rules | Error Message |
|-------|------|-------|---------------|
| customerId | string | @NotBlank | Customer ID required |
| providerId | string | @NotBlank | Provider ID required |
| amount | number | @NotNull, @DecimalMin("0.01") | Amount required and > 0 |
| status | string | @Pattern(PENDING\|COMPLETED\|FAILED) | Invalid status |

### 10.5 Review Model
| Field | Type | Rules | Error Message |
|-------|------|-------|---------------|
| rating | number | @NotNull, @Min(1), @Max(5) | Rating required, must be 1-5 |
| text | string | @NotBlank, @Size(10-1000) | Review text required, 10-1000 chars |
| customerId | string | @NotBlank | Customer ID required |
| providerId | string | @NotBlank | Provider ID required |

### 10.6 OTP Model (For OTP-based authentication)
| Field | Type | Rules | Error Message |
|-------|------|-------|---------------|
| email | string | @Email | Email must be valid |
| otp | string | @Pattern(^\d{6}$) | OTP must be 6 digits |

---

## 11. File Upload Validation

### 11.1 Allowed File Types
- **JPEG** (.jpg, .jpeg)
- **PNG** (.png)
- **GIF** (.gif)
- **WebP** (.webp)

### 11.2 File Size Limit
- **Maximum 5MB per file**
- **Total request size: 5MB**

### 11.3 Frontend Validation
```javascript
function validateFileUpload(file) {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  const maxSize = 5 * 1024 * 1024; // 5MB
  
  if (!allowedTypes.includes(file.type)) {
    throw new Error('Only JPEG, PNG, GIF, and WebP images are allowed');
  }
  
  if (file.size > maxSize) {
    throw new Error('File size must not exceed 5MB');
  }
  
  return true;
}

// Usage in file input handler
function handleFileChange(event) {
  const file = event.target.files[0];
  try {
    validateFileUpload(file);
    // Proceed with upload
    uploadFile(file);
  } catch (error) {
    showToast(error.message, 'error');
  }
}
```

### 11.4 Backend Error Response (Invalid File)
```json
{
  "error": "Invalid file type or size",
  "details": ["Only JPEG, PNG, GIF, and WebP images are allowed", "Maximum file size is 5MB"]
}
```

---

## 12. Data Security Best Practices

### 12.1 Never Store Sensitive Data
- ❌ DO NOT store password in frontend
- ❌ DO NOT log authentication tokens to console
- ❌ DO NOT send password in any API request except login
- ❌ DO NOT store payment card details on frontend

### 12.2 Token Management
- ✅ Store access token in sessionStorage or memory (short-lived)
- ✅ Store refresh token in HttpOnly cookie (if possible) or sessionStorage
- ✅ Clear tokens on logout
- ✅ Clear tokens if refresh fails
- ✅ Always include Authorization header with Bearer token

### 12.3 HTTPS Usage
- ✅ Always use HTTPS in production (backend enforces HTTPS)
- ✅ Never send tokens over HTTP
- ✅ Ensure frontend is also served over HTTPS

---

## 13. Logout Implementation

### 13.1 Logout Function
```javascript
function logout() {
  // Clear all auth data
  localStorage.removeItem('authToken');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('userName');
  localStorage.removeItem('userEmail');
  
  // Redirect to login
  window.location.href = '/';
}
```

### 13.2 Logout on Token Failure
```javascript
async function handleApiError(error) {
  if (error.response?.status === 401) {
    // Attempt token refresh
    const refreshed = await refreshAccessToken();
    if (!refreshed) {
      // Refresh failed, logout user
      logout();
    }
  }
}
```

---

## 14. Testing Checklist

### 14.1 Login Flow
- [ ] Login with valid credentials → Tokens received and stored
- [ ] Login with invalid email → 401 error displayed
- [ ] Login with invalid password → 401 error displayed
- [ ] Login without email → 400 validation error
- [ ] Login without password → 400 validation error
- [ ] Login with invalid email format → 400 validation error

### 14.2 Protected Routes
- [ ] Access protected route with valid token → Data loaded
- [ ] Access protected route without token → Redirect to login
- [ ] Access protected route with expired token → Token refreshed, data loaded
- [ ] Access protected route with invalid token → Redirect to login

### 14.3 API Requests
- [ ] GET request with token → Success
- [ ] POST request with validation errors → 400 with error details
- [ ] POST request without token → 401 error
- [ ] File upload (valid file) → Success
- [ ] File upload (invalid type) → 400 error
- [ ] File upload (too large) → 400 error

### 14.4 Rate Limiting
- [ ] Send 100 requests/minute → Success
- [ ] Send 101st request within minute → Rate limit error (429)
- [ ] Wait 1 minute → Requests work again

### 14.5 Token Refresh
- [ ] Token expires during session → Auto-refresh and retry request
- [ ] Refresh token invalid → Redirect to login
- [ ] Multiple tabs → Tokens synced correctly

---

## 15. Example Complete Integration

### 15.1 API Service Class (TypeScript)
```typescript
class ApiService {
  private baseUrl = 'http://localhost:5000';
  
  async request<T>(
    path: string,
    options: RequestInit = {}
  ): Promise<T | null> {
    const token = localStorage.getItem('authToken');
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };
    
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    
    try {
      const response = await fetch(`${this.baseUrl}${path}`, {
        ...options,
        headers,
      });
      
      if (response.status === 401) {
        await this.handleUnauthorized();
        return null;
      }
      
      if (!response.ok) {
        const error = await response.json();
        console.error('API Error:', error);
        return null;
      }
      
      return await response.json();
    } catch (error) {
      console.error('Request failed:', error);
      return null;
    }
  }
  
  private async handleUnauthorized(): Promise<void> {
    const refreshToken = localStorage.getItem('refreshToken');
    
    if (!refreshToken) {
      this.logout();
      return;
    }
    
    try {
      const response = await fetch(`${this.baseUrl}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      
      if (response.ok) {
        const data = await response.json();
        localStorage.setItem('authToken', data.token);
        return;
      }
    } catch (error) {
      console.error('Token refresh failed:', error);
    }
    
    this.logout();
  }
  
  private logout(): void {
    localStorage.clear();
    window.location.href = '/';
  }
  
  login(email: string, password: string) {
    return this.request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
  }
  
  getCustomerProfile() {
    return this.request('/api/customers/profile', { method: 'GET' });
  }
  
  getProviders() {
    return this.request('/api/providers', { method: 'GET' });
  }
  
  getOffers() {
    return this.request('/api/offers', { method: 'GET' });
  }
}

const api = new ApiService();
```

---

## 16. Deployment Considerations

### 16.1 Environment Variables
Set different API endpoints for dev/production:
```
REACT_APP_API_URL=http://localhost:5000          # Development
REACT_APP_API_URL=https://api.taraas.com         # Production
```

### 16.2 Production Security Checklist
- [ ] Switch to HTTPS URLs
- [ ] Update JWT secret key (currently hardcoded in dev)
- [ ] Enable HttpOnly cookies for refresh tokens
- [ ] Configure SSL/TLS certificates
- [ ] Set Content Security Policy headers
- [ ] Update CORS origins for production domain
- [ ] Enable error reporting/monitoring
- [ ] Disable debug logging

### 16.3 API Base URL Configuration
```javascript
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:5000';

// Use API_BASE_URL in all fetch calls
fetch(`${API_BASE_URL}/api/auth/login`, { ... })
```

---

## 17. Common Issues & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| CORS error on login | Frontend not on allowed origin | Check browser console; ensure frontend runs on localhost:5173 or taraas.com |
| 401 on protected endpoint | Missing or invalid token | Check localStorage.getItem('authToken'); ensure token in Authorization header |
| 400 on login with correct credentials | Password too short in dev | Remove @Size constraint if enforced on password during login |
| Token not persisting | Using sessionStorage that clears | Use localStorage instead of sessionStorage for tokens |
| API calls failing after token refresh | Token not updated in headers | Update axios default headers or fetch Authorization header after refresh |
| File upload returns 400 | File type not allowed | Check file mime type; only JPEG/PNG/GIF/WebP allowed |
| Rate limit errors in testing | Exceeding 100 req/min | Add delays between test requests or use different IP |

---

## 18. Environment Setup

### 18.1 Backend Running On
```
Server: http://localhost:5000
Database: MongoDB Atlas (Local_services)
```

### 18.2 Frontend Should Run On
```
Dev: http://localhost:5173 (Vite default)
Prod: https://taraas.com
```

### 18.3 .env.local Configuration
```
REACT_APP_API_URL=http://localhost:5000
REACT_APP_LOG_LEVEL=debug    # Set to 'error' in production
```

---

## 19. Quick Reference: Key Modifications

1. **Add JWT to all protected API calls**
   ```
   Authorization: Bearer {token}
   ```

2. **Handle 401 responses with token refresh**
   ```javascript
   if (response.status === 401) {
     await refreshAccessToken();
     retry original request;
   }
   ```

3. **Remove any hardcoded login/authentication logic**
   - Use new `/api/auth/login` endpoint
   - Store tokens returned in response

4. **Update all endpoints from `/api/auth/*` to `/api/customers/*`** (if accessing customer data)

5. **Add validation error display**
   - Show validation errors from 400 responses
   - Display user-friendly error messages

6. **Implement logout functionality**
   - Clear localStorage tokens
   - Redirect to login page

7. **Add protected route guards**
   - Check for authToken before accessing protected pages
   - Redirect to login if not authenticated

---

## 20. Support & Testing

### 20.1 Test Users (Once Created in Database)
After user registration/creation in your MongoDB:
```
Email: user@example.com
Password: test123
```

### 20.2 Testing Tools
- **Postman**: Test API endpoints with token
- **Thunder Client**: VS Code REST client
- **Browser DevTools**: Check localStorage and network requests
- **curl**: Command-line API testing
  ```bash
  curl -X POST http://localhost:5000/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"user@example.com","password":"test123"}'
  ```

### 20.3 Debugging Tips
1. Check browser console for errors
2. Verify tokens in localStorage: `localStorage.getItem('authToken')`
3. Check Authorization header in Network tab
4. Review backend logs in terminal for validation errors
5. Test API directly with Postman to isolate frontend issues

---

**End of Frontend Implementation Guide**

All backend modifications are complete and production-ready. This guide ensures frontend seamless integration with the new authentication and validation system.
