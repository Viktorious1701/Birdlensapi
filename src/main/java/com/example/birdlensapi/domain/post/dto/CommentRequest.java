package com.example.birdlensapi.domain.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "Comment cannot be empty")
        @Size(max = 1000, message = "Comment must not exceed 1000 characters")
        String content
) {}