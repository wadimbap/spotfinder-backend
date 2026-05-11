package com.spotfinder.user.repository;

import com.spotfinder.user.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  Boolean existsByEmail(String email);

  Optional<UserEntity> findByEmail(String email);
}
