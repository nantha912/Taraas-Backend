package com.LocalService.lsp.service;

import com.LocalService.lsp.model.*;
import com.LocalService.lsp.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PollService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PollRepository pollRepository;

    @Autowired
    private PollVoteRepository pollVoteRepository;

    @Autowired
    private PollLikeRepository pollLikeRepository;

    @Autowired
    private PollCommentRepository pollCommentRepository;

    @Autowired
    private PollViewRepository pollViewRepository;

    @Autowired
    private ProviderRepository providerRepository;

    public Poll createPoll(Poll poll) {
        poll.setTotalVotes(0);
        poll.setLikeCount(0);
        poll.setCommentCount(0);
        poll.setCreatedAt(LocalDateTime.now());
        poll.setUpdatedAt(LocalDateTime.now());
        if (poll.getOptions() != null) {
            for (PollOption opt : poll.getOptions()) {
                if (opt.getOptionId() == null || opt.getOptionId().isBlank()) {
                    opt.setOptionId(UUID.randomUUID().toString());
                }
                opt.setVoteCount(0);
            }
        }
        return pollRepository.save(poll);
    }

    public Optional<Poll> getPollById(String id) {
        return pollRepository.findById(id);
    }

    public List<Poll> getMostEngagedFeed(String userId, int page, int limit) {
        List<AggregationOperation> operations = new ArrayList<>();

        if (userId != null && !userId.isBlank()) {
            String lookupViewsJson = String.format(
                "{ '$lookup': { 'from': 'poll_views', 'let': { 'pId': '$_id' }, 'pipeline': [ { '$match': { '$expr': { '$and': [ { '$eq': [ { '$toString': '$pollId' }, { '$toString': '$$pId' } ] }, { '$eq': [ '$userId', '%s' ] } ] } } } ], 'as': 'userViews' } }",
                userId
            );
            String lookupVotesJson = String.format(
                "{ '$lookup': { 'from': 'poll_votes', 'let': { 'pId': '$_id' }, 'pipeline': [ { '$match': { '$expr': { '$and': [ { '$eq': [ { '$toString': '$pollId' }, { '$toString': '$$pId' } ] }, { '$eq': [ '$userId', '%s' ] } ] } } } ], 'as': 'userVotes' } }",
                userId
            );
            operations.add(new CustomAggregationOperation(lookupViewsJson));
            operations.add(new CustomAggregationOperation(lookupVotesJson));

            String addFlagsJson = "{ '$addFields': { " +
                "'isViewed': { '$cond': [ { '$gt': [ { '$size': '$userViews' }, 0 ] }, 1, 0 ] }, " +
                "'isVoted': { '$cond': [ { '$gt': [ { '$size': '$userVotes' }, 0 ] }, 1, 0 ] } " +
                "} }";
            operations.add(new CustomAggregationOperation(addFlagsJson));

            String addPriorityJson = "{ '$addFields': { " +
                "'feedPriority': { '$cond': [ { '$eq': [ '$isViewed', 0 ] }, 1, { '$cond': [ { '$eq': [ '$isVoted', 0 ] }, 2, 3 ] } ] } " +
                "} }";
            operations.add(new CustomAggregationOperation(addPriorityJson));
        } else {
            operations.add(Aggregation.addFields()
                .addFieldWithValue("isViewed", 0)
                .addFieldWithValue("isVoted", 0)
                .addFieldWithValue("feedPriority", 1)
                .build());
        }

        String addFieldsJson = "{ '$addFields': { 'engagementScore': { '$add': [ { '$multiply': [ '$likeCount', 2 ] }, { '$multiply': [ '$commentCount', 3 ] }, '$totalVotes' ] } } }";
        operations.add(new CustomAggregationOperation(addFieldsJson));

        String sortJson = "{ '$sort': { 'feedPriority': 1, 'engagementScore': -1 } }";
        operations.add(new CustomAggregationOperation(sortJson));

        operations.add(Aggregation.skip((long) page * limit));
        operations.add(Aggregation.limit(limit));

        Aggregation aggregation = Aggregation.newAggregation(operations);
        return mongoTemplate.aggregate(aggregation, "polls", Poll.class).getMappedResults();
    }

    public List<Poll> getMostRecentFeed(String userId, int page, int limit) {
        List<AggregationOperation> operations = new ArrayList<>();

        if (userId != null && !userId.isBlank()) {
            String lookupViewsJson = String.format(
                "{ '$lookup': { 'from': 'poll_views', 'let': { 'pId': '$_id' }, 'pipeline': [ { '$match': { '$expr': { '$and': [ { '$eq': [ { '$toString': '$pollId' }, { '$toString': '$$pId' } ] }, { '$eq': [ '$userId', '%s' ] } ] } } } ], 'as': 'userViews' } }",
                userId
            );
            String lookupVotesJson = String.format(
                "{ '$lookup': { 'from': 'poll_votes', 'let': { 'pId': '$_id' }, 'pipeline': [ { '$match': { '$expr': { '$and': [ { '$eq': [ { '$toString': '$pollId' }, { '$toString': '$$pId' } ] }, { '$eq': [ '$userId', '%s' ] } ] } } } ], 'as': 'userVotes' } }",
                userId
            );
            operations.add(new CustomAggregationOperation(lookupViewsJson));
            operations.add(new CustomAggregationOperation(lookupVotesJson));

            String addFlagsJson = "{ '$addFields': { " +
                "'isViewed': { '$cond': [ { '$gt': [ { '$size': '$userViews' }, 0 ] }, 1, 0 ] }, " +
                "'isVoted': { '$cond': [ { '$gt': [ { '$size': '$userVotes' }, 0 ] }, 1, 0 ] } " +
                "} }";
            operations.add(new CustomAggregationOperation(addFlagsJson));

            String addPriorityJson = "{ '$addFields': { " +
                "'feedPriority': { '$cond': [ { '$eq': [ '$isViewed', 0 ] }, 1, { '$cond': [ { '$eq': [ '$isVoted', 0 ] }, 2, 3 ] } ] } " +
                "} }";
            operations.add(new CustomAggregationOperation(addPriorityJson));
        } else {
            operations.add(Aggregation.addFields()
                .addFieldWithValue("isViewed", 0)
                .addFieldWithValue("isVoted", 0)
                .addFieldWithValue("feedPriority", 1)
                .build());
        }

        String sortJson = "{ '$sort': { 'feedPriority': 1, 'createdAt': -1 } }";
        operations.add(new CustomAggregationOperation(sortJson));

        operations.add(Aggregation.skip((long) page * limit));
        operations.add(Aggregation.limit(limit));

        Aggregation aggregation = Aggregation.newAggregation(operations);
        return mongoTemplate.aggregate(aggregation, "polls", Poll.class).getMappedResults();
    }

    public List<Poll> getReelsFeed(String userId, int page, int limit) {
        return getMostEngagedFeed(userId, page, limit);
    }

    public Poll vote(String pollId, String userId, String optionId) {
        Optional<PollVote> existingVote = pollVoteRepository.findByPollIdAndUserId(pollId, userId);
        
        Poll poll = pollRepository.findById(pollId).orElse(null);
        if (poll == null) {
            throw new IllegalStateException("Poll not found");
        }
        
        boolean optionExists = poll.getOptions() != null && poll.getOptions().stream().anyMatch(o -> o.getOptionId().equals(optionId));
        if (!optionExists) {
            throw new IllegalStateException("Option not found");
        }

        if (existingVote.isPresent()) {
            PollVote vote = existingVote.get();
            String oldOptionId = vote.getSelectedOptionId();
            if (oldOptionId.equals(optionId)) {
                return poll; // No change
            }
            
            // Decrement old option
            Query queryOld = new Query(Criteria.where("_id").is(pollId).and("options.optionId").is(oldOptionId));
            Update updateOld = new Update().inc("options.$.voteCount", -1);
            mongoTemplate.updateFirst(queryOld, updateOld, Poll.class);
            
            // Increment new option
            Query queryNew = new Query(Criteria.where("_id").is(pollId).and("options.optionId").is(optionId));
            Update updateNew = new Update().inc("options.$.voteCount", 1);
            mongoTemplate.updateFirst(queryNew, updateNew, Poll.class);
            
            // Update vote
            vote.setSelectedOptionId(optionId);
            vote.setVotedAt(LocalDateTime.now());
            pollVoteRepository.save(vote);
            
            return pollRepository.findById(pollId).orElse(null);
        } else {
            Query query = new Query(Criteria.where("_id").is(pollId).and("options.optionId").is(optionId));
            Update update = new Update().inc("totalVotes", 1).inc("options.$.voteCount", 1);
            Poll updatedPoll = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Poll.class);

            if (updatedPoll != null) {
                PollVote vote = new PollVote();
                vote.setPollId(pollId);
                vote.setUserId(userId);
                vote.setSelectedOptionId(optionId);
                vote.setVotedAt(LocalDateTime.now());
                pollVoteRepository.save(vote);
            }

            return updatedPoll;
        }
    }

    public void deletePoll(String pollId, String userId) {
        Optional<Poll> pollOpt = pollRepository.findById(pollId);
        if (pollOpt.isPresent()) {
            Poll poll = pollOpt.get();
            String providerId = providerRepository.findByCustomerId(userId).map(Provider::getId).orElse(null);
            boolean isAuthor = poll.getAuthorId().equals(userId) || (providerId != null && poll.getAuthorId().equals(providerId));
            if (!isAuthor) {
                throw new IllegalStateException("Unauthorized to delete this poll");
            }
            pollRepository.deleteById(pollId);
            mongoTemplate.remove(Query.query(Criteria.where("pollId").is(pollId)), PollVote.class);
            mongoTemplate.remove(Query.query(Criteria.where("pollId").is(pollId)), PollLike.class);
            mongoTemplate.remove(Query.query(Criteria.where("pollId").is(pollId)), PollComment.class);
            mongoTemplate.remove(Query.query(Criteria.where("pollId").is(pollId)), PollView.class);
        } else {
            throw new IllegalStateException("Poll not found");
        }
    }

    public void deleteComment(String commentId, String userId) {
        Optional<PollComment> commentOpt = pollCommentRepository.findById(commentId);
        if (commentOpt.isPresent()) {
            PollComment comment = commentOpt.get();
            String providerId = providerRepository.findByCustomerId(userId).map(Provider::getId).orElse(null);
            boolean isAuthor = comment.getAuthorId().equals(userId) || (providerId != null && comment.getAuthorId().equals(providerId));
            if (!isAuthor) {
                throw new IllegalStateException("Unauthorized to delete this comment");
            }
            pollCommentRepository.deleteById(commentId);
            mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(comment.getPollId())),
                new Update().inc("commentCount", -1),
                Poll.class
            );
        } else {
            throw new IllegalStateException("Comment not found");
        }
    }

    public boolean toggleLike(String pollId, String userId) {
        Optional<PollLike> existingLike = pollLikeRepository.findByPollIdAndUserId(pollId, userId);
        if (existingLike.isPresent()) {
            pollLikeRepository.delete(existingLike.get());
            mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(pollId)),
                new Update().inc("likeCount", -1),
                Poll.class
            );
            return false;
        } else {
            PollLike like = new PollLike();
            like.setPollId(pollId);
            like.setUserId(userId);
            like.setCreatedAt(LocalDateTime.now());
            pollLikeRepository.save(like);
            mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(pollId)),
                new Update().inc("likeCount", 1),
                Poll.class
            );
            return true;
        }
    }

    public PollComment addOrUpdateComment(String pollId, String authorId, String authorType, String authorName, String authorProfilePhoto, String text, String parentCommentId) {
        Optional<PollComment> existingComment = pollCommentRepository.findByPollIdAndAuthorIdAndParentCommentId(pollId, authorId, parentCommentId);
        
        if (existingComment.isPresent()) {
            PollComment comment = existingComment.get();
            comment.setText(text);
            comment.setUpdatedAt(LocalDateTime.now());
            return pollCommentRepository.save(comment);
        } else {
            PollComment comment = new PollComment();
            comment.setPollId(pollId);
            comment.setAuthorId(authorId);
            comment.setAuthorType(authorType);
            comment.setAuthorName(authorName);
            comment.setAuthorProfilePhoto(authorProfilePhoto);
            comment.setParentCommentId(parentCommentId);
            comment.setText(text);
            comment.setLikeCount(0);
            comment.setCreatedAt(LocalDateTime.now());
            comment.setUpdatedAt(LocalDateTime.now());
            PollComment saved = pollCommentRepository.save(comment);

            mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(pollId)),
                new Update().inc("commentCount", 1),
                Poll.class
            );
            return saved;
        }
    }

    public List<PollComment> getPollComments(String pollId, String userId) {
        List<PollComment> comments = pollCommentRepository.findByPollId(pollId);
        
        String providerId = null;
        if (userId != null && !userId.isBlank()) {
            providerId = providerRepository.findByCustomerId(userId).map(Provider::getId).orElse(null);
        }

        final String finalProviderId = providerId;
        comments.sort((c1, c2) -> {
            boolean isUser1 = (userId != null && (c1.getAuthorId().equals(userId) || (finalProviderId != null && c1.getAuthorId().equals(finalProviderId))));
            boolean isUser2 = (userId != null && (c2.getAuthorId().equals(userId) || (finalProviderId != null && c2.getAuthorId().equals(finalProviderId))));
            
            if (isUser1 && !isUser2) return -1;
            if (!isUser1 && isUser2) return 1;

            int likeDiff = Integer.compare(c2.getLikeCount(), c1.getLikeCount());
            if (likeDiff != 0) return likeDiff;
            
            return c2.getCreatedAt().compareTo(c1.getCreatedAt());
        });

        return comments;
    }

    public void recordView(String pollId, String userId) {
        if (userId != null && !userId.isBlank()) {
            if (!pollViewRepository.existsByUserIdAndPollId(userId, pollId)) {
                PollView view = new PollView();
                view.setUserId(userId);
                view.setPollId(pollId);
                view.setViewedAt(LocalDateTime.now());
                pollViewRepository.save(view);
            }
        }
    }

    public Map<String, List<Poll>> getUserActivity(String userId) {
        String providerId = providerRepository.findByCustomerId(userId).map(Provider::getId).orElse(null);
        
        List<String> authorIds = new ArrayList<>();
        authorIds.add(userId);
        if (providerId != null) {
            authorIds.add(providerId);
        }

        // 1. Created Polls
        List<Poll> created = pollRepository.findByAuthorIdIn(authorIds);

        // 2. Liked Polls
        List<PollLike> likes = pollLikeRepository.findByUserId(userId);
        List<String> likedIds = likes.stream().map(PollLike::getPollId).collect(Collectors.toList());
        List<Poll> liked = new ArrayList<>();
        if (!likedIds.isEmpty()) {
            liked = pollRepository.findAllById(likedIds);
        }

        // 3. Commented Polls
        List<PollComment> comments = pollCommentRepository.findByAuthorIdIn(authorIds);
        List<String> commentedIds = comments.stream().map(PollComment::getPollId).distinct().collect(Collectors.toList());
        List<Poll> commented = new ArrayList<>();
        if (!commentedIds.isEmpty()) {
            commented = pollRepository.findAllById(commentedIds);
        }

        // 4. Voted Polls
        List<PollVote> votes = pollVoteRepository.findByUserId(userId);
        List<String> votedIds = votes.stream().map(PollVote::getPollId).collect(Collectors.toList());
        List<Poll> voted = new ArrayList<>();
        if (!votedIds.isEmpty()) {
            voted = pollRepository.findAllById(votedIds);
        }

        Map<String, List<Poll>> activity = new HashMap<>();
        activity.put("created", created);
        activity.put("liked", liked);
        activity.put("commented", commented);
        activity.put("voted", voted);

        return activity;
    }

    public long getCreatedCount(String customerId, String providerId) {
        List<String> ids = new ArrayList<>();
        ids.add(customerId);
        if (providerId != null) ids.add(providerId);
        Query query = new Query(Criteria.where("authorId").in(ids));
        return mongoTemplate.count(query, Poll.class);
    }

    public long getLikedCount(String customerId) {
        Query query = new Query(Criteria.where("userId").is(customerId));
        return mongoTemplate.count(query, PollLike.class);
    }

    public long getCommentedCount(String customerId, String providerId) {
        List<String> ids = new ArrayList<>();
        ids.add(customerId);
        if (providerId != null) ids.add(providerId);
        Query query = new Query(Criteria.where("authorId").in(ids));
        List<?> pollIds = mongoTemplate.findDistinct(query, "pollId", PollComment.class, String.class);
        return pollIds != null ? pollIds.size() : 0;
    }

    public long getVotedCount(String customerId) {
        Query query = new Query(Criteria.where("userId").is(customerId));
        return mongoTemplate.count(query, PollVote.class);
    }

    public List<Poll> getUserActivityByType(String customerId, String providerId, String type, int page, int limit) {
        List<Poll> results = new ArrayList<>();
        int skip = page * limit;
        List<String> authorIds = new ArrayList<>();
        authorIds.add(customerId);
        if (providerId != null) authorIds.add(providerId);

        if ("created".equalsIgnoreCase(type)) {
            Query query = new Query(Criteria.where("authorId").in(authorIds));
            query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
            query.skip(skip).limit(limit);
            results = mongoTemplate.find(query, Poll.class);

        } else if ("liked".equalsIgnoreCase(type)) {
            Query query = new Query(Criteria.where("userId").is(customerId));
            query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
            query.skip(skip).limit(limit);
            List<PollLike> likes = mongoTemplate.find(query, PollLike.class);
            List<String> likedIds = likes.stream().map(PollLike::getPollId).collect(Collectors.toList());
            if (!likedIds.isEmpty()) {
                Query pollQuery = new Query(Criteria.where("id").in(likedIds));
                results = mongoTemplate.find(pollQuery, Poll.class);
            }

        } else if ("commented".equalsIgnoreCase(type)) {
            Query query = new Query(Criteria.where("authorId").in(authorIds));
            query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
            List<PollComment> comments = mongoTemplate.find(query, PollComment.class);
            List<String> commentedIds = comments.stream()
                    .map(PollComment::getPollId)
                    .distinct()
                    .skip(skip)
                    .limit(limit)
                    .collect(Collectors.toList());
            if (!commentedIds.isEmpty()) {
                Query pollQuery = new Query(Criteria.where("id").in(commentedIds));
                results = mongoTemplate.find(pollQuery, Poll.class);
            }

        } else if ("voted".equalsIgnoreCase(type)) {
            Query query = new Query(Criteria.where("userId").is(customerId));
            query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "votedAt"));
            query.skip(skip).limit(limit);
            List<PollVote> votes = mongoTemplate.find(query, PollVote.class);
            List<String> votedIds = votes.stream().map(PollVote::getPollId).collect(Collectors.toList());
            if (!votedIds.isEmpty()) {
                Query pollQuery = new Query(Criteria.where("id").in(votedIds));
                results = mongoTemplate.find(pollQuery, Poll.class);
            }
        }

        return results;
    }

    public List<Poll> searchPolls(String query) {
        return pollRepository.findByTitleContainingIgnoreCase(query);
    }
}
