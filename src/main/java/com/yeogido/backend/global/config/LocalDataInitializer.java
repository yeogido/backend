package com.yeogido.backend.global.config;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Profile("local")
@Component
@RequiredArgsConstructor
public class LocalDataInitializer implements CommandLineRunner {

    public static final String TEST_USER_EMAIL = "local-test-user@yeogido.com";

    private static final String TEST_REGION_NAME = "서울";
    private static final String TEST_REGION_FULL_NAME = "서울특별시";

    private final RegionRepository regionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Region region = regionRepository.findByName(TEST_REGION_NAME)
                .orElseGet(() -> regionRepository.save(Region.builder()
                        .name(TEST_REGION_NAME)
                        .fullName(TEST_REGION_FULL_NAME)
                        .type(RegionType.REGION)
                        .build()));

        userRepository.findByEmail(TEST_USER_EMAIL)
                .orElseGet(() -> userRepository.save(User.builder()
                        .nickname("로컬테스트유저")
                        .email(TEST_USER_EMAIL)
                        .password(null)
                        .gender(Gender.MALE)
                        .birthYear("2000")
                        .region(region)
                        .role(UserRole.USER)
                        .status(UserStatus.ACTIVE)
                        .profileImage(null)
                        .build()));
    }
}
