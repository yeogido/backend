package com.yeogido.backend.domain.terms.dto;

import com.yeogido.backend.domain.terms.enums.TermsType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class TermsReqDTO {
  public record Agreement(
    @Schema(description = "필수 약관 종류", example = "SERVICE") TermsType type,
    @Schema(description = "조회한 약관의 버전", example = "1.0") String version,
    @Schema(description = "명시적인 동의 여부. 필수 약관은 true여야 합니다.", example = "true") Boolean agreed
  ) {}

  public record Request(@Valid @NotNull List<Agreement> agreements) {}
}
