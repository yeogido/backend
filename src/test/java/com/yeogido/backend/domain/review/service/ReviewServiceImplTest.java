package com.yeogido.backend.domain.review.service;

import com.yeogido.backend.domain.course.entity.CourseReview;
import com.yeogido.backend.domain.course.entity.CourseReviewImage;
import com.yeogido.backend.domain.course.repository.CourseLikeRepository;
import com.yeogido.backend.domain.course.repository.CourseReviewImageRepository;
import com.yeogido.backend.domain.course.repository.CourseReviewRepository;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.review.dto.request.ReviewReqDTO;
import com.yeogido.backend.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private CourseReviewRepository courseReviewRepository;

    @Mock
    private CourseReviewImageRepository courseReviewImageRepository;

    @Mock
    private CourseLikeRepository courseLikeRepository;

    @Mock
    private FileService fileService;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void updateReview_WhenImagesRequested_DeletesAndFlushesBeforeSavingImages() {
        CourseReview review = createReview();
        ReviewReqDTO.UpdateRequest request = new ReviewReqDTO.UpdateRequest(
                5,
                "수정된 리뷰입니다.",
                List.of(new ReviewReqDTO.ReviewImageRequest("courses/existing.jpg", 1))
        );

        when(courseReviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(fileService.moveToDirectory(anyString(), any())).thenReturn("courses/existing.jpg");

        reviewService.updateReview(1L, 1L, request);

        InOrder inOrder = inOrder(courseReviewImageRepository);
        inOrder.verify(courseReviewImageRepository).deleteAllByCourseReview_Id(1L);
        inOrder.verify(courseReviewImageRepository).flush();
        inOrder.verify(courseReviewImageRepository).saveAll(anyList());
    }

    @Test
    void updateReview_WhenImagesEmpty_DeletesAndFlushesWithoutSavingImages() {
        CourseReview review = createReview();
        ReviewReqDTO.UpdateRequest request = new ReviewReqDTO.UpdateRequest(
                4,
                "이미지 전체 삭제",
                List.of()
        );

        when(courseReviewRepository.findById(1L)).thenReturn(Optional.of(review));

        reviewService.updateReview(1L, 1L, request);

        InOrder inOrder = inOrder(courseReviewImageRepository);
        inOrder.verify(courseReviewImageRepository).deleteAllByCourseReview_Id(1L);
        inOrder.verify(courseReviewImageRepository).flush();
        verify(courseReviewImageRepository, never()).saveAll(anyList());
    }

    @Test
    void updateReview_WhenImagesNull_KeepsExistingImages() {
        CourseReview review = createReview();
        ReviewReqDTO.UpdateRequest request = new ReviewReqDTO.UpdateRequest(
                4,
                "이미지 유지",
                null
        );

        when(courseReviewRepository.findById(1L)).thenReturn(Optional.of(review));

        reviewService.updateReview(1L, 1L, request);

        verify(courseReviewImageRepository, never()).deleteAllByCourseReview_Id(1L);
        verify(courseReviewImageRepository, never()).flush();
        verify(courseReviewImageRepository, never()).saveAll(anyList());
    }

    @Test
    void updateReview_WhenExistingImageKeyRequested_SavesSameImageKey() {
        CourseReview review = createReview();
        ReviewReqDTO.UpdateRequest request = new ReviewReqDTO.UpdateRequest(
                5,
                "기존 이미지 유지",
                List.of(new ReviewReqDTO.ReviewImageRequest("courses/existing.jpg", 1))
        );

        when(courseReviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(fileService.moveToDirectory(eq("courses/existing.jpg"), any()))
                .thenReturn("courses/existing.jpg");

        reviewService.updateReview(1L, 1L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CourseReviewImage>> imageCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(courseReviewImageRepository).saveAll(imageCaptor.capture());

        assertThat(imageCaptor.getValue()).hasSize(1);
        assertThat(imageCaptor.getValue().get(0).getImageKey()).isEqualTo("courses/existing.jpg");
        assertThat(imageCaptor.getValue().get(0).getImageOrder()).isOne();
    }

    private CourseReview createReview() {
        User user = User.builder()
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        CourseReview review = CourseReview.builder()
                .user(user)
                .rating(3)
                .content("기존 리뷰입니다.")
                .build();
        ReflectionTestUtils.setField(review, "id", 1L);

        return review;
    }
}
