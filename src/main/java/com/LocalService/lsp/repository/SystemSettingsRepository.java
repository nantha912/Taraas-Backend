package com.LocalService.lsp.repository;
import com.LocalService.lsp.model.SystemSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemSettingsRepository extends MongoRepository<SystemSettings, String> {}