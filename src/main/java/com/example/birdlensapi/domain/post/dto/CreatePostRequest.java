package com.example.birdlensapi.domain.post.dto;

import com.example.birdlensapi.domain.post.PostType;
import com.example.birdlensapi.domain.post.PrivacyLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreatePostRequest(
        @NotBlank(message = "Content must not be empty") String content,
        String locationName,
        @Min(-90) @Max(90) Double lat,
        @Min(-180) @Max(180) Double lng,
        @NotNull(message = "Privacy level is required") PrivacyLevel privacyLevel,
        @NotNull(message = "Post type is required") PostType type,
        LocalDate sightingDate,
        String taggedSpeciesCode,
        List<String> mediaKeys
) {}