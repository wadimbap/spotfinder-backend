package com.spotfinder.spot.dto.mapper;

import com.spotfinder.spot.dto.SpotPhotoResponse;
import com.spotfinder.spot.entity.SpotPhotoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SpotPhotoMapper {

    @Mapping(target = "spotId", source = "spot.id")
    @Mapping(target = "createdByUserId", source = "createdBy.id")
    SpotPhotoResponse toResponse(SpotPhotoEntity entity);
}
