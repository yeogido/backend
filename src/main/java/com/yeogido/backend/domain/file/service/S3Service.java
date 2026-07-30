package com.yeogido.backend.domain.file.service;

import com.yeogido.backend.domain.file.dto.response.FileResDTO;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final Duration PRESIGNED_URL_DURATION = Duration.ofMinutes(10);
    private static final String TEMP_DIRECTORY = "temp";
    private static final String TEMP_PREFIX = TEMP_DIRECTORY + "/";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

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

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return FileResDTO.PresignedUrlRes.builder()
                .uploadUrl(presignedRequest.url().toString())
                .objectKey(objectKey)
                .build();
    }

    public String getImageUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }

        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + objectKey;
    }

    public String moveToDirectory(String tempKey, ImageDirectory directory) {
        if (tempKey == null || tempKey.isBlank() || !tempKey.startsWith(TEMP_PREFIX)) {
            return tempKey;
        }

        String objectKey = createObjectKey(directory, tempKey);

        try {
            copyObject(tempKey, objectKey);
            deleteObject(tempKey);
        } catch (AwsServiceException | SdkClientException e) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
        }

        return objectKey;
    }

    private String createObjectKey(String fileName) {
        return TEMP_PREFIX + UUID.randomUUID() + "." + extractExtension(fileName);
    }

    private String createObjectKey(ImageDirectory directory, String tempKey) {
        return directory.getPath() + "/" + UUID.randomUUID() + "." + extractExtension(tempKey);
    }

    private void copyObject(String sourceKey, String destinationKey) {
        CopyObjectRequest copyObjectRequest = CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceKey)
                .destinationBucket(bucket)
                .destinationKey(destinationKey)
                .build();

        s3Client.copyObject(copyObjectRequest);
    }

    private void deleteObject(String objectKey) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    private String extractExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf(".");

        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
        }

        return fileName.substring(extensionIndex + 1);
    }
}
