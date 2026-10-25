package com.spotfinder.user.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.spotfinder.user.entity.ActivityType;
import jakarta.validation.constraints.Size;

public class UpdateCurrentUserRequest {

    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String displayName;

    private ActivityType primaryActivity;

    @JsonIgnore
    private boolean primaryActivityProvided;

    public UpdateCurrentUserRequest() {
    }

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public UpdateCurrentUserRequest(String displayName, ActivityType primaryActivity) {
        this.displayName = displayName;
        setPrimaryActivity(primaryActivity);
    }

    public String displayName() {
        return displayName;
    }

    @JsonSetter("displayName")
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ActivityType primaryActivity() {
        return primaryActivity;
    }

    @JsonSetter("primaryActivity")
    public void setPrimaryActivity(ActivityType primaryActivity) {
        this.primaryActivity = primaryActivity;
        this.primaryActivityProvided = true;
    }

    public boolean hasPrimaryActivity() {
        return primaryActivityProvided;
    }
}
