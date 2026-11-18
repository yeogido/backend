package com.yeogido.backend.domain.user.repository;

import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BusinessInfoRepository extends JpaRepository<BusinessInfo, Long> {

    Optional<BusinessInfo> findByBusinessNumber(String businessNumber);

    boolean existsByPlace_Id(Long placeId);

    List<BusinessInfo>
    findAllByUser_IdAndVerificationStatusOrderByIdDesc(
            Long userId,
            BusinessVerificationStatus verificationStatus
    );

    Optional<BusinessInfo> findByIdAndUser_IdAndVerificationStatus(
            Long id,
            Long userId,
            BusinessVerificationStatus verificationStatus
    );

    /**
     * 사용자의 사업자 정보 중 연결된 장소에 활성 홍보글이 있는 것의 ID 집합.
     * 사업장 목록에 "홍보 중" 표시를 붙일 때 사업장 수와 관계없이 한 번만 조회한다.
     */
    @Query("""
        select businessInfo.id
        from BusinessInfo businessInfo
        where businessInfo.user.id = :userId
          and businessInfo.verificationStatus = :verificationStatus
          and exists (
              select 1
              from BusinessPromotion promotion
              where promotion.place = businessInfo.place
                and promotion.status = com.yeogido.backend.domain.business.enums.PromotionStatus.ACTIVE
          )
        """)
    Set<Long> findIdsWithActivePromotion(
            @Param("userId") Long userId,
            @Param("verificationStatus") BusinessVerificationStatus verificationStatus
    );
}