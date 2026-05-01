package com.example.birdlensapi.domain.post;

import com.example.birdlensapi.common.exception.ResourceNotFoundException;
import com.example.birdlensapi.config.RabbitMQConfig;
import com.example.birdlensapi.domain.post.dto.CreatePostRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final TaxonomyRepository taxonomyRepository;
    private final RabbitTemplate rabbitTemplate;
    private final GeometryFactory geometryFactory;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       TaxonomyRepository taxonomyRepository,
                       RabbitTemplate rabbitTemplate) {
        this.postRepository = postRepository;
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

        // Map spatial coordinates if provided
        if (request.lat() != null && request.lng() != null) {
            Point point = geometryFactory.createPoint(new Coordinate(request.lng(), request.lat()));
            post.setLocationPoint(point);
        }

        // Map taxonomy species if provided
        if (request.taggedSpeciesCode() != null && !request.taggedSpeciesCode().isBlank()) {
            BirdTaxonomy species = taxonomyRepository.findById(request.taggedSpeciesCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Species code not found: " + request.taggedSpeciesCode()));
            post.setTaggedSpecies(species);
        }

        // Attach media records (they start as PENDING processing status)
        if (request.mediaKeys() != null) {
            for (String key : request.mediaKeys()) {
                PostMedia media = new PostMedia();
                media.setOriginalUrl(key);
                media.setProcessingStatus(ProcessingStatus.PENDING);
                post.addMedia(media); // This handles the bidirectional mapping cleanly
            }
        }

        Post savedPost = postRepository.save(post);

        // Publish the event to RabbitMQ for asynchronous image processing
        if (request.mediaKeys() != null && !request.mediaKeys().isEmpty()) {
            PostCreatedEvent event = new PostCreatedEvent(savedPost.getId());
            rabbitTemplate.convertAndSend(RabbitMQConfig.POSTS_EXCHANGE, RabbitMQConfig.POST_CREATED_ROUTING_KEY, event);
        }

        return PostResponse.fromEntity(savedPost);
    }
}