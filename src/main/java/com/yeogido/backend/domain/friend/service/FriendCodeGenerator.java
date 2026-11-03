package com.yeogido.backend.domain.friend.service;

import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendCodeGenerator {

    // 헷갈리는 0/O, 1/I 제외 (V37 마이그레이션과 같은 문자 집합)
    static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 8;
    static final int MAX_ATTEMPTS = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;

    public static String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }

    // 이미 사용 중인 코드면 다시 생성
    public String generateUnique() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!userRepository.existsByFriendCode(code)) {
                return code;
            }
        }
        throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
}
