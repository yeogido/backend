package com.yeogido.backend.domain.hashtag.controller;

import com.yeogido.backend.domain.hashtag.dto.response.HashtagResDTO;
import com.yeogido.backend.domain.hashtag.service.HashtagService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Hashtag", description = "해시태그 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/hashtags")
public class HashtagController {

    private final HashtagService hashtagService;

    @Operation(
            summary = "해시태그 목록 조회",
            description = "추천 코스, 문화 콘텐츠, 홍보글 작성 화면에서 사용하는 해시태그 목록을 조회합니다."
    )
    @GetMapping
    public ApiResponse<List<HashtagResDTO.HashtagRes>> getHashtags() {
        List<HashtagResDTO.HashtagRes> response = hashtagService.getHashtags();

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}
