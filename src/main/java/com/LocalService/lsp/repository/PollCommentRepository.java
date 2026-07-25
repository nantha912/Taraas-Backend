package com.LocalService.lsp.repository;

import com.LocalService.lsp.model.PollComment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PollCommentRepository extends MongoRepository<PollComment, String> {
    Optional<PollComment> findByPollIdAndAuthorIdAndParentCommentId(String pollId, String authorId, String parentCommentId);
    List<PollComment> findByPollId(String pollId);
    List<PollComment> findByAuthorIdIn(List<String> authorIds);
}
