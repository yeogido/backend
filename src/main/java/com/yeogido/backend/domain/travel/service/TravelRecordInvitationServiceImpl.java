package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.friend.repository.FriendshipRepository;
import com.yeogido.backend.domain.travel.converter.TravelRecordInvitationConverter;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordInvitationResDTO;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.travel.repository.TravelRecordInvitationRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordMemberRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelRecordInvitationServiceImpl implements TravelRecordInvitationService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final TravelRecordRepository travelRecordRepository;
    private final TravelRecordMemberRepository travelRecordMemberRepository;
    private final TravelRecordInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final EntityManager entityManager;
    private final S3Service s3Service;

    @Override
    public CursorResponse<TravelRecordInvitationResDTO.ReceivedInvitation> getReceivedInvitations(
            Long userId, TravelRecordInvitationReqDTO.ListRequest request
    ) {
        int size = request.size() == null ? DEFAULT_PAGE_SIZE : request.size();
        if (size <= 0 || size > MAX_PAGE_SIZE || (request.cursor() != null && request.cursor() <= 0)) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_PAGE_REQUEST);
        }
        TravelRecordInvitation cursor = request.cursor() == null ? null
                : invitationRepository.findReceivedCursor(userId, request.cursor())
                    .orElseThrow(() -> new GeneralException(TravelRecordErrorCode.INVALID_PAGE_REQUEST));
        List<TravelRecordInvitation> invitations = invitationRepository.findPendingReceived(userId, cursor, size + 1);
        boolean hasNext = invitations.size() > size;
        List<TravelRecordInvitation> content = hasNext ? invitations.subList(0, size) : invitations;
        List<TravelRecordInvitationResDTO.ReceivedInvitation> items = content.stream()
                .map(invitation -> TravelRecordInvitationConverter.toReceivedInvitation(invitation, s3Service::getImageUrl))
                .toList();
        return CursorResponse.of(items, null, content.isEmpty() ? null : content.getLast().getId(), hasNext);
    }

    /** 앨범 행 잠금 아래 모든 대상을 검증한 뒤 초대를 함께 저장하며 참여 관계는 생성하지 않습니다. */
    @Override
    @Transactional
    public TravelRecordInvitationResDTO.CreateResponse createInvitations(
            Long travelRecordId, Long userId, TravelRecordInvitationReqDTO.CreateRequest request
    ) {
        TravelRecord record = travelRecordRepository.findActiveDetailForUpdate(travelRecordId)
                .orElseThrow(() -> new GeneralException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
        entityManager.refresh(record, LockModeType.PESSIMISTIC_WRITE);
        // MySQL REPEATABLE READ에서도 잠금 대기 전에 만들어진 스냅샷으로 참여 권한을 판단하지 않습니다.
        User inviter = userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
        Set<Long> memberIds = travelRecordMemberRepository.findCurrentMembers(List.of(travelRecordId)).stream()
                .map(member -> member.getUser().getId()).collect(Collectors.toSet());
        boolean owner = record.getUser().getId().equals(userId);
        if (!owner && (record.getVisibility() != TravelRecordVisibility.FRIENDS || !memberIds.contains(userId))) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        if (record.getVisibility() != TravelRecordVisibility.FRIENDS) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_INVITATION_STATE);
        }

        List<Long> ids = request.inviteeUserIds();
        validateTargetIds(ids, userId);
        memberIds.add(record.getUser().getId());
        if (ids.stream().anyMatch(memberIds::contains)) {
            throw new GeneralException(TravelRecordErrorCode.ALREADY_PARTICIPATING);
        }
        Map<Long, User> targets = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        validateFriends(ids, targets, userId);
        List<TravelRecordInvitation> previous = invitationRepository
                .findByTravelRecordIdAndInviteeIdIn(travelRecordId, ids);
        if (previous.stream().anyMatch(invitation -> invitation.getStatus() == TravelRecordInvitationStatus.PENDING)) {
            throw new GeneralException(TravelRecordErrorCode.INVITATION_ALREADY_PENDING);
        }
        // 종료된 초대는 현재 참여 관계와 구분하고, 재초대는 새 ID와 생성 일시로 저장합니다.
        if (!previous.isEmpty()) {
            invitationRepository.deleteAll(previous);
            invitationRepository.flush();
        }
        List<TravelRecordInvitation> invitations = ids.stream()
                .map(id -> TravelRecordInvitation.builder().travelRecord(record)
                        .inviter(inviter).invitee(targets.get(id)).build()).toList();
        return TravelRecordInvitationConverter.toCreateResponse(travelRecordId,
                invitationRepository.saveAllAndFlush(invitations));
    }

    /** 빈 목록, 잘못된 ID, 중복, 자기 자신을 대상으로 한 요청을 거부합니다. */
    private void validateTargetIds(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size() || ids.contains(userId)) {
            throw new GeneralException(TravelRecordErrorCode.INVALID_INVITATION_TARGET);
        }
    }

    /** 요청자를 기준으로 활성 대상과 수락 완료된 친구 관계를 확인합니다. */
    private void validateFriends(List<Long> ids, Map<Long, User> targets, Long userId) {
        for (Long id : ids) {
            User target = targets.get(id);
            if (target == null || target.getStatus() != UserStatus.ACTIVE
                    || !friendshipRepository.existsByUserLowIdAndUserHighIdAndStatus(
                        Math.min(userId, id), Math.max(userId, id), FriendshipStatus.ACCEPTED)) {
                throw new GeneralException(TravelRecordErrorCode.INVALID_INVITATION_TARGET);
            }
        }
    }
}
