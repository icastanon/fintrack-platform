package com.fintrack.workerservice.category.cache;

import com.fintrack.workerservice.category.cache.model.CachedCategorizationRule;
import com.fintrack.workerservice.category.cache.model.CategorizationRuleCacheSnapshot;
import com.fintrack.workerservice.category.entity.Category;
import com.fintrack.workerservice.category.entity.CategorizationRule;
import com.fintrack.workerservice.category.repository.CategoryRepository;
import com.fintrack.workerservice.category.repository.CategorizationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorizationRuleCacheTest {

    private static final String CACHE_KEY = "fintrack:categorization-rules:v1";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CategorizationRuleRepository categorizationRuleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private JsonMapper jsonMapper;
    private CategorizationRuleCache categorizationRuleCache;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().findAndAddModules().build();

        categorizationRuleCache = new CategorizationRuleCache(
                redisTemplate,
                jsonMapper,
                categorizationRuleRepository,
                categoryRepository,
                CACHE_TTL
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getSnapshotReturnsCachedSnapshotWithoutQueryingPostgreSQL() throws JacksonException {
        CategorizationRuleCacheSnapshot cachedSnapshot = new CategorizationRuleCacheSnapshot(
                9L,
                List.of(new CachedCategorizationRule(1L, "PUBLIX", 2L, 10))
        );

        String cachedJson = jsonMapper.writeValueAsString(cachedSnapshot);
        when(valueOperations.get(CACHE_KEY)).thenReturn(cachedJson);

        CategorizationRuleCacheSnapshot result = categorizationRuleCache.getSnapshot();

        assertThat(result.getFallbackCategoryId()).isEqualTo(9L);
        assertThat(result.getRules()).hasSize(1);

        CachedCategorizationRule resultRule = result.getRules().getFirst();

        assertThat(resultRule.getId()).isEqualTo(1L);
        assertThat(resultRule.getNormalizedMerchantPattern()).isEqualTo("PUBLIX");
        assertThat(resultRule.getCategoryId()).isEqualTo(2L);
        assertThat(resultRule.getPriority()).isEqualTo(10);

        verify(valueOperations).get(CACHE_KEY);
        verify(valueOperations, never()).set(eq(CACHE_KEY), anyString(), eq(CACHE_TTL));
        verifyNoInteractions(categorizationRuleRepository, categoryRepository);
    }

    @Test
    void getSnapshotLoadsPostgreSQLAndWritesRedisWhenCacheIsEmpty() throws JacksonException {
        Category other = mock(Category.class);
        CategorizationRule rule = mock(CategorizationRule.class);

        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.of(other));
        when(other.getId()).thenReturn(9L);

        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of(rule));
        when(rule.getId()).thenReturn(1L);
        when(rule.getMerchantPattern()).thenReturn("  publix    store  ");
        when(rule.getCategoryId()).thenReturn(2L);
        when(rule.getPriority()).thenReturn(10);

        CategorizationRuleCacheSnapshot result = categorizationRuleCache.getSnapshot();

        assertThat(result.getFallbackCategoryId()).isEqualTo(9L);
        assertThat(result.getRules()).hasSize(1);
        assertThat(result.getRules().getFirst().getNormalizedMerchantPattern())
                .isEqualTo("PUBLIX STORE");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

        verify(valueOperations).set(eq(CACHE_KEY), jsonCaptor.capture(), eq(CACHE_TTL));

        CategorizationRuleCacheSnapshot storedSnapshot = jsonMapper.readValue(
                jsonCaptor.getValue(),
                CategorizationRuleCacheSnapshot.class
        );

        assertThat(storedSnapshot.getFallbackCategoryId()).isEqualTo(9L);
        assertThat(storedSnapshot.getRules()).hasSize(1);
        assertThat(storedSnapshot.getRules().getFirst().getId()).isEqualTo(1L);
        assertThat(storedSnapshot.getRules().getFirst().getNormalizedMerchantPattern())
                .isEqualTo("PUBLIX STORE");
        assertThat(storedSnapshot.getRules().getFirst().getCategoryId()).isEqualTo(2L);
        assertThat(storedSnapshot.getRules().getFirst().getPriority()).isEqualTo(10);
    }

    @Test
    void getSnapshotEvictsCorruptJsonAndRebuildsCache() {
        Category other = mock(Category.class);

        when(valueOperations.get(CACHE_KEY)).thenReturn("{invalid-json");
        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.of(other));
        when(other.getId()).thenReturn(9L);
        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of());

        CategorizationRuleCacheSnapshot result = categorizationRuleCache.getSnapshot();

        assertThat(result.getFallbackCategoryId()).isEqualTo(9L);
        assertThat(result.getRules()).isEmpty();

        verify(redisTemplate).delete(CACHE_KEY);
        verify(valueOperations).set(eq(CACHE_KEY), anyString(), eq(CACHE_TTL));
    }

    @Test
    void getSnapshotFallsBackToPostgreSQLWhenRedisReadFails() {
        Category other = mock(Category.class);

        when(valueOperations.get(CACHE_KEY))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.of(other));
        when(other.getId()).thenReturn(9L);
        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of());

        CategorizationRuleCacheSnapshot result = categorizationRuleCache.getSnapshot();

        assertThat(result.getFallbackCategoryId()).isEqualTo(9L);
        assertThat(result.getRules()).isEmpty();

        verify(categoryRepository).findByNameIgnoreCase("Other");
        verify(categorizationRuleRepository).findAllByActiveTrueOrderByPriorityAscIdAsc();
        verify(valueOperations).set(eq(CACHE_KEY), anyString(), eq(CACHE_TTL));
    }

    @Test
    void getSnapshotReturnsPostgreSQLSnapshotWhenRedisWriteFails() {
        Category other = mock(Category.class);

        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.of(other));
        when(other.getId()).thenReturn(9L);
        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of());

        doThrow(new DataAccessResourceFailureException("Redis unavailable"))
                .when(valueOperations)
                .set(eq(CACHE_KEY), anyString(), eq(CACHE_TTL));

        CategorizationRuleCacheSnapshot result = categorizationRuleCache.getSnapshot();

        assertThat(result.getFallbackCategoryId()).isEqualTo(9L);
        assertThat(result.getRules()).isEmpty();
    }

    @Test
    void getSnapshotThrowsWhenRequiredFallbackCategoryIsMissing() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categorizationRuleCache.getSnapshot())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Required fallback category 'Other' is missing");

        verifyNoInteractions(categorizationRuleRepository);
        verify(valueOperations, never()).set(eq(CACHE_KEY), anyString(), eq(CACHE_TTL));
    }
}