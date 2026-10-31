package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.travel.converter.TravelRecordConverter;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.entity.TravelRecordSticker;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.StickerType;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.travel.repository.StickerRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordMemberRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordPhotoRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordStickerRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelRecordServiceImpl implements TravelRecordService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final FolderTheme DEFAULT_FOLDER_THEME = FolderTheme.BASIC;

    private final TravelRecordRepository travelRecordRepository;
    private final TravelRecordMemberRepository travelRecordMemberRepository;
    private final TravelRecordPhotoRepository travelRecordPhotoRepository;
    private final TravelRecordStickerRepository travelRecordStickerRepository;
    private final StickerRepository stickerRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final FileService fileService;
    private final S3Service s3Service;
    private final EntityManager entityManager;

    @Override
    public CursorResponse<TravelRecordResDTO.TravelRecordSummary> getMyTravelRecords(
            Long userId,
            TravelRecordReqDTO.ListRequest request
    ) {
        int size = resolveSize(request.size());
        int year = resolveYear(request.year());
        validateCursor(request.cursor());

        Long targetUserId = resolveTargetUserId(userId, request.userId());
        TravelRecordVisibility visibility = resolveVisibilityFilter(request.visibility());
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        TravelRecord cursor = request.cursor() == null ? null
                : travelRecordRepository.findVisibleCursor(userId, targetUserId, visibility,
                        request.cursor(), yearStart, yearEnd)
                    .orElseThrow(() -> new GeneralException(TravelRecordErrorCode.INVALID_PAGE_REQUEST));
        List<TravelRecord> travelRecords = travelRecordRepository.findVisibleRecords(
                userId, targetUserId, visibility, yearStart, yearEnd, cursor, size + 1);

        boolean hasNext = travelRecords.size() > size;
        List<TravelRecord> content = hasNext
                ? travelRecords.subList(0, size)
                : travelRecords;

        if (content.isEmpty()) {
            return CursorResponse.of(List.of(), null, null, false);
        }

        // 현재 페이지의 관계를 일괄 조회해 폴더 개수에 비례한 N+1 조회를 피합니다.
        List<Long> recordIds = content.stream().map(TravelRecord::getId).toList();
        Map<Long, List<TravelRecordMember>> members = travelRecordMemberRepository.findCurrentMembers(recordIds)
                .stream().collect(Collectors.groupingBy(member -> member.getTravelRecord().getId()));
        Map<Long, List<TravelRecordPhoto>> photos = travelRecordPhotoRepository
                .findByTravelRecordIdInOrderByTravelRecordIdAscImageOrderAsc(recordIds)
                .stream().collect(Collectors.groupingBy(photo -> photo.getTravelRecord().getId()));
        Map<Long, List<TravelRecordSticker>> stickers = travelRecordStickerRepository.findByTravelRecordIds(recordIds)
                .stream().collect(Collectors.groupingBy(sticker -> sticker.getTravelRecord().getId()));
        List<TravelRecordResDTO.TravelRecordSummary> items = content.stream()
                .map(record -> TravelRecordConverter.toTravelRecordSummary(record,
                        members.getOrDefault(record.getId(), List.of()),
                        photos.getOrDefault(record.getId(), List.of()),
                        stickers.getOrDefault(record.getId(), List.of()), s3Service::getImageUrl))
                .toList();

        Long nextCursor = items.isEmpty()
                ? null
                : items.get(items.size() - 1).travelRecordId();

        return CursorResponse.of(items,null, nextCursor, hasNext);
    }

    @Override
    public TravelRecordResDTO.YearListResponse getMyTravelRecordYears(
            Long userId, TravelRecordReqDTO.YearListRequest request
    ) {
        Long targetUserId = resolveTargetUserId(userId, request.userId());
        return new TravelRecordResDTO.YearListResponse(travelRecordRepository.findVisibleYears(
                userId, targetUserId, resolveVisibilityFilter(request.visibility())));
    }

    @Override
    public TravelRecordResDTO.DetailResponse getTravelRecord(Long userId, Long travelRecordId) {
        TravelRecord travelRecord = travelRecordRepository.findActiveDetailById(travelRecordId)
                .orElseThrow(() -> new GeneralException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
        List<TravelRecordMember> members = travelRecordMemberRepository.findCurrentMembers(List.of(travelRecordId));
        boolean owner = travelRecord.getUser().getId().equals(userId);
        boolean participant = travelRecord.getVisibility() == TravelRecordVisibility.FRIENDS
                && members.stream().anyMatch(member -> member.getUser().getId().equals(userId));
        if (!owner && !participant && travelRecord.getVisibility() != TravelRecordVisibility.PUBLIC) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }

        // 사진 순서는 화면 표시와 직접 연결되므로 imageOrder 오름차순을 보장
        List<TravelRecordPhoto> photos =
                travelRecordPhotoRepository.findByTravelRecordIdOrderByImageOrderAsc(travelRecordId);
        List<TravelRecordSticker> stickers =
                travelRecordStickerRepository.findByTravelRecordIdOrderByZIndexAsc(travelRecordId);

        return TravelRecordConverter.toDetailResponse(
                travelRecord,
                photos,
                stickers,
                members,
                userId,
                s3Service::getImageUrl
        );
    }

    @Override
    @Transactional
    public TravelRecordResDTO.CreateResponse createTravelRecord(
            Long userId,
            TravelRecordReqDTO.CreateRequest request
    ) {
        TravelRecordVisibility visibility = parseVisibility(request.visibility());
        User user = getUserOrThrow(userId);
        Region region = getRegionOrThrow(request.regionId());

        validateDateRange(request);
        validateImageOrders(request.images());

        List<TravelRecordReqDTO.ImageRequest> movedImages = moveImages(request.images());
        String coverImageKey = findCoverImageKey(movedImages);

        // travel_record.cover_image_key가 NOT NULL이라 대표 이미지 key를 여행 기록에도 저장
        TravelRecord travelRecord = TravelRecordConverter.toTravelRecord(
                request,
                user,
                region,
                coverImageKey,
                DEFAULT_FOLDER_THEME,
                visibility
        );

        TravelRecord savedTravelRecord = travelRecordRepository.save(travelRecord);

        List<TravelRecordPhoto> photos = TravelRecordConverter.toTravelRecordPhotos(
                savedTravelRecord,
                movedImages
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

    /** 생성자·현재 참여자의 최종 상태를 저장하며 파일 이동 전에 버전을 확보합니다. */
    @Override
    @Transactional
    public TravelRecordResDTO.UpdateResponse updateTravelRecord(
            Long travelRecordId,
            Long userId,
            TravelRecordReqDTO.UpdateRequest request
    ) {
        TravelRecord travelRecord = travelRecordRepository.findActiveDetailById(travelRecordId)
                .orElseThrow(() -> new GeneralException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
        validateEditor(travelRecord, userId);
        if (!travelRecord.getVersion().equals(request.version())) {
            throw new GeneralException(TravelRecordErrorCode.VERSION_CONFLICT);
        }

        Region region = getRegionOrThrow(request.regionId());
        validateDateRange(request.startDate(), request.endDate());
        validateImageOrders(request.images());

        // 같은 버전의 다른 요청은 파일을 이동하거나 자식 데이터를 변경하기 전에 종료합니다.
        if (travelRecordRepository.reserveVersion(travelRecordId, request.version()) == 0) {
            throw new GeneralException(TravelRecordErrorCode.VERSION_CONFLICT);
        }

        List<TravelRecordReqDTO.ImageRequest> movedImages = moveImages(request.images());
        String coverImageKey = findCoverImageKey(movedImages);

        // 버전 선점으로 잡은 행 잠금은 콘텐츠 저장이 끝날 때까지 같은 트랜잭션에서 유지됩니다.
        int updated = travelRecordRepository.updateIfVersionMatches(
                travelRecordId, request.version() + 1, region, request.title(), request.startDate(),
                request.endDate(), coverImageKey, DEFAULT_FOLDER_THEME, LocalDateTime.now()
        );
        if (updated == 0) {
            throw new GeneralException(TravelRecordErrorCode.VERSION_CONFLICT);
        }
        // JPQL 일괄 수정 이후 영속성 컨텍스트도 최신 버전으로 맞춥니다.
        entityManager.refresh(travelRecord);

        // 수정 요청의 images는 "수정 후 최종 목록"이므로 기존 사진을 전체 교체합니다.
        travelRecordPhotoRepository.deleteAllByTravelRecord_Id(travelRecordId);
        travelRecordPhotoRepository.flush();

        List<TravelRecordPhoto> photos = TravelRecordConverter.toTravelRecordPhotos(
                travelRecord,
                movedImages
        );
        travelRecordPhotoRepository.saveAll(photos);

        updateStickersIfRequested(travelRecord, request.stickers(), userId);

        return TravelRecordConverter.toUpdateResponse(travelRecord);
    }

    /** 생성자만 앨범을 삭제하며 참여 관계·초대는 기존 FK CASCADE로 함께 정리합니다. */
    @Override
    @Transactional
    public void deleteTravelRecord(Long travelRecordId, Long userId) {
        validateActiveUser(userId);
        TravelRecord travelRecord = getLockedTravelRecordOrThrow(travelRecordId);
        validateOwner(travelRecord, userId);

        // FK 제약이 있으므로 자식 테이블 데이터를 먼저 삭제한 뒤 여행 기록을 삭제합니다.
        travelRecordStickerRepository.deleteAllByTravelRecord_Id(travelRecordId);
        travelRecordPhotoRepository.deleteAllByTravelRecord_Id(travelRecordId);
        travelRecordRepository.delete(travelRecord);
        travelRecordRepository.flush();
    }

    /** 현재 FRIENDS 참여 관계만 제거하고 이전 화면·진행 중인 수정의 버전을 무효화합니다. */
    @Override
    @Transactional
    public void leaveTravelRecord(Long travelRecordId, Long userId) {
        validateActiveUser(userId);
        TravelRecord travelRecord = getLockedTravelRecordOrThrow(travelRecordId);
        if (travelRecord.getUser().getId().equals(userId)) {
            throw new GeneralException(TravelRecordErrorCode.OWNER_CANNOT_LEAVE);
        }
        if (travelRecord.getVisibility() != TravelRecordVisibility.FRIENDS
                || travelRecordMemberRepository.deleteCurrentMembership(travelRecordId, userId) == 0) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        // 앨범 행 잠금을 유지한 채 버전을 변경하므로 먼저 권한을 확인한 수정도 덮어쓰지 못합니다.
        travelRecordRepository.reserveVersion(travelRecordId, travelRecord.getVersion());
        entityManager.refresh(travelRecord);
    }

    /** 탈퇴한 사용자는 남아 있는 참여 행이 있더라도 변경 API를 사용할 수 없습니다. */
    private void validateActiveUser(Long userId) {
        if (getUserOrThrow(userId).getStatus() != UserStatus.ACTIVE) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }

        if (size <= 0 || size > MAX_PAGE_SIZE) {
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

        if (year <= 0 || year > 9999) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_PAGE_REQUEST);
        }

        return year;
    }

    private Long resolveTargetUserId(Long viewerId, Long requestedUserId) {
        if (requestedUserId == null || requestedUserId.equals(viewerId)) {
            return viewerId;
        }
        if (requestedUserId <= 0) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
        return userRepository.findById(requestedUserId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND)).getId();
    }

    private TravelRecordVisibility resolveVisibilityFilter(String value) {
        return value == null ? null : parseVisibility(value);
    }

    private TravelRecordVisibility parseVisibility(String value) {
        if (value == null) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_VISIBILITY);
        }
        try {
            return TravelRecordVisibility.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_VISIBILITY);
        }
    }

    /** 잠금 대기 중 반영된 버전도 읽도록 기존 영속 상태를 갱신합니다. */
    private TravelRecord getLockedTravelRecordOrThrow(Long travelRecordId) {
        TravelRecord record = travelRecordRepository.findActiveDetailForUpdate(travelRecordId)
                .orElseThrow(() -> new GeneralException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
        entityManager.refresh(record);
        return record;
    }

    private Region getRegionOrThrow(Long regionId) {
        return regionRepository.findById(regionId)
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));
    }

    /** 로그인한 활성 생성자 또는 FRIENDS의 현재 참여자만 공동 편집을 허용합니다. */
    private void validateEditor(TravelRecord record, Long userId) {
        User editor = getUserOrThrow(userId);
        if (editor.getStatus() != UserStatus.ACTIVE) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        boolean owner = record.getUser().getId().equals(userId);
        boolean participant = record.getVisibility() == TravelRecordVisibility.FRIENDS
                && travelRecordMemberRepository.findCurrentMembers(List.of(record.getId())).stream()
                    .anyMatch(member -> member.getUser().getId().equals(userId));
        if (!owner && !participant) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
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

        if (stickerRequests.isEmpty()) {
            travelRecordStickerRepository.deleteAllByTravelRecord_Id(travelRecord.getId());
            return;
        }

        validateStickerZIndex(stickerRequests);

        Map<Long, Long> existingStickerCounts = getExistingStickerCounts(travelRecord.getId());
        Map<Long, Long> requestedStickerCounts = countRequestedStickerIds(stickerRequests);
        Map<Long, Sticker> stickerMap = getAvailableStickerMapForUpdate(
                stickerRequests,
                userId,
                existingStickerCounts,
                requestedStickerCounts
        );

        travelRecordStickerRepository.deleteAllByTravelRecord_Id(travelRecord.getId());
        travelRecordStickerRepository.flush();

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
                .orElseThrow(() -> new GeneralException(TravelRecordErrorCode.INVALID_IMAGE_ORDER));
    }

    private List<TravelRecordReqDTO.ImageRequest> moveImages(
            List<TravelRecordReqDTO.ImageRequest> images
    ) {
        return images.stream()
                .map(image -> new TravelRecordReqDTO.ImageRequest(
                        fileService.moveToDirectory(image.imageKey(), ImageDirectory.TRAVEL_RECORD),
                        image.imageOrder()
                ))
                .toList();
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

    private Map<Long, Long> getExistingStickerCounts(Long travelRecordId) {
        return travelRecordStickerRepository.findByTravelRecordIdOrderByZIndexAsc(travelRecordId)
                .stream()
                .collect(Collectors.groupingBy(
                        recordSticker -> recordSticker.getSticker().getId(),
                        Collectors.counting()
                ));
    }

    private Map<Long, Long> countRequestedStickerIds(
            List<TravelRecordReqDTO.StickerRequest> stickerRequests
    ) {
        return stickerRequests.stream()
                .collect(Collectors.groupingBy(
                        TravelRecordReqDTO.StickerRequest::stickerId,
                        Collectors.counting()
                ));
    }

    private Map<Long, Sticker> getAvailableStickerMapForUpdate(
            List<TravelRecordReqDTO.StickerRequest> stickerRequests,
            Long userId,
            Map<Long, Long> existingStickerCounts,
            Map<Long, Long> requestedStickerCounts
    ) {
        List<Long> stickerIds = stickerRequests.stream()
                .map(TravelRecordReqDTO.StickerRequest::stickerId)
                .distinct()
                .toList();

        Map<Long, Sticker> stickerMap = stickerRepository.findByIdIn(stickerIds).stream()
                .filter(sticker -> isAvailableStickerForUpdate(
                        sticker,
                        userId,
                        existingStickerCounts,
                        requestedStickerCounts
                ))
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

    /** 본인 스티커의 신규 사용과 기존 배치된 타인·삭제 스티커의 수량 내 유지를 구분합니다. */
    private boolean isAvailableStickerForUpdate(
            Sticker sticker,
            Long userId,
            Map<Long, Long> existingStickerCounts,
            Map<Long, Long> requestedStickerCounts
    ) {
        if (sticker.getStickerType() == StickerType.DEFAULT) {
            return true;
        }

        boolean isOwner = sticker.getUser() != null
                && sticker.getUser().getId().equals(userId);

        // 다른 참여자의 기존 스티커는 유지·이동할 수 있지만 새로 추가할 수는 없습니다.
        if (!isOwner) {
            return requestedStickerCounts.getOrDefault(sticker.getId(), 0L)
                    <= existingStickerCounts.getOrDefault(sticker.getId(), 0L);
        }

        if (sticker.getDeletedAt() == null) {
            return true;
        }

        Long stickerId = sticker.getId();
        long existingCount = existingStickerCounts.getOrDefault(stickerId, 0L);
        long requestedCount = requestedStickerCounts.getOrDefault(stickerId, 0L);

        return requestedCount <= existingCount;
    }
}
