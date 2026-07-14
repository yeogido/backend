package com.yeogido.backend.domain.file.service;

import com.yeogido.backend.domain.file.dto.request.FileReqDTO;
import com.yeogido.backend.domain.file.dto.response.FileResDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final S3Service s3Service;

    @Override
    public FileResDTO.PresignedUrlRes createPresignedUrl(FileReqDTO.PresignedUrlReq request) {
        return s3Service.createPresignedUrl(
                request.directory(),
                request.fileName(),
                request.contentType()
        );
    }
}
