package com.yeogido.backend.domain.sample.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SampleQueryRepositoryImpl implements SampleQueryRepository {

    private final JPAQueryFactory queryFactory;

}