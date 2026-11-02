package com.spotfinder.spot.state;

import com.spotfinder.spot.entity.SpotEntity;

public class ApprovedSpotState  implements SpotState {

    @Override
    public void approve(SpotEntity spot) {
        // Secondary approvement acceptable: spot already approved
    }
}
