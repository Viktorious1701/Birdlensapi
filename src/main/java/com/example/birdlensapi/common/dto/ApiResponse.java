package com.example.birdlensapi.common.dto;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetails error,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetails(code, message), Instant.now());
    }

    public record ErrorDetails(String code, String message) {}
}