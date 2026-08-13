package com.yeogido.backend.domain.content.dto;

import java.time.LocalDateTime;

public class TourContentSyncDTO {

    public record Result(
            int receivedCount,
            int createdCount,
            int updatedCount,
            int skippedCount,
            LocalDateTime synchronizedAt
    ) {
    }
}
