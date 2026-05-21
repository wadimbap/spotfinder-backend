package com.spotfinder.spot.repository;

import com.spotfinder.spot.entity.SpotPhotoEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotPhotoRepository extends JpaRepository<SpotPhotoEntity, UUID> {

    List<SpotPhotoEntity> findAllBySpotIdOrderByCreatedAtAsc(UUID spotId);

    Optional<SpotPhotoEntity> findByIdAndSpotId(UUID id, UUID spotId);
}
