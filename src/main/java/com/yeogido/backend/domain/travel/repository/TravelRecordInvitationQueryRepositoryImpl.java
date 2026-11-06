package com.yeogido.backend.domain.travel.repository;

import static com.yeogido.backend.domain.travel.entity.QTravelRecordInvitation.travelRecordInvitation;
import static com.yeogido.backend.domain.travel.entity.QTravelRecord.travelRecord;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.user.entity.QUser;
import com.yeogido.backend.domain.user.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

public class TravelRecordInvitationQueryRepositoryImpl implements TravelRecordInvitationQueryRepository {

    private final JPAQueryFactory queryFactory;

    public TravelRecordInvitationQueryRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    /** 수신자와 현재 유효한 대기 초대만 조회하며 사진 정보는 로드하지 않습니다. */
    @Override
    public List<TravelRecordInvitation> findPendingReceived(Long userId, TravelRecordInvitation cursor, int limit) {
        QUser owner = new QUser("invitationOwner");
        QUser inviter = new QUser("actualInviter");
        return queryFactory.selectFrom(travelRecordInvitation)
                .join(travelRecordInvitation.travelRecord, travelRecord).fetchJoin()
                .join(travelRecord.user, owner).fetchJoin()
                .join(travelRecordInvitation.inviter, inviter).fetchJoin()
                .join(travelRecordInvitation.friendship)
                .where(travelRecordInvitation.invitee.id.eq(userId),
                        travelRecordInvitation.status.eq(TravelRecordInvitationStatus.PENDING),
                        travelRecord.visibility.eq(TravelRecordVisibility.FRIENDS),
                        owner.status.eq(UserStatus.ACTIVE), inviter.status.eq(UserStatus.ACTIVE),
                        travelRecordInvitation.friendship.status.eq(
                                FriendshipStatus.ACCEPTED), afterCursor(cursor))
                .orderBy(travelRecordInvitation.createdAt.desc(), travelRecordInvitation.id.desc())
                .limit(limit).fetch();
    }

    /** 수락, 거절 처리된 초대도 본인이 받은 초대라면 다음 페이지 기준으로 사용할 수 있습니다. */
    @Override
    public Optional<TravelRecordInvitation> findReceivedCursor(Long userId, Long cursorId) {
        return Optional.ofNullable(queryFactory.selectFrom(travelRecordInvitation)
                .where(travelRecordInvitation.id.eq(cursorId), travelRecordInvitation.invitee.id.eq(userId))
                .fetchOne());
    }

    private BooleanExpression afterCursor(TravelRecordInvitation cursor) {
        return cursor == null ? null : travelRecordInvitation.createdAt.lt(cursor.getCreatedAt())
                .or(travelRecordInvitation.createdAt.eq(cursor.getCreatedAt())
                        .and(travelRecordInvitation.id.lt(cursor.getId())));
    }
}
