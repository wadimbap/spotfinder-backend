package com.spotfinder.spot.state;

import com.spotfinder.spot.entity.SpotEntity;

public class PendingSpotState implements SpotState {

    @Override
    public void approve(SpotEntity spot) {
        spot.setApproved(true);
    }
}
