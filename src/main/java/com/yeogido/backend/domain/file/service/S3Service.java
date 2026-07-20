package com.yeogido.backend.domain.file.service;

import com.yeogido.backend.domain.file.dto.response.FileResDTO;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    public FileResDTO.PresignedUrlRes createPresignedUrl(
            ImageDirectory directory,
            String fileName,
            String contentType
    ) {
        String objectKey = createObjectKey(directory, fileName);

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

    private String createObjectKey(ImageDirectory directory, String fileName) {
        return directory.getPath() + "/" + UUID.randomUUID() + "." + extractExtension(fileName);
    }

    private String extractExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf(".");

        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
        }

        return fileName.substring(extensionIndex + 1);
    }
}
