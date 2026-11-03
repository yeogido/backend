package com.yeogido.backend.domain.travel.entity;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private TravelRecordInvitationStatus status = TravelRecordInvitationStatus.PENDING;
}
