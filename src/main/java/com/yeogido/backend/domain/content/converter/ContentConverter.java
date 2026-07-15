package com.yeogido.backend.domain.content.converter;

import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.entity.Content;

public class ContentConverter {
    public static ContentResDTO.ContentInfo toContentInfo(
            Content content,
            Long likeCount
    ) {
        return ContentResDTO.ContentInfo.builder()
                .contentId(content.getId())
                .placeId(content.getPlace().getId())
                .title(content.getTitle())
                .thumbnailImageUrl(content.getThumbnailImage())
                .regionName(content.getPlace().getRegion().getName())
                .likeCount(likeCount)
                .startDate(content.getStartDate())
                .endDate(content.getEndDate())
                .build();
    }
}
