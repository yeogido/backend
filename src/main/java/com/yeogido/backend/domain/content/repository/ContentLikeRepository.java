package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentLike;
import com.yeogido.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContentLikeRepository extends JpaRepository<ContentLike,Long> {

    long countByContent(Content content);

    boolean existsByContentAndUser(Content content, User user);

    boolean existsByUserAndContent(User user, Content content);

    Optional<ContentLike> findByUserAndContent(User user, Content content);

    void deleteByContent(Content content);


}
