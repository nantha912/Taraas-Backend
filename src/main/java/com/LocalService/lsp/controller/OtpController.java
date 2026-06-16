package com.LocalService.lsp.controller;

import com.LocalService.lsp.service.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/otp")
public class OtpController {

    @Autowired private OtpService otpService;

    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> payload) {
        if (payload == null) return ResponseEntity.badRequest().body("Payload required");
        String email = payload.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        try {
            otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(Map.of("message", "OTP sent successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> payload) {
        if (payload == null) return ResponseEntity.badRequest().body("Payload required");
        String email = payload.get("email");
        String otp = payload.get("otp");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        if (otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest().body("OTP is required");
        }
        try {
            boolean isValid = otpService.verifyOtp(email, otp);
            if (isValid) {
                return ResponseEntity.ok(Map.of("message", "Verification successful."));
            } else {
                return ResponseEntity.status(401).body(Map.of("message", "Invalid OTP code."));
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }
    }
}
