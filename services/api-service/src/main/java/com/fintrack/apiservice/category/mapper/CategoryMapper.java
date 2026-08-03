package com.fintrack.apiservice.category.mapper;

import com.fintrack.apiservice.category.dto.CategoryResponse;
import com.fintrack.apiservice.category.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}