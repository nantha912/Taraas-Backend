package com.LocalService.lsp.repository;
import com.LocalService.lsp.model.OnboardingTracker;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OnboardingTrackerRepository extends MongoRepository<OnboardingTracker, String> {}