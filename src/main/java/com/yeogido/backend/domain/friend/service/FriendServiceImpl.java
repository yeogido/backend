package com.yeogido.backend.domain.friend.service;

import com.yeogido.backend.domain.friend.converter.FriendConverter;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public FriendResDTO.MyFriendCode getMyFriendCode(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        return FriendConverter.toMyFriendCode(user);
    }
}
