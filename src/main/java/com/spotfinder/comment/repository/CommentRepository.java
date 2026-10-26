package com.spotfinder.comment.repository;

import com.spotfinder.comment.entity.CommentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

    List<CommentEntity> findAllBySpotIdOrderByCreatedAtAscIdAsc(UUID spotId);

    @Query("select c.spot.id from CommentEntity c where c.id = :id")
    Optional<UUID> findSpotIdById(Long id);

    boolean existsByParentCommentId(Long parentId);
}
