package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.Statement;
import com.LocalService.lsp.repository.StatementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/statements")
@CrossOrigin(origins = "*")
public class StatementController {

    @Autowired
    private StatementRepository statementRepository;

    /**
     * Fetch all billing history for a specific provider.
     */
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getStatementsByProvider(@PathVariable String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return ResponseEntity.badRequest().body("Provider ID is required");
        }
        return ResponseEntity.ok(statementRepository.findByProviderId(providerId));
    }

    /**
     * Mark a statement as paid.
     * In MVP, this would be called after a successful UPI payment to the admin.
     */
    @PutMapping("/{id}/pay")
    public ResponseEntity<?> payStatement(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Statement ID is required");
        }
        return statementRepository.findById(id).map(s -> {
            s.setStatus("PAID");
            s.setPaidAt(LocalDateTime.now());
            return ResponseEntity.ok(statementRepository.save(s));
        }).orElse(ResponseEntity.notFound().build());
    }
}
