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

    private Long nextCursor;

    private boolean hasNext;

    public static <T> CursorResponse<T> of(
            List<T> items,
            Long nextCursor,
            boolean hasNext
    ) {
        return CursorResponse.<T>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }
}