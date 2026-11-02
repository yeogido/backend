package com.yeogido.backend.domain.friend.dto;

import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.friend.enums.RelationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public class FriendResDTO {

    public record MyFriendCode(
            @Schema(description = "내 친구코드", example = "K7M2QX9A")
            String friendCode,

            @Schema(description = "QR에 담는 딥링크", example = "yeogido://friend?code=K7M2QX9A")
            String qrValue
    ) {
    }

    public record UserSummary(
            @Schema(description = "사용자 ID", example = "2")
            Long userId,

            @Schema(description = "닉네임", example = "여기도")
            String nickname,

            @Schema(description = "프로필 이미지 URL")
            String profileImageUrl,

            @Schema(description = "친구코드", example = "K7M2QX9A")
            String friendCode
    ) {
    }

    public record SearchResult(
            @Schema(description = "사용자 ID", example = "2")
            Long userId,

            @Schema(description = "닉네임", example = "여기도")
            String nickname,

            @Schema(description = "프로필 이미지 URL")
            String profileImageUrl,

            @Schema(description = "이메일", example = "yeogido@example.com")
            String email,

            @Schema(description = "나와의 관계 (NONE, FRIEND, REQUESTED: 내가 보냄, RECEIVED: 상대가 보냄)")
            RelationStatus relationStatus,

            @Schema(description = "대기 중인 친구 요청 ID (REQUESTED, RECEIVED일 때만 값이 있음)", example = "10")
            Long requestId
    ) {
    }

    public record SentRequest(
            @Schema(description = "친구 요청 ID", example = "10")
            Long requestId,

            @Schema(description = "요청을 받은 사용자")
            UserSummary receiver,

            @Schema(description = "요청 상태", example = "PENDING")
            FriendshipStatus status,

            @Schema(description = "요청 시각")
            LocalDateTime createdAt
    ) {
    }

    public record ReceivedRequestItem(
            @Schema(description = "친구 요청 ID", example = "10")
            Long requestId,

            @Schema(description = "요청을 보낸 사용자")
            UserSummary requester,

            @Schema(description = "요청 시각")
            LocalDateTime createdAt
    ) {
    }

    public record SentRequestItem(
            @Schema(description = "친구 요청 ID", example = "10")
            Long requestId,

            @Schema(description = "요청을 받은 사용자")
            UserSummary receiver,

            @Schema(description = "요청 시각")
            LocalDateTime createdAt
    ) {
    }

    public record AcceptResult(
            @Schema(description = "친구 요청 ID", example = "10")
            Long requestId,

            @Schema(description = "요청 상태", example = "ACCEPTED")
            FriendshipStatus status,

            @Schema(description = "새로 친구가 된 사용자")
            UserSummary friend
    ) {
    }
}