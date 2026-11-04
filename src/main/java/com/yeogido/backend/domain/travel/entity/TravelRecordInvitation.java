package com.yeogido.backend.domain.travel.entity;

import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
  name = "travel_record_invitation",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_travel_record_invitation_record_invitee",
    columnNames = {"travel_record_id", "invitee_user_id"}
  ),
  indexes = {
    @Index(
      name = "idx_travel_record_invitation_invitee_status_created",
      columnList = "invitee_user_id, status, created_at"
    ),
    @Index(
      name = "idx_travel_record_invitation_inviter",
      columnList = "inviter_user_id"
    )
  }
)
public class TravelRecordInvitation extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "travel_record_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private TravelRecord travelRecord;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inviter_user_id", nullable = false)
  private User inviter;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invitee_user_id", nullable = false)
  private User invitee;

  /** 대기 초대만 당시의 친구 관계에 연결해 친구 해제 시 함께 삭제합니다. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "friendship_id")
  @OnDelete(action = OnDeleteAction.CASCADE)
  private Friendship friendship;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private TravelRecordInvitationStatus status = TravelRecordInvitationStatus.PENDING;

  public void respond(TravelRecordInvitationStatus status) {
    if (this.status != TravelRecordInvitationStatus.PENDING || status == null
        || status == TravelRecordInvitationStatus.PENDING) {
      throw new IllegalStateException("대기 초대는 수락 또는 거절로만 응답할 수 있습니다.");
    }
    this.status = status;
    this.friendship = null;
  }
}
