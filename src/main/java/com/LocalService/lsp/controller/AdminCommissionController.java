package com.LocalService.lsp.controller;

import com.LocalService.lsp.service.StatementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/commission")
@CrossOrigin(origins = "*")
public class AdminCommissionController {

    @Autowired private StatementService statementService;

    @PostMapping("/calculate")
    public ResponseEntity<?> triggerCalculation(@RequestBody Map<String, Object> payload) {
        if (payload == null) {
            return ResponseEntity.badRequest().body("Payload required");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        // Simple manual check for ROLE_ADMIN (or email if roles are not correctly propagated)
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !auth.getName().equalsIgnoreCase("admin@taraas.com")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied."));
        }

        Object billingMonthObj = payload.get("billingMonth");
        if (billingMonthObj == null) {
            return ResponseEntity.badRequest().body("billingMonth required (YYYY-MM)");
        }
        String billingMonth = billingMonthObj.toString();
        
        boolean force = false;
        Object forceObj = payload.get("forceRecalculate");
        if (forceObj != null) {
            force = Boolean.parseBoolean(forceObj.toString());
        }

        statementService.calculateForMonth(billingMonth, force, "ADMIN");
        Map<String, String> response = new HashMap<>();
        response.put("message", "Calculation process initiated");
        return ResponseEntity.ok(response);
    }
}
