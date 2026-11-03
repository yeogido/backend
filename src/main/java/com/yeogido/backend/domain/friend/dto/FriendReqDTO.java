package com.yeogido.backend.domain.friend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class FriendReqDTO {

    @Schema(name = "FriendRequestSendReq", description = "친구 요청 보내기 요청")
    public record SendRequest(
            @Schema(description = "상대방 친구코드 (앞뒤 공백 제거 후 대문자로 처리)", example = "K7M2QX9A")
            @NotBlank(message = "친구코드는 필수입니다.")
            String friendCode
    ) {
    }
}