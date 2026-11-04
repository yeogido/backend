package com.yeogido.backend.domain.terms.dto;

import com.yeogido.backend.domain.terms.enums.TermsType;
import java.time.LocalDateTime;
import java.util.List;

public class TermsResDTO {
  public record Document(Long id, TermsType type, String version, String title,
                         String content, boolean required, LocalDateTime effectiveAt) {}

  public record AgreementState(TermsType type, String version, boolean agreed, LocalDateTime agreedAt) {}
  public record Status(boolean termsAgreementRequired, List<AgreementState> agreements) {}
}
