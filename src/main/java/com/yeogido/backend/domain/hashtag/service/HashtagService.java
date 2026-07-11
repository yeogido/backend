package com.yeogido.backend.domain.hashtag.service;

import com.yeogido.backend.domain.hashtag.dto.response.HashtagResDTO;

import java.util.List;

public interface HashtagService {

    List<HashtagResDTO.HashtagRes> getHashtags();
}
