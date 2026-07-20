package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.user.converter.UserConverter;
import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;

    @Override
    public CursorResponse<UserResDTO.LikedResponse> getLikedList(
            String category,
            Long cursor,
            Integer size
    ) {
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResDTO.Profile getMyPage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        return UserConverter.toProfile(user);
    }

}
