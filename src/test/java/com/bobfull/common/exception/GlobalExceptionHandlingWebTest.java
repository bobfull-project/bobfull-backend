package com.bobfull.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bobfull.common.support.TestApiController;
import com.bobfull.common.support.TestRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GlobalExceptionHandler가 CustomException, Bean Validation 실패, 미처리 예외를
 * 공통 실패 응답으로 일관되게 변환하는지 검증한다.
 * Security 필터는 이 테스트의 관심사가 아니므로 비활성화한다.
 */
@WebMvcTest(controllers = TestApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlingWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void CustomException_발생시_해당_에러코드로_공통_실패_응답을_반환한다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/errors/custom"));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("MEMBER_NOT_FOUND")));
    }

    @Test
    void 요청_값_검증에_실패하면_INVALID_INPUT_VALUE로_공통_실패_응답을_반환한다() throws Exception {
        // given
        String invalidBody = objectMapper.writeValueAsString(new TestRequest(""));

        // when
        ResultActions result = mockMvc.perform(post("/api/errors/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("INVALID_INPUT_VALUE")));
    }

    @Test
    void 처리되지_않은_예외가_발생하면_INTERNAL_SERVER_ERROR로_공통_실패_응답을_반환한다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/errors/internal"));

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("INTERNAL_SERVER_ERROR")));
    }

    @Test
    void 처리되지_않은_예외가_발생하면_서버_로그에_기록된다() throws Exception {
        // given
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            // when
            mockMvc.perform(get("/api/errors/internal"));

            // then
            assertThat(listAppender.list)
                    .anyMatch(event -> event.getLevel() == Level.ERROR);
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    @Test
    void 새로운_도메인_ErrorCode를_추가해도_GlobalExceptionHandler_수정없이_공통_실패_응답을_반환한다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/errors/domain"));

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("SAMPLE_DOMAIN_CONFLICT")));
    }

    @Test
    void 정상_요청은_공통_성공_응답으로_감싸서_반환한다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/public/hello"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", is("public-ok")))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void 시각_응답은_Asia_Seoul_오프셋을_포함한_ISO_8601로_직렬화된다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/time/hello"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAt", is("2026-07-23T09:00:00+09:00")));
    }
}
