package com.bobfull.restaurant.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bobfull.common.config.ClockConfig;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.RestaurantErrorCode;
import com.bobfull.common.security.SecurityConfig;
import com.bobfull.restaurant.dto.RestaurantDetailResponse;
import com.bobfull.restaurant.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 사용자용 식당 상세 조회 API가 인증 없이 접근 가능한지, 없는 식당은 404인지 검증한다.
 */
@WebMvcTest(controllers = RestaurantController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=restaurant-controller-web-test-secret-key-please-keep-this-long",
        "jwt.access-token-expiration-seconds=3600"
})
class RestaurantControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantService restaurantService;

    @Test
    void 인증_없이_식당_상세를_조회할_수_있다() throws Exception {
        // given
        given(restaurantService.getRestaurantDetail(1L)).willReturn(
                new RestaurantDetailResponse(1L, "밥풀식당", "제주시 애월읍 1", "한식", "설명", "흑돼지,혼밥", 10000));

        // when
        ResultActions result = mockMvc.perform(get("/api/restaurants/1"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantId", is(1)))
                .andExpect(jsonPath("$.data.name", is("밥풀식당")));
    }

    @Test
    void 존재하지_않는_식당을_조회하면_404를_반환한다() throws Exception {
        // given
        given(restaurantService.getRestaurantDetail(999L))
                .willThrow(new CustomException(RestaurantErrorCode.RESTAURANT_ID_NOT_FOUND));

        // when
        ResultActions result = mockMvc.perform(get("/api/restaurants/999"));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESTAURANT_ID_NOT_FOUND")));
    }
}
