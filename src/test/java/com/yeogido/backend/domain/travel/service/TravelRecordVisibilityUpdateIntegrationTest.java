package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.exception.ErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:travel-visibility-update;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TravelRecordVisibilityUpdateIntegrationTest {

    @Autowired private TravelRecordService service;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mvc;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;
    private Long ownerId;
    private Long viewerId;
    private Long regionId;
    private Long albumId;

    @BeforeEach
    void setUp() {
        tx(() -> {
            Region region = Region.builder().name("공개범위 수정 지역").type(RegionType.REGION).build();
            em.persist(region);
            regionId = region.getId();
            User owner = user(region);
            User viewer = user(region);
            ownerId = owner.getId();
            viewerId = viewer.getId();
            TravelRecord album = TravelRecord.builder().user(owner).region(region).title("최초 제목")
                    .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
                    .visibility(TravelRecordVisibility.PRIVATE).coverImageKey("travel-records/original.jpg").build();
            em.persist(album);
            albumId = album.getId();
            em.persist(TravelRecordPhoto.builder().travelRecord(album).imageKey("travel-records/original.jpg")
                    .imageOrder(1).build());
            return null;
        });
        when(s3Service.getImageUrl(anyString())).thenAnswer(call -> "https://example.com/" + call.getArgument(0));
        when(fileService.moveToDirectory(anyString(), any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void switchingVisibilityImmediatelyChangesReadAccessAndPreservesContent() {
        assertThatThrownBy(() -> service.getTravelRecord(viewerId, albumId)).isInstanceOf(GeneralException.class);
        var published = change("PUBLIC", 0L);
        assertThat(published.visibility()).isEqualTo(TravelRecordVisibility.PUBLIC);
        assertThat(published.version()).isEqualTo(1L);
        var detail = service.getTravelRecord(viewerId, albumId);
        assertThat(detail.title()).isEqualTo("최초 제목");
        assertThat(detail.images().getFirst().imageKey()).isEqualTo("travel-records/original.jpg");
        var hidden = change("PRIVATE", 1L);
        assertThat(hidden.version()).isEqualTo(2L);
        assertThatThrownBy(() -> service.getTravelRecord(viewerId, albumId)).isInstanceOf(GeneralException.class);
        verifyNoInteractions(fileService);
    }

    @Test
    void sameVisibilityDoesNotIncreaseVersionButStaleVersionStillConflicts() {
        assertThat(change("PRIVATE", 0L).version()).isZero();
        change("PUBLIC", 0L);
        assertThat(change("PUBLIC", 1L).version()).isEqualTo(1L);
        assertError(() -> change("PUBLIC", 0L), TravelRecordErrorCode.VERSION_CONFLICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIVATE", "PUBLIC", "FRIENDS"})
    void nonOwnerCannotChangeVisibilityIncludingCurrentSharedParticipants(String initial) {
        tx(() -> {
            em.find(TravelRecord.class, albumId).updateVisibility(TravelRecordVisibility.valueOf(initial));
            em.persist(TravelRecordMember.builder().travelRecord(em.getReference(TravelRecord.class, albumId))
                    .user(em.getReference(User.class, viewerId)).build());
            return null;
        });
        assertError(() -> service.updateVisibility(albumId, viewerId,
                new TravelRecordReqDTO.VisibilityUpdateRequest("PUBLIC", currentVersion())), GeneralErrorCode.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIVATE", "PUBLIC", "FRIENDS"})
    void friendsAlbumCannotChangeEvenToTheSameVisibility(String target) {
        tx(() -> { em.find(TravelRecord.class, albumId).updateVisibility(TravelRecordVisibility.FRIENDS); return null; });
        assertError(() -> change(target, currentVersion()), TravelRecordErrorCode.INVALID_INVITATION_STATE);
        assertThat(service.getTravelRecord(ownerId, albumId).visibility()).isEqualTo(TravelRecordVisibility.FRIENDS);
    }

    @Test
    void cannotConvertPrivateOrPublicIntoFriends() {
        assertError(() -> change("FRIENDS", 0L), TravelRecordErrorCode.INVALID_INVITATION_STATE);
        change("PUBLIC", 0L);
        assertError(() -> change("FRIENDS", 1L), TravelRecordErrorCode.INVALID_INVITATION_STATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "public", "UNKNOWN"})
    void rejectsInvalidVisibility(String target) {
        assertError(() -> change(target, 0L), TravelRecordErrorCode.INVALID_VISIBILITY);
        assertThat(currentVersion()).isZero();
    }

    @Test
    void apiRequiresLoginAndReturnsSavedVisibilityAndVersion() throws Exception {
        mvc.perform(patch(path()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\",\"version\":0}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(path()).with(auth(ownerId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.travelRecordId").value(albumId))
                .andExpect(jsonPath("$.result.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.result.version").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"visibility\":\"PUBLIC\"}", "{\"visibility\":\"PUBLIC\",\"version\":null}",
            "{\"visibility\":\"PUBLIC\",\"version\":-1}"})
    void apiRejectsMissingOrNegativeVersion(String body) throws Exception {
        mvc.perform(patch(path()).with(auth(ownerId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON4001"));
        assertThat(currentVersion()).isZero();
    }

    @Test
    void apiRejectsMissingVisibilityAndDeletedAlbum() throws Exception {
        mvc.perform(patch(path()).with(auth(ownerId)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRAVEL_RECORD4008"));
        service.deleteTravelRecord(albumId, ownerId);
        assertError(() -> change("PUBLIC", 0L), TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND);
    }

    @Test
    void simultaneousChangesWithSameVersionHaveOnlyOneWinner() throws Exception {
        var results = race(() -> result(() -> change("PUBLIC", 0L)), () -> result(() -> change("PUBLIC", 0L)));
        assertThat(results).containsExactlyInAnyOrder("OK", "TRAVEL_RECORD4091");
        assertThat(currentVersion()).isEqualTo(1L);
    }

    @Test
    void visibilityChangeAndContentEditCannotOverwriteEachOthersVersion() throws Exception {
        var results = race(() -> result(() -> change("PUBLIC", 0L)), () -> result(() -> service.updateTravelRecord(
                albumId, ownerId, new TravelRecordReqDTO.UpdateRequest(0L, "수정 제목", regionId,
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), "BASIC",
                        List.of(new TravelRecordReqDTO.ImageRequest("travel-records/original.jpg", 1)), null))));
        assertThat(results).containsExactlyInAnyOrder("OK", "TRAVEL_RECORD4091");
        var detail = service.getTravelRecord(ownerId, albumId);
        assertThat(detail.version()).isEqualTo(1L);
        if (detail.visibility() == TravelRecordVisibility.PUBLIC) {
            assertThat(detail.title()).isEqualTo("최초 제목");
        } else {
            assertThat(detail.title()).isEqualTo("수정 제목");
        }
    }

    private com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO.VisibilityUpdateResponse change(
            String visibility, Long version
    ) {
        return service.updateVisibility(albumId, ownerId, new TravelRecordReqDTO.VisibilityUpdateRequest(visibility, version));
    }

    private long currentVersion() { return service.getTravelRecord(ownerId, albumId).version(); }

    private void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private String result(Runnable call) {
        try { call.run(); return "OK"; }
        catch (GeneralException e) { return e.getErrorCode().getCode(); }
    }

    private List<String> race(Supplier<String> first, Supplier<String> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return first.get(); });
            var b = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return second.get(); });
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private String path() { return "/api/v1/travel-records/" + albumId + "/visibility"; }

    private RequestPostProcessor auth(Long id) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthUser(id, UserRole.USER, "visibility-test"),
                null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private <T> T tx(Supplier<T> call) {
        return new TransactionTemplate(transactionManager).execute(status -> call.get());
    }

    private User user(Region region) {
        User user = User.builder().nickname("공개범위 테스트").email(UUID.randomUUID()+"@test.com")
                .gender(Gender.FEMALE).birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build();
        em.persist(user);
        return user;
    }
}
