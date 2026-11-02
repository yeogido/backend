package com.yeogido.backend.domain.terms;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "terms_document", uniqueConstraints = {
  @UniqueConstraint(columnNames = {"type", "version"}),
  @UniqueConstraint(columnNames = {"type", "effective_at"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TermsDocument {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TermsType type;

  @Column(nullable = false, length = 50)
  private String version;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "effective_at", nullable = false)
  private LocalDateTime effectiveAt;
}
