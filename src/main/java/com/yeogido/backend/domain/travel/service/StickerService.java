package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.dto.response.StickerResDTO;

public interface StickerService {

    StickerResDTO.StickerListResponse getStickers(Long userId);
}
