package com.yeogido.backend.global.common.response;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yeogido.backend.global.common.code.SuccessCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiResponseStatusAdviceTest {

    @Test
    void beforeBodyWriteAppliesSuccessCodeHttpStatus() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new ApiResponseStatusAdvice())
                .build();

        mockMvc.perform(post("/created"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON201"))
                .andExpect(jsonPath("$.message").value("생성에 성공했습니다."))
                .andExpect(jsonPath("$.result").value("created"))
                .andExpect(jsonPath("$.httpStatus").doesNotExist());
    }

    @Test
    void okSuccessCodeReturnsOkStatus() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new ApiResponseStatusAdvice())
                .build();

        mockMvc.perform(get("/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON200"));
    }

    @RestController
    private static class TestController {

        @PostMapping("/created")
        ApiResponse<String> created() {
            return ApiResponse.onSuccess(SuccessCode.CREATED, "created");
        }

        @GetMapping("/ok")
        ApiResponse<String> ok() {
            return ApiResponse.onSuccess(SuccessCode.OK, "ok");
        }
    }
}
