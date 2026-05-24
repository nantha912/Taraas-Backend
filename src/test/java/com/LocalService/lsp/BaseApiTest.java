package com.LocalService.lsp;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

public abstract class BaseApiTest {

    protected static RequestSpecification requestSpec;

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 5000;
        RestAssured.basePath = "";

        requestSpec = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();
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
     * Note: This assumes a user with admin@taraas.com and ROLE_ADMIN exists in your DB.
     * You may need to seed this user first or use an existing admin account.
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
