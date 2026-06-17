package com.LocalService.lsp.controller;

import com.LocalService.lsp.service.scheduler.ProviderMetricsSyncScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/maintenance")
public class MaintenanceController {

    @Autowired
    private ProviderMetricsSyncScheduler syncScheduler;

    /**
     * Manually triggers the migration script to sync all existing 
     * review counts, rating averages, and order tracking metrics.
     */
    @PostMapping("/sync-provider-metrics")
    public ResponseEntity<?> forceInitializeMetrics() {
        int recordCount = syncScheduler.triggerManualMigration();
        return ResponseEntity.ok(Map.of(
            "message", "Historical data initialization migration successful",
            "providersSynchronized", recordCount
        ));
    }
}
