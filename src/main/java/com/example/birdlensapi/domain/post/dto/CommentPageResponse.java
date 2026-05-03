package com.example.birdlensapi.domain.post.dto;

import java.util.List;

public record CommentPageResponse(
        List<CommentResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {}