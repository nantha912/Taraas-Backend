package com.LocalService.lsp.dto;

import com.LocalService.lsp.model.Poll;
import com.LocalService.lsp.model.PollOption;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PollResponseDTO {
    private String id;
    private String authorId;
    private String authorType;
    private String authorName;
    private String authorProfilePhoto;
    private String title;
    private List<PollOption> options;
    private int totalVotes;
    private int likeCount;
    private int commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String userVotedOptionId;
    private boolean userLiked;

    public PollResponseDTO(Poll poll, String profilePhoto) {
        if (poll != null) {
            this.id = poll.getId();
            this.authorId = poll.getAuthorId();
            this.authorType = poll.getAuthorType();
            this.authorName = poll.getAuthorName();
            this.authorProfilePhoto = profilePhoto != null ? profilePhoto : poll.getAuthorProfilePhoto();
            this.title = poll.getTitle();
            this.options = poll.getOptions();
            this.totalVotes = poll.getTotalVotes();
            this.likeCount = poll.getLikeCount();
            this.commentCount = poll.getCommentCount();
            this.createdAt = poll.getCreatedAt();
            this.updatedAt = poll.getUpdatedAt();
            this.userVotedOptionId = poll.getUserVotedOptionId();
            this.userLiked = poll.isUserLiked();
        }
    }
}
