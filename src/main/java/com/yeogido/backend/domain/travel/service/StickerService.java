package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.dto.request.StickerReqDTO;
import com.yeogido.backend.domain.travel.dto.response.StickerResDTO;

public interface StickerService {

    StickerResDTO.StickerListResponse getStickers(Long userId);

    StickerResDTO.CreateResponse createCustomSticker(
            Long userId,
            StickerReqDTO.CreateRequest request
    );

    void deleteCustomSticker(Long userId, Long stickerId);
}
