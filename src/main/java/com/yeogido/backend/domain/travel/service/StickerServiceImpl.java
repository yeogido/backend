package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.travel.converter.StickerConverter;
import com.yeogido.backend.domain.travel.dto.request.StickerReqDTO;
import com.yeogido.backend.domain.travel.dto.response.StickerResDTO;
import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.enums.StickerCategory;
import com.yeogido.backend.domain.travel.enums.StickerType;
import com.yeogido.backend.domain.travel.exception.StickerErrorCode;
import com.yeogido.backend.domain.travel.repository.StickerRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
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

    private static final int CUSTOM_STICKER_LIMIT = 10;
    private static final String TEMP_IMAGE_PREFIX = "temp/";
    private static final String CUSTOM_STICKER_NAME_PREFIX = "나만의 스티커 ";

    private final StickerRepository stickerRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
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

    @Override
    @Transactional
    public StickerResDTO.CreateResponse createCustomSticker(
            Long userId,
            StickerReqDTO.CreateRequest request
    ) {
        User user = getUserOrThrow(userId);

        validateTempImageKey(request.imageKey());
        validateCustomStickerLimit(userId);

        String movedImageKey = fileService.moveToDirectory(
                request.imageKey(),
                ImageDirectory.STICKER
        );
        String stickerName = createCustomStickerName(userId);

        Sticker sticker = StickerConverter.toCustomSticker(
                user,
                stickerName,
                movedImageKey
        );

        Sticker savedSticker = stickerRepository.save(sticker);

        return StickerConverter.toCreateResponse(savedSticker, s3Service::getImageUrl);
    }

    @Override
    @Transactional
    public void deleteCustomSticker(Long userId, Long stickerId) {
        Sticker sticker = stickerRepository.findById(stickerId)
                .orElseThrow(() -> new GeneralException(StickerErrorCode.STICKER_NOT_FOUND));

        validateDeletableCustomSticker(sticker, userId);

        sticker.delete();
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

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
    }

    private void validateTempImageKey(String imageKey) {
        if (imageKey == null
                || !imageKey.startsWith(TEMP_IMAGE_PREFIX)
                || !hasFileExtension(imageKey)) {
            throw new GeneralException(StickerErrorCode.INVALID_STICKER_IMAGE_KEY);
        }
    }

    private boolean hasFileExtension(String imageKey) {
        int extensionIndex = imageKey.lastIndexOf(".");

        return extensionIndex > TEMP_IMAGE_PREFIX.length()
                && extensionIndex < imageKey.length() - 1;
    }

    private void validateCustomStickerLimit(Long userId) {
        long activeCustomStickerCount = stickerRepository.countByUser_IdAndStickerTypeAndDeletedAtIsNull(
                userId,
                StickerType.CUSTOM
        );

        if (activeCustomStickerCount >= CUSTOM_STICKER_LIMIT) {
            throw new GeneralException(StickerErrorCode.CUSTOM_STICKER_LIMIT_EXCEEDED);
        }
    }

    private String createCustomStickerName(Long userId) {
        long totalCustomStickerCount = stickerRepository.countByUser_IdAndStickerType(
                userId,
                StickerType.CUSTOM
        );

        return CUSTOM_STICKER_NAME_PREFIX + (totalCustomStickerCount + 1);
    }

    private void validateDeletableCustomSticker(Sticker sticker, Long userId) {
        if (sticker.isDeleted()) {
            throw new GeneralException(StickerErrorCode.STICKER_NOT_FOUND);
        }

        if (sticker.getStickerType() == StickerType.DEFAULT) {
            throw new GeneralException(StickerErrorCode.DEFAULT_STICKER_DELETE_NOT_ALLOWED);
        }

        if (sticker.getUser() == null || !sticker.getUser().getId().equals(userId)) {
            throw new GeneralException(StickerErrorCode.STICKER_ACCESS_DENIED);
        }
    }
}
