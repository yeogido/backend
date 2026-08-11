package com.yeogido.backend.domain.file.service;

import com.yeogido.backend.domain.file.dto.response.FileResDTO;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.exception.FileErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private static final Duration PRESIGNED_URL_DURATION = Duration.ofMinutes(10);
    private static final Duration EXTERNAL_IMAGE_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration EXTERNAL_IMAGE_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_EXTERNAL_IMAGE_BYTES = 10 * 1024 * 1024;

    private static final String TEMP_DIRECTORY = "temp";
    private static final String TEMP_PREFIX = TEMP_DIRECTORY + "/";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(EXTERNAL_IMAGE_CONNECT_TIMEOUT)
            .build();

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.cloudfront.domain}")
    private String cloudFrontDomain;

    public FileResDTO.PresignedUrlRes createPresignedUrl(
            String fileName,
            String contentType
    ) {
        String objectKey = createObjectKey(fileName);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_DURATION)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        return FileResDTO.PresignedUrlRes.builder()
                .uploadUrl(presignedRequest.url().toString())
                .objectKey(objectKey)
                .build();
    }

    public String getImageUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }

        if (
                objectKey.startsWith("https://")
                        || objectKey.startsWith("http://")
        ) {
            return objectKey;
        }

        return normalizeCloudFrontDomain()
                + "/"
                + normalizeObjectKey(objectKey);
    }

    public String uploadImageFromUrl(
            String imageUrl,
            ImageDirectory directory
    ) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        try {
            HttpResponse<byte[]> response = downloadImage(imageUrl);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GeneralException(
                        GeneralErrorCode.INTERNAL_SERVER_ERROR
                );
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("image/jpeg");

            if (
                    !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                            || response.body().length == 0
                            || response.body().length > MAX_EXTERNAL_IMAGE_BYTES
            ) {
                return null;
            }

            String objectKey = createObjectKey(
                    directory,
                    imageUrl,
                    contentType
            );

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromBytes(response.body())
            );

            return objectKey;
        } catch (
                IllegalArgumentException
                | IOException
                | InterruptedException
                | AwsServiceException
                | SdkClientException e
        ) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return null;
        }
    }

    public String moveToDirectory(
            String tempKey,
            ImageDirectory directory
    ) {
        if (
                tempKey == null
                        || tempKey.isBlank()
                        || !tempKey.startsWith(TEMP_PREFIX)
        ) {
            return tempKey;
        }

        String objectKey = createObjectKey(directory, tempKey);

        try {
            copyObject(tempKey, objectKey);
            deleteObject(tempKey);
        } catch (AwsServiceException | SdkClientException e) {
            if (isMissingS3Key(e)) {
                log.warn(
                        "Invalid S3 image key requested. sourceKey={}, destinationKey={}",
                        tempKey,
                        objectKey
                );

                throw new GeneralException(FileErrorCode.INVALID_IMAGE_KEY);
            }

            log.error(
                    "Failed to move S3 image. sourceKey={}, destinationKey={}",
                    tempKey,
                    objectKey,
                    e
            );

            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        return objectKey;
    }

    private String createObjectKey(String fileName) {
        return TEMP_PREFIX
                + UUID.randomUUID()
                + "."
                + extractExtension(fileName);
    }

    private String normalizeCloudFrontDomain() {
        String domain = cloudFrontDomain.trim();

        String url = domain.startsWith("http://")
                || domain.startsWith("https://")
                ? domain
                : "https://" + domain;

        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

    private String normalizeObjectKey(String objectKey) {
        String normalizedObjectKey = objectKey.trim();

        while (normalizedObjectKey.startsWith("/")) {
            normalizedObjectKey =
                    normalizedObjectKey.substring(1);
        }

        return normalizedObjectKey;
    }

    private String createObjectKey(
            ImageDirectory directory,
            String tempKey
    ) {
        return directory.getPath()
                + "/"
                + UUID.randomUUID()
                + "."
                + extractExtension(tempKey);
    }

    private String createObjectKey(
            ImageDirectory directory,
            String imageUrl,
            String contentType
    ) {
        return directory.getPath()
                + "/"
                + UUID.randomUUID()
                + "."
                + extractExtension(imageUrl, contentType);
    }

    private HttpResponse<byte[]> downloadImage(
            String imageUrl
    ) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(imageUrl)
                )
                .timeout(EXTERNAL_IMAGE_REQUEST_TIMEOUT)
                .GET()
                .build();

        return HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private void copyObject(
            String sourceKey,
            String destinationKey
    ) {
        CopyObjectRequest copyObjectRequest =
                CopyObjectRequest.builder()
                        .sourceBucket(bucket)
                        .sourceKey(sourceKey)
                        .destinationBucket(bucket)
                        .destinationKey(destinationKey)
                        .build();

        s3Client.copyObject(copyObjectRequest);
    }

    private void deleteObject(String objectKey) {
        DeleteObjectRequest deleteObjectRequest =
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    private boolean isMissingS3Key(Exception e) {
        return e instanceof NoSuchKeyException
                || (e instanceof S3Exception s3Exception
                && s3Exception.statusCode() == 404);
    }

    private String extractExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf(".");

        if (
                extensionIndex < 0
                        || extensionIndex == fileName.length() - 1
        ) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }

        return fileName.substring(extensionIndex + 1);
    }

    private String extractExtension(
            String imageUrl,
            String contentType
    ) {
        String extension = extractExtensionFromUrl(imageUrl);

        if (extension != null) {
            return extension;
        }

        return switch (
                contentType
                        .toLowerCase(Locale.ROOT)
                        .split(";")[0]
                        .trim()
                ) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    private String extractExtensionFromUrl(String imageUrl) {
        String path = URI.create(imageUrl).getPath();
        int extensionIndex = path.lastIndexOf(".");

        if (
                extensionIndex < 0
                        || extensionIndex == path.length() - 1
        ) {
            return null;
        }

        String extension = path.substring(extensionIndex + 1)
                .toLowerCase(Locale.ROOT);

        if (
                extension.equals("jpg")
                        || extension.equals("jpeg")
                        || extension.equals("png")
                        || extension.equals("webp")
                        || extension.equals("gif")
        ) {
            return extension.equals("jpeg")
                    ? "jpg"
                    : extension;
        }

        return null;
    }
}
