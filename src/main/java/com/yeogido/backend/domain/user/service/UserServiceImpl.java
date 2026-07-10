package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.global.common.response.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{

    @Override
    public CursorResponse<UserResDTO.LikedResponse> getLikedList(
            String category,
            Long cursor,
            Integer size
    ) {
        return null;
    }

    @Override
    public UserResDTO.Profile getMyPage(String authorization) {
        return new UserResDTO.Profile(
                1L,
                "abc@example.com",
                "홍길동",
                "서울",
                UserRole.USER,
                "https://s3.ap-northeast-2.amazonaws.com/.../profile.jpg"
        );
    }

}
