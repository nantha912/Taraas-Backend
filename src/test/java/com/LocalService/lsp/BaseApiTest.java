package com.LocalService.lsp;

import com.LocalService.lsp.model.Customer;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.OtpRepository;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseApiTest {

    @Autowired
    protected CustomerRepository customerRepository;

    @Autowired
    protected OtpRepository otpRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected static RequestSpecification requestSpec;

    @BeforeAll
    public void setup() {
        // Match the port defined in application.properties
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 5000;
        RestAssured.basePath = "";

        requestSpec = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        seedAdminUser();
    }

    /**
     * Seeds an admin user into the database if it doesn't exist.
     * Note: ROLE_ADMIN is hardcoded in some controller checks, 
     * but JwtAuthenticationFilter currently hardcodes ROLE_USER for all tokens.
     */
    private void seedAdminUser() {
        String adminEmail = "admin@taraas.com";
        if (!customerRepository.existsByEmail(adminEmail)) {
            Customer admin = new Customer();
            admin.setName("System Admin");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode("adminPassword123"));
            admin.setCity("Mumbai");
            customerRepository.save(admin);
            System.out.println("Admin user seeded: " + adminEmail);
        }
    }

    /**
     * Helper to get JWT token for a specific user
     */
    protected String getAuthToken(String email, String password) {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("email", email);
        loginRequest.put("password", password);

        return given()
                .spec(requestSpec)
                .body(loginRequest)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    /**
     * Helper for Admin Token
     */
    protected String getAdminToken() {
        return getAuthToken("admin@taraas.com", "adminPassword123");
    }

    /**
     * Helper for User Token
     */
    protected String getUserToken() {
        return getAuthToken("user@example.com", "userPassword123");
    }
}
