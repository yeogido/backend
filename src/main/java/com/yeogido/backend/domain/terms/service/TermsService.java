package com.yeogido.backend.domain.terms.service;

import com.yeogido.backend.domain.terms.dto.TermsReqDTO;
import com.yeogido.backend.domain.terms.dto.TermsResDTO;
import com.yeogido.backend.domain.terms.entity.TermsDocument;
import com.yeogido.backend.domain.user.entity.User;
import java.util.List;

public interface TermsService {
  List<TermsResDTO.Document> currentDocuments();
  List<TermsDocument> validate(List<TermsReqDTO.Agreement> requested);
  void record(User user, List<TermsDocument> validated);
  TermsResDTO.Status agree(Long userId, List<TermsReqDTO.Agreement> requested);
  TermsResDTO.Status status(Long userId);
  boolean isAgreementRequired(Long userId);
}
