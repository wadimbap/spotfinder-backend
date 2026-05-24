package com.spotfinder.spot.repository;

import com.spotfinder.spot.entity.SpotEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SpotRepository extends JpaRepository<SpotEntity, UUID> {

    @Query("SELECT s FROM SpotEntity s WHERE s.approved = true")
    List<SpotEntity> getAllApprovedSpots();

    Optional<SpotEntity> findByIdAndApprovedTrue(UUID id);

    List<SpotEntity> findAllByApprovedFalse();

    List<SpotEntity> findAllByCreatedByIdOrderByCreatedAtDesc(UUID userId);

    Optional<SpotEntity> findByIdAndCreatedById(UUID spotId, UUID userId);
}
