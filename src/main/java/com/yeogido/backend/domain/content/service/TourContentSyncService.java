package com.yeogido.backend.domain.content.service;

import com.yeogido.backend.domain.content.dto.TourContentSyncDTO;

public interface TourContentSyncService {

    TourContentSyncDTO.Result synchronize();

    TourContentSyncDTO.Result synchronize(Long userId);
}
