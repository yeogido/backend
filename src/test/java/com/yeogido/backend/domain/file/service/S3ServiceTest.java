package com.yeogido.backend.domain.file.service;

import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Client, s3Presigner);
        ReflectionTestUtils.setField(s3Service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "region", "ap-northeast-2");
    }

    @Test
    void moveToDirectoryCopiesTempObjectAndDeletesSource() {
        String tempKey = "temp/source-image.jpg";

        String objectKey = s3Service.moveToDirectory(tempKey, ImageDirectory.COURSE);

        assertThat(objectKey).matches("courses/[0-9a-f-]{36}\\.jpg");

        ArgumentCaptor<CopyObjectRequest> copyCaptor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);

        InOrder inOrder = inOrder(s3Client);
        inOrder.verify(s3Client).copyObject(copyCaptor.capture());
        inOrder.verify(s3Client).deleteObject(deleteCaptor.capture());

        CopyObjectRequest copyRequest = copyCaptor.getValue();
        assertThat(copyRequest.sourceBucket()).isEqualTo("test-bucket");
        assertThat(copyRequest.sourceKey()).isEqualTo(tempKey);
        assertThat(copyRequest.destinationBucket()).isEqualTo("test-bucket");
        assertThat(copyRequest.destinationKey()).isEqualTo(objectKey);

        DeleteObjectRequest deleteRequest = deleteCaptor.getValue();
        assertThat(deleteRequest.bucket()).isEqualTo("test-bucket");
        assertThat(deleteRequest.key()).isEqualTo(tempKey);
    }

    @Test
    void moveToDirectoryUsesExtensionFromTempKey() {
        String objectKey = s3Service.moveToDirectory("temp/archive.photo.png", ImageDirectory.TRAVEL_RECORD);

        assertThat(objectKey).matches("travel-records/[0-9a-f-]{36}\\.png");
    }

    @Test
    void moveToDirectoryRejectsNonTempKey() {
        assertThatThrownBy(() -> s3Service.moveToDirectory("courses/image.jpg", ImageDirectory.COURSE))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GeneralErrorCode.INVALID_REQUEST);

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void moveToDirectoryRejectsTempKeyWithoutExtension() {
        assertThatThrownBy(() -> s3Service.moveToDirectory("temp/image", ImageDirectory.COURSE))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GeneralErrorCode.INVALID_REQUEST);

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void moveToDirectoryWrapsCopyFailure() {
        when(s3Client.copyObject(any(CopyObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("copy failed").build());

        assertThatThrownBy(() -> s3Service.moveToDirectory("temp/image.jpg", ImageDirectory.COURSE))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void moveToDirectoryWrapsDeleteFailure() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("delete failed").build());

        assertThatThrownBy(() -> s3Service.moveToDirectory("temp/image.jpg", ImageDirectory.COURSE))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
}
