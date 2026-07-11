package com.yeogido.backend.domain.hashtag.service;

import com.yeogido.backend.domain.hashtag.dto.response.HashtagResDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HashtagServiceImpl implements HashtagService {

    @Override
    public List<HashtagResDTO.HashtagRes> getHashtags() {
        // TODO: 해시태그 목록 조회 로직 구현
        return List.of(
                HashtagResDTO.HashtagRes.builder()
                        .id(1L)
                        .name("자연")
                        .build(),
                HashtagResDTO.HashtagRes.builder()
                        .id(2L)
                        .name("바다")
                        .build(),
                HashtagResDTO.HashtagRes.builder()
                        .id(3L)
                        .name("맛집")
                        .build(),
                HashtagResDTO.HashtagRes.builder()
                        .id(4L)
                        .name("카페")
                        .build(),
                HashtagResDTO.HashtagRes.builder()
                        .id(5L)
                        .name("드라이브")
                        .build(),
                HashtagResDTO.HashtagRes.builder()
                        .id(6L)
                        .name("축제")
                        .build()
        );
    }
}
