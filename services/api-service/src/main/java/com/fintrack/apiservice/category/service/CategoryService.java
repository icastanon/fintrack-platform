package com.fintrack.apiservice.category.service;

import com.fintrack.apiservice.category.dto.CategoryResponse;
import com.fintrack.apiservice.category.mapper.CategoryMapper;
import com.fintrack.apiservice.category.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository
                .findAllByOrderByNameAsc()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }
}