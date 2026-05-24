package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.OtpRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OtpRepository extends MongoRepository<OtpRecord, String> {
    Optional<OtpRecord> findByEmail(String email);
    void deleteByEmail(String email);
}