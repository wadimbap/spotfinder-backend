package com.spotfinder.spot.repository;

import com.spotfinder.spot.entity.SpotEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SpotRepository extends JpaRepository<SpotEntity, UUID>, JpaSpecificationExecutor<SpotEntity> {

    @Query("SELECT s FROM SpotEntity s WHERE s.approved = true")
    List<SpotEntity> getAllApprovedSpots();

    Optional<SpotEntity> findByIdAndApprovedTrue(UUID id);

    List<SpotEntity> findAllByApprovedFalse();

    List<SpotEntity> findAllByCreatedByIdOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SpotEntity> findByIdAndCreatedById(UUID spotId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SpotEntity s where s.id = :id")
    Optional<SpotEntity> findByIdForUpdate(UUID id);
}
