package com.yeogido.backend.domain.terms;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {
  Optional<UserTermsAgreement> findByUserIdAndDocumentId(Long userId, Long documentId);
}
