package com.yeogido.backend.domain.travel.converter;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.entity.TravelRecordSticker;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.user.entity.User;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

    public static List<TravelRecordPhoto> toTravelRecordPhotos(
            TravelRecord travelRecord,
            TravelRecordReqDTO.UpdateRequest request
    ) {
        return toTravelRecordPhotos(travelRecord, request.images());
    }

    public static List<TravelRecordSticker> toTravelRecordStickers(
            TravelRecord travelRecord,
            List<TravelRecordReqDTO.StickerRequest> stickers,
            Map<Long, Sticker> stickerMap
    ) {
        return stickers.stream()
                .map(sticker -> TravelRecordSticker.builder()
                        .travelRecord(travelRecord)
                        .sticker(stickerMap.get(sticker.stickerId()))
                        .positionX(sticker.positionX())
                        .positionY(sticker.positionY())
                        .rotation(sticker.rotation())
                        .scale(sticker.scale())
                        .zIndex(sticker.zIndex())
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
            List<TravelRecordPhoto> photos,
            List<TravelRecordSticker> stickers,
            Function<String, String> imageUrlResolver
    ) {
        List<TravelRecordResDTO.ImageResponse> images = photos.stream()
                .map(TravelRecordConverter::toImageResponse)
                .toList();

        List<TravelRecordResDTO.StickerResponse> stickerResponses = stickers.stream()
                .map(sticker -> toStickerResponse(sticker, imageUrlResolver))
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
                stickerResponses,
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

    public static TravelRecordResDTO.StickerResponse toStickerResponse(
            TravelRecordSticker recordSticker,
            Function<String, String> imageUrlResolver
    ) {
        Sticker sticker = recordSticker.getSticker();

        return new TravelRecordResDTO.StickerResponse(
                recordSticker.getId(),
                sticker.getId(),
                imageUrlResolver.apply(sticker.getImageKey()),
                recordSticker.getPositionX(),
                recordSticker.getPositionY(),
                recordSticker.getRotation(),
                recordSticker.getScale(),
                recordSticker.getZIndex()
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

    public static TravelRecordResDTO.UpdateResponse toUpdateResponse(
            TravelRecord travelRecord
    ) {
        return new TravelRecordResDTO.UpdateResponse(travelRecord.getId());
    }
}
