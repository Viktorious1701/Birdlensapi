package com.example.birdlensapi.domain.post.dto;

import com.example.birdlensapi.domain.post.Post;
import com.example.birdlensapi.domain.post.PostType;
import com.example.birdlensapi.domain.post.PrivacyLevel;
import com.example.birdlensapi.domain.user.UserResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record PostResponse(
        UUID id,
        UserResponse user,
        String content,
        String locationName,
        Double lat,
        Double lng,
        PrivacyLevel privacyLevel,
        PostType type,
        LocalDate sightingDate,
        String taggedSpeciesCode,
        List<PostMediaResponse> media,
        Instant createdAt
) {
    public static PostResponse fromEntity(Post post) {
        Double lat = null;
        Double lng = null;
        if (post.getLocationPoint() != null) {
            lat = post.getLocationPoint().getY();
            lng = post.getLocationPoint().getX();
        }

        String speciesCode = post.getTaggedSpecies() != null ? post.getTaggedSpecies().getSpeciesCode() : null;

        List<PostMediaResponse> mediaResponses = post.getMedia().stream()
                .map(PostMediaResponse::fromEntity)
                .collect(Collectors.toList());

        return new PostResponse(
                post.getId(),
                UserResponse.fromEntity(post.getUser()),
                post.getContent(),
                post.getLocationName(),
                lat,
                lng,
                post.getPrivacyLevel(),
                post.getType(),
                post.getSightingDate(),
                speciesCode,
                mediaResponses,
                post.getCreatedAt()
        );
    }
}