package com.yeogido.backend.domain.file.service;

import com.yeogido.backend.domain.file.dto.request.FileReqDTO;
import com.yeogido.backend.domain.file.dto.response.FileResDTO;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    private static final String MOCK_S3_BASE_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com";

    @Override
    public FileResDTO.PresignedUrlRes createPresignedUrl(FileReqDTO.PresignedUrlReq request) {
        // TODO: Presigned URL 생성 로직 구현
        String imageKey = createMockObjectKey(request.directory(), request.fileName());
        String uploadUrl = MOCK_S3_BASE_URL + "/" + imageKey;

        return FileResDTO.PresignedUrlRes.builder()
                .uploadUrl(uploadUrl)
                .objectKey(imageKey)
                .build();
    }

    private String createMockObjectKey(String directory, String fileName) {
        return directory + "/" + UUID.randomUUID() + "-" + fileName;
    }
}
