package com.spotfinder.spot.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotfinder.common.exception.SpotNotFoundException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

    @Mock SpotRepository spotRepository;

    @Mock SpotMapper spotMapper;

    @Mock UserReader userReader;

    SpotService spotService;

    @BeforeEach
    void setUp() {
        spotService = new SpotService(userReader, spotRepository, spotMapper);
    }

    @Test
    void createSpot_shouldCreateSpotAndReturnResponse() {
        UserEntity user = userEntity();
        CreateSpotRequest request = createSpotRequest();
        SpotResponse expectedResponse = spotResponse(false);

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
        assertThat(spotToSave.getApproved()).isFalse();
        assertThat(spotToSave.getFeatures())
                .isEqualTo(Set.of(SpotFeature.FLAT, SpotFeature.STAIRS, SpotFeature.LEDGE));

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotMapper).toResponse(any(SpotEntity.class));
    }

    @Test
    void createApprovedSpot_shouldCreateApprovedSpotAndReturnResponse() {
        UserEntity user = userEntity();
        CreateSpotRequest request = createSpotRequest();
        SpotResponse expectedResponse = spotResponse(true);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotRepository.save(any(SpotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(spotMapper.toResponse(any(SpotEntity.class))).thenReturn(expectedResponse);

        SpotResponse actualResponse = spotService.createApprovedSpot(USER_ID, request);

        assertThat(actualResponse).isEqualTo(expectedResponse);

        ArgumentCaptor<SpotEntity> spotCaptor = ArgumentCaptor.forClass(SpotEntity.class);
        verify(spotRepository).save(spotCaptor.capture());

        SpotEntity spotToSave = spotCaptor.getValue();

        assertThat(spotToSave.getName()).isEqualTo("Central Plaza");
        assertThat(spotToSave.getCreatedBy()).isEqualTo(user);
        assertThat(spotToSave.getApproved()).isTrue();

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotMapper).toResponse(any(SpotEntity.class));
    }

    @Test
    void createSpot_shouldTrimName() {
        UserEntity user = userEntity();
        CreateSpotRequest request =
                new CreateSpotRequest(
                        "  Central Plaza  ",
                        "Flat, stairs and ledges",
                        55.751244,
                        37.618423,
                        SpotType.STREET,
                        Set.of(SpotFeature.FLAT));

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotRepository.save(any(SpotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(spotMapper.toResponse(any(SpotEntity.class))).thenReturn(spotResponse(false));

        spotService.createSpot(USER_ID, request);

        ArgumentCaptor<SpotEntity> spotCaptor = ArgumentCaptor.forClass(SpotEntity.class);
        verify(spotRepository).save(spotCaptor.capture());

        assertThat(spotCaptor.getValue().getName()).isEqualTo("Central Plaza");
    }

    @Test
    void createSpot_shouldUseEmptyFeaturesWhenFeaturesAreNull() {
        UserEntity user = userEntity();
        CreateSpotRequest request =
                new CreateSpotRequest(
                        "Central Plaza", "Flat area", 55.751244, 37.618423, SpotType.STREET, null);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotRepository.save(any(SpotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(spotMapper.toResponse(any(SpotEntity.class))).thenReturn(spotResponse(false));

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

    @Test
    void getAllApprovedSpots_shouldReturnApprovedSpots() {
        UserEntity user = userEntity();
        SpotEntity firstSpot = spotEntity(true);
        SpotEntity secondSpot = spotEntity(true);

        SpotResponse firstResponse = spotResponse(true);
        SpotResponse secondResponse = spotResponse(true);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotRepository.getAllApprovedSpots()).thenReturn(List.of(firstSpot, secondSpot));
        when(spotMapper.toResponse(firstSpot)).thenReturn(firstResponse);
        when(spotMapper.toResponse(secondSpot)).thenReturn(secondResponse);

        List<SpotResponse> actualResponses = spotService.getAllApprovedSpots(USER_ID);

        assertThat(actualResponses).isEqualTo(List.of(firstResponse, secondResponse));

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotRepository).getAllApprovedSpots();
        verify(spotMapper).toResponse(firstSpot);
        verify(spotMapper).toResponse(secondSpot);
    }

    @Test
    void getAllApprovedSpots_shouldThrowExceptionWhenUserNotFound() {
        when(userReader.getByIdOrElseThrow(USER_ID)).thenThrow(new UserNotFoundException(USER_ID));

        assertThatThrownBy(() -> spotService.getAllApprovedSpots(USER_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(USER_ID.toString());

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotRepository, never()).getAllApprovedSpots();
        verify(spotMapper, never()).toResponse(any(SpotEntity.class));
    }

    @Test
    void getBySpotIdAndApprovedIsTrue_shouldReturnSpotResponse() {
        UserEntity user = userEntity();
        SpotEntity spot = spotEntity(true);
        SpotResponse expectedResponse = spotResponse(true);

        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(user);
        when(spotRepository.findByIdAndApprovedTrue(SPOT_ID)).thenReturn(Optional.of(spot));
        when(spotMapper.toResponse(spot)).thenReturn(expectedResponse);

        SpotResponse actualResponse = spotService.getBySpotIdAndApprovedIsTrue(USER_ID, SPOT_ID);

        assertThat(actualResponse).isEqualTo(expectedResponse);

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotRepository).findByIdAndApprovedTrue(SPOT_ID);
        verify(spotMapper).toResponse(spot);
    }

    @Test
    void getBySpotIdAndApprovedIsTrue_shouldThrowExceptionWhenUserNotFound() {
        when(userReader.getByIdOrElseThrow(USER_ID)).thenThrow(new UserNotFoundException(USER_ID));

        assertThatThrownBy(() -> spotService.getBySpotIdAndApprovedIsTrue(USER_ID, SPOT_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(USER_ID.toString());

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotRepository, never()).findByIdAndApprovedTrue(SPOT_ID);
        verify(spotMapper, never()).toResponse(any(SpotEntity.class));
    }

    @Test
    void getBySpotIdAndApprovedIsTrue_shouldThrowExceptionWhenSpotNotFoundOrNotApproved() {
        when(userReader.getByIdOrElseThrow(USER_ID)).thenReturn(userEntity());
        when(spotRepository.findByIdAndApprovedTrue(SPOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spotService.getBySpotIdAndApprovedIsTrue(USER_ID, SPOT_ID))
                .isInstanceOf(SpotNotFoundException.class)
                .hasMessageContaining(SPOT_ID.toString());

        verify(userReader).getByIdOrElseThrow(USER_ID);
        verify(spotRepository).findByIdAndApprovedTrue(SPOT_ID);
        verify(spotMapper, never()).toResponse(any(SpotEntity.class));
    }

    @Test
    void getAllPendingSpotsForAdmin_shouldReturnPendingSpots() {
        SpotEntity firstSpot = spotEntity(false);
        SpotEntity secondSpot = spotEntity(false);

        SpotResponse firstResponse = spotResponse(false);
        SpotResponse secondResponse = spotResponse(false);

        when(spotRepository.findAllByApprovedFalse()).thenReturn(List.of(firstSpot, secondSpot));
        when(spotMapper.toResponse(firstSpot)).thenReturn(firstResponse);
        when(spotMapper.toResponse(secondSpot)).thenReturn(secondResponse);

        List<SpotResponse> actualResponses = spotService.getAllPendingSpotsForAdmin();

        assertThat(actualResponses).isEqualTo(List.of(firstResponse, secondResponse));

        verify(spotRepository).findAllByApprovedFalse();
        verify(spotMapper).toResponse(firstSpot);
        verify(spotMapper).toResponse(secondSpot);
    }

    @Test
    void getSpotByIdForAdmin_shouldReturnSpotResponse() {
        SpotEntity spot = spotEntity(false);
        SpotResponse expectedResponse = spotResponse(false);

        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.of(spot));
        when(spotMapper.toResponse(spot)).thenReturn(expectedResponse);

        SpotResponse actualResponse = spotService.getSpotByIdForAdmin(SPOT_ID);

        assertThat(actualResponse).isEqualTo(expectedResponse);

        verify(spotRepository).findById(SPOT_ID);
        verify(spotMapper).toResponse(spot);
    }

    @Test
    void getSpotByIdForAdmin_shouldThrowExceptionWhenSpotNotFound() {
        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spotService.getSpotByIdForAdmin(SPOT_ID))
                .isInstanceOf(SpotNotFoundException.class)
                .hasMessageContaining(SPOT_ID.toString());

        verify(spotRepository).findById(SPOT_ID);
        verify(spotMapper, never()).toResponse(any(SpotEntity.class));
    }

    @Test
    void approveSpot_shouldApproveSpotAndReturnResponse() {
        SpotEntity spot = spotEntity(false);
        SpotResponse expectedResponse = spotResponse(true);

        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.of(spot));
        when(spotMapper.toResponse(spot)).thenReturn(expectedResponse);

        SpotResponse actualResponse = spotService.approveSpot(SPOT_ID);

        assertThat(actualResponse).isEqualTo(expectedResponse);
        assertThat(spot.getApproved()).isTrue();

        verify(spotRepository).findById(SPOT_ID);
        verify(spotMapper).toResponse(spot);
    }

    @Test
    void approveSpot_shouldReturnResponseWhenSpotAlreadyApproved() {
        SpotEntity spot = spotEntity(true);
        SpotResponse expectedResponse = spotResponse(true);

        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.of(spot));
        when(spotMapper.toResponse(spot)).thenReturn(expectedResponse);

        SpotResponse actualResponse = spotService.approveSpot(SPOT_ID);

        assertThat(actualResponse).isEqualTo(expectedResponse);
        assertThat(spot.getApproved()).isTrue();

        verify(spotRepository).findById(SPOT_ID);
        verify(spotMapper).toResponse(spot);
    }

    @Test
    void approveSpot_shouldThrowExceptionWhenSpotNotFound() {
        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spotService.approveSpot(SPOT_ID))
                .isInstanceOf(SpotNotFoundException.class)
                .hasMessageContaining(SPOT_ID.toString());

        verify(spotRepository).findById(SPOT_ID);
        verify(spotMapper, never()).toResponse(any(SpotEntity.class));
    }

    @Test
    void deleteSpot_shouldDeleteSpot() {
        SpotEntity spot = spotEntity(true);

        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.of(spot));

        spotService.deleteSpot(SPOT_ID);

        verify(spotRepository).findById(SPOT_ID);
        verify(spotRepository).delete(spot);
    }

    @Test
    void deleteSpot_shouldThrowExceptionWhenSpotNotFound() {
        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spotService.deleteSpot(SPOT_ID))
                .isInstanceOf(SpotNotFoundException.class)
                .hasMessageContaining(SPOT_ID.toString());

        verify(spotRepository).findById(SPOT_ID);
        verify(spotRepository, never()).delete(any(SpotEntity.class));
    }

    @Test
    void rejectSpot_shouldDeleteSpot() {
        SpotEntity spot = spotEntity(false);

        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.of(spot));

        spotService.rejectSpot(SPOT_ID);

        verify(spotRepository).findById(SPOT_ID);
        verify(spotRepository).delete(spot);
        verify(spotMapper, never()).toResponse(any(SpotEntity.class));
    }

    @Test
    void rejectSpot_shouldThrowExceptionWhenSpotNotFound() {
        when(spotRepository.findById(SPOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spotService.rejectSpot(SPOT_ID))
                .isInstanceOf(SpotNotFoundException.class)
                .hasMessageContaining(SPOT_ID.toString());

        verify(spotRepository).findById(SPOT_ID);
        verify(spotRepository, never()).delete(any(SpotEntity.class));
        verify(spotMapper, never()).toResponse(any(SpotEntity.class));
    }

    private CreateSpotRequest createSpotRequest() {
        return new CreateSpotRequest(
                "Central Plaza",
                "Flat, stairs and ledges",
                55.751244,
                37.618423,
                SpotType.STREET,
                Set.of(SpotFeature.FLAT, SpotFeature.STAIRS, SpotFeature.LEDGE));
    }

    private SpotEntity spotEntity(boolean approved) {
        SpotEntity spot = new SpotEntity();
        spot.setId(SPOT_ID);
        spot.setName("Central Plaza");
        spot.setDescription("Flat, stairs and ledges");
        spot.setLatitude(55.751244);
        spot.setLongitude(37.618423);
        spot.setType(SpotType.STREET);
        spot.setFeatures(new HashSet<>(Set.of(SpotFeature.FLAT, SpotFeature.STAIRS, SpotFeature.LEDGE)));
        spot.setCreatedBy(userEntity());
        spot.setApproved(approved);
        return spot;
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

    private SpotResponse spotResponse(boolean approved) {
        return new SpotResponse(
                SPOT_ID,
                "Central Plaza",
                "Flat, stairs and ledges",
                55.751244,
                37.618423,
                SpotType.STREET,
                Set.of(SpotFeature.FLAT, SpotFeature.STAIRS, SpotFeature.LEDGE),
                approved,
                Instant.now(),
                Instant.now());
    }
}
