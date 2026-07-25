package com.LocalService.lsp.dto;

import com.LocalService.lsp.model.PollComment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PollCommentResponseDTO {
    private String id;
    private String pollId;
    private String authorId;
    private String authorType;
    private String authorName;
    private String authorProfilePhoto;
    private String parentCommentId;
    private String text;
    private int likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PollCommentResponseDTO(PollComment comment, String profilePhoto) {
        if (comment != null) {
            this.id = comment.getId();
            this.pollId = comment.getPollId();
            this.authorId = comment.getAuthorId();
            this.authorType = comment.getAuthorType();
            this.authorName = comment.getAuthorName();
            this.authorProfilePhoto = profilePhoto != null ? profilePhoto : comment.getAuthorProfilePhoto();
            this.parentCommentId = comment.getParentCommentId();
            this.text = comment.getText();
            this.likeCount = comment.getLikeCount();
            this.createdAt = comment.getCreatedAt();
            this.updatedAt = comment.getUpdatedAt();
        }
    }
}
