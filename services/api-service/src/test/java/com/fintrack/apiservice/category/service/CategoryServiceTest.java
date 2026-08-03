package com.fintrack.apiservice.category.service;

import com.fintrack.apiservice.category.dto.CategoryResponse;
import com.fintrack.apiservice.category.entity.Category;
import com.fintrack.apiservice.category.mapper.CategoryMapper;
import com.fintrack.apiservice.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Spy
    private CategoryMapper categoryMapper = new CategoryMapper();

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void getCategoriesReturnsMappedCategoriesInRepositoryOrder() {
        Category groceries = mock(Category.class);

        Category housing = mock(Category.class);

        when(groceries.getId()).thenReturn(2L);
        when(groceries.getName()).thenReturn("Groceries");

        when(housing.getId()).thenReturn(1L);
        when(housing.getName()).thenReturn("Housing");

        when(categoryRepository.findAllByOrderByNameAsc())
                .thenReturn(
                        List.of(
                                groceries,
                                housing
                        )
                );

        List<CategoryResponse> result = categoryService.getCategories();

        assertThat(result).hasSize(2);

        assertThat(result.get(0).getId()).isEqualTo(2L);

        assertThat(result.get(0).getName()).isEqualTo("Groceries");

        assertThat(result.get(1).getId()).isEqualTo(1L);

        assertThat(result.get(1).getName()).isEqualTo("Housing");

        verify(categoryRepository).findAllByOrderByNameAsc();
    }

    @Test
    void getCategoriesReturnsEmptyListWhenNoCategoriesExist() {
        when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        List<CategoryResponse> result = categoryService.getCategories();

        assertThat(result).isEmpty();

        verify(categoryRepository).findAllByOrderByNameAsc();
    }
}