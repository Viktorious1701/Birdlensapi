package com.example.birdlensapi.domain.post;

import com.example.birdlensapi.common.exception.ResourceNotFoundException;
import com.example.birdlensapi.config.RabbitMQConfig;
import com.example.birdlensapi.domain.post.dto.CommentPageResponse;
import com.example.birdlensapi.domain.post.dto.CommentRequest;
import com.example.birdlensapi.domain.post.dto.CommentResponse;
import com.example.birdlensapi.domain.post.dto.CreatePostRequest;
import com.example.birdlensapi.domain.post.dto.FeedPageResponse;
import com.example.birdlensapi.domain.post.dto.PostFeedResponse;
import com.example.birdlensapi.domain.post.dto.PostResponse;
import com.example.birdlensapi.domain.taxonomy.BirdTaxonomy;
import com.example.birdlensapi.domain.taxonomy.TaxonomyRepository;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import com.example.birdlensapi.messaging.events.PostCreatedEvent;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final PostReactionRepository postReactionRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;
    private final TaxonomyRepository taxonomyRepository;
    private final RabbitTemplate rabbitTemplate;
    private final GeometryFactory geometryFactory;

    public PostService(PostRepository postRepository,
                       PostReactionRepository postReactionRepository,
                       PostCommentRepository postCommentRepository,
                       UserRepository userRepository,
                       TaxonomyRepository taxonomyRepository,
                       RabbitTemplate rabbitTemplate) {
        this.postRepository = postRepository;
        this.postReactionRepository = postReactionRepository;
        this.postCommentRepository = postCommentRepository;
        this.userRepository = userRepository;
        this.taxonomyRepository = taxonomyRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    }

    @Transactional
    public PostResponse createPost(CreatePostRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Post post = new Post();
        post.setUser(user);
        post.setContent(request.content());
        post.setLocationName(request.locationName());
        post.setPrivacyLevel(request.privacyLevel());
        post.setType(request.type());
        post.setSightingDate(request.sightingDate());

        if (request.lat() != null && request.lng() != null) {
            Point point = geometryFactory.createPoint(new Coordinate(request.lng(), request.lat()));
            post.setLocationPoint(point);
        }

        if (request.taggedSpeciesCode() != null && !request.taggedSpeciesCode().isBlank()) {
            BirdTaxonomy species = taxonomyRepository.findById(request.taggedSpeciesCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Species code not found: " + request.taggedSpeciesCode()));
            post.setTaggedSpecies(species);
        }

        if (request.mediaKeys() != null) {
            for (String key : request.mediaKeys()) {
                PostMedia media = new PostMedia();
                media.setOriginalUrl(key);
                media.setProcessingStatus(ProcessingStatus.PENDING);
                post.addMedia(media);
            }
        }

        Post savedPost = postRepository.save(post);

        if (request.mediaKeys() != null && !request.mediaKeys().isEmpty()) {
            PostCreatedEvent event = new PostCreatedEvent(savedPost.getId());
            rabbitTemplate.convertAndSend(RabbitMQConfig.POSTS_EXCHANGE, RabbitMQConfig.POST_CREATED_ROUTING_KEY, event);
        }

        return PostResponse.fromEntity(savedPost);
    }

    @Cacheable(value = "feed", key = "#pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()")
    @Transactional(readOnly = true)
    public FeedPageResponse getFeed(Pageable pageable) {
        Page<Post> postPage = postRepository.findFeedPosts(pageable, ProcessingStatus.COMPLETED);

        List<PostFeedResponse> content = postPage.getContent().stream()
                .map(PostFeedResponse::fromEntity)
                .collect(Collectors.toList());

        return new FeedPageResponse(
                content,
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages(),
                postPage.isLast()
        );
    }

    @Transactional
    public void toggleLike(UUID postId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        Optional<PostReaction> existingReaction = postReactionRepository
                .findByPostIdAndUserIdAndReactionType(post.getId(), user.getId(), ReactionType.LIKE);

        if (existingReaction.isPresent()) {
            // User already liked it, so we delete it (Toggle Off)
            postReactionRepository.delete(existingReaction.get());
        } else {
            // User hasn't liked it, so we create it (Toggle On)
            PostReaction reaction = new PostReaction();
            reaction.setPost(post);
            reaction.setUser(user);
            reaction.setReactionType(ReactionType.LIKE);
            postReactionRepository.save(reaction);
        }
    }

    @Transactional
    public CommentResponse addComment(UUID postId, CommentRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        PostComment comment = new PostComment();
        comment.setPost(post);
        comment.setUser(user);
        comment.setContent(request.content());

        PostComment savedComment = postCommentRepository.save(comment);

        return CommentResponse.fromEntity(savedComment);
    }

    @Transactional(readOnly = true)
    public CommentPageResponse getComments(UUID postId, Pageable pageable) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post not found");
        }

        Page<PostComment> commentPage = postCommentRepository.findByPostId(postId, pageable);

        List<CommentResponse> content = commentPage.getContent().stream()
                .map(CommentResponse::fromEntity)
                .collect(Collectors.toList());

        return new CommentPageResponse(
                content,
                commentPage.getNumber(),
                commentPage.getSize(),
                commentPage.getTotalElements(),
                commentPage.getTotalPages(),
                commentPage.isLast()
        );
    }
}