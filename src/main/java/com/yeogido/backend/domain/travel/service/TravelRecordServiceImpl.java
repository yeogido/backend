package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.travel.converter.TravelRecordConverter;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.entity.TravelRecordSticker;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.StickerType;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.travel.repository.StickerRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordPhotoRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordStickerRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelRecordServiceImpl implements TravelRecordService {

    private static final Long MOCK_USER_ID = 1L;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final FolderTheme DEFAULT_FOLDER_THEME = FolderTheme.BASIC;

    private final TravelRecordRepository travelRecordRepository;
    private final TravelRecordPhotoRepository travelRecordPhotoRepository;
    private final TravelRecordStickerRepository travelRecordStickerRepository;
    private final StickerRepository stickerRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final S3Service s3Service;

    @Override
    public CursorResponse<TravelRecordResDTO.TravelRecordSummary> getMyTravelRecords(
            TravelRecordReqDTO.ListRequest request
    ) {
        Long userId = getCurrentUserId();
        int size = resolveSize(request.size());
        int year = resolveYear(request.year());
        validateCursor(request.cursor());

        // size + 1개를 조회해서 다음 페이지 존재 여부를 계산합니다.
        Pageable pageable = PageRequest.of(0, size + 1);
        List<TravelRecord> travelRecords = findTravelRecords(
                userId,
                request.cursor(),
                year,
                pageable
        );

        boolean hasNext = travelRecords.size() > size;
        List<TravelRecord> content = hasNext
                ? travelRecords.subList(0, size)
                : travelRecords;

        List<TravelRecordResDTO.TravelRecordSummary> items = content.stream()
                .map(TravelRecordConverter::toTravelRecordSummary)
                .toList();

        Long nextCursor = items.isEmpty()
                ? null
                : items.get(items.size() - 1).travelRecordId();

        return CursorResponse.of(items,null, nextCursor, hasNext);
    }

    @Override
    public TravelRecordResDTO.YearListResponse getMyTravelRecordYears() {
        Long userId = getCurrentUserId();

        // endDate 기준으로 기록이 존재하는 연도만 내려주며, 최신 연도부터 정렬합니다.
        List<Integer> years = travelRecordRepository.findTravelYearsByUserId(userId);

        return new TravelRecordResDTO.YearListResponse(years);
    }

    @Override
    public TravelRecordResDTO.DetailResponse getTravelRecord(Long travelRecordId) {
        Long userId = getCurrentUserId();

        TravelRecord travelRecord = getTravelRecordOrThrow(travelRecordId);
        validateOwner(travelRecord, userId);

        // 사진 순서는 화면 표시와 직접 연결되므로 imageOrder 오름차순을 보장
        List<TravelRecordPhoto> photos =
                travelRecordPhotoRepository.findByTravelRecordIdOrderByImageOrderAsc(travelRecordId);
        List<TravelRecordSticker> stickers =
                travelRecordStickerRepository.findByTravelRecordIdOrderByZIndexAsc(travelRecordId);

        return TravelRecordConverter.toDetailResponse(
                travelRecord,
                photos,
                stickers,
                s3Service::getImageUrl
        );
    }

    @Override
    @Transactional
    public TravelRecordResDTO.CreateResponse createTravelRecord(
            TravelRecordReqDTO.CreateRequest request
    ) {
        User user = getCurrentUser();
        Region region = getRegionOrThrow(request.regionId());

        validateDateRange(request);

        String coverImageKey = findCoverImageKey(request.images());

        // travel_record.cover_image_key가 NOT NULL이라 대표 이미지 key를 여행 기록에도 저장
        TravelRecord travelRecord = TravelRecordConverter.toTravelRecord(
                request,
                user,
                region,
                coverImageKey,
                DEFAULT_FOLDER_THEME
        );

        TravelRecord savedTravelRecord = travelRecordRepository.save(travelRecord);

        List<TravelRecordPhoto> photos = TravelRecordConverter.toTravelRecordPhotos(
                savedTravelRecord,
                request.images()
        );

        travelRecordPhotoRepository.saveAll(photos);

        List<TravelRecordReqDTO.StickerRequest> stickerRequests = resolveStickers(request.stickers());
        if (!stickerRequests.isEmpty()) {
            validateStickerZIndex(stickerRequests);

            Map<Long, Sticker> stickerMap = getAvailableStickerMap(
                    stickerRequests,
                    user.getId()
            );

            List<TravelRecordSticker> stickers = TravelRecordConverter.toTravelRecordStickers(
                    savedTravelRecord,
                    stickerRequests,
                    stickerMap
            );

            travelRecordStickerRepository.saveAll(stickers);
        }

        return TravelRecordConverter.toCreateResponse(savedTravelRecord);
    }

    @Override
    @Transactional
    public TravelRecordResDTO.UpdateResponse updateTravelRecord(
            Long travelRecordId,
            Long userId,
            TravelRecordReqDTO.UpdateRequest request
    ) {
        TravelRecord travelRecord = getTravelRecordOrThrow(travelRecordId);
        validateOwner(travelRecord, userId);

        Region region = getRegionOrThrow(request.regionId());
        validateDateRange(request.startDate(), request.endDate());
        validateImageOrders(request.images());

        String coverImageKey = findCoverImageKey(request.images());

        // 폴더 테마는 현재 기획상 변경 기능이 없어 BASIC으로 고정합니다.
        travelRecord.update(
                region,
                request.title(),
                request.startDate(),
                request.endDate(),
                coverImageKey,
                DEFAULT_FOLDER_THEME
        );

        // 수정 요청의 images는 "수정 후 최종 목록"이므로 기존 사진을 전체 교체합니다.
        travelRecordPhotoRepository.deleteAllByTravelRecord_Id(travelRecordId);
        travelRecordPhotoRepository.flush();

        List<TravelRecordPhoto> photos = TravelRecordConverter.toTravelRecordPhotos(
                travelRecord,
                request
        );
        travelRecordPhotoRepository.saveAll(photos);

        updateStickersIfRequested(travelRecord, request.stickers(), userId);

        return TravelRecordConverter.toUpdateResponse(travelRecord);
    }

    @Override
    @Transactional
    public void deleteTravelRecord(Long travelRecordId, Long userId) {
        TravelRecord travelRecord = getTravelRecordOrThrow(travelRecordId);
        validateOwner(travelRecord, userId);

        // FK 제약이 있으므로 자식 테이블 데이터를 먼저 삭제한 뒤 여행 기록을 삭제합니다.
        travelRecordStickerRepository.deleteAllByTravelRecord_Id(travelRecordId);
        travelRecordPhotoRepository.deleteAllByTravelRecord_Id(travelRecordId);
        travelRecordRepository.delete(travelRecord);
    }

    private Long getCurrentUserId() {
        // TODO: Spring Security 적용 후 인증 사용자 ID로 교체
        return MOCK_USER_ID;
    }

    private User getCurrentUser() {
        // TODO: Spring Security 적용 후 인증 사용자 조회로 교체
        return userRepository.findById(MOCK_USER_ID)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }

        if (size <= 0) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_PAGE_REQUEST);
        }

        return size;
    }

    private void validateCursor(Long cursor) {
        if (cursor != null && cursor <= 0) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_PAGE_REQUEST);
        }
    }

    private int resolveYear(Integer year) {
        if (year == null) {
            return Year.now().getValue();
        }

        if (year <= 0) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_PAGE_REQUEST);
        }

        return year;
    }

    private List<TravelRecord> findTravelRecords(
            Long userId,
            Long cursor,
            int year,
            Pageable pageable
    ) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        if (cursor == null) {
            return travelRecordRepository.findByUserIdAndTravelYear(
                    userId,
                    yearStart,
                    yearEnd,
                    pageable
            );
        }

        TravelRecord cursorRecord = getTravelRecordOrThrow(cursor);
        validateOwner(cursorRecord, userId);

        return travelRecordRepository.findByUserIdAndTravelYearAfterCursor(
                userId,
                yearStart,
                yearEnd,
                cursorRecord.getEndDate(),
                cursorRecord.getId(),
                pageable
        );
    }

    private TravelRecord getTravelRecordOrThrow(Long travelRecordId) {
        return travelRecordRepository.findById(travelRecordId)
                .orElseThrow(() -> new GeneralException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
    }

    private Region getRegionOrThrow(Long regionId) {
        return regionRepository.findById(regionId)
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));
    }

    private void validateOwner(TravelRecord travelRecord, Long userId) {
        if (!travelRecord.getUser().getId().equals(userId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    private void validateDateRange(TravelRecordReqDTO.CreateRequest request) {
        validateDateRange(request.startDate(), request.endDate());
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_DATE_RANGE);
        }
    }

    private void validateImageOrders(List<TravelRecordReqDTO.ImageRequest> images) {
        Set<Integer> imageOrders = new HashSet<>();
        boolean hasCoverImage = false;

        for (TravelRecordReqDTO.ImageRequest image : images) {
            if (!imageOrders.add(image.imageOrder())) {
                throw new GeneralException(TravelRecordErrorCode.INVALID_IMAGE_ORDER);
            }

            if (Integer.valueOf(1).equals(image.imageOrder())) {
                hasCoverImage = true;
            }
        }

        if (!hasCoverImage) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_IMAGE_ORDER);
        }
    }

    private void updateStickersIfRequested(
            TravelRecord travelRecord,
            List<TravelRecordReqDTO.StickerRequest> stickerRequests,
            Long userId
    ) {
        if (stickerRequests == null) {
            return;
        }

        travelRecordStickerRepository.deleteAllByTravelRecord_Id(travelRecord.getId());
        travelRecordStickerRepository.flush();

        if (stickerRequests.isEmpty()) {
            return;
        }

        validateStickerZIndex(stickerRequests);

        Map<Long, Sticker> stickerMap = getAvailableStickerMap(stickerRequests, userId);

        List<TravelRecordSticker> stickers = TravelRecordConverter.toTravelRecordStickers(
                travelRecord,
                stickerRequests,
                stickerMap
        );
        travelRecordStickerRepository.saveAll(stickers);
    }

    private String findCoverImageKey(List<TravelRecordReqDTO.ImageRequest> images) {
        return images.stream()
                .filter(image -> Integer.valueOf(1).equals(image.imageOrder()))
                .findFirst()
                .map(TravelRecordReqDTO.ImageRequest::imageKey)
                .orElse(images.get(0).imageKey());
    }

    private List<TravelRecordReqDTO.StickerRequest> resolveStickers(
            List<TravelRecordReqDTO.StickerRequest> stickers
    ) {
        if (stickers == null) {
            return List.of();
        }

        return stickers;
    }

    private void validateStickerZIndex(List<TravelRecordReqDTO.StickerRequest> stickers) {
        Set<Integer> zIndexes = new HashSet<>();

        for (TravelRecordReqDTO.StickerRequest sticker : stickers) {
            if (!zIndexes.add(sticker.zIndex())) {
                throw new GeneralException(TravelRecordErrorCode.INVALID_STICKER_POSITION);
            }
        }
    }

    private Map<Long, Sticker> getAvailableStickerMap(
            List<TravelRecordReqDTO.StickerRequest> stickerRequests,
            Long userId
    ) {
        List<Long> stickerIds = stickerRequests.stream()
                .map(TravelRecordReqDTO.StickerRequest::stickerId)
                .distinct()
                .toList();

        Map<Long, Sticker> stickerMap = stickerRepository.findByIdIn(stickerIds).stream()
                .filter(isAvailableSticker(userId))
                .collect(Collectors.toMap(Sticker::getId, Function.identity()));

        if (stickerMap.size() != stickerIds.size()) {
            throw new GeneralException(TravelRecordErrorCode.STICKER_NOT_FOUND);
        }

        return stickerMap;
    }

    private Predicate<Sticker> isAvailableSticker(Long userId) {
        return sticker -> {
            if (sticker.getStickerType() == StickerType.DEFAULT) {
                return true;
            }

            return sticker.getDeletedAt() == null
                    && sticker.getUser() != null
                    && sticker.getUser().getId().equals(userId);
        };
    }
}
