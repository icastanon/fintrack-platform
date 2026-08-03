package com.fintrack.apiservice.category.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CategoryResponse {

    private final Long id;
    private final String name;
}