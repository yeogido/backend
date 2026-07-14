package com.yeogido.backend.domain.hashtag.repository;

import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    List<Hashtag> findAllByOrderByIdAsc();
}
