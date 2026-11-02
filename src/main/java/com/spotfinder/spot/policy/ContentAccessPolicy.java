package com.spotfinder.spot.policy;

import com.spotfinder.common.exception.SpotNotFoundException;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import org.springframework.stereotype.Component;

@Component
public class ContentAccessPolicy {

    public void ensureCanAccessContent(UserEntity user, SpotEntity spot) {
        boolean approved = Boolean.TRUE.equals(spot.getApproved());

        boolean owner = user.getId().equals(spot.getCreatedBy().getId());

        boolean privileged = user.getRole() == UserRole.MODERATOR
                || user.getRole() == UserRole.ADMIN;

        boolean allowed = approved || owner || privileged;

        if (!allowed) {
            throw new SpotNotFoundException(spot.getId());
        }
    }
}
