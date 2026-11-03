package com.yeogido.backend.domain.terms.converter;

import com.yeogido.backend.domain.terms.dto.TermsResDTO;
import com.yeogido.backend.domain.terms.entity.TermsDocument;

public class TermsConverter {
  private TermsConverter() {}

  public static TermsResDTO.Document toDocument(TermsDocument document) {
    return new TermsResDTO.Document(document.getId(), document.getType(), document.getVersion(),
      document.getTitle(), document.getContent(), true, document.getEffectiveAt());
  }
}
