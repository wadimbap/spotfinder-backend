package com.spotfinder.spot.state;

import com.spotfinder.spot.entity.SpotEntity;
import org.springframework.stereotype.Component;

@Component
public class SpotWorkflow {

    private final SpotState pendingState = new PendingSpotState();
    private final SpotState approvedState = new ApprovedSpotState();

    public void approve(SpotEntity spot) {
        SpotState state = stateOf(spot);
        state.approve(spot);
    }

    private SpotState stateOf(SpotEntity spot) {
        return spot.getApproved() ? approvedState : pendingState;
    }
}
