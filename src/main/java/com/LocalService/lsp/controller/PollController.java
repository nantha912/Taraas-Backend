package com.LocalService.lsp.controller;

import com.LocalService.lsp.model.*;
import com.LocalService.lsp.dto.PollResponseDTO;
import com.LocalService.lsp.dto.PollCommentResponseDTO;
import com.LocalService.lsp.repository.CustomerRepository;
import com.LocalService.lsp.repository.ProviderRepository;
import com.LocalService.lsp.repository.PollVoteRepository;
import com.LocalService.lsp.repository.PollLikeRepository;
import com.LocalService.lsp.service.PollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/polls")
public class PollController {

    @Autowired
    private PollService pollService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private PollVoteRepository pollVoteRepository;

    @Autowired
    private PollLikeRepository pollLikeRepository;

    private void populateTransientFields(List<Poll> polls) {
        Customer customer = getCurrentCustomer();
        if (customer == null || polls == null || polls.isEmpty()) {
            return;
        }
        String userId = customer.getId();
        
        List<PollLike> likes = pollLikeRepository.findByUserId(userId);
        Set<String> likedPollIds = new HashSet<>();
        for (PollLike l : likes) {
            likedPollIds.add(l.getPollId());
        }

        List<PollVote> votes = pollVoteRepository.findByUserId(userId);
        Map<String, String> pollToOptionMap = new HashMap<>();
        for (PollVote v : votes) {
            pollToOptionMap.put(v.getPollId(), v.getSelectedOptionId());
        }

        for (Poll poll : polls) {
            if (poll != null) {
                poll.setUserLiked(likedPollIds.contains(poll.getId()));
                poll.setUserVotedOptionId(pollToOptionMap.get(poll.getId()));
            }
        }
    }

    private String getProfilePhoto(String authorId, String authorType) {
        if ("PROVIDER".equals(authorType)) {
            return providerRepository.findById(authorId)
                    .map(Provider::getProfilePhotoUrl)
                    .orElse(null);
        } else {
            return customerRepository.findById(authorId)
                    .map(Customer::getProfilePhotoUrl)
                    .orElse(null);
        }
    }

    private PollResponseDTO toPollResponseDTO(Poll poll) {
        if (poll == null) return null;
        String photo = getProfilePhoto(poll.getAuthorId(), poll.getAuthorType());
        return new PollResponseDTO(poll, photo);
    }

    private List<PollResponseDTO> toPollResponseDTOList(List<Poll> polls) {
        if (polls == null) return Collections.emptyList();
        List<PollResponseDTO> dtos = new ArrayList<>();
        for (Poll poll : polls) {
            dtos.add(toPollResponseDTO(poll));
        }
        return dtos;
    }

    private PollCommentResponseDTO toCommentResponseDTO(PollComment comment) {
        if (comment == null) return null;
        String photo = getProfilePhoto(comment.getAuthorId(), comment.getAuthorType());
        return new PollCommentResponseDTO(comment, photo);
    }

    private List<PollCommentResponseDTO> toCommentResponseDTOList(List<PollComment> comments) {
        if (comments == null) return Collections.emptyList();
        List<PollCommentResponseDTO> dtos = new ArrayList<>();
        for (PollComment comment : comments) {
            dtos.add(toCommentResponseDTO(comment));
        }
        return dtos;
    }

    private boolean containsUrl(String text) {
        if (text == null) return false;
        return text.matches("(?i).*(https?://|www\\.|\\b\\w+\\.(com|org|net|co|in|info|biz|io|edu|gov)\\b).*");
    }

    private Customer getCurrentCustomer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth != null) ? auth.getName() : null;
        if (email == null || email.equals("anonymousUser")) {
            return null;
        }
        return customerRepository.findByEmail(email).orElse(null);
    }

    @PostMapping
    public ResponseEntity<?> createPoll(@RequestBody Poll poll) {
        Customer customer = getCurrentCustomer();
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        if (poll.getTitle() == null || poll.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Poll title is required"));
        }
        if (poll.getTitle().length() > 256) {
            return ResponseEntity.badRequest().body(Map.of("error", "Poll title must not exceed 256 characters"));
        }
        if (containsUrl(poll.getTitle())) {
            return ResponseEntity.badRequest().body(Map.of("error", "External links are not permitted in poll titles"));
        }

        if (poll.getOptions() == null || poll.getOptions().isEmpty() || poll.getOptions().size() > 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "Poll must contain between 1 and 4 options"));
        }

        for (PollOption opt : poll.getOptions()) {
            if (opt.getText() == null || opt.getText().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "All active option inputs must be filled out"));
            }
            if (opt.getText().length() > 50) {
                return ResponseEntity.badRequest().body(Map.of("error", "Option text must not exceed 50 characters"));
            }
            if (containsUrl(opt.getText())) {
                return ResponseEntity.badRequest().body(Map.of("error", "External links are not permitted in poll options"));
            }
        }

        // Determine author type based on provider profile check
        Optional<Provider> providerOpt = providerRepository.findByCustomerId(customer.getId());
        if (providerOpt.isPresent()) {
            poll.setAuthorId(providerOpt.get().getId());
            poll.setAuthorType("PROVIDER");
            poll.setAuthorName(providerOpt.get().getName());
            poll.setAuthorProfilePhoto(providerOpt.get().getProfilePhotoUrl());
        } else {
            poll.setAuthorId(customer.getId());
            poll.setAuthorType("CUSTOMER");
            poll.setAuthorName(customer.getName());
            poll.setAuthorProfilePhoto(customer.getProfilePhotoUrl());
        }

        Poll savedPoll = pollService.createPoll(poll);
        populateTransientFields(Collections.singletonList(savedPoll));
        return ResponseEntity.status(HttpStatus.CREATED).body(toPollResponseDTO(savedPoll));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPollById(@PathVariable String id) {
        Optional<Poll> pollOpt = pollService.getPollById(id);
        pollOpt.ifPresent(p -> populateTransientFields(Collections.singletonList(p)));
        return pollOpt
                .map(p -> ResponseEntity.ok(toPollResponseDTO(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/feed")
    public ResponseEntity<?> getPollsFeed(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit) {
        
        Map<String, Object> feed = new HashMap<>();

        if (q != null && !q.isBlank()) {
            List<Poll> searchResults = pollService.searchPolls(q);
            populateTransientFields(searchResults);
            feed.put("mostEngaged", toPollResponseDTOList(searchResults));
            feed.put("mostRecent", toPollResponseDTOList(searchResults));
        } else {
            List<Poll> engaged = pollService.getMostEngagedFeed(userId, page, limit);
            List<Poll> recent = pollService.getMostRecentFeed(userId, page, limit);
            populateTransientFields(engaged);
            populateTransientFields(recent);
            feed.put("mostEngaged", toPollResponseDTOList(engaged));
            feed.put("mostRecent", toPollResponseDTOList(recent));
        }

        return ResponseEntity.ok(feed);
    }

    @GetMapping("/reels")
    public ResponseEntity<?> getReelsFeed(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit) {
        List<Poll> reels = pollService.getReelsFeed(userId, page, limit);
        populateTransientFields(reels);
        return ResponseEntity.ok(toPollResponseDTOList(reels));
    }

    @PostMapping("/{id}/vote")
    public ResponseEntity<?> voteOnPoll(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        Customer customer = getCurrentCustomer();
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String optionId = body.get("optionId");
        if (optionId == null || optionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Option ID is required"));
        }

        try {
            Poll updated = pollService.vote(id, customer.getId(), optionId);
            populateTransientFields(Collections.singletonList(updated));
            return ResponseEntity.ok(toPollResponseDTO(updated));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<?> toggleLikePoll(@PathVariable String id) {
        Customer customer = getCurrentCustomer();
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        boolean liked = pollService.toggleLike(id, customer.getId());
        return ResponseEntity.ok(Map.of("liked", liked));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        Customer customer = getCurrentCustomer();
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String text = body.get("text");
        String parentCommentId = body.get("parentCommentId");

        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment text is required"));
        }
        if (text.length() > 256) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment text must not exceed 256 characters"));
        }
        if (containsUrl(text)) {
            return ResponseEntity.badRequest().body(Map.of("error", "External links are not permitted in comments"));
        }

        String authorId = customer.getId();
        String authorType = "CUSTOMER";
        String authorName = customer.getName();
        String authorProfilePhoto = customer.getProfilePhotoUrl();

        // If provider profile exists, post comment as provider
        Optional<Provider> providerOpt = providerRepository.findByCustomerId(customer.getId());
        if (providerOpt.isPresent()) {
            authorId = providerOpt.get().getId();
            authorType = "PROVIDER";
            authorName = providerOpt.get().getName();
            authorProfilePhoto = providerOpt.get().getProfilePhotoUrl();
        }

        PollComment comment = pollService.addOrUpdateComment(id, authorId, authorType, authorName, authorProfilePhoto, text, parentCommentId);
        return ResponseEntity.ok(toCommentResponseDTO(comment));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<?> getPollComments(
            @PathVariable String id,
            @RequestParam(required = false) String userId) {
        List<PollComment> comments = pollService.getPollComments(id, userId);
        return ResponseEntity.ok(toCommentResponseDTOList(comments));
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<?> recordPollView(@PathVariable String id) {
        Customer customer = getCurrentCustomer();
        if (customer != null) {
            pollService.recordView(id, customer.getId());
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/activity/counts")
    public ResponseEntity<?> getUserActivityCounts() {
        Customer customer = getCurrentCustomer();
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        String customerId = customer.getId();
        String providerId = providerRepository.findByCustomerId(customerId)
                .map(Provider::getId)
                .orElse(null);

        long createdCount = pollService.getCreatedCount(customerId, providerId);
        long likedCount = pollService.getLikedCount(customerId);
        long commentedCount = pollService.getCommentedCount(customerId, providerId);
        long votedCount = pollService.getVotedCount(customerId);

        return ResponseEntity.ok(Map.of(
            "created", createdCount,
            "liked", likedCount,
            "commented", commentedCount,
            "voted", votedCount
        ));
    }

    @GetMapping("/user/activity")
    public ResponseEntity<?> getUserActivity(
            @RequestParam String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int limit) {
        Customer customer = getCurrentCustomer();
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        String customerId = customer.getId();
        String providerId = providerRepository.findByCustomerId(customerId)
                .map(Provider::getId)
                .orElse(null);

        List<Poll> polls = pollService.getUserActivityByType(customerId, providerId, type, page, limit);
        populateTransientFields(polls);
        return ResponseEntity.ok(toPollResponseDTOList(polls));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePoll(@PathVariable String id) {
        Customer customer = getCurrentCustomer();
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        try {
            pollService.deletePoll(id, customer.getId());
            return ResponseEntity.ok(Map.of("message", "Poll deleted successfully"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable String commentId) {
        Customer customer = getCurrentCustomer();
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        try {
            pollService.deleteComment(commentId, customer.getId());
            return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}
