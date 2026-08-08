package com.fintrack.workerservice.category.service;

import com.fintrack.workerservice.category.entity.Category;
import com.fintrack.workerservice.category.entity.CategorizationRule;
import com.fintrack.workerservice.category.repository.CategoryRepository;
import com.fintrack.workerservice.category.repository.CategorizationRuleRepository;
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
    private CategorizationRuleRepository categorizationRuleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategorizationService categorizationService;

    @Test
    void categorizeMerchantReturnsCategoryIdFromFirstMatchingRule() {
        CategorizationRule uberEatsRule = org.mockito.Mockito.mock(CategorizationRule.class);
        CategorizationRule uberRule = org.mockito.Mockito.mock(CategorizationRule.class);

        when(uberEatsRule.getMerchantPattern()).thenReturn("UBER EATS");
        when(uberEatsRule.getCategoryId()).thenReturn(4L);
        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of(uberEatsRule, uberRule));

        Long result = categorizationService.categorizeMerchant("UBER EATS ORDER 9284");

        assertThat(result).isEqualTo(4L);

        verify(uberRule, never()).getMerchantPattern();
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void categorizeMerchantIgnoresCaseAndExtraWhitespace() {
        CategorizationRule publixRule = org.mockito.Mockito.mock(CategorizationRule.class);

        when(publixRule.getMerchantPattern()).thenReturn("publix");
        when(publixRule.getCategoryId()).thenReturn(2L);
        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of(publixRule));

        Long result = categorizationService.categorizeMerchant(
                "   Publix     Store 1472   "
        );

        assertThat(result).isEqualTo(2L);
    }

    @Test
    void categorizeMerchantReturnsCategoryFromGeneralRuleWhenSpecificRuleDoesNotMatch() {
        CategorizationRule uberEatsRule = org.mockito.Mockito.mock(CategorizationRule.class);
        CategorizationRule uberRule = org.mockito.Mockito.mock(CategorizationRule.class);

        when(uberEatsRule.getMerchantPattern()).thenReturn("UBER EATS");
        when(uberRule.getMerchantPattern()).thenReturn("UBER");
        when(uberRule.getCategoryId()).thenReturn(3L);
        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of(uberEatsRule, uberRule));

        Long result = categorizationService.categorizeMerchant(
                "UBER TRIP HELP.UBER.COM"
        );

        assertThat(result).isEqualTo(3L);

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void categorizeMerchantReturnsOtherWhenNoRuleMatches() {
        CategorizationRule publixRule = org.mockito.Mockito.mock(CategorizationRule.class);
        Category other = org.mockito.Mockito.mock(Category.class);

        when(publixRule.getMerchantPattern()).thenReturn("PUBLIX");
        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of(publixRule));
        when(categoryRepository.findByNameIgnoreCase("Other"))
                .thenReturn(Optional.of(other));
        when(other.getId()).thenReturn(9L);

        Long result = categorizationService.categorizeMerchant(
                "IVAN'S LOCAL STORE"
        );

        assertThat(result).isEqualTo(9L);
    }

    @Test
    void categorizeMerchantReturnsOtherForNullMerchant() {
        Category other = org.mockito.Mockito.mock(Category.class);

        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of());
        when(categoryRepository.findByNameIgnoreCase("Other"))
                .thenReturn(Optional.of(other));
        when(other.getId()).thenReturn(9L);

        Long result = categorizationService.categorizeMerchant(null);

        assertThat(result).isEqualTo(9L);
    }

    @Test
    void categorizeMerchantDoesNotInspectRulePatternsForBlankMerchant() {
        CategorizationRule rule = org.mockito.Mockito.mock(CategorizationRule.class);
        Category other = org.mockito.Mockito.mock(Category.class);

        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of(rule));
        when(categoryRepository.findByNameIgnoreCase("Other"))
                .thenReturn(Optional.of(other));
        when(other.getId()).thenReturn(9L);

        Long result = categorizationService.categorizeMerchant("   ");

        assertThat(result).isEqualTo(9L);

        verify(rule, never()).getMerchantPattern();
    }

    @Test
    void categorizeMerchantThrowsWhenFallbackCategoryIsMissing() {
        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of());
        when(categoryRepository.findByNameIgnoreCase("Other"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                categorizationService.categorizeMerchant("UNKNOWN MERCHANT")
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Required fallback category 'Other' is missing");
    }
}