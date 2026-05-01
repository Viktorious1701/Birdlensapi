package com.example.birdlensapi.domain.post.dto;

import com.example.birdlensapi.domain.post.PostMedia;
import com.example.birdlensapi.domain.post.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record PostMediaResponse(
        UUID id,
        String originalUrl,
        String thumbnailUrl,
        String compressedUrl,
        ProcessingStatus processingStatus,
        Instant createdAt
) {
    public static PostMediaResponse fromEntity(PostMedia media) {
        return new PostMediaResponse(
                media.getId(),
                media.getOriginalUrl(),
                media.getThumbnailUrl(),
                media.getCompressedUrl(),
                media.getProcessingStatus(),
                media.getCreatedAt()
        );
    }
}