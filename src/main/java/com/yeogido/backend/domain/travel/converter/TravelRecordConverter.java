package com.yeogido.backend.domain.travel.converter;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.user.entity.User;
import java.util.Collections;
import java.util.List;

public class TravelRecordConverter {

    private TravelRecordConverter() {
    }

    public static TravelRecord toTravelRecord(
            TravelRecordReqDTO.CreateRequest request,
            User user,
            Region region,
            String coverImageKey,
            FolderTheme folderTheme
    ) {
        return TravelRecord.builder()
                .user(user)
                .region(region)
                .title(request.title())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .coverImageKey(coverImageKey)
                .folderTheme(folderTheme)
                .build();
    }

    public static List<TravelRecordPhoto> toTravelRecordPhotos(
            TravelRecord travelRecord,
            List<TravelRecordReqDTO.ImageRequest> images
    ) {
        return images.stream()
                .map(image -> TravelRecordPhoto.builder()
                        .travelRecord(travelRecord)
                        .imageKey(image.imageKey())
                        .imageOrder(image.imageOrder())
                        .build())
                .toList();
    }

    public static TravelRecordResDTO.TravelRecordSummary toTravelRecordSummary(
            TravelRecord travelRecord
    ) {
        return new TravelRecordResDTO.TravelRecordSummary(
                travelRecord.getId(),
                travelRecord.getTitle(),
                travelRecord.getRegion().getId(),
                travelRecord.getStartDate(),
                travelRecord.getEndDate(),
                travelRecord.getCoverImageKey(),
                travelRecord.getFolderTheme() == null ? null : travelRecord.getFolderTheme().name(),
                travelRecord.getCreatedAt()
        );
    }

    public static TravelRecordResDTO.DetailResponse toDetailResponse(
            TravelRecord travelRecord,
            List<TravelRecordPhoto> photos
    ) {
        List<TravelRecordResDTO.ImageResponse> images = photos.stream()
                .map(TravelRecordConverter::toImageResponse)
                .toList();

        return new TravelRecordResDTO.DetailResponse(
                travelRecord.getId(),
                travelRecord.getTitle(),
                travelRecord.getRegion().getId(),
                travelRecord.getStartDate(),
                travelRecord.getEndDate(),
                travelRecord.getCoverImageKey(),
                travelRecord.getFolderTheme() == null ? null : travelRecord.getFolderTheme().name(),
                images,
                Collections.emptyList(),
                travelRecord.getCreatedAt(),
                travelRecord.getUpdatedAt()
        );
    }

    public static TravelRecordResDTO.ImageResponse toImageResponse(
            TravelRecordPhoto photo
    ) {
        return new TravelRecordResDTO.ImageResponse(
                photo.getId(),
                photo.getImageKey(),
                photo.getImageOrder()
        );
    }

    public static TravelRecordResDTO.CreateResponse toCreateResponse(
            TravelRecord travelRecord
    ) {
        return new TravelRecordResDTO.CreateResponse(
                travelRecord.getId(),
                travelRecord.getTitle(),
                travelRecord.getCoverImageKey(),
                travelRecord.getCreatedAt()
        );
    }
}
