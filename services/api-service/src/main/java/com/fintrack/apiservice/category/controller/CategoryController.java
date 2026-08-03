package com.fintrack.apiservice.category.controller;

import com.fintrack.apiservice.category.dto.CategoryResponse;
import com.fintrack.apiservice.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fintrack.apiservice.common.config.OpenApiConfig.BEARER_AUTH;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(
        name = "Categories",
        description = "System-defined financial transaction categories"
)
@SecurityRequirement(name = BEARER_AUTH)
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(
            summary = "List categories",
            description = "Returns all system-defined categories alphabetically"
    )
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(categoryService.getCategories());
    }
}