package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContentRepository extends JpaRepository<Content,Long> {

    List<Content> findTop5ByEndDateGreaterThanEqualOrderByEndDateAsc(LocalDate today);

}
