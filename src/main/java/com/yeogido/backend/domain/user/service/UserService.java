package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.user.dto.UserReqDTO;
import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.enums.LikeCategory;
import com.yeogido.backend.domain.user.enums.PostCategory;
import com.yeogido.backend.domain.user.enums.SortType;
import com.yeogido.backend.global.common.response.CursorResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UserService {

    CursorResponse<UserResDTO.LikedResponse> getLikedList(
            Long userId,
            LikeCategory category,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size,
            Double latitude,
            Double longitude
    );

    UserResDTO.Profile getMyPage(Long userId);

    void withdraw(Long userId);

    CursorResponse<UserResDTO.MyPostResponse> getMyPosts(
            Long userId,
            PostCategory category,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size
    );

    UserResDTO.UpdateProfile updateMyProfile(Long userId, UserReqDTO.UpdateProfile request);
}
