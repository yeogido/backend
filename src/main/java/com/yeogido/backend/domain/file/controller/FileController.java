package com.yeogido.backend.domain.file.controller;

import com.yeogido.backend.domain.file.dto.request.FileReqDTO;
import com.yeogido.backend.domain.file.dto.response.FileResDTO;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "File", description = "파일 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    @Operation(
            summary = "Presigned URL 발급",
            description = "이미지 업로드를 위한 Presigned URL을 발급합니다."
    )
    @PostMapping("/presigned-url")
    public ApiResponse<FileResDTO.PresignedUrlRes> createPresignedUrl(
            @Valid @RequestBody FileReqDTO.PresignedUrlReq request
    ) {
        FileResDTO.PresignedUrlRes response = fileService.createPresignedUrl(request);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}
