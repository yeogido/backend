package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.user.dto.UserResDTO;
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


}
