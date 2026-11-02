package com.spotfinder.spot.state;

import com.spotfinder.spot.entity.SpotEntity;

public interface SpotState {
    void approve(SpotEntity spot);
}
