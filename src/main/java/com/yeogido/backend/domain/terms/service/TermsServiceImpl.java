package com.yeogido.backend.domain.terms.service;

import com.yeogido.backend.domain.terms.converter.TermsConverter;
import com.yeogido.backend.domain.terms.dto.TermsReqDTO;
import com.yeogido.backend.domain.terms.dto.TermsResDTO;
import com.yeogido.backend.domain.terms.entity.TermsDocument;
import com.yeogido.backend.domain.terms.entity.UserTermsAgreement;
import com.yeogido.backend.domain.terms.enums.TermsType;
import com.yeogido.backend.domain.terms.exception.TermsErrorCode;
import com.yeogido.backend.domain.terms.repository.TermsDocumentRepository;
import com.yeogido.backend.domain.terms.repository.UserTermsAgreementRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsServiceImpl implements TermsService {
  private final TermsDocumentRepository documents;
  private final UserTermsAgreementRepository agreements;
  private final UserRepository users;

  @Override
  public List<TermsResDTO.Document> currentDocuments() {
    return current().stream().map(TermsConverter::toDocument).toList();
  }

  // Return the validated snapshot so signup persists exactly the versions it validated.
  @Override
  public List<TermsDocument> validate(List<TermsReqDTO.Agreement> requested) {
    if (requested == null || requested.size() != TermsType.values().length) {
      throw new GeneralException(TermsErrorCode.REQUIRED_AGREEMENT);
    }
    var byType = new EnumMap<TermsType, TermsReqDTO.Agreement>(TermsType.class);
    for (var item : requested) {
      if (item == null || item.type() == null || !Boolean.TRUE.equals(item.agreed())
          || !StringUtils.hasText(item.version()) || byType.put(item.type(), item) != null) {
        throw new GeneralException(TermsErrorCode.REQUIRED_AGREEMENT);
      }
    }
    var current = current();
    for (var document : current) {
      if (!document.getVersion().equals(byType.get(document.getType()).version())) {
        throw new GeneralException(TermsErrorCode.VERSION_MISMATCH);
      }
    }
    return current;
  }

  @Override
  @Transactional
  public void record(User user, List<TermsDocument> validated) {
    var now = LocalDateTime.now();
    for (var document : validated) {
      if (agreements.findByUserIdAndDocumentId(user.getId(), document.getId()).isEmpty()) {
        agreements.save(UserTermsAgreement.builder().user(user).document(document).agreedAt(now).build());
      }
    }
  }

  @Override
  @Transactional
  public TermsResDTO.Status agree(Long userId, List<TermsReqDTO.Agreement> requested) {
    // Serializes retries for the same account without changing the original agreement time.
    var user = users.findByIdForTermsAgreement(userId)
      .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
    if (user.getStatus() == UserStatus.DELETED) {
      throw new GeneralException(UserErrorCode.USER_WITHDRAWN);
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new GeneralException(UserErrorCode.USER_NOT_FOUND);
    }
    record(user, validate(requested));
    return status(userId);
  }

  @Override
  public TermsResDTO.Status status(Long userId) {
    var states = current().stream().map(document -> {
      var agreement = agreements.findByUserIdAndDocumentId(userId, document.getId());
      return new TermsResDTO.AgreementState(document.getType(), document.getVersion(), agreement.isPresent(),
        agreement.map(UserTermsAgreement::getAgreedAt).orElse(null));
    }).toList();
    return new TermsResDTO.Status(states.stream().anyMatch(state -> !state.agreed()), states);
  }

  @Override
  public boolean isAgreementRequired(Long userId) {
    // Existing members can still log in when operational documents have not yet been published.
    try {
      return status(userId).termsAgreementRequired();
    } catch (GeneralException e) {
      if (e.getErrorCode() == TermsErrorCode.NOT_CONFIGURED) return true;
      throw e;
    }
  }

  private List<TermsDocument> current() {
    var now = LocalDateTime.now();
    return Arrays.stream(TermsType.values()).map(type -> documents
      .findFirstByTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(type, now)
      .filter(document -> StringUtils.hasText(document.getTitle()) && StringUtils.hasText(document.getContent()))
      .orElseThrow(() -> new GeneralException(TermsErrorCode.NOT_CONFIGURED))).toList();
  }
}
