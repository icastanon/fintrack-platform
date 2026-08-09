package com.fintrack.workerservice.category.service;

import com.fintrack.workerservice.category.cache.CategorizationRuleCache;
import com.fintrack.workerservice.category.cache.model.CachedCategorizationRule;
import com.fintrack.workerservice.category.cache.model.CategorizationRuleCacheSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorizationServiceTest {

    @Mock
    private CategorizationRuleCache categorizationRuleCache;

    @InjectMocks
    private CategorizationService categorizationService;

    @Test
    void categorizeMerchantReturnsCategoryIdFromFirstMatchingRule() {
        CategorizationRuleCacheSnapshot snapshot = new CategorizationRuleCacheSnapshot(
                9L,
                List.of(
                        new CachedCategorizationRule(1L, "UBER EATS", 4L, 10),
                        new CachedCategorizationRule(2L, "UBER", 3L, 20)
                )
        );

        when(categorizationRuleCache.getSnapshot()).thenReturn(snapshot);

        Long result = categorizationService.categorizeMerchant("UBER EATS ORDER 9284");

        assertThat(result).isEqualTo(4L);
    }

    @Test
    void categorizeMerchantIgnoresCaseAndExtraWhitespace() {
        CategorizationRuleCacheSnapshot snapshot = new CategorizationRuleCacheSnapshot(
                9L,
                List.of(new CachedCategorizationRule(1L, "PUBLIX", 2L, 10))
        );

        when(categorizationRuleCache.getSnapshot()).thenReturn(snapshot);

        Long result = categorizationService.categorizeMerchant("   Publix     Store 1472   ");

        assertThat(result).isEqualTo(2L);
    }

    @Test
    void categorizeMerchantReturnsCategoryFromGeneralRuleWhenSpecificRuleDoesNotMatch() {
        CategorizationRuleCacheSnapshot snapshot = new CategorizationRuleCacheSnapshot(
                9L,
                List.of(
                        new CachedCategorizationRule(1L, "UBER EATS", 4L, 10),
                        new CachedCategorizationRule(2L, "UBER", 3L, 20)
                )
        );

        when(categorizationRuleCache.getSnapshot()).thenReturn(snapshot);

        Long result = categorizationService.categorizeMerchant("UBER TRIP HELP.UBER.COM");

        assertThat(result).isEqualTo(3L);
    }

    @Test
    void categorizeMerchantReturnsFallbackWhenNoRuleMatches() {
        CategorizationRuleCacheSnapshot snapshot = new CategorizationRuleCacheSnapshot(
                9L,
                List.of(new CachedCategorizationRule(1L, "PUBLIX", 2L, 10))
        );

        when(categorizationRuleCache.getSnapshot()).thenReturn(snapshot);

        Long result = categorizationService.categorizeMerchant("IVAN'S LOCAL STORE");

        assertThat(result).isEqualTo(9L);
    }

    @Test
    void categorizeMerchantReturnsFallbackForNullMerchant() {
        CategorizationRuleCacheSnapshot snapshot =
                new CategorizationRuleCacheSnapshot(9L, List.of());

        when(categorizationRuleCache.getSnapshot()).thenReturn(snapshot);

        Long result = categorizationService.categorizeMerchant(null);

        assertThat(result).isEqualTo(9L);
    }

    @Test
    void categorizeMerchantReturnsFallbackForBlankMerchant() {
        CategorizationRuleCacheSnapshot snapshot = new CategorizationRuleCacheSnapshot(
                9L,
                List.of(new CachedCategorizationRule(1L, "PUBLIX", 2L, 10))
        );

        when(categorizationRuleCache.getSnapshot()).thenReturn(snapshot);

        Long result = categorizationService.categorizeMerchant("   ");

        assertThat(result).isEqualTo(9L);
    }

    @Test
    void categorizeMerchantIgnoresBlankCachedPattern() {
        CategorizationRuleCacheSnapshot snapshot = new CategorizationRuleCacheSnapshot(
                9L,
                List.of(new CachedCategorizationRule(1L, "", 2L, 10))
        );

        when(categorizationRuleCache.getSnapshot()).thenReturn(snapshot);

        Long result = categorizationService.categorizeMerchant("PUBLIX STORE");

        assertThat(result).isEqualTo(9L);
    }
}