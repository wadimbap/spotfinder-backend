package com.spotfinder.comment.entity;

import com.spotfinder.common.entity.BaseNumberEntity;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
public class CommentEntity extends BaseNumberEntity {

    @ManyToOne
    @JoinColumn(name = "spot_id")
    private SpotEntity spot;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text;

    @ManyToOne
    @JoinColumn(name = "parent_comment_id")
    private CommentEntity parentComment;
}
