package com.yeogido.backend.domain.travel.converter;

import com.yeogido.backend.domain.travel.dto.response.StickerResDTO;
import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.enums.StickerCategory;
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
                category.name(),
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
                sticker.getStickerType().name()
        );
    }
}
