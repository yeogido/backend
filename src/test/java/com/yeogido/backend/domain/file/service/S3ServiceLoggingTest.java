package com.yeogido.backend.domain.file.service;

import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.exception.FileErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class S3ServiceLoggingTest {

    @Mock private S3Client client;
    @Mock private S3Presigner presigner;
    private S3Service service;
    private static final String KEY = "temp/fake-private-business-number.jpg";

    @BeforeEach
    void setUp() {
        service = new S3Service(client, presigner);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
    }

    @Test
    void moveFailureKeepsStatusAndOriginalErrorCodeWithoutKeyOrException(CapturedOutput output) {
        var failure = S3Exception.builder().statusCode(403)
                .message("Authorization: fake-access-token")
                .cause(new RuntimeException("X-Amz-Signature=fake-signature")).build();
        failure.addSuppressed(new RuntimeException("fake-refresh-token"));
        when(client.copyObject(any(CopyObjectRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.moveToDirectory(KEY, ImageDirectory.COURSE))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR))
                .hasNoCause();

        assertThat(output.getAll()).contains("provider=S3", "operation=move", "httpStatus=403", "failureType=http_error")
                .doesNotContain(KEY, "fake-private-business-number", "fake-access-token", "fake-signature", "fake-refresh-token");
    }

    @Test
    void missingKeyKeepsBusinessFailureWithoutPrintingUserSuppliedKey(CapturedOutput output) {
        when(client.copyObject(any(CopyObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message(KEY).build());

        assertThatThrownBy(() -> service.moveToDirectory(KEY, ImageDirectory.COURSE))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FileErrorCode.INVALID_IMAGE_KEY));

        assertThat(output.getAll()).contains("provider=S3", "operation=move", "failureType=missing_key", "httpStatus=404")
                .doesNotContain("fake-private-business-number");
    }

    @Test
    void deleteClientFailureRemainsSwallowedWithoutLoggingSecrets(CapturedOutput output) {
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("https://example.com?X-Amz-Signature=fake-signature",
                        new RuntimeException("Cookie: fake-cookie")));

        assertThatCode(() -> service.deleteImage(KEY)).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("provider=S3", "operation=delete", "failureType=client_error")
                .doesNotContain("fake-private-business-number", "fake-signature", "fake-cookie");
    }
}
