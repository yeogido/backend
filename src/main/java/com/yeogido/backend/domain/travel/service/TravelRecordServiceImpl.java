package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.travel.repository.TravelRecordPhotoRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralException;
import java.util.Collections;
import java.util.List;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
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
    private static final int MAX_IMAGE_COUNT = 5;
    private static final FolderTheme DEFAULT_FOLDER_THEME = FolderTheme.BASIC;
    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final TravelRecordRepository travelRecordRepository;
    private final TravelRecordPhotoRepository travelRecordPhotoRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;

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
                .map(this::toTravelRecordSummary)
                .toList();

        Long nextCursor = items.isEmpty()
                ? null
                : items.get(items.size() - 1).travelRecordId();

        return CursorResponse.of(items, nextCursor, hasNext);
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

        return toDetailResponse(travelRecord, photos);
    }

    @Override
    @Transactional
    public TravelRecordResDTO.CreateResponse createTravelRecord(
            TravelRecordReqDTO.CreateRequest request
    ) {
        User user = getCurrentUser();
        Region region = getRegionOrThrow(request.regionId());

        validateDateRange(request);
        validateImages(request.images());

        String coverImageKey = findCoverImageKey(request.images());

        // travel_record.cover_image_key가 NOT NULL이라 대표 이미지 key를 여행 기록에도 저장
        TravelRecord travelRecord = TravelRecord.builder()
                .user(user)
                .region(region)
                .title(request.title())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .coverImageKey(coverImageKey)
                // 폴더 색상 변경 기능이 없으므로 현재는 기본 테마로 고정합니다.
                .folderTheme(DEFAULT_FOLDER_THEME)
                .build();

        TravelRecord savedTravelRecord = travelRecordRepository.save(travelRecord);

        List<TravelRecordPhoto> photos = request.images().stream()
                .map(image -> TravelRecordPhoto.builder()
                        .travelRecord(savedTravelRecord)
                        .imageKey(image.imageKey())
                        .imageOrder(image.imageOrder())
                        .build())
                .toList();

        travelRecordPhotoRepository.saveAll(photos);

        // 스티커 요청값은 현재 범위에서는 저장 로직을 구현하지 않음
        return toCreateResponse(savedTravelRecord);
    }

    private Long getCurrentUserId() {
        // TODO: Spring Security 적용 후 인증 사용자 ID로 교체
        return MOCK_USER_ID;
    }

    private User getCurrentUser() {
        // TODO: Spring Security 적용 후 인증 사용자 조회로 교체
        return userRepository.findById(MOCK_USER_ID)
                .orElseThrow(() -> new GeneralException(TravelRecordErrorCode.ACCESS_DENIED));
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
            return Year.now(SERVICE_ZONE_ID).getValue();
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
            throw new GeneralException(TravelRecordErrorCode.ACCESS_DENIED);
        }
    }

    private void validateDateRange(TravelRecordReqDTO.CreateRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_DATE_RANGE);
        }
    }

    private void validateImages(List<TravelRecordReqDTO.ImageRequest> images) {
        if (images == null || images.isEmpty()) {
            throw new GeneralException(TravelRecordErrorCode.IMAGE_REQUIRED);
        }

        if (images.size() > MAX_IMAGE_COUNT) {
            throw new GeneralException(TravelRecordErrorCode.IMAGE_LIMIT_EXCEEDED);
        }

        images.forEach(this::validateImage);
    }

    private void validateImage(TravelRecordReqDTO.ImageRequest image) {
        if (image.imageKey() == null || image.imageKey().isBlank()) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_IMAGE_KEY);
        }

        if (image.imageOrder() == null || image.imageOrder() < 1) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_IMAGE_ORDER);
        }
    }

    private String findCoverImageKey(List<TravelRecordReqDTO.ImageRequest> images) {
        return images.stream()
                .filter(image -> Integer.valueOf(1).equals(image.imageOrder()))
                .findFirst()
                .map(TravelRecordReqDTO.ImageRequest::imageKey)
                .orElse(images.get(0).imageKey());
    }

    private TravelRecordResDTO.TravelRecordSummary toTravelRecordSummary(
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

    private TravelRecordResDTO.DetailResponse toDetailResponse(
            TravelRecord travelRecord,
            List<TravelRecordPhoto> photos
    ) {
        List<TravelRecordResDTO.ImageResponse> images = photos.stream()
                .map(photo -> new TravelRecordResDTO.ImageResponse(
                        photo.getId(),
                        photo.getImageKey(),
                        photo.getImageOrder()
                ))
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
                Collections.emptyList(),
                travelRecord.getCreatedAt(),
                travelRecord.getUpdatedAt()
        );
    }

    private TravelRecordResDTO.CreateResponse toCreateResponse(TravelRecord travelRecord) {
        return new TravelRecordResDTO.CreateResponse(
                travelRecord.getId(),
                travelRecord.getTitle(),
                travelRecord.getCoverImageKey(),
                travelRecord.getCreatedAt()
        );
    }
}
