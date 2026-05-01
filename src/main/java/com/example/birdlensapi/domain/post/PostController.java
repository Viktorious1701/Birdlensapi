package com.example.birdlensapi.domain.post;

import com.example.birdlensapi.common.dto.ApiResponse;
import com.example.birdlensapi.domain.post.dto.PresignedUrlRequest;
import com.example.birdlensapi.domain.post.dto.PresignedUrlResponse;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.storage.S3StorageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final S3StorageService s3StorageService;

    public PostController(S3StorageService s3StorageService) {
        this.s3StorageService = s3StorageService;
    }

    @PostMapping("/media/request-upload")
    public ResponseEntity<ApiResponse<List<PresignedUrlResponse>>> requestUploadUrls(
            @Valid @RequestBody PresignedUrlRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Since our User entity implements UserDetails, we can safely cast it to get the ID
        String userId = ((User) userDetails).getId().toString();

        List<PresignedUrlResponse> urls = s3StorageService.generatePresignedUploadUrls(userId, request.files());
        return ResponseEntity.ok(ApiResponse.success(urls));
    }
}