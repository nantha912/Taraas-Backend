package com.LocalService.lsp.service;

import com.LocalService.lsp.model.Provider;
import com.LocalService.lsp.model.Statement;
import com.LocalService.lsp.model.Transaction;
import com.LocalService.lsp.repository.ProviderRepository;
import com.LocalService.lsp.repository.StatementRepository;
import com.LocalService.lsp.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class StatementService {
    private static final Logger logger = LoggerFactory.getLogger(StatementService.class);

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private StatementRepository statementRepository;

    /**
     * Scheduled Job: Run at 02:00 AM on the 1st of every month.
     * Calculates for the previous month.
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void scheduledMonthlyCalculation() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        String monthStr = lastMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        calculateForMonth(monthStr, false, "SYSTEM");
    }

    public void calculateForMonth(String billingMonth, boolean forceRecalculate, String triggeredBy) {
        if (billingMonth == null) {
            logger.error("billingMonth is null, cannot calculate statements.");
            return;
        }
        logger.info("Starting Commission Calculation for {} by {}", billingMonth, triggeredBy);

        YearMonth ym = YearMonth.parse(billingMonth);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);

        List<Provider> providers = providerRepository.findAll();
        if (providers == null) return;

        int successCount = 0;
        int failureCount = 0;

        for (Provider provider : providers) {
            if (provider == null || provider.getId() == null) continue;
            try {
                // Idempotency Check
                boolean exists = statementRepository.existsByProviderIdAndBillingMonth(provider.getId(), billingMonth);
                if (exists && !forceRecalculate) {
                    continue;
                }

                // Fetch Eligible Transactions
                // Logic: COMPLETED status, within date range, and NOT previously billed
                List<Transaction> txList = transactionRepository.findByProviderIdAndStatusAndBilledFalseAndCreatedAtBetween(
                        provider.getId(), "COMPLETED", start, end);

                if (txList == null || txList.isEmpty()) continue;

                double totalConfirmed = txList.stream()
                        .filter(Objects::nonNull)
                        .mapToDouble(t -> Optional.ofNullable(t.getAmount()).orElse(0.0))
                        .sum();
                
                double commissionPercentage = 5.0; // Default MVP commission
                double commissionAmount = (totalConfirmed * commissionPercentage) / 100.0;

                // Round to 2 decimals
                commissionAmount = Math.round(commissionAmount * 100.0) / 100.0;

                Statement statement = new Statement();
                statement.setProviderId(provider.getId());
                statement.setBillingMonth(billingMonth);
                statement.setBillingStartDate(start);
                statement.setBillingEndDate(end);
                statement.setConfirmedTotal(totalConfirmed);
                statement.setCommissionPercentage(commissionPercentage);
                statement.setCommissionAmount(commissionAmount);
                statement.setStatus("UNPAID");
                statement.setGeneratedAt(LocalDateTime.now());
                statement.setGeneratedBy(triggeredBy);

                statementRepository.save(statement);

                // Mark transactions as billed
                for (Transaction tx : txList) {
                    if (tx == null) continue;
                    tx.setBilled(true);
                    transactionRepository.save(tx);
                }
                successCount++;
            } catch (Exception e) {
                logger.error("Failed to calculate for provider {}: {}", provider.getId(), e.getMessage());
                failureCount++;
            }
        }
        logger.info("Calculation Finished. Success: {}, Failures: {}", successCount, failureCount);
    }
}
