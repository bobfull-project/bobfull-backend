package com.bobfull.common.exception;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bobfull.common.support.TestApiController;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
        // given & when & then
        mockMvc.perform(get("/api/errors/custom"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("MEMBER_NOT_FOUND")));
    }

    @Test
    void 요청_값_검증에_실패하면_INVALID_INPUT_VALUE로_공통_실패_응답을_반환한다() throws Exception {
        // given
        String invalidBody = objectMapper.writeValueAsString(new com.bobfull.common.support.TestRequest(""));

        // when & then
        mockMvc.perform(post("/api/errors/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("INVALID_INPUT_VALUE")));
    }

    @Test
    void 처리되지_않은_예외가_발생하면_INTERNAL_SERVER_ERROR로_공통_실패_응답을_반환한다() throws Exception {
        // given & when & then
        mockMvc.perform(get("/api/errors/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("INTERNAL_SERVER_ERROR")));
    }

    @Test
    void 정상_요청은_공통_성공_응답으로_감싸서_반환한다() throws Exception {
        // given & when & then
        mockMvc.perform(get("/api/public/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", is("public-ok")))
                .andExpect(jsonPath("$.code").doesNotExist());
    }
}
