package com.yeogido.backend.domain.placeenrichment.client;

import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailure;
import com.yeogido.backend.domain.placeenrichment.service.PlaceOverviewApiGate;
import java.util.Objects;
import java.util.function.Supplier;

/** 공유 클라이언트의 다른 호출과 분리된 동기 오버뷰 처리 전용 차단 범위 */
public final class PlaceOverviewApiScope implements AutoCloseable {
  private static final ThreadLocal<PlaceOverviewApiGate> CURRENT = new ThreadLocal<>();
  private final PlaceOverviewApiGate previous;

  private PlaceOverviewApiScope(PlaceOverviewApiGate gate) {
    previous = CURRENT.get();
    CURRENT.set(Objects.requireNonNull(gate));
  }

  public static PlaceOverviewApiScope open(PlaceOverviewApiGate gate) {
    return new PlaceOverviewApiScope(gate);
  }

  public static <T> T call(String provider, String operation, Supplier<T> call) {
    PlaceOverviewApiGate gate = CURRENT.get();
    // 오버뷰 처리 범위 밖의 공유 클라이언트 호출에 대한 기존 동작 유지
    if (gate == null) {
      return call.get();
    }
    // 선점 이후 발견된 공통 장애의 전파를 위한 실제 호출 직전 차단 재확인
    gate.check(provider, operation);
    try {
      return call.get();
    } catch (ExternalApiFailure failure) {
      try {
        gate.recordFailure(failure);
      } catch (RuntimeException persistenceFailure) {
        // 차단 저장 실패 시 최초 외부 오류 보존 및 추가 실패 정보 첨부
        failure.addSuppressed(persistenceFailure);
      }
      throw failure;
    }
  }

  @Override
  public void close() {
    // 처리 종료 시 스레드 정보 제거 또는 중첩 호출의 이전 범위 복원
    if (previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(previous);
    }
  }
}
