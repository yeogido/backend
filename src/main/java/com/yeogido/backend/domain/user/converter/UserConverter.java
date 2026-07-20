package com.yeogido.backend.domain.user.converter;

import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.entity.User;

public class UserConverter {

    private UserConverter() {
    }

    public static UserResDTO.Profile toProfile(User user) {
        return new UserResDTO.Profile(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRegion().getName(),
                user.getBirthYear(),
                user.getRole(),
                user.getProfileImage()
        );
    }
}
