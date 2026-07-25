package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.PollVote;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PollVoteRepository extends MongoRepository<PollVote, String> {
    Optional<PollVote> findByPollIdAndUserId(String pollId, String userId);
    List<PollVote> findByUserId(String userId);
}
