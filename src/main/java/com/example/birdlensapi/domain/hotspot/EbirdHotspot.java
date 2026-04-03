package com.example.birdlensapi.domain.hotspot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "ebird_hotspots")
public class EbirdHotspot {

    // eBird uses its own unique location ID (e.g., "L123456"), so we do not
    // auto-generate this ID.
    @Id
    @Column(name = "loc_id", length = 50)
    private String locId;

    @Column(name = "loc_name", nullable = false)
    private String locName;

    @Column(name = "country_code", length = 5)
    private String countryCode;

    @Column(name = "subnational1_code", length = 20)
    private String subnational1Code;

    @Column(name = "subnational2_code", length = 20)
    private String subnational2Code;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    // Maps directly to the PostGIS GEOGRAPHY type. Point uses SRID 4326 natively.
    @Column(columnDefinition = "GEOGRAPHY(Point, 4326)", nullable = false)
    private Point location;

    @Column(name = "latest_obs_dt")
    private LocalDate latestObsDt;

    @Column(name = "num_species_all_time")
    private Integer numSpeciesAllTime;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EbirdHotspot() {
    }

    public String getLocId() {
        return locId;
    }

    public void setLocId(String locId) {
        this.locId = locId;
    }

    public String getLocName() {
        return locName;
    }

    public void setLocName(String locName) {
        this.locName = locName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getSubnational1Code() {
        return subnational1Code;
    }

    public void setSubnational1Code(String subnational1Code) {
        this.subnational1Code = subnational1Code;
    }

    public String getSubnational2Code() {
        return subnational2Code;
    }

    public void setSubnational2Code(String subnational2Code) {
        this.subnational2Code = subnational2Code;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }

    public LocalDate getLatestObsDt() {
        return latestObsDt;
    }

    public void setLatestObsDt(LocalDate latestObsDt) {
        this.latestObsDt = latestObsDt;
    }

    public Integer getNumSpeciesAllTime() {
        return numSpeciesAllTime;
    }

    public void setNumSpeciesAllTime(Integer numSpeciesAllTime) {
        this.numSpeciesAllTime = numSpeciesAllTime;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}