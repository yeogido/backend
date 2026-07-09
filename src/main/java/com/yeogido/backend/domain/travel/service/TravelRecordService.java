package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TravelRecordService {

    private static final String DEFAULT_FOLDER_THEME = "BASIC";
    private static final LocalDate SAMPLE_START_DATE = LocalDate.of(2026, 5, 22);
    private static final LocalDate SAMPLE_END_DATE = LocalDate.of(2026, 5, 24);
    private static final LocalDateTime SAMPLE_CREATED_AT = LocalDateTime.of(2026, 5, 22, 14, 30);

    public TravelRecordResDTO.ListResponse getMyTravelRecords(
            TravelRecordReqDTO.ListRequest request
    ) {
        TravelRecordResDTO.TravelRecordSummary travelRecord =
                new TravelRecordResDTO.TravelRecordSummary(
                        20L,
                        "부산 감성 바다 여행",
                        1L,
                        SAMPLE_START_DATE,
                        SAMPLE_END_DATE,
                        "travel-records/20/image-1.jpg",
                        DEFAULT_FOLDER_THEME,
                        SAMPLE_CREATED_AT
                );

        return new TravelRecordResDTO.ListResponse(
                List.of(travelRecord),
                19L,
                true
        );
    }

    public TravelRecordResDTO.DetailResponse getTravelRecord(Long travelRecordId) {

        TravelRecordResDTO.ImageResponse image = new TravelRecordResDTO.ImageResponse(
                1L,
                "travel-records/1/image-1.jpg",
                1
        );

        TravelRecordResDTO.StickerResponse sticker = new TravelRecordResDTO.StickerResponse(
                1L,
                "stickers/heart.png",
                120.5,
                80.0,
                15.0,
                1.2,
                1
        );

        return new TravelRecordResDTO.DetailResponse(
                travelRecordId,
                "부산 감성 바다 여행",
                1L,
                SAMPLE_START_DATE,
                SAMPLE_END_DATE,
                "travel-records/1/image-1.jpg",
                DEFAULT_FOLDER_THEME,
                List.of(image),
                List.of(sticker),
                SAMPLE_CREATED_AT,
                null
        );
    }

    public TravelRecordResDTO.CreateResponse createTravelRecord(
            TravelRecordReqDTO.CreateRequest request
    ) {
        return new TravelRecordResDTO.CreateResponse(
                1L,
                request.title(),
                findCoverImageKey(request.images()),
                SAMPLE_CREATED_AT
        );
    }

    private String findCoverImageKey(List<TravelRecordReqDTO.ImageRequest> images) {
        return images.stream()
                .filter(image -> Integer.valueOf(1).equals(image.imageOrder()))
                .findFirst()
                .map(TravelRecordReqDTO.ImageRequest::imageKey)
                .orElse(images.get(0).imageKey());
    }
}
