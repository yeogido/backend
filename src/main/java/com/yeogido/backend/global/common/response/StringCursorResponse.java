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
public class StringCursorResponse<T> {

    private List<T> items;

    private String nextCursor;

    private boolean hasNext;

    public static <T> StringCursorResponse<T> of(
            List<T> items,
            String nextCursor,
            boolean hasNext
    ) {
        return StringCursorResponse.<T>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }
}