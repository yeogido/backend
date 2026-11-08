package com.yeogido.backend.domain.auth.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.global.logging.SafeLogDetails;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.function.Function;
import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SocialUserInfoClientLoggingTest {

    private Logger logger;
    private MockRestServiceServer server;
    private Function<String, SocialUserInfo> call;
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @AfterEach
    void tearDown() {
        if (logger != null) logger.detachAppender(logs);
        logs.stop();
        MDC.clear();
    }

    @ParameterizedTest
    @CsvSource({"kakao,401", "kakao,429", "kakao,503", "naver,401", "naver,429", "naver,503"})
    void httpFailureHasOneSafeEventAndKeepsExistingException(String provider, int status) {
        setUp(provider);
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("secret-user-email secret-access-token secret-refresh-token"));

        assertExistingError();

        assertThat(fields(provider)).containsEntry("upstreamHttpStatus", status)
                .containsEntry("failureType", SafeLogDetails.httpFailureType(status))
                .containsEntry("retryable", status == 429 || status >= 500);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kakao", "naver"})
    void timeoutDoesNotExposeUrlTokenOrCause(String provider) {
        setUp(provider);
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("secret-url-token")));

        assertExistingError();

        assertThat(fields(provider)).containsEntry("failureType", "timeout")
                .containsEntry("retryable", true).doesNotContainKey("upstreamHttpStatus");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kakao", "naver"})
    void malformedJsonHasSafeParseClassification(String provider) {
        setUp(provider);
        server.expect(anything()).andRespond(withSuccess("{secret-user-email", MediaType.APPLICATION_JSON));

        assertExistingError();

        assertThat(fields(provider)).containsEntry("failureType", "response_parse_error");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kakao", "naver"})
    void missingProviderIdentityKeepsExistingRejection(String provider) {
        setUp(provider);
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertExistingError();

        assertThat(fields(provider)).containsEntry("failureType", "invalid_response");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kakao", "naver"})
    void successfulUserInfoKeepsPersonalDataOutOfLogs(String provider) {
        setUp(provider);
        String response = "kakao".equals(provider)
                ? "{\"id\":123,\"kakao_account\":{\"email\":\"secret-user-email\",\"name\":\"secret-user-name\"}}"
                : "{\"response\":{\"id\":\"123\",\"email\":\"secret-user-email\",\"name\":\"secret-user-name\"}}";
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        SocialUserInfo user = call.apply("secret-access-token");

        assertThat(user.providerId()).isEqualTo("123");
        assertThat(user.email()).isEqualTo("secret-user-email");
        assertThat(user.name()).isEqualTo("secret-user-name");
        assertThat(logs.list).isEmpty();
        server.verify();
    }

    private void setUp(String provider) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Object client;
        if ("kakao".equals(provider)) {
            var kakao = new KakaoUserInfoClient(builder);
            client = kakao;
            call = kakao::getUserInfo;
        } else {
            var naver = new NaverUserInfoClient(builder);
            client = naver;
            call = naver::getUserInfo;
        }
        ReflectionTestUtils.setField(client, "userInfoUri", "https://provider.test/user?key=secret-url-token");
        logger = (Logger) LoggerFactory.getLogger(client.getClass());
        logs.start();
        logger.addAppender(logs);
        MDC.put("requestId", "request-test");
    }

    private void assertExistingError() {
        assertThatThrownBy(() -> call.apply("secret-access-token"))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_SOCIAL_ACCESS_TOKEN))
                .hasNoCause();
        assertThat(MDC.get("requestId")).isEqualTo("request-test");
    }

    private Map<String, Object> fields(String provider) {
        assertThat(logs.list).hasSize(1);
        var event = logs.list.getFirst();
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "request-test");
        var fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields).containsEntry("event", "external_api.call.failed")
                .containsEntry("provider", provider).containsEntry("operation", "user_info")
                .containsEntry("errorCode", AuthErrorCode.INVALID_SOCIAL_ACCESS_TOKEN.getCode());
        assertThat(event.getFormattedMessage() + fields).doesNotContain("secret-", "https://", "Bearer ");
        return fields;
    }
}
