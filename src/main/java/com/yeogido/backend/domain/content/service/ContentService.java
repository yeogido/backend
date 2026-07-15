package com.yeogido.backend.domain.content.service;


import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.common.response.StringCursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

public interface ContentService {

    //문화콘텐츠 목록 조회
    StringCursorResponse<ContentResDTO.ContentInfo> getContents(ContentReqDTO.ContentListReq request);

    //문화콘텐츠 상세 조회
    ContentResDTO.ContentDetailRes getContentDetail(Long contentId);

    //문화콘텐츠 등록
    ContentResDTO.ContentCreateRes createContent(ContentReqDTO.ContentCreateReq request);

    //문화콘텐츠 수정
    ContentResDTO.ContentUpdateRes updateContent(Long contentId, ContentReqDTO.ContentCreateReq request);

    //문화콘텐츠 좋아요 등록
    ContentResDTO.ContentLikeRes likeContent(Long contentId);
}
