package com.yeogido.backend.domain.placeenrichment.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.placeenrichment.repository.PlaceOverviewApiOperationRepository;
import com.yeogido.backend.domain.placeenrichment.service.PlaceOverviewApiGate;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaceOverviewApiAdminControllerTest {
    private static final String URL = "/api/v1/place-overview/api-operations/release";
    private static final String BODY = "{\"provider\":\"tour\",\"operation\":\"/detailCommon2\"}";

    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean PlaceOverviewApiOperationRepository operations;
    @MockitoBean PlaceOverviewApiGate gate;

    @Test
    void adminReleasesRegisteredOperation() throws Exception {
        when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).role(UserRole.ADMIN).build()));
        when(operations.existsById("tour:/detailCommon2")).thenReturn(true);

        mvc.perform(authenticated(BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(gate).release("tour", "/detailCommon2");
    }

    @Test
    void currentDatabaseRoleMustBeAdminEvenIfTokenClaimsAdmin() throws Exception {
        when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).role(UserRole.USER).build()));

        mvc.perform(authenticated(BODY)).andExpect(status().isForbidden());

        verifyNoInteractions(operations, gate);
    }

    @Test
    void anonymousCannotRelease() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, operations, gate);
    }

    @Test
    void unknownOperationReturnsNotFound() throws Exception {
        when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).role(UserRole.ADMIN).build()));

        mvc.perform(authenticated(BODY)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_ENRICHMENT4041"));
        verifyNoInteractions(gate);
    }

    @Test
    void blankOperationIsRejected() throws Exception {
        mvc.perform(authenticated("{\"provider\":\"tour\",\"operation\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users, operations, gate);
    }

    private MockHttpServletRequestBuilder authenticated(String body) {
        var principal = new AuthUser(1L, UserRole.ADMIN, "test-session");
        var token = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        return post(URL).with(authentication(token)).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
