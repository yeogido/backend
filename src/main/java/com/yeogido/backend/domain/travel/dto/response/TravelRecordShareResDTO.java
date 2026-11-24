package com.yeogido.backend.domain.travel.dto.response;

import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public class TravelRecordShareResDTO {
    @Schema(name = "TravelRecordShareLink", description = "프론트 공유 페이지 URL에 넣을 토큰. 로그인 필수")
    public record Link(Long travelRecordId, String token) {}

    @Schema(name = "TravelRecordSharedView", description = "PUBLIC 및 참여자는 detail, FRIENDS 비참여자는 preview만 제공")
    public record View(Long travelRecordId, TravelRecordVisibility visibility, boolean canAccept,
                       TravelRecordResDTO.UserProfile owner,
                       Preview preview,
                       TravelRecordResDTO.DetailResponse detail) {}

    @Schema(name = "TravelRecordSharedPreview", description = "FRIENDS 링크 방문자의 수락 팝업 및 배경 화면 정보")
    public record Preview(
            Long travelRecordId, String title, Long regionId, String regionName,
            LocalDate startDate, LocalDate endDate, FolderTheme folderTheme,
            @Schema(description = "앨범에 등록된 사진 전체를 표시 순서로 제공. 팝업의 폴더 구성 및 하단 사진 블러 표시에 사용")
            List<TravelRecordResDTO.PreviewImage> previewImages,
            List<TravelRecordResDTO.StickerResponse> stickers, int photoCount
    ) {
    }

    @Schema(name = "TravelRecordShareRespond", description = "링크 수락 또는 거절 결과. 거절만으로 참여 관계가 삭제되지는 않음")
    public record Respond(Long travelRecordId, TravelRecordInvitationStatus status) {}
}
