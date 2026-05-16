package com.spotfinder.spot.repository;

import com.spotfinder.spot.entity.SpotEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotRepository extends JpaRepository<SpotEntity, UUID> {
}
