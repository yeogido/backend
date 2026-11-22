package com.yeogido.backend.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yeogido.backend.domain.auth.service.RefreshTokenService;
import com.yeogido.backend.domain.business.entity.BusinessPromotion;
import com.yeogido.backend.domain.business.enums.PromotionCategory;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 사업장 목록의 "홍보 중" 판정 쿼리가 활성 홍보글이 있는 사업장만 고르는지 실제 DB(H2)에서 확인한다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:business-active-promotion;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class BusinessInfoActivePromotionQueryTest {

    private static final AtomicLong BUSINESS_NUMBER_SEQUENCE = new AtomicLong(3_000_000_000L);

    @Autowired private BusinessInfoRepository businessInfoRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private RefreshTokenService refreshTokenService;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;

    @Test
    void returnsOnlyBusinessesWhosePlaceHasActivePromotion() {
        Fixture fixture = fixture();

        Set<Long> ids = businessInfoRepository.findIdsWithActivePromotion(
                fixture.owner(), BusinessVerificationStatus.APPROVED);

        assertThat(ids).containsExactly(fixture.withActivePromotion());
    }

    @Test
    void ignoresOtherUsersBusinesses() {
        Fixture fixture = fixture();

        Set<Long> ids = businessInfoRepository.findIdsWithActivePromotion(
                fixture.otherUser(), BusinessVerificationStatus.APPROVED);

        assertThat(ids).isEmpty();
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Region region = Region.builder().name("부산").type(RegionType.REGION).build();
            entityManager.persist(region);
            User owner = user("사장님", region);
            User otherUser = user("다른 사용자", region);

            // 1. 활성 홍보글이 있는 사업장
            Place activePlace = place(region);
            Long withActivePromotion = businessInfo(owner, activePlace).getId();
            promotion(activePlace, owner, PromotionStatus.ACTIVE);

            // 2. 삭제된 홍보글만 있는 사업장
            Place deletedPlace = place(region);
            businessInfo(owner, deletedPlace);
            promotion(deletedPlace, owner, PromotionStatus.DELETED);

            // 3. 홍보글이 없는 사업장, 4. 장소가 연결되지 않은 사업장
            businessInfo(owner, place(region));
            businessInfo(owner, null);

            entityManager.flush();
            return new Fixture(owner.getId(), otherUser.getId(), withActivePromotion);
        });
    }

    private User user(String name, Region region) {
        User user = User.builder().nickname(name).email(UUID.randomUUID() + "@test.com").gender(Gender.FEMALE)
                .birthYear("2000").region(region).role(UserRole.BUSINESS).status(UserStatus.ACTIVE).build();
        entityManager.persist(user);
        return user;
    }

    private Place place(Region region) {
        Place place = Place.builder().region(region).externalPlaceId("place-" + UUID.randomUUID())
                .source(PlaceSource.KAKAO).name("가게").latitude(BigDecimal.valueOf(35.15))
                .longitude(BigDecimal.valueOf(129.11)).build();
        entityManager.persist(place);
        return place;
    }

    private BusinessInfo businessInfo(User owner, Place place) {
        BusinessInfo businessInfo = BusinessInfo.builder().user(owner).place(place)
                .businessNumber(String.valueOf(BUSINESS_NUMBER_SEQUENCE.incrementAndGet()))
                .openingDate(LocalDate.of(2020, 1, 1)).representativeName("홍길동")
                .registrationImageKey("businesses/certificate.jpg").businessName("가게")
                .businessAddress("부산 수영구").verificationStatus(BusinessVerificationStatus.APPROVED)
                .verifiedAt(LocalDateTime.now()).build();
        entityManager.persist(businessInfo);
        return businessInfo;
    }

    private void promotion(Place place, User owner, PromotionStatus status) {
        entityManager.persist(BusinessPromotion.builder().place(place).user(owner)
                .shortDescription("소개글").promotionCategory(PromotionCategory.CAFE)
                .phoneNumber("051-123-4567").status(status).build());
    }

    private record Fixture(Long owner, Long otherUser, Long withActivePromotion) {
    }
}
