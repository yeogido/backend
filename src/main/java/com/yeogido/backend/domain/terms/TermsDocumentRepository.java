package com.yeogido.backend.domain.terms;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsDocumentRepository extends JpaRepository<TermsDocument, Long> {
  Optional<TermsDocument> findFirstByTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
    TermsType type, LocalDateTime now);
}
