package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.ReferralLedger;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReferralLedgerRepository extends MongoRepository<ReferralLedger, String> {
    List<ReferralLedger> findByPromoterId(String promoterId);
}