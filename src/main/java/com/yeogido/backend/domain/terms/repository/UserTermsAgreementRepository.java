package com.yeogido.backend.domain.terms.repository;

import com.yeogido.backend.domain.terms.entity.UserTermsAgreement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {
  Optional<UserTermsAgreement> findByUserIdAndDocumentId(Long userId, Long documentId);
}
