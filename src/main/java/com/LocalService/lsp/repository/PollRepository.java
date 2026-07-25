package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.Poll;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PollRepository extends MongoRepository<Poll, String> {
    List<Poll> findByAuthorIdIn(List<String> authorIds);
    List<Poll> findByTitleContainingIgnoreCase(String query);
}
