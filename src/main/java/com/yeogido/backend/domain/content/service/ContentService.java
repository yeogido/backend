package com.yeogido.backend.domain.content.service;


import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentService {
    public ContentResDTO.ContentListRes getContents(ContentReqDTO.ContentListReq request){
        return new ContentResDTO.ContentListRes(
                List.of(),
                null,
                false
        );
    }

    public ContentResDTO.ContentDetailRes getContentDetail(Long contentId){
        return new ContentResDTO.ContentDetailRes(
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

    }

    public ContentResDTO.ContentCreateRes createContent(ContentReqDTO.ContentCreateReq request){
        return new ContentResDTO.ContentCreateRes(null);
    }

    public ContentResDTO.ContentUpdateRes updateContent(Long contentId, ContentReqDTO.ContentCreateReq request){
        return new ContentResDTO.ContentUpdateRes(contentId);
    }

    public ContentResDTO.ContentLikeRes likeContent(Long contentId){
        return new ContentResDTO.ContentLikeRes(true, 0L);
    }
}
