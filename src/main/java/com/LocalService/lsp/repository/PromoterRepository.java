package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.Promoter;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PromoterRepository extends MongoRepository<Promoter, String> {
    Optional<Promoter> findByReferralCodeIgnoreCase(String referralCode);
    Optional<Promoter> findByEmail(String email);
    Optional<Promoter> findByEmailIgnoreCase(String email);
}