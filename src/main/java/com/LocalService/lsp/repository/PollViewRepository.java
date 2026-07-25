package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.PollView;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PollViewRepository extends MongoRepository<PollView, String> {
    boolean existsByUserIdAndPollId(String userId, String pollId);
    List<PollView> findByUserId(String userId);
}
