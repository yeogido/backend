package com.yeogido.backend.global.logging;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void createsServerIdAndIgnoresUntrustedHeader() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "fake-token\r\nAuthorization: secret");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            String id = MDC.get(RequestIdFilter.MDC_KEY);
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
            assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(id);
            assertThat(id).doesNotContain("fake-token", "secret");
        });

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void restoresAllPreviousContextAfterSuccess() throws Exception {
        Map<String, String> previous = Map.of("requestId", "outer", "existing", "context");
        MDC.setContextMap(previous);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
            assertThat(MDC.get("requestId")).isNotEqualTo("outer");
            MDC.put("temporary", "value");
            MDC.remove("existing");
        });

        assertThat(MDC.getCopyOfContextMap()).isEqualTo(previous);
    }

    @Test
    void replacesEvenValidClientProvidedId() throws Exception {
        String clientId = UUID.randomUUID().toString();
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, clientId);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get("requestId")).isNotEqualTo(clientId));

        assertThat(response.getHeader("X-Request-ID")).isNotEqualTo(clientId);
    }

    @Test
    void restoresContextAndKeepsHeaderWhenChainThrows() {
        Map<String, String> previous = Map.of("existing", "context");
        MDC.setContextMap(previous);
        var response = new MockHttpServletResponse();
        var failure = new ServletException("fake-sensitive-message");

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-ID"));
            MDC.put("temporary", "value");
            throw failure;
        })).isSameAs(failure);

        assertThat(MDC.getCopyOfContextMap()).isEqualTo(previous);
        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
    }

    @Test
    void clearsContextWhenUnhandledFailureHasNoPreviousContext() {
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (req, res) -> {
                    throw new IllegalStateException("fake-secret");
                })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void concurrentRequestsKeepSeparateIdsAndRestoreTheirThreadContext() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> runConcurrentRequest("first", barrier));
            var second = executor.submit(() -> runConcurrentRequest("second", barrier));

            assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void servletRedispatchesReuseTheOriginalId() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        String original = response.getHeader("X-Request-ID");

        for (DispatcherType type : new DispatcherType[]{DispatcherType.ASYNC, DispatcherType.ERROR}) {
            request.setDispatcherType(type);
            var dispatchedResponse = new MockHttpServletResponse();
            filter.doFilter(request, dispatchedResponse, (req, res) ->
                    assertThat(MDC.get("requestId")).isEqualTo(original));
            assertThat(dispatchedResponse.getHeader("X-Request-ID")).isEqualTo(original);
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }
    }

    private String runConcurrentRequest(String worker, CyclicBarrier barrier) throws Exception {
        Map<String, String> previous = Map.of("requestId", worker, "worker", worker);
        MDC.setContextMap(previous);
        try {
            var response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
                String id = MDC.get("requestId");
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new ServletException(failure);
                }
                assertThat(MDC.get("requestId")).isEqualTo(id);
                assertThat(id).isNotEqualTo(worker);
                assertThat(response.getHeader("X-Request-ID")).isEqualTo(id);
            });
            assertThat(MDC.getCopyOfContextMap()).isEqualTo(previous);
            return response.getHeader("X-Request-ID");
        } finally {
            MDC.clear();
        }
    }
}
