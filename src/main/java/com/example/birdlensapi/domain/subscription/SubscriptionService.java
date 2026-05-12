package com.example.birdlensapi.domain.subscription;

import com.example.birdlensapi.domain.subscription.dto.SubscriptionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getAvailableSubscriptions() {
        return subscriptionRepository.findAll().stream()
                .map(SubscriptionResponse::fromEntity)
                .collect(Collectors.toList());
    }
}