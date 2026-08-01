package com.yeogido.backend.domain.travel.converter;

import com.yeogido.backend.domain.travel.dto.response.StickerResDTO;
import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.enums.StickerCategory;
import com.yeogido.backend.domain.travel.enums.StickerType;
import com.yeogido.backend.domain.user.entity.User;
import java.util.List;
import java.util.function.Function;

public class StickerConverter {

    private StickerConverter() {
    }

    public static StickerResDTO.StickerListResponse toStickerListResponse(
            List<StickerResDTO.StickerCategoryResponse> categories
    ) {
        return new StickerResDTO.StickerListResponse(categories);
    }

    public static StickerResDTO.StickerCategoryResponse toStickerCategoryResponse(
            StickerCategory category,
            List<StickerResDTO.StickerResponse> stickers
    ) {
        return new StickerResDTO.StickerCategoryResponse(
                category,
                stickers
        );
    }

    public static StickerResDTO.StickerResponse toStickerResponse(
            Sticker sticker,
            Function<String, String> imageUrlResolver
    ) {
        return new StickerResDTO.StickerResponse(
                sticker.getId(),
                sticker.getName(),
                imageUrlResolver.apply(sticker.getImageKey()),
                sticker.getStickerType()
        );
    }

    public static Sticker toCustomSticker(
            User user,
            String name,
            String imageKey
    ) {
        return Sticker.builder()
                .user(user)
                .name(name)
                .imageKey(imageKey)
                .stickerType(StickerType.CUSTOM)
                .category(StickerCategory.CUSTOM)
                .displayOrder(null)
                .deletedAt(null)
                .build();
    }

    public static StickerResDTO.CreateResponse toCreateResponse(
            Sticker sticker,
            Function<String, String> imageUrlResolver
    ) {
        return new StickerResDTO.CreateResponse(
                sticker.getId(),
                sticker.getName(),
                imageUrlResolver.apply(sticker.getImageKey()),
                sticker.getCategory(),
                sticker.getStickerType(),
                sticker.getCreatedAt()
        );
    }
}
