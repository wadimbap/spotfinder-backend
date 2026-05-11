package com.spotfinder.spot.entity;

import com.spotfinder.common.entity.BaseUuidEntity;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "spots")
@Getter
@Setter
@NoArgsConstructor
public class SpotEntity extends BaseUuidEntity {
    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpotType type;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "spot_features",
            joinColumns = @JoinColumn(name = "spot_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false)
    private Set<SpotFeature> features = new HashSet<>();
}
