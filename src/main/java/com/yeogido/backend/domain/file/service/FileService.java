package com.yeogido.backend.domain.file.service;

import com.yeogido.backend.domain.file.dto.request.FileReqDTO;
import com.yeogido.backend.domain.file.dto.response.FileResDTO;

public interface FileService {

    FileResDTO.PresignedUrlRes createPresignedUrl(FileReqDTO.PresignedUrlReq request);
}
