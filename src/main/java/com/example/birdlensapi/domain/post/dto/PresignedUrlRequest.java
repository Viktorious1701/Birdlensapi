package com.example.birdlensapi.domain.post.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PresignedUrlRequest(
        @NotEmpty(message = "Files list cannot be empty")
        @Valid List<PresignedUrlRequestItem> files
) {}