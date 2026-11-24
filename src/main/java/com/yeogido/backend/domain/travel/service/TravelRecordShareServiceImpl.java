package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.travel.converter.TravelRecordConverter;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordShareResDTO;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.travel.repository.TravelRecordRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordMemberRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordInvitationRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordPhotoRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordStickerRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TravelRecordShareServiceImpl implements TravelRecordShareService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final TravelRecordRepository records;
    private final TravelRecordMemberRepository members;
    private final TravelRecordInvitationRepository invitations;
    private final TravelRecordPhotoRepository photos;
    private final TravelRecordStickerRepository stickers;
    private final UserRepository users;
    private final TravelRecordParticipationLockService participationLocks;
    private final EntityManager em;
    private final S3Service s3Service;

    @Override
    public TravelRecordShareResDTO.Link createLink(Long recordId, Long userId) {
        TravelRecord record = records.findActiveDetailForUpdate(recordId).orElseThrow(this::notFound);
        em.refresh(record, LockModeType.PESSIMISTIC_WRITE);
        activeUser(userId);
        boolean owner = record.getUser().getId().equals(userId);
        boolean member = members.findCurrentForUpdate(recordId, userId).isPresent();
        if (record.getVisibility() == TravelRecordVisibility.PRIVATE
                || (record.getVisibility() == TravelRecordVisibility.FRIENDS && !owner && !member)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        // 같은 공개범위에서 재공유해도 이미 전달된 링크는 유지합니다.
        if (record.getShareToken() == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            record.assignShareToken(HexFormat.of().formatHex(bytes));
            records.flush();
        }
        return new TravelRecordShareResDTO.Link(recordId, record.getShareToken());
    }

    @Override
    public TravelRecordShareResDTO.View getSharedRecord(String token, Long userId) {
        // 공개범위 변경/삭제와 동일한 앨범 잠금에서 권한과 응답을 결정합니다.
        TravelRecord record = lockedRecord(token);
        activeUser(userId);
        var currentMembers = members.findCurrentMembers(List.of(record.getId()));
        boolean canRead = record.getVisibility() == TravelRecordVisibility.PUBLIC
                || record.getUser().getId().equals(userId)
                || currentMembers.stream().anyMatch(m -> m.getUser().getId().equals(userId));
        var recordPhotos = photos.findByTravelRecordIdOrderByImageOrderAsc(record.getId());
        var recordStickers = stickers.findByTravelRecordIdOrderByZIndexAsc(record.getId());
        User owner = record.getUser();
        var ownerProfile = new TravelRecordResDTO.UserProfile(owner.getId(), owner.getNickname(),
                owner.getProfileImage() == null ? null : s3Service.getImageUrl(owner.getProfileImage()));
        if (canRead) {
            return new TravelRecordShareResDTO.View(record.getId(), record.getVisibility(), false, ownerProfile, null,
                    TravelRecordConverter.toDetailResponse(record, recordPhotos, recordStickers, currentMembers,
                            userId, s3Service::getImageUrl));
        }
        var preview = new TravelRecordShareResDTO.Preview(record.getId(), record.getTitle(),
                record.getRegion().getId(), record.getRegion().getName(), record.getStartDate(), record.getEndDate(), record.getFolderTheme(),
                recordPhotos.stream().map(p -> TravelRecordConverter.toPreviewImage(p, s3Service::getImageUrl)).toList(),
                recordStickers.stream().map(s -> TravelRecordConverter.toStickerResponse(s, s3Service::getImageUrl)).toList(),
                recordPhotos.size());
        return new TravelRecordShareResDTO.View(record.getId(), record.getVisibility(), true, ownerProfile, preview, null);
    }

    @Override
    public TravelRecordShareResDTO.Respond respond(String token, Long userId,
                                                   TravelRecordInvitationReqDTO.RespondRequest request) {
        if (!"ACCEPTED".equals(request.status()) && !"DECLINED".equals(request.status())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
        // 기존 수락 및 탈퇴 처리와 동일하게 사용자 참여 잠금, 앨범 순서를 유지합니다.
        participationLocks.acquire(userId);
        TravelRecord record = lockedRecord(token);
        User user = activeUser(userId);
        if (record.getVisibility() != TravelRecordVisibility.FRIENDS) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_INVITATION_STATE);
        }
        boolean alreadyJoined = record.getUser().getId().equals(userId)
                || members.findCurrentForUpdate(record.getId(), userId).isPresent();
        var response = TravelRecordInvitationStatus.valueOf(request.status());
        if (alreadyJoined && response == TravelRecordInvitationStatus.DECLINED) {
            throw new GeneralException(TravelRecordErrorCode.ALREADY_PARTICIPATING);
        }
        if (!alreadyJoined && response == TravelRecordInvitationStatus.ACCEPTED) {
            members.saveAndFlush(TravelRecordMember.builder().travelRecord(record).user(user).build());
            if (records.reserveVersion(record.getId(), record.getVersion()) == 0) {
                throw new GeneralException(TravelRecordErrorCode.VERSION_CONFLICT);
            }
        }
        // 링크 방문만으로 대기 초대를 만들지 않고, 기존 지정 초대가 있으면 응답 상태를 맞춥니다.
        invitations.respondPendingFromLink(record.getId(), userId, response);
        return new TravelRecordShareResDTO.Respond(record.getId(), response);
    }

    private TravelRecord lockedRecord(String token) {
        if (token == null || !token.matches("[0-9a-f]{64}")) {
            throw notFound();
        }
        TravelRecord record = records.findByShareTokenForUpdate(token).orElseThrow(this::notFound);
        em.refresh(record, LockModeType.PESSIMISTIC_WRITE);
        if (!token.equals(record.getShareToken()) || record.getVisibility() == TravelRecordVisibility.PRIVATE
                || record.getUser().getStatus() != UserStatus.ACTIVE) {
            throw notFound();
        }
        return record;
    }

    private User activeUser(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
        em.refresh(user, LockModeType.PESSIMISTIC_READ);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        return user;
    }

    private GeneralException notFound() {
        return new GeneralException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND);
    }
}
