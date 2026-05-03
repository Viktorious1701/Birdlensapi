package com.example.birdlensapi.domain.post.dto;

import com.example.birdlensapi.domain.post.PostMedia;
import com.example.birdlensapi.domain.post.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record PostMediaFeedResponse(
        UUID id,
        String thumbnailUrl,
        String compressedUrl,
        ProcessingStatus processingStatus,
        Instant createdAt
) {
    public static PostMediaFeedResponse fromEntity(PostMedia media) {
        return new PostMediaFeedResponse(
                media.getId(),
                media.getThumbnailUrl(),
                media.getCompressedUrl(),
                media.getProcessingStatus(),
                media.getCreatedAt()
        );
    }
}