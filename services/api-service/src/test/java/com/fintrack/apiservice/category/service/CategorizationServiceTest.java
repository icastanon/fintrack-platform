package com.fintrack.apiservice.category.service;

import com.fintrack.apiservice.category.entity.Category;
import com.fintrack.apiservice.category.entity.CategorizationRule;
import com.fintrack.apiservice.category.repository.CategoryRepository;
import com.fintrack.apiservice.category.repository.CategorizationRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorizationServiceTest {

    @Mock
    private CategorizationRuleRepository ruleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategorizationService categorizationService;

    @Test
    void categorizeMerchantReturnsCategoryFromFirstMatchingRule() {
        Category groceries = org.mockito.Mockito.mock(Category.class);
        CategorizationRule publixRule = org.mockito.Mockito.mock(CategorizationRule.class);

        when(publixRule.getMerchantPattern()).thenReturn("PUBLIX");

        when(publixRule.getCategory()).thenReturn(groceries);

        when(ruleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(publixRule));

        Category result = categorizationService.categorizeMerchant("PUBLIX #1472 TAMPA FL");

        assertThat(result).isSameAs(groceries);

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void categorizeMerchantIgnoresCaseAndExtraWhitespace() {
        Category groceries = org.mockito.Mockito.mock(Category.class);
        CategorizationRule publixRule = org.mockito.Mockito.mock(CategorizationRule.class);

        when(publixRule.getMerchantPattern()).thenReturn("publix");

        when(publixRule.getCategory()).thenReturn(groceries);

        when(ruleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(publixRule));

        Category result = categorizationService.categorizeMerchant("   Publix     #1472   Tampa   ");

        assertThat(result).isSameAs(groceries);
    }

    @Test
    void categorizeMerchantUsesFirstRuleWhenPatternsOverlap() {
        Category restaurants = org.mockito.Mockito.mock(Category.class);

        Category transportation = org.mockito.Mockito.mock(Category.class);

        CategorizationRule uberEatsRule = org.mockito.Mockito.mock(CategorizationRule.class);

        CategorizationRule uberRule = org.mockito.Mockito.mock(CategorizationRule.class);

        when(uberEatsRule.getMerchantPattern()).thenReturn("UBER EATS");

        when(uberEatsRule.getCategory()).thenReturn(restaurants);


        when(ruleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(uberEatsRule, uberRule));

        Category result = categorizationService.categorizeMerchant("UBER EATS ORDER 9284");

        assertThat(result).isSameAs(restaurants);

        verify(uberRule, never()).getMerchantPattern();

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void categorizeMerchantUsesGeneralRuleWhenSpecificRuleDoesNotMatch() {
        Category transportation = org.mockito.Mockito.mock(Category.class);

        CategorizationRule uberEatsRule = org.mockito.Mockito.mock(CategorizationRule.class);

        CategorizationRule uberRule = org.mockito.Mockito.mock(CategorizationRule.class);

        when(uberEatsRule.getMerchantPattern()).thenReturn("UBER EATS");

        when(uberRule.getMerchantPattern()).thenReturn("UBER");

        when(uberRule.getCategory()).thenReturn(transportation);

        when(ruleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(uberEatsRule, uberRule));

        Category result = categorizationService.categorizeMerchant("UBER TRIP HELP.UBER.COM");

        assertThat(result).isSameAs(transportation);

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void categorizeMerchantReturnsOtherWhenNoRuleMatches() {
        Category other = org.mockito.Mockito.mock(Category.class);

        CategorizationRule publixRule = org.mockito.Mockito.mock(CategorizationRule.class);

        when(publixRule.getMerchantPattern()).thenReturn("PUBLIX");

        when(ruleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(publixRule));

        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.of(other));

        Category result = categorizationService.categorizeMerchant("IVAN'S LOCAL STORE");

        assertThat(result).isSameAs(other);

        verify(categoryRepository).findByNameIgnoreCase("Other");
    }

    @Test
    void categorizeMerchantReturnsOtherForBlankMerchant() {
        Category other = org.mockito.Mockito.mock(Category.class);

        CategorizationRule publixRule = org.mockito.Mockito.mock(CategorizationRule.class);

        when(ruleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(publixRule));

        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.of(other));

        Category result = categorizationService.categorizeMerchant("   ");

        assertThat(result).isSameAs(other);

        verify(publixRule, never()).getMerchantPattern();
    }

    @Test
    void categorizeMerchantReturnsOtherForNullMerchant() {
        Category other = org.mockito.Mockito.mock(Category.class);

        when(ruleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());

        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.of(other));

        Category result = categorizationService.categorizeMerchant(null);

        assertThat(result).isSameAs(other);
    }

    @Test
    void categorizeMerchantThrowsWhenFallbackCategoryIsMissing() {
        when(ruleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());

        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categorizationService.categorizeMerchant("UNKNOWN MERCHANT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Required fallback category 'Other' is missing");
    }
}