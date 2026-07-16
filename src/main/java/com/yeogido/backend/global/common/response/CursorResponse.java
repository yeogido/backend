package com.yeogido.backend.global.common.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CursorResponse<T> {

    private List<T> items;

    private Object cursorValue;

    private Long cursorId;

    private boolean hasNext;

    public static <T> CursorResponse<T> of(
            List<T> items,
            Object cursorValue,
            Long cursorId,
            boolean hasNext
    ) {
        return CursorResponse.<T>builder()
                .items(items)
                .cursorValue(cursorValue)
                .cursorId(cursorId)
                .hasNext(hasNext)
                .build();
    }
}