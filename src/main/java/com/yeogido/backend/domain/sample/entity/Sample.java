package com.yeogido.backend.domain.sample.entity;

import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sample")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Sample extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sample_id")
    private Long id;

    @Column(name = "message", nullable = false, length = 100)
    private String message;

}