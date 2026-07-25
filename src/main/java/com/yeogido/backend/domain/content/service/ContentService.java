package com.yeogido.backend.domain.content.service;


import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

import java.util.List;

public interface ContentService {

    //문화콘텐츠 목록 조회
    CursorResponse<ContentResDTO.ContentInfo> getContents(ContentReqDTO.ContentListReq request);

    //문화콘텐츠 상세 조회
    ContentResDTO.ContentDetailRes getContentDetail(Long contentId);

    //문화콘텐츠 등록
    ContentResDTO.ContentCreateRes createContent(ContentReqDTO.ContentCreateReq request, Long userId);

    //문화콘텐츠 수정
    ContentResDTO.ContentUpdateRes updateContent(Long contentId, ContentReqDTO.ContentCreateReq request, Long userId);

    //문화콘텐츠 삭제
    void deleteContent(Long contentId, Long userId);

    //문화콘텐츠 추천 대표 행사
    List<ContentResDTO.BannerRes> getBannerContents();

    //문화콘텐츠 좋아요 등록
    ContentResDTO.ContentLikeRes likeContent(Long contentId, Long userId);

    //문화콘텐츠 좋아요 취소
    ContentResDTO.ContentLikeRes unlikeContent(Long contentId, Long userId);
}
