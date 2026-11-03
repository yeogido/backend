package com.yeogido.backend.domain.terms.repository;

import com.yeogido.backend.domain.terms.entity.TermsDocument;
import com.yeogido.backend.domain.terms.enums.TermsType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsDocumentRepository extends JpaRepository<TermsDocument, Long> {
  Optional<TermsDocument> findFirstByTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
    TermsType type, LocalDateTime now);
}
