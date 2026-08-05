package com.yeogido.backend.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsApiResponseWhenApiPathDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/regions/search/not-exists"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(NoResourceFoundException.class))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(GeneralErrorCode.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(GeneralErrorCode.RESOURCE_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void returnsBadRequestWhenPathVariableTypeDoesNotMatch() throws Exception {
        mockMvc.perform(get("/api/v1/courses/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(GeneralErrorCode.INVALID_PARAMETER.getCode()))
                .andExpect(jsonPath("$.message").value(GeneralErrorCode.INVALID_PARAMETER.getMessage()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void returnsBadRequestWhenRequiredRequestParamIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(GeneralErrorCode.REQUIRED_FIELD_MISSING.getCode()))
                .andExpect(jsonPath("$.message").value(GeneralErrorCode.REQUIRED_FIELD_MISSING.getMessage()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }
}
