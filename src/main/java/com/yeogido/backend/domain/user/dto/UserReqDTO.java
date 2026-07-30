package com.yeogido.backend.domain.user.dto;

import com.yeogido.backend.global.validation.ValidBirthYear;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class UserReqDTO {

    @Schema(name = "UserUpdateProfileReq", description = "내 프로필 수정 요청")
    public record UpdateProfile(
            @Schema(description = "닉네임", example = "새로운길동")
            @Size(min = 2, max = 10, message = "닉네임 길이는 2자 이상 10자 이하여야 합니다.")
            String nickname,

            @Schema(description = "출생연도", example = "2001")
            @ValidBirthYear
            String birthYear,

            @Schema(description = "지역 ID", example = "1")
            @Positive(message = "지역 ID는 양수여야 합니다.")
            Long regionId
    ) {
    }
}
