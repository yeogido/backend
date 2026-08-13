package com.yeogido.backend.domain.hashtag.service;

import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.hashtag.dto.response.HashtagResDTO;
import com.yeogido.backend.domain.hashtag.repository.HashtagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HashtagServiceImpl implements HashtagService {

    private final HashtagRepository hashtagRepository;

    @Override
    public List<HashtagResDTO.HashtagRes> getHashtags() {
        return hashtagRepository.findAllByDisplayOrder().stream()
                .map(this::toHashtagRes)
                .toList();
    }

    private HashtagResDTO.HashtagRes toHashtagRes(Hashtag hashtag) {
        return HashtagResDTO.HashtagRes.builder()
                .id(hashtag.getId())
                .name(hashtag.getHashtagName())
                .build();
    }
}
