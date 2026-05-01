package com.example.birdlensapi.domain.post.dto;

import jakarta.validation.constraints.NotBlank;

public record PresignedUrlRequestItem(
        @NotBlank(message = "Filename must not be blank") String filename,
        @NotBlank(message = "Content type must not be blank") String contentType
) {}