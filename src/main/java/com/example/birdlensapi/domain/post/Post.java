package com.example.birdlensapi.domain.post;

import com.example.birdlensapi.domain.taxonomy.BirdTaxonomy;
import com.example.birdlensapi.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "location_name")
    private String locationName;

    @Column(name = "location_point", columnDefinition = "GEOGRAPHY(Point, 4326)")
    private Point locationPoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy_level", nullable = false)
    private PrivacyLevel privacyLevel = PrivacyLevel.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostType type;

    @Column(name = "sighting_date")
    private LocalDate sightingDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tagged_species_code")
    private BirdTaxonomy taggedSpecies;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostMedia> media = new ArrayList<>();

    // Hibernate will automatically execute these subqueries efficiently when fetching a Post
    @Formula("(SELECT count(*) FROM post_reactions r WHERE r.post_id = id AND r.reaction_type = 'LIKE')")
    private Integer likeCount = 0;

    @Formula("(SELECT count(*) FROM post_comments c WHERE c.post_id = id)")
    private Integer commentCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Post() {}

    public void addMedia(PostMedia postMedia) {
        media.add(postMedia);
        postMedia.setPost(this);
    }

    public void removeMedia(PostMedia postMedia) {
        media.remove(postMedia);
        postMedia.setPost(null);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public Point getLocationPoint() { return locationPoint; }
    public void setLocationPoint(Point locationPoint) { this.locationPoint = locationPoint; }

    public PrivacyLevel getPrivacyLevel() { return privacyLevel; }
    public void setPrivacyLevel(PrivacyLevel privacyLevel) { this.privacyLevel = privacyLevel; }

    public PostType getType() { return type; }
    public void setType(PostType type) { this.type = type; }

    public LocalDate getSightingDate() { return sightingDate; }
    public void setSightingDate(LocalDate sightingDate) { this.sightingDate = sightingDate; }

    public BirdTaxonomy getTaggedSpecies() { return taggedSpecies; }
    public void setTaggedSpecies(BirdTaxonomy taggedSpecies) { this.taggedSpecies = taggedSpecies; }

    public List<PostMedia> getMedia() { return media; }
    public void setMedia(List<PostMedia> media) { this.media = media; }

    public Integer getLikeCount() { return likeCount == null ? 0 : likeCount; }
    public Integer getCommentCount() { return commentCount == null ? 0 : commentCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}