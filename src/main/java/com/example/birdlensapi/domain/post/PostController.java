package com.example.birdlensapi.domain.post;

import com.example.birdlensapi.common.dto.ApiResponse;
import com.example.birdlensapi.domain.post.dto.CreatePostRequest;
import com.example.birdlensapi.domain.post.dto.FeedPageResponse;
import com.example.birdlensapi.domain.post.dto.PostResponse;
import com.example.birdlensapi.domain.post.dto.PresignedUrlRequest;
import com.example.birdlensapi.domain.post.dto.PresignedUrlResponse;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.storage.S3StorageService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final S3StorageService s3StorageService;
    private final PostService postService;

    public PostController(S3StorageService s3StorageService, PostService postService) {
        this.s3StorageService = s3StorageService;
        this.postService = postService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FeedPageResponse>> getFeed(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        FeedPageResponse response = postService.getFeed(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/media/request-upload")
    public ResponseEntity<ApiResponse<List<PresignedUrlResponse>>> requestUploadUrls(
            @Valid @RequestBody PresignedUrlRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = ((User) userDetails).getId().toString();

        List<PresignedUrlResponse> urls = s3StorageService.generatePresignedUploadUrls(userId, request.files());
        return ResponseEntity.ok(ApiResponse.success(urls));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        PostResponse response = postService.createPost(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}