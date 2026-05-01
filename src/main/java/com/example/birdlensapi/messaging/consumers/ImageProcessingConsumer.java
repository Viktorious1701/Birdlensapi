package com.example.birdlensapi.messaging.consumers;

import com.example.birdlensapi.domain.post.PostMedia;
import com.example.birdlensapi.domain.post.PostMediaRepository;
import com.example.birdlensapi.domain.post.ProcessingStatus;
import com.example.birdlensapi.messaging.events.PostCreatedEvent;
import com.example.birdlensapi.storage.S3StorageService;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

@Component
@Profile("worker")
public class ImageProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessingConsumer.class);

    private final PostMediaRepository postMediaRepository;
    private final S3StorageService s3StorageService;

    public ImageProcessingConsumer(PostMediaRepository postMediaRepository, S3StorageService s3StorageService) {
        this.postMediaRepository = postMediaRepository;
        this.s3StorageService = s3StorageService;
    }

    @RabbitListener(queues = "image-processing-queue")
    @Transactional
    public void processImages(PostCreatedEvent event) {
        log.info("Worker picked up PostCreatedEvent for post: {}", event.postId());

        List<PostMedia> mediaList = postMediaRepository.findByPostId(event.postId());

        for (PostMedia media : mediaList) {
            if (media.getProcessingStatus() != ProcessingStatus.PENDING) {
                continue;
            }

            try {
                String originalKey = media.getOriginalUrl();

                // 1. Generate 300x300 Thumbnail directly from stream to save RAM
                String thumbKey = insertSuffix(originalKey, "_thumb");
                try (InputStream is = s3StorageService.downloadFileStream(originalKey)) {
                    ByteArrayOutputStream thumbOs = new ByteArrayOutputStream();
                    Thumbnails.of(is)
                            .size(300, 300)
                            .outputFormat("jpg")
                            .toOutputStream(thumbOs);
                    s3StorageService.uploadFile(thumbKey, thumbOs.toByteArray(), "image/jpeg");
                }

                // 2. Generate Compressed Web Version (70% quality)
                String compKey = insertSuffix(originalKey, "_comp");
                try (InputStream is = s3StorageService.downloadFileStream(originalKey)) {
                    ByteArrayOutputStream compOs = new ByteArrayOutputStream();
                    Thumbnails.of(is)
                            .scale(1.0)
                            .outputQuality(0.7)
                            .outputFormat("jpg")
                            .toOutputStream(compOs);
                    s3StorageService.uploadFile(compKey, compOs.toByteArray(), "image/jpeg");
                }

                // 3. Update Database
                media.setThumbnailUrl(thumbKey);
                media.setCompressedUrl(compKey);
                media.setProcessingStatus(ProcessingStatus.COMPLETED);
                postMediaRepository.save(media);

                log.info("Successfully processed and resized media: {}", media.getId());
            } catch (Exception e) {
                log.error("Failed to process media: {}", media.getId(), e);
                // Throwing the exception forces the transaction to roll back, and Spring AMQP
                // will automatically retry it 3 times before moving it to the DLQ.
                throw new RuntimeException("Image processing failed for media ID: " + media.getId(), e);
            }
        }
    }

    private String insertSuffix(String key, String suffix) {
        int dotIndex = key.lastIndexOf('.');
        if (dotIndex == -1) {
            return key + suffix + ".jpg"; // fallback
        }
        return key.substring(0, dotIndex) + suffix + ".jpg";
    }
}