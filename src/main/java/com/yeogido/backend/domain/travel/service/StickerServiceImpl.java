package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.travel.converter.StickerConverter;
import com.yeogido.backend.domain.travel.dto.response.StickerResDTO;
import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.enums.StickerCategory;
import com.yeogido.backend.domain.travel.enums.StickerType;
import com.yeogido.backend.domain.travel.repository.StickerRepository;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StickerServiceImpl implements StickerService {

    private final StickerRepository stickerRepository;
    private final S3Service s3Service;

    @Override
    public StickerResDTO.StickerListResponse getStickers(Long userId) {
        List<Sticker> defaultStickers = stickerRepository.findAllByStickerTypeOrderByCategoryAscDisplayOrderAsc(
                StickerType.DEFAULT
        );
        List<Sticker> customStickers = stickerRepository.findAllByUser_IdAndStickerTypeAndDeletedAtIsNullOrderByIdAsc(
                userId,
                StickerType.CUSTOM
        );

        Map<StickerCategory, List<Sticker>> stickerMap = new EnumMap<>(StickerCategory.class);
        stickerMap.putAll(groupByCategory(defaultStickers));
        stickerMap.put(StickerCategory.CUSTOM, customStickers);

        List<StickerResDTO.StickerCategoryResponse> categories = Arrays.stream(StickerCategory.values())
                .map(category -> toCategoryResponse(category, stickerMap.getOrDefault(category, List.of())))
                .toList();

        return StickerConverter.toStickerListResponse(categories);
    }

    private Map<StickerCategory, List<Sticker>> groupByCategory(List<Sticker> stickers) {
        return stickers.stream()
                .collect(Collectors.groupingBy(
                        Sticker::getCategory,
                        () -> new EnumMap<>(StickerCategory.class),
                        Collectors.toList()
                ));
    }

    private StickerResDTO.StickerCategoryResponse toCategoryResponse(
            StickerCategory category,
            List<Sticker> stickers
    ) {
        List<StickerResDTO.StickerResponse> stickerResponses = stickers.stream()
                .map(sticker -> StickerConverter.toStickerResponse(sticker, s3Service::getImageUrl))
                .toList();

        return StickerConverter.toStickerCategoryResponse(category, stickerResponses);
    }
}
