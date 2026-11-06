package com.yeogido.backend.domain.friend.dto;

import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.friend.enums.RelationStatus;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class FriendResDTO {

    public record MyFriendCode(
            @Schema(description = "내 친구코드 (고정)", example = "K7M2QX9A")
            String friendCode,

            @Schema(description = "QR에 담을 토큰. 호출할 때마다 새로 발급되고 이전 토큰은 무효가 됨")
            String qrToken,

            @Schema(description = "QR 토큰 만료 시각 (발급 후 3분)")
            LocalDateTime qrExpiresAt
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

    public record FriendProfile(
            @Schema(description = "사용자 ID", example = "2")
            Long userId,

            @Schema(description = "닉네임", example = "여기도")
            String nickname,

            @Schema(description = "프로필 이미지 URL")
            String profileImageUrl,

            @Schema(description = "친구코드", example = "K7M2QX9A")
            String friendCode,

            @Schema(description = "이메일 (친구일 때만 값이 있음)", example = "friend@example.com")
            String email,

            @Schema(description = "지역")
            FriendRegion region,

            @Schema(description = "나와의 관계 (NONE, FRIEND, REQUESTED: 내가 보냄, RECEIVED: 상대가 보냄)")
            RelationStatus relationStatus,

            @Schema(description = "대기 중인 친구 요청 ID (REQUESTED, RECEIVED일 때만 값이 있음)", example = "10")
            Long requestId
    ) {
    }

    @Schema(name = "FriendTravelMapItem", description = "프로필 여행 지도 항목. 블러 항목은 지역, 좌표, 기간만 있음")
    public record TravelMapItem(
            @Schema(description = "블러 여부. 친구에게만, 내가 열람할 수 없는 기록일 때 true")
            boolean blurred,

            @Schema(description = "여행 기록 ID (블러 항목은 null)", example = "20", nullable = true)
            Long travelRecordId,

            @Schema(description = "여행 기록 제목 (블러 항목은 null)", example = "부산 바다 여행", nullable = true)
            String title,

            @Schema(description = "지역 ID", example = "1")
            Long regionId,

            @Schema(description = "지역명", example = "부산")
            String regionName,

            @Schema(description = "지역 중심 위도. 좌표가 불완전하면 null", nullable = true, example = "35.1796")
            BigDecimal latitude,

            @Schema(description = "지역 중심 경도. 좌표가 불완전하면 null", nullable = true, example = "129.0756")
            BigDecimal longitude,

            @Schema(description = "여행 시작일", example = "2026-05-22")
            LocalDate startDate,

            @Schema(description = "여행 종료일", example = "2026-05-24")
            LocalDate endDate,

            @Schema(description = "공개범위 (블러 항목은 null)", nullable = true)
            TravelRecordVisibility visibility,

            @Schema(description = "앨범의 전체 사진. imageOrder 오름차순 (블러 항목은 null)", nullable = true)
            List<TravelRecordResDTO.ImageResponse> images
    ) {
    }

    public record FriendRegion(
            @Schema(description = "지역 ID", example = "1")
            Long regionId,

            @Schema(description = "지역명", example = "서울")
            String name
    ) {
    }

    public record SearchResult(
            @Schema(description = "사용자 ID", example = "2")
            Long userId,

            @Schema(description = "닉네임", example = "여기도")
            String nickname,

            @Schema(description = "프로필 이미지 URL")
            String profileImageUrl,

            @Schema(description = "친구코드", example = "K7M2QX9A")
            String friendCode,

            @Schema(description = "이메일 (친구일 때만 값이 있음)", example = "yeogido@example.com")
            String email,

            @Schema(description = "나와의 관계 (NONE, FRIEND, REQUESTED: 내가 보냄, RECEIVED: 상대가 보냄)")
            RelationStatus relationStatus,

            @Schema(description = "대기 중인 친구 요청 ID (REQUESTED, RECEIVED일 때만 값이 있음)", example = "10")
            Long requestId
    ) {
    }

    public record QrUser(
            @Schema(description = "사용자 ID", example = "2")
            Long userId,

            @Schema(description = "닉네임", example = "여기도")
            String nickname,

            @Schema(description = "프로필 이미지 URL")
            String profileImageUrl,

            @Schema(description = "친구코드", example = "K7M2QX9A")
            String friendCode,

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