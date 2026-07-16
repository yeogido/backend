package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.course.entity.CourseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseItemRepository extends JpaRepository<CourseItem, Long> {

    List<CourseItem> findByContentOrderByOrderNoAsc(Content content);

}
