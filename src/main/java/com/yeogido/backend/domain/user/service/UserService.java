package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

public interface UserService {

    CursorResponse<UserResDTO.LikedResponse> getLikedList(
            String category,
            Long cursor,
            Integer size
    );
}
