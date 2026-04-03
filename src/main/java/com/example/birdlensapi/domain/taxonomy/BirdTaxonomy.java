package com.example.birdlensapi.domain.taxonomy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "bird_taxonomy")
public class BirdTaxonomy {

    // eBird uses its own unique species code (e.g., "mallar3"), so we do not auto-generate this ID.
    @Id
    @Column(name = "species_code", length = 20)
    private String speciesCode;

    @Column(name = "common_name", nullable = false)
    private String commonName;

    @Column(name = "scientific_name", nullable = false)
    private String scientificName;

    @Column(length = 50)
    private String category;

    @Column(name = "taxon_order")
    private BigDecimal taxonOrder;

    @Column(name = "bird_order", length = 100)
    private String birdOrder;

    @Column(name = "family_common_name", length = 100)
    private String familyCommonName;

    @Column(name = "family_scientific_name", length = 100)
    private String familyScientificName;

    public BirdTaxonomy() {}

    public String getSpeciesCode() { return speciesCode; }
    public void setSpeciesCode(String speciesCode) { this.speciesCode = speciesCode; }

    public String getCommonName() { return commonName; }
    public void setCommonName(String commonName) { this.commonName = commonName; }

    public String getScientificName() { return scientificName; }
    public void setScientificName(String scientificName) { this.scientificName = scientificName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getTaxonOrder() { return taxonOrder; }
    public void setTaxonOrder(BigDecimal taxonOrder) { this.taxonOrder = taxonOrder; }

    public String getBirdOrder() { return birdOrder; }
    public void setBirdOrder(String birdOrder) { this.birdOrder = birdOrder; }

    public String getFamilyCommonName() { return familyCommonName; }
    public void setFamilyCommonName(String familyCommonName) { this.familyCommonName = familyCommonName; }

    public String getFamilyScientificName() { return familyScientificName; }
    public void setFamilyScientificName(String familyScientificName) { this.familyScientificName = familyScientificName; }
}