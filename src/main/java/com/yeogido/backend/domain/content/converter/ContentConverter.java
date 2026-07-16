package com.yeogido.backend.domain.content.converter;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.QContent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ContentConverter {
    public static ContentResDTO.ContentInfo toContentInfo(
            Content content,
            Long likeCount
    ) {
        // TODO : 반환값에 해시태그 추가
        return new ContentResDTO.ContentInfo(
                content.getId(),
                content.getPlace().getId(),
                content.getTitle(),
                content.getThumbnailImage(),
                content.getPlace().getRegion().getName(),
                likeCount,
                content.getStartDate(),
                content.getEndDate()
        );
    }

    public static ContentResDTO.ContentInfo toContentInfo(
            Tuple tuple,
            QContent qContent,
            NumberExpression<Long> likeCountExpression
    ) {
        Content content = tuple.get(qContent);
        Long likeCount = tuple.get(likeCountExpression);

        return toContentInfo(content, likeCount);
    }


}
