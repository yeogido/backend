package com.yeogido.backend.domain.terms;

import com.yeogido.backend.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "user_terms_agreement", uniqueConstraints =
  @UniqueConstraint(columnNames = {"user_id", "terms_document_id"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserTermsAgreement {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "terms_document_id", nullable = false)
  private TermsDocument document;

  @Column(name = "agreed_at", nullable = false)
  private LocalDateTime agreedAt;
}
