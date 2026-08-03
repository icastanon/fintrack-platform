package com.fintrack.apiservice.category.controller;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.auth.security.JwtService;
import com.fintrack.apiservice.auth.security.RestAccessDeniedHandler;
import com.fintrack.apiservice.auth.security.RestAuthenticationEntryPoint;
import com.fintrack.apiservice.auth.security.SecurityConfig;
import com.fintrack.apiservice.category.dto.CategoryResponse;
import com.fintrack.apiservice.category.service.CategoryService;
import com.fintrack.apiservice.common.exception.GlobalExceptionHandler;
import com.fintrack.apiservice.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtService jwtService;

    private AuthenticatedUserPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new AuthenticatedUserPrincipal(7L, "ivan", Role.USER);

        when(jwtService.extractPrincipal("valid-token")).thenReturn(principal);
    }

    @Test
    void getCategoriesReturnsCategoriesForAuthenticatedUser()
            throws Exception {

        when(categoryService.getCategories())
                .thenReturn(
                        List.of(
                                new CategoryResponse(
                                        5L,
                                        "Entertainment"
                                ),
                                new CategoryResponse(
                                        2L,
                                        "Groceries"
                                )
                        )
                );

        mockMvc.perform(
                        get("/api/v1/categories")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(5)
                )
                .andExpect(
                        jsonPath("$[0].name")
                                .value("Entertainment")
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[1].name")
                                .value("Groceries")
                );

        verify(categoryService).getCategories();
    }

    @Test
    void getCategoriesWithoutJwtReturnsUnauthorized() throws Exception {

        mockMvc.perform(
                        get("/api/v1/categories")
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(categoryService);
    }
}