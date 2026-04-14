package com.example.birdlensapi.domain.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PostReactionRepository extends JpaRepository<PostReaction, UUID> {
}