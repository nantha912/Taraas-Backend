package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.PollLike;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PollLikeRepository extends MongoRepository<PollLike, String> {
    Optional<PollLike> findByPollIdAndUserId(String pollId, String userId);
    List<PollLike> findByUserId(String userId);
}
