package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.enums.LikeCategory;
import com.yeogido.backend.global.common.response.CursorResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UserService {

    CursorResponse<UserResDTO.LikedResponse> getLikedList(
            Long userId,
            LikeCategory category,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size
    );

    UserResDTO.Profile getMyPage(Long userId);
}
