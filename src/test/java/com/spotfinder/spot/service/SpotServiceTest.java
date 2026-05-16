package com.spotfinder.spot.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotfinder.common.exception.UserNotFoundException;
import com.spotfinder.spot.dto.CreateSpotRequest;
import com.spotfinder.spot.dto.SpotResponse;
import com.spotfinder.spot.dto.mapper.SpotMapper;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.entity.SpotFeature;
import com.spotfinder.spot.entity.SpotType;
import com.spotfinder.spot.repository.SpotRepository;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import com.spotfinder.user.service.UserReader;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpotServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPOT_ID = UUID.randomUUID();

    @Mock
    SpotRepository spotRepository;

    @Mock
    SpotMapper spotMapper;

    @Mock
    UserReader userReader;

    SpotService spotService;

    @BeforeEach
    void setUp() {
        spotService = new SpotService(
                userReader,
                spotRepository,
                spotMapper
        );
    }

    @Test
    void createSpot_shouldCreateSpotAndReturnResponse() {
        UserEntity user = userEntity();
        CreateSpotRequest request = createSpotRequest();
        SpotResponse expectedResponse = spotResponse();

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotRepository.save(any(SpotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(spotMapper.toResponse(any(SpotEntity.class))).thenReturn(expectedResponse);

        SpotResponse actualResponse = spotService.createSpot(USER_ID, request);

        assertThat(actualResponse).isEqualTo(expectedResponse);

        ArgumentCaptor<SpotEntity> spotCaptor = ArgumentCaptor.forClass(SpotEntity.class);
        verify(spotRepository).save(spotCaptor.capture());

        SpotEntity spotToSave = spotCaptor.getValue();

        assertThat(spotToSave.getName()).isEqualTo("Central Plaza");
        assertThat(spotToSave.getDescription()).isEqualTo("Flat, stairs and ledges");
        assertThat(spotToSave.getLatitude()).isEqualTo(55.751244);
        assertThat(spotToSave.getLongitude()).isEqualTo(37.618423);
        assertThat(spotToSave.getType()).isEqualTo(SpotType.STREET);
        assertThat(spotToSave.getCreatedBy()).isEqualTo(user);
        assertThat(spotToSave.getFeatures()).isEqualTo(Set.of(
                SpotFeature.FLAT,
                SpotFeature.STAIRS,
                SpotFeature.LEDGE
        ));

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotMapper).toResponse(any(SpotEntity.class));
    }

    @Test
    void createSpot_shouldTrimName() {
        UserEntity user = userEntity();
        CreateSpotRequest request = new CreateSpotRequest(
                "  Central Plaza  ",
                "Flat, stairs and ledges",
                55.751244,
                37.618423,
                SpotType.STREET,
                Set.of(SpotFeature.FLAT)
        );

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotRepository.save(any(SpotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(spotMapper.toResponse(any(SpotEntity.class))).thenReturn(spotResponse());

        spotService.createSpot(USER_ID, request);

        ArgumentCaptor<SpotEntity> spotCaptor = ArgumentCaptor.forClass(SpotEntity.class);
        verify(spotRepository).save(spotCaptor.capture());

        assertThat(spotCaptor.getValue().getName()).isEqualTo("Central Plaza");
    }

    @Test
    void createSpot_shouldUseEmptyFeaturesWhenFeaturesAreNull() {
        UserEntity user = userEntity();
        CreateSpotRequest request = new CreateSpotRequest(
                "Central Plaza",
                "Flat area",
                55.751244,
                37.618423,
                SpotType.STREET,
                null
        );

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotRepository.save(any(SpotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(spotMapper.toResponse(any(SpotEntity.class))).thenReturn(spotResponse());

        spotService.createSpot(USER_ID, request);

        ArgumentCaptor<SpotEntity> spotCaptor = ArgumentCaptor.forClass(SpotEntity.class);
        verify(spotRepository).save(spotCaptor.capture());

        assertThat(spotCaptor.getValue().getFeatures()).isEqualTo(Set.of());
    }

    @Test
    void createSpot_shouldThrowExceptionWhenUserNotFound() {
        CreateSpotRequest request = createSpotRequest();

        when(userReader.getByIdOrElseThrow(USER_ID)).thenThrow(new UserNotFoundException(USER_ID));

        assertThatThrownBy(() -> spotService.createSpot(USER_ID, request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(USER_ID.toString());

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotRepository, never()).save(any(SpotEntity.class));
        verify(spotMapper, never()).toResponse(any(SpotEntity.class));
    }

    private CreateSpotRequest createSpotRequest() {
        return new CreateSpotRequest(
                "Central Plaza",
                "Flat, stairs and ledges",
                55.751244,
                37.618423,
                SpotType.STREET,
                Set.of(
                        SpotFeature.FLAT,
                        SpotFeature.STAIRS,
                        SpotFeature.LEDGE
                )
        );
    }

    private UserEntity userEntity() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail("user@example.com");
        user.setDisplayName("user_name");
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        return user;
    }

    private SpotResponse spotResponse() {
        return new SpotResponse(
                SPOT_ID,
                "Central Plaza",
                "Flat, stairs and ledges",
                55.751244,
                37.618423,
                SpotType.STREET,
                Set.of(
                        SpotFeature.FLAT,
                        SpotFeature.STAIRS,
                        SpotFeature.LEDGE
                ),
                Instant.now(),
                Instant.now()
        );
    }
}
