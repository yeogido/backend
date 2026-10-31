package com.yeogido.backend.domain.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FriendServiceImpl friendService;

    @Test
    void getMyFriendCodeReturnsCodeAndQrDeepLink() {
        User user = User.builder().id(USER_ID).friendCode("K7M2QX9A").build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        FriendResDTO.MyFriendCode result = friendService.getMyFriendCode(USER_ID);

        assertThat(result.friendCode()).isEqualTo("K7M2QX9A");
        assertThat(result.qrValue()).isEqualTo("yeogido://friend?code=K7M2QX9A");
    }

    @Test
    void getMyFriendCodeFailsWhenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.getMyFriendCode(USER_ID))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }
}
