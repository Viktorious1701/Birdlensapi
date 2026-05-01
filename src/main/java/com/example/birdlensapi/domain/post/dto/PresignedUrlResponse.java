package com.example.birdlensapi.domain.post.dto;

public record PresignedUrlResponse(
        String objectKey,
        String presignedUrl
) {}