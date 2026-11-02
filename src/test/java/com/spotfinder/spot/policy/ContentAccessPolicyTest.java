package com.spotfinder.spot.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spotfinder.common.exception.SpotNotFoundException;
import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContentAccessPolicyTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SPOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final ContentAccessPolicy policy = new ContentAccessPolicy();

    @ParameterizedTest(name = "approved={0}, owner={1}, role={2}, allowed={3}")
    @CsvSource({
            "true, true, USER, true",
            "true, false, USER, true",
            "true, true, MODERATOR, true",
            "true, false, MODERATOR, true",
            "true, true, ADMIN, true",
            "true, false, ADMIN, true",
            "false, true, USER, true",
            "false, false, USER, false",
            "false, true, MODERATOR, true",
            "false, false, MODERATOR, true",
            "false, true, ADMIN, true",
            "false, false, ADMIN, true"
    })
    void ensureCanAccessContent_shouldEnforceAccessMatrix(
            boolean approved, boolean owner, UserRole role, boolean allowed) {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setRole(role);

        UserEntity spotOwner = new UserEntity();
        spotOwner.setId(owner ? USER_ID : OTHER_USER_ID);

        SpotEntity spot = new SpotEntity();
        spot.setId(SPOT_ID);
        spot.setApproved(approved);
        spot.setCreatedBy(spotOwner);

        if (allowed) {
            assertThatCode(() -> policy.ensureCanAccessContent(user, spot)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policy.ensureCanAccessContent(user, spot))
                    .isInstanceOf(SpotNotFoundException.class)
                    .hasMessageContaining(SPOT_ID.toString());
        }
    }
}
