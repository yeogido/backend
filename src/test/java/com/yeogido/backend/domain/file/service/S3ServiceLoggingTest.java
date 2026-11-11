package com.yeogido.backend.domain.file.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.Map;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
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
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class S3ServiceLoggingTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(S3Service.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
        logs.stop();
        MDC.clear();
    }

    private Map<String, Object> loggedFields() {
        return loggedFields("aws_s3");
    }

    private Map<String, Object> loggedFields(String provider) {
        assertThat(logs.list).hasSize(1);
        var event = logs.list.getFirst();
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "request-test");
        var fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields).containsEntry("event", "external_api.call.failed")
                .containsEntry("provider", provider);
        assertThat(event.getFormattedMessage() + fields).doesNotContain("secret-", "private-", "fake-", "https://");
        return fields;
    }


    @Mock private S3Client client;
    @Mock private S3Presigner presigner;
    private S3Service service;
    private static final String KEY = "temp/fake-private-business-number.jpg";

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        MDC.put("requestId", "request-test");
        service = new S3Service(client, presigner);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
    }

    @Test
    void sdkTimeoutIsRetryableAndKeepsIgnoredDeleteBehavior() {
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(ApiCallTimeoutException.create(1000));

        assertThatCode(() -> service.deleteImage(KEY)).doesNotThrowAnyException();

        assertThat(loggedFields()).containsEntry("operation", "delete")
                .containsEntry("failureType", "timeout").containsEntry("retryable", true)
                .containsEntry("outcome", "ignored").doesNotContainKey("upstreamHttpStatus");
    }

    @Test
    void imageUploadFailureKeepsNullFallbackWithOneSafeS3Event() throws Exception {
        HttpServer imageServer = imageServer(200);
        try {
            when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(S3Exception.builder().statusCode(503).message("fake-signature fake-user-email").build());

            assertThat(service.uploadImageFromUrl(imageUrl(imageServer), ImageDirectory.COURSE)).isNull();

            assertThat(loggedFields()).containsEntry("operation", "put_object")
                    .containsEntry("failureType", "provider_server_error")
                    .containsEntry("upstreamHttpStatus", 503).containsEntry("outcome", "ignored")
                    .containsEntry("retryable", true).doesNotContainKey("errorCode");
        } finally {
            imageServer.stop(0);
        }
    }

    @Test
    void imageDownloadHttpFailureKeepsExistingExceptionAndSeparatesProvider() throws Exception {
        HttpServer imageServer = imageServer(503);
        try {
            assertThatThrownBy(() -> service.uploadImageFromUrl(imageUrl(imageServer), ImageDirectory.COURSE))
                    .isInstanceOfSatisfying(GeneralException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR));

            assertThat(loggedFields("external_image")).containsEntry("operation", "download_image")
                    .containsEntry("upstreamHttpStatus", 503).containsEntry("outcome", "failed")
                    .containsEntry("errorCode", GeneralErrorCode.INTERNAL_SERVER_ERROR.getCode());
        } finally {
            imageServer.stop(0);
        }
    }

    private HttpServer imageServer(int status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image.jpg", exchange -> {
            byte[] image = new byte[]{1, 2, 3};
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(status, image.length);
            try (var body = exchange.getResponseBody()) {
                body.write(image);
            }
        });
        server.start();
        return server;
    }

    private String imageUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/image.jpg?token=fake-signature";
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

        assertThat(loggedFields()).containsEntry("operation", "move").containsEntry("upstreamHttpStatus", 403)
                .containsEntry("failureType", "permission_or_access_failure").containsEntry("outcome", "failed")
                .containsEntry("retryable", false);
        assertThat(output.getAll()).doesNotContain(KEY, "fake-private-business-number", "fake-access-token", "fake-signature", "fake-refresh-token");
    }

    @Test
    void missingKeyKeepsBusinessFailureWithoutPrintingUserSuppliedKey(CapturedOutput output) {
        when(client.copyObject(any(CopyObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message(KEY).build());

        assertThatThrownBy(() -> service.moveToDirectory(KEY, ImageDirectory.COURSE))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FileErrorCode.INVALID_IMAGE_KEY));

        assertThat(loggedFields()).containsEntry("operation", "move").containsEntry("failureType", "missing_key")
                .containsEntry("upstreamHttpStatus", 404).containsEntry("errorCode", FileErrorCode.INVALID_IMAGE_KEY.getCode());
        assertThat(output.getAll()).doesNotContain("fake-private-business-number");
    }

    @Test
    void deleteClientFailureRemainsSwallowedWithoutLoggingSecrets(CapturedOutput output) {
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("https://example.com?X-Amz-Signature=fake-signature",
                        new RuntimeException("Cookie: fake-cookie")));

        assertThatCode(() -> service.deleteImage(KEY)).doesNotThrowAnyException();

        assertThat(loggedFields()).containsEntry("operation", "delete").containsEntry("failureType", "client_error")
                .containsEntry("outcome", "ignored").doesNotContainKeys("errorCode", "upstreamHttpStatus");
        assertThat(output.getAll()).doesNotContain("fake-private-business-number", "fake-signature", "fake-cookie");
    }
}
