package com.yeogido.backend.domain.travel.entity;

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
  name = "travel_record_member",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_travel_record_member_record_user",
    columnNames = {"travel_record_id", "user_id"}
  ),
  indexes = @Index(
    name = "idx_travel_record_member_user_record",
    columnList = "user_id, travel_record_id"
  )
)
public class TravelRecordMember extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "travel_record_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private TravelRecord travelRecord;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** 생성자는 travel_record.user_id로 관리하므로 추가 참여자로 중복 저장하지 않습니다. */
  @PrePersist
  private void rejectOwnerMember() {
    if (travelRecord == null || user == null) {
      return;
    }
    User owner = travelRecord.getUser();
    if (owner == user || owner != null && owner.getId() != null && owner.getId().equals(user.getId())) {
      throw new IllegalStateException("앨범 생성자는 추가 참여자로 등록할 수 없습니다.");
    }
  }
}
