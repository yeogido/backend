package com.yeogido.backend.domain.friend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class FriendResDTO {

    public record MyFriendCode(
            @Schema(description = "내 친구코드", example = "K7M2QX9A")
            String friendCode,

            @Schema(description = "QR에 담는 딥링크", example = "yeogido://friend?code=K7M2QX9A")
            String qrValue
    ) {
    }
}
