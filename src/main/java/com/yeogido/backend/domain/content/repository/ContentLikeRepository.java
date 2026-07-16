package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentLikeRepository extends JpaRepository<ContentLike,Long> {


}
