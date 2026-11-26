package com.yeogido.backend.domain.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.dto.UserReqDTO;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileImageTest {
    @Mock UserRepository userRepository;
    @Mock RegionRepository regionRepository;
    @Mock FileService fileService;
    @Mock S3Service s3Service;
    @InjectMocks UserServiceImpl userService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).nickname("기존이름").birthYear("2000")
                .region(Region.builder().id(1L).name("서울").build())
                .profileImage("profiles/old.png").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void blankImageRemovesImageAndGetReturnsNull(String image) {
        when(s3Service.getImageUrl(null)).thenCallRealMethod();

        var response = userService.updateMyProfile(1L, new UserReqDTO.UpdateProfile(null, null, null, image));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(user.getProfileImage()).isNull();
        assertThat(userService.getMyPage(1L).profileImageUrl()).isNull();
        verifyNoInteractions(fileService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"profileImageUrl\":null}"})
    void omittedOrNullImagePreservesImage(String json) throws Exception {
        var request = objectMapper.readValue(json, UserReqDTO.UpdateProfile.class);

        userService.updateMyProfile(1L, request);

        assertThat(user.getProfileImage()).isEqualTo("profiles/old.png");
        verifyNoInteractions(fileService);
    }

    @Test
    void objectKeyStillMovesAndReplacesImage() {
        when(fileService.moveToDirectory("temp/new.png", ImageDirectory.PROFILE))
                .thenReturn("profiles/new.png");

        userService.updateMyProfile(1L, new UserReqDTO.UpdateProfile(null, null, null, "temp/new.png"));

        assertThat(user.getProfileImage()).isEqualTo("profiles/new.png");
        verify(fileService).moveToDirectory("temp/new.png", ImageDirectory.PROFILE);
    }

    @Test
    void imageRemovalCanBeCombinedWithOtherProfileChanges() {
        Region region = Region.builder().id(2L).name("부산").build();
        when(regionRepository.findById(2L)).thenReturn(Optional.of(region));

        userService.updateMyProfile(1L, new UserReqDTO.UpdateProfile("새로운이름", "2001", 2L, ""));

        assertThat(user.getProfileImage()).isNull();
        assertThat(user.getNickname()).isEqualTo("새로운이름");
        assertThat(user.getBirthYear()).isEqualTo("2001");
        assertThat(user.getRegion()).isSameAs(region);
        verifyNoInteractions(fileService);
    }

    @Test
    void repeatedRemovalOfDefaultImageSucceeds() {
        var request = new UserReqDTO.UpdateProfile(null, null, null, "");

        userService.updateMyProfile(1L, request);
        userService.updateMyProfile(1L, request);

        assertThat(user.getProfileImage()).isNull();
        verifyNoInteractions(fileService);
    }
}
