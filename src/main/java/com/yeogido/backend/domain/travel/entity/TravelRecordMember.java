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
}
