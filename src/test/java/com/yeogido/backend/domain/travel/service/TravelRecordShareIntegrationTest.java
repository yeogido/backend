package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.entity.*;
import com.yeogido.backend.domain.travel.enums.*;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.*;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:travel-share;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TravelRecordShareIntegrationTest {
    @Autowired private TravelRecordShareService service;
    @Autowired private TravelRecordService recordService;
    @Autowired private EntityManager em;
    @Autowired private MockMvc mvc;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;
    private Region region;
    private User owner;
    private User visitor;
    private TravelRecord album;

    @BeforeEach
    void setUp() {
        region = Region.builder().name("공유 지역").type(RegionType.REGION).build();
        em.persist(region);
        owner = user("생성자");
        visitor = user("링크 방문자");
        album = TravelRecord.builder().user(owner).region(region).title("공유 여행")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 2))
                .coverImageKey("travel-records/1.jpg").folderTheme(FolderTheme.BASIC)
                .visibility(TravelRecordVisibility.FRIENDS).build();
        em.persist(album);
        for (int i = 1; i <= 5; i++) {
            em.persist(TravelRecordPhoto.builder().travelRecord(album).imageKey("travel-records/" + i + ".jpg").imageOrder(i).build());
        }
        when(s3Service.getImageUrl(anyString())).thenAnswer(c -> "https://example.com/" + c.getArgument(0));
        em.flush();
    }

    @Test
    void nonFriendCanPreviewAcceptAndReadButCannotDelete() throws Exception {
        String token = service.createLink(album.getId(), owner.getId()).token();
        assertThat(service.createLink(album.getId(), owner.getId()).token()).isEqualTo(token);
        mvc.perform(get(path(token)).with(auth(visitor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.canAccept").value(true))
                .andExpect(jsonPath("$.result.preview.previewImages.length()").value(5))
                .andExpect(jsonPath("$.result.preview.previewImages[4].imageOrder").value(5))
                .andExpect(jsonPath("$.result.preview.previewImages[4].imageUrl").value("https://example.com/travel-records/5.jpg"))
                .andExpect(jsonPath("$.result.preview.regionName").value("공유 지역"))
                .andExpect(jsonPath("$.result.preview.photoCount").value(5))
                .andExpect(jsonPath("$.result.detail").isEmpty());
        mvc.perform(get("/api/v1/travel-records/" + album.getId()).with(auth(visitor)))
                .andExpect(status().isForbidden());
        respond(token, "ACCEPTED");
        em.clear();
        Long version = em.find(TravelRecord.class, album.getId()).getVersion();
        respond(token, "ACCEPTED");
        em.clear();
        assertThat(em.find(TravelRecord.class, album.getId()).getVersion()).isEqualTo(version);
        assertThat(em.createQuery("select count(m) from TravelRecordMember m where m.travelRecord.id = :id", Long.class)
                .setParameter("id", album.getId()).getSingleResult()).isEqualTo(1);
        mvc.perform(get(path(token)).with(auth(visitor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.canAccept").value(false))
                .andExpect(jsonPath("$.result.detail.images.length()").value(5))
                .andExpect(jsonPath("$.result.detail.permissions.canEdit").value(true))
                .andExpect(jsonPath("$.result.detail.permissions.canDelete").value(false));
        assertThat(service.createLink(album.getId(), visitor.getId()).token()).isEqualTo(token);
    }

    @Test
    void publicViewerCanReadAndShareButCannotJoinOrEdit() throws Exception {
        album.updateVisibility(TravelRecordVisibility.PUBLIC);
        em.flush();
        String token = service.createLink(album.getId(), visitor.getId()).token();
        mvc.perform(get(path(token)).with(auth(visitor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.canAccept").value(false))
                .andExpect(jsonPath("$.result.detail.images.length()").value(5))
                .andExpect(jsonPath("$.result.detail.permissions.canEdit").value(false));
        mvc.perform(patch(path(token)).with(auth(visitor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}")).andExpect(status().isConflict());
    }

    @Test
    void declineDoesNotCreateMembershipAndLaterAcceptanceIsAllowed() throws Exception {
        String token = service.createLink(album.getId(), owner.getId()).token();
        respond(token, "DECLINED");
        assertThat(service.getSharedRecord(token, visitor.getId()).canAccept()).isTrue();
        respond(token, "ACCEPTED");
        mvc.perform(patch(path(token)).with(auth(visitor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DECLINED\"}")).andExpect(status().isConflict());
    }

    @Test
    void existingPendingInvitationDisappearsAfterLinkResponseAndRetryIsSafe() throws Exception {
        Friendship friend = Friendship.request(owner, visitor);
        friend.accept();
        em.persist(friend);
        var invitation = TravelRecordInvitation.builder().travelRecord(album).inviter(owner).invitee(visitor).friendship(friend).build();
        em.persist(invitation);
        em.flush();
        String token = service.createLink(album.getId(), owner.getId()).token();
        respond(token, "ACCEPTED");
        em.clear();
        assertThat(em.find(TravelRecordInvitation.class, invitation.getId()).getStatus()).isEqualTo(TravelRecordInvitationStatus.ACCEPTED);
        respond(token, "ACCEPTED");
    }

    @Test
    void visibilityChangeInvalidatesOldTokenEvenAfterReturningToPreviousVisibility() throws Exception {
        String token = service.createLink(album.getId(), owner.getId()).token();
        album.updateVisibility(TravelRecordVisibility.PUBLIC);
        em.flush();
        mvc.perform(get(path(token)).with(auth(visitor))).andExpect(status().isNotFound());
        album.updateVisibility(TravelRecordVisibility.FRIENDS);
        em.flush();
        String replacement = service.createLink(album.getId(), owner.getId()).token();
        assertThat(replacement).isNotEqualTo(token);
        mvc.perform(get(path(token)).with(auth(visitor))).andExpect(status().isNotFound());
    }

    @Test
    void lastParticipantLeavingRevokesLinkAndReturnsToPublic() throws Exception {
        String token = service.createLink(album.getId(), owner.getId()).token();
        respond(token, "ACCEPTED");
        em.clear();
        recordService.leaveTravelRecord(album.getId(), visitor.getId());
        em.flush(); em.clear();
        assertThat(em.find(TravelRecord.class, album.getId()).getVisibility()).isEqualTo(TravelRecordVisibility.PUBLIC);
        mvc.perform(get(path(token)).with(auth(visitor))).andExpect(status().isNotFound());
    }

    @Test
    void privateAndNonMemberCannotIssueAndAnonymousCannotUseLinks() throws Exception {
        mvc.perform(post("/api/v1/travel-records/" + album.getId() + "/share-link").with(auth(visitor)))
                .andExpect(status().isForbidden());
        String token = service.createLink(album.getId(), owner.getId()).token();
        mvc.perform(get(path(token))).andExpect(status().isUnauthorized());
        mvc.perform(patch(path(token)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isUnauthorized());
        album.updateVisibility(TravelRecordVisibility.PRIVATE); em.flush();
        mvc.perform(post("/api/v1/travel-records/" + album.getId() + "/share-link").with(auth(owner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedFolderAndInvalidTokenAreNotFound() throws Exception {
        String token = service.createLink(album.getId(), owner.getId()).token();
        recordService.deleteTravelRecord(album.getId(), owner.getId());
        mvc.perform(get(path(token)).with(auth(visitor))).andExpect(status().isNotFound());
        mvc.perform(patch(path(token)).with(auth(visitor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}")).andExpect(status().isNotFound());
        mvc.perform(get(path("invalid")).with(auth(visitor))).andExpect(status().isNotFound());
    }

    private void respond(String token, String status) throws Exception {
        mvc.perform(patch(path(token)).with(auth(visitor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}")).andExpect(status().isOk());
    }
    private String path(String token) { return "/api/v1/travel-records/shared/" + token; }
    private User user(String nickname) {
        User user = User.builder().nickname(nickname).email(UUID.randomUUID() + "@test.com")
                .gender(Gender.FEMALE).birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build();
        em.persist(user); return user;
    }
    private RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthUser(user.getId(), UserRole.USER, "share-test"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
