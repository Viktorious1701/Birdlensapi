package com.example.birdlensapi.domain.tour;

import com.example.birdlensapi.domain.tour.dto.EventPageResponse;
import com.example.birdlensapi.domain.tour.dto.EventResponse;
import com.example.birdlensapi.domain.tour.dto.TourPageResponse;
import com.example.birdlensapi.domain.tour.dto.TourResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TourService {

    private final EventRepository eventRepository;
    private final TourRepository tourRepository;

    public TourService(EventRepository eventRepository, TourRepository tourRepository) {
        this.eventRepository = eventRepository;
        this.tourRepository = tourRepository;
    }

    @Transactional(readOnly = true)
    public EventPageResponse getEvents(Pageable pageable) {
        Page<Event> eventPage = eventRepository.findAll(pageable);

        List<EventResponse> content = eventPage.getContent().stream()
                .map(EventResponse::fromEntity)
                .collect(Collectors.toList());

        return new EventPageResponse(
                content,
                eventPage.getNumber(),
                eventPage.getSize(),
                eventPage.getTotalElements(),
                eventPage.getTotalPages(),
                eventPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public TourPageResponse getTours(Pageable pageable) {
        Page<Tour> tourPage = tourRepository.findAll(pageable);

        List<TourResponse> content = tourPage.getContent().stream()
                .map(TourResponse::fromEntity)
                .collect(Collectors.toList());

        return new TourPageResponse(
                content,
                tourPage.getNumber(),
                tourPage.getSize(),
                tourPage.getTotalElements(),
                tourPage.getTotalPages(),
                tourPage.isLast()
        );
    }
}