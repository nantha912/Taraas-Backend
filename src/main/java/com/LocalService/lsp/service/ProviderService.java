package com.LocalService.lsp.service;

import com.LocalService.lsp.dto.ProviderSearchResultDTO;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ProviderService - Logic Refined for City-Oriented Proximity
 * Implementation of the 4-Scenario Search Capability:
 * 1. Nearby + No City = GPS Proximity Ranking.
 * 2. Nearby + City = Strict City Filtering (Ignores GPS to allow remote city browsing).
 * 3. Remote + No City = Global Remote results.
 * 4. Remote + City = Remote results within a specific city.
 */
@Service
public class ProviderService {

    private static final Logger logger = LoggerFactory.getLogger(ProviderService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<ProviderSearchResultDTO> searchWithRanking(String service, Double lat, Double lon, String city, String mode) {
        logger.info("Marketplace Search -> Mode: {}, Service: {}, City: {}", mode, service, city);

        List<AggregationOperation> operations = new ArrayList<>();
        boolean isRemoteMode = "REMOTE".equalsIgnoreCase(mode);
        boolean hasCity = city != null && !city.isBlank();
        boolean hasCoords = lat != null && lon != null;

        // SCENARIO 1: Nearby mode, No city entered, GPS available -> Proximity Search
        if (!isRemoteMode && !hasCity && hasCoords) {
            Document geoQuery = new Document("serviceDeliveryType", new Document("$in", Arrays.asList("LOCAL", "HYBRID")));
            if (service != null && !service.isBlank()) {
                geoQuery.append("serviceCategory", new Document("$regex", service.trim()).append("$options", "i"));
            }

            operations.add(new CustomAggregationOperation(new Document("$geoNear", new Document()
                    .append("near", new Document("type", "Point").append("coordinates", Arrays.asList(lon, lat)))
                    .append("distanceField", "dist.calculated")
                    .append("maxDistance", 50000) // 50km
                    .append("spherical", true)
                    .append("query", geoQuery)
            )));
        }
        // SCENARIOS 2, 3, & 4: Criteria-based matching
        else {
            Criteria criteria = new Criteria();

            if (isRemoteMode) {
                // SCENARIOS 3 & 4: Remote Logic
                criteria.and("serviceDeliveryType").in("REMOTE", "HYBRID");
                if (hasCity) {
                    // Scenario 4: Remote + City
                    criteria.and("city").regex(city.trim(), "i");
                }
            } else {
                // SCENARIO 2: Nearby selected + City entered -> City Oriented
                // Note: We ignore GPS coords here because the user explicitly asked for a different city.
                criteria.and("serviceDeliveryType").in("LOCAL", "HYBRID");
                if (hasCity) {
                    criteria.and("city").regex(city.trim(), "i");
                }
            }

            if (service != null && !service.isBlank()) {
                criteria.and("serviceCategory").regex(service.trim(), "i");
            }

            operations.add(Aggregation.match(criteria));
        }

        // --- RELATIONAL DATA JOINS ---
        operations.add(Aggregation.addFields().addFieldWithValue("idStr", ConvertOperators.ToString.toString("$_id")).build());
        operations.add(Aggregation.lookup("reviews", "idStr", "providerId", "rawReviews"));
        operations.add(Aggregation.lookup("transactions", "idStr", "providerId", "rawTransactions"));

        // --- METRIC CALCULATIONS ---
        operations.add(Aggregation.addFields()
                .addFieldWithValue("reviewCount", ArrayOperators.Size.lengthOfArray("rawReviews"))
                .addFieldWithValue("averageRating", new Document("$ifNull", Arrays.asList(new Document("$avg", "$rawReviews.rating"), 0.0)))
                .addFieldWithValue("completedOrders", ArrayOperators.Size.lengthOfArray(
                        ArrayOperators.Filter.filter("rawTransactions")
                                .as("tx").by(ComparisonOperators.Eq.valueOf("tx.status").equalToValue("COMPLETED"))))
                .build());

        // --- WEIGHTED RANKING ENGINE ---
        Document scoringFormula = isRemoteMode ?
                // Remote Priority: Trust (40) + Volume (30) + Activity (30)
                new Document("$add", Arrays.asList(
                        new Document("$multiply", Arrays.asList("$averageRating", 8)),
                        new Document("$min", Arrays.asList(new Document("$multiply", Arrays.asList("$completedOrders", 0.3)), 30)),
                        20
                )) :
                // Nearby Priority: Proximity (30) + Trust (30) + Volume (20)
                new Document("$add", Arrays.asList(
                        new Document("$multiply", Arrays.asList("$averageRating", 6)),
                        new Document("$min", Arrays.asList(new Document("$multiply", Arrays.asList("$completedOrders", 0.2)), 20)),
                        new Document("$cond", Arrays.asList(new Document("$lt", Arrays.asList(new Document("$ifNull", Arrays.asList("$dist.calculated", 999999)), 5000)), 30, 10)),
                        20
                ));

        operations.add(Aggregation.addFields().addFieldWithValue("searchScore", scoringFormula).build());

        // --- FINAL PROJECTION & SORT ---
        operations.add(Aggregation.sort(Sort.Direction.DESC, "searchScore"));

        // FIXED: Removed mixed inclusion/exclusion. Only excluding temporary join fields.
        // Computed fields like searchScore, reviewCount, etc. are included by default.
        operations.add(Aggregation.project()
                .andExclude("rawReviews", "rawTransactions", "idStr"));

        operations.add(Aggregation.addFields().addFieldWithValue("id", ConvertOperators.ToString.toString("$_id")).build());

        return mongoTemplate.aggregate(Aggregation.newAggregation(operations), "providers", ProviderSearchResultDTO.class).getMappedResults();
    }

    private static class CustomAggregationOperation implements AggregationOperation {
        private final Document document;
        public CustomAggregationOperation(Document document) { this.document = document; }
        @Override public Document toDocument(AggregationOperationContext context) { return document; }
    }
}