package com.example.birdlensapi.domain.post.dto;

import com.example.birdlensapi.domain.post.PostComment;
import com.example.birdlensapi.domain.user.UserResponse;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UserResponse user,
        String content,
        Instant createdAt
) {
    public static CommentResponse fromEntity(PostComment comment) {
        return new CommentResponse(
                comment.getId(),
                UserResponse.fromEntity(comment.getUser()),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}