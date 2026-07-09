package com.yeogido.backend.domain.content.dto;

import java.time.LocalDate;
import java.util.List;

public class ContentReqDTO {
    public record ContentListReq(
            Long regionId,
            String category,
            String sort,
            Long cursor,
            Integer size
    ){}

    public record ContentCreateReq(
            Long placeId,
            String title,
            String description,
            String category,
            LocalDate startDate,
            LocalDate endDate,
            String contactPhone,
            String thumbnailImageKey,
            List<String> hashtags

    ){}


}
