package com.spotfinder.comment.repository;

import com.spotfinder.comment.entity.CommentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

    List<CommentEntity> findAllBySpotIdOrderByCreatedAtAsc(UUID spotId);
}
