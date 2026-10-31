package com.yeogido.backend.domain.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FriendCodeGeneratorTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FriendCodeGenerator friendCodeGenerator;

    @Test
    void randomCodeUsesEightAllowedCharacters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(FriendCodeGenerator.randomCode()).matches("[A-HJ-NP-Z2-9]{8}");
        }
    }

    @Test
    void generateUniqueRetriesWhenCodeAlreadyExists() {
        when(userRepository.existsByFriendCode(anyString())).thenReturn(true, true, false);

        String code = friendCodeGenerator.generateUnique();

        assertThat(code).matches("[A-HJ-NP-Z2-9]{8}");
        verify(userRepository, times(3)).existsByFriendCode(anyString());
    }

    @Test
    void generateUniqueFailsAfterMaxAttempts() {
        when(userRepository.existsByFriendCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> friendCodeGenerator.generateUnique())
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR);
        verify(userRepository, times(FriendCodeGenerator.MAX_ATTEMPTS)).existsByFriendCode(anyString());
    }
}
