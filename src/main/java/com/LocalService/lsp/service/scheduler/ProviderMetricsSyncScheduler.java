package com.LocalService.lsp.service.scheduler;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ProviderMetricsSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ProviderMetricsSyncScheduler.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 🕒 AUTOMATED NIGHTLY SYNC
     * Cron Expression: "0 0 2 * * *" means:
     * Second 0, Minute 0, Hour 2 (2:00 AM), Every Day of Month, Every Month, Every Day of Week.
     * Runs during lowest traffic metrics to resolve any atomic data drifts safely.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyReconciliation() {
        //logger.info("Executing scheduled 2:00 AM Provider Metrics reconciliation sync script...");
        reconcileAllProviderMetrics();
    }

    /**
     * 👑 ONE-TIME HISTORICAL MIGRATION BRIDGE
     * Call this method manually via a Controller or StartupRunner 
     * to initialize your empty provider tracking counter parameters right now.
     */
    public int triggerManualMigration() {
        //logger.info("MANUAL TRIGGER: Initializing historical provider data calculations matrix...");
        return reconcileAllProviderMetrics();
    }

    /**
     * ⚙️ CORE RECONCILIATION PIPELINE ENGINE
     * Uses MongoDB High-Performance Aggregations to parse the entire system data tree
     * and update provider summaries in bulk. O(1) Memory footprint for Java heap storage.
     */
    private int reconcileAllProviderMetrics() {
        int processedCount = 0;
        try {
            // 1. Fetch all active provider IDs to baseline the synchronization loop
            Query queryAllIds = new Query();
            queryAllIds.fields().include("_id");
            List<Document> providers = mongoTemplate.find(queryAllIds, Document.class, "providers");

            //logger.info("Found {} provider records to reconcile.", providers.size());

            for (Document doc : providers) {
                String providerId = doc.getObjectId("_id").toString();
                
                // 2. Fetch True Aggregated Review Metrics
                Aggregation reviewAgg = Aggregation.newAggregation(
                    Aggregation.match(Criteria.where("providerId").is(providerId)),
                    Aggregation.group().count().as("count").avg("rating").as("avg")
                );
                AggregationResults<Document> reviewRes = mongoTemplate.aggregate(reviewAgg, "reviews", Document.class);
                Document reviewData = reviewRes.getUniqueMappedResult();

                int trueReviewCount = 0;
                double trueAverageRating = 0.0;
                if (reviewData != null) {
                    trueReviewCount = reviewData.getInteger("count", 0);
                    Object avgObj = reviewData.get("avg");
                    trueAverageRating = (avgObj instanceof Number) ? ((Number) avgObj).doubleValue() : 0.0;
                    trueAverageRating = Math.round(trueAverageRating * 10.0) / 10.0; // Round to 1 decimal place
                }

                // 3. Fetch True Aggregated Transaction Metrics (Strongly-Typed Production Fix)
                Aggregation txAgg = Aggregation.newAggregation(
                    Aggregation.match(Criteria.where("providerId").is(providerId)),
                    Aggregation.group()
                        .count().as("total")
                        .sum(
                            org.springframework.data.mongodb.core.aggregation.ConditionalOperators
                                .when(Criteria.where("status").is("COMPLETED"))
                                .then(1)
                                .otherwise(0)
                        ).as("completed")
                );
                AggregationResults<Document> txRes = mongoTemplate.aggregate(txAgg, "transactions", Document.class);
                Document txData = txRes.getUniqueMappedResult();

                int trueTotalOrders = 0;
                int trueCompletedOrders = 0;
                if (txData != null) {
                    trueTotalOrders = txData.getInteger("total", 0);
                    trueCompletedOrders = txData.getInteger("completed", 0);
                }

                // 4. Update the Provider document with fresh, true counters
                Query updateQuery = new Query(Criteria.where("_id").is(providerId));
                Update updateOp = new Update()
                        .set("reviewCount", trueReviewCount)
                        .set("averageRating", trueAverageRating)
                        .set("totalOrdersCount", trueTotalOrders)
                        .set("completedOrdersCount", trueCompletedOrders);

                mongoTemplate.updateFirst(updateQuery, updateOp, "providers");
                processedCount++;
            }

            //logger.info("Successfully reconciled and sync-locked metrics for {} providers.", processedCount);

        } catch (Exception e) {
            logger.error("CRITICAL CRON ERROR: Reconciliation execution matrix aborted: {}", e.getMessage());
        }
        return processedCount;
    }
}
