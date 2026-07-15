package com.yeogido.backend.global.common.response;

import com.yeogido.backend.global.common.dto.NextCursor;
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
public class ComplexCursorResponse<T> {

    private List<T> items;

    private NextCursor<?> nextCursor;

    private boolean hasNext;

    public static <T> ComplexCursorResponse<T> of(
            List<T> items,
            NextCursor<?> nextCursor,
            boolean hasNext
    ) {
        return ComplexCursorResponse.<T>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }
}