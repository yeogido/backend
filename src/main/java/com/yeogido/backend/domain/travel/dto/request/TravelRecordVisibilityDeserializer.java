package com.yeogido.backend.domain.travel.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import java.io.IOException;

/** 등록 요청에서 공개범위 생략과 명시적 null을 구분합니다. */
public class TravelRecordVisibilityDeserializer extends StdDeserializer<String> {

  public TravelRecordVisibilityDeserializer() {
    super(String.class);
  }

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (!parser.hasToken(JsonToken.VALUE_STRING)) {
      return (String) context.handleUnexpectedToken(String.class, parser);
    }
    return parser.getText();
  }

  /** 필드가 없을 때만 기본값을 적용하며 JSON null은 서비스의 공개범위 검증으로 전달합니다. */
  @Override
  public Object getAbsentValue(DeserializationContext context) {
    return TravelRecordVisibility.PRIVATE.name();
  }
}
