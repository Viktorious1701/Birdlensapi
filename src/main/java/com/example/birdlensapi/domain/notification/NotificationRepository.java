package com.example.birdlensapi.domain.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<UserNotification, UUID> {
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}