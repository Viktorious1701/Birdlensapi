package com.example.birdlensapi.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    // Fetch JOIN prevents the N+1 problem by pulling the User and Media details in the same query.
    // The WHERE clause explicitly mandates that a post must either have NO media, or its attached media must match the provided status.
    @Query(value = """
            SELECT DISTINCT p FROM Post p 
            LEFT JOIN FETCH p.media m 
            LEFT JOIN FETCH p.user u 
            WHERE SIZE(p.media) = 0 OR m.processingStatus = :status
            """,
            countQuery = """
            SELECT count(DISTINCT p) FROM Post p 
            LEFT JOIN p.media m 
            WHERE SIZE(p.media) = 0 OR m.processingStatus = :status
            """)
    Page<Post> findFeedPosts(Pageable pageable, @Param("status") ProcessingStatus status);
}