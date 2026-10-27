
package com.tracker.flight.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    @Test
    void shouldLogHttpMethodPathStatusAndDuration()
            throws Exception {

        Logger logger = (Logger) LoggerFactory.getLogger(
                RequestLoggingFilter.class
        );

        ListAppender<ILoggingEvent> appender =
                new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {
            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "GET",
                            "/api/v1/flights/SU100"
                    );

            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            RequestLoggingFilter filter =
                    new RequestLoggingFilter();

            filter.doFilter(
                    request,
                    response,
                    new MockFilterChain()
            );

            assertThat(response.getStatus())
                    .isEqualTo(200);

            assertThat(appender.list)
                    .anySatisfy(event -> {
                        String message =
                                event.getFormattedMessage();

                        assertThat(message)
                                .contains("method=GET")
                                .contains("path=/api/v1/flights/SU100")
                                .contains("status=200")
                                .contains("durationMs=");
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
