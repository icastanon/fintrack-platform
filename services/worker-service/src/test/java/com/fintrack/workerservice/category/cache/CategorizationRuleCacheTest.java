package com.fintrack.workerservice.category.cache;

import com.fintrack.workerservice.category.cache.model.CachedCategorizationRule;
import com.fintrack.workerservice.category.cache.model.CategorizationRuleCacheSnapshot;
import com.fintrack.workerservice.category.entity.Category;
import com.fintrack.workerservice.category.entity.CategorizationRule;
import com.fintrack.workerservice.category.repository.CategoryRepository;
import com.fintrack.workerservice.category.repository.CategorizationRuleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.lenient;

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
    private SimpleMeterRegistry meterRegistry;
    private CategorizationRuleCacheMetrics metrics;
    private CategorizationRuleCache categorizationRuleCache;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        meterRegistry = new SimpleMeterRegistry();
        metrics = new CategorizationRuleCacheMetrics(meterRegistry);

        categorizationRuleCache = new CategorizationRuleCache(
                redisTemplate,
                jsonMapper,
                categorizationRuleRepository,
                categoryRepository,
                metrics,
                CACHE_TTL
        );

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getSnapshotReturnsCachedSnapshotWithoutQueryingPostgreSQL() throws JacksonException {
        CategorizationRuleCacheSnapshot cachedSnapshot = new CategorizationRuleCacheSnapshot(
                9L,
                List.of(new CachedCategorizationRule(1L, "PUBLIX", 2L, 10))
        );

        when(valueOperations.get(CACHE_KEY))
                .thenReturn(jsonMapper.writeValueAsString(cachedSnapshot));

        CategorizationRuleCacheSnapshot result = categorizationRuleCache.getSnapshot();

        assertThat(result.getFallbackCategoryId()).isEqualTo(9L);
        assertThat(result.getRules()).hasSize(1);
        assertThat(result.getRules().getFirst().getCategoryId()).isEqualTo(2L);
        assertThat(requestCount("hit")).isEqualTo(1.0);
        assertThat(requestCount("miss")).isZero();

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
        assertThat(result.getRules().getFirst().getNormalizedMerchantPattern())
                .isEqualTo("PUBLIX STORE");
        assertThat(requestCount("hit")).isZero();
        assertThat(requestCount("miss")).isEqualTo(1.0);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(CACHE_KEY), jsonCaptor.capture(), eq(CACHE_TTL));

        CategorizationRuleCacheSnapshot storedSnapshot =
                jsonMapper.readValue(jsonCaptor.getValue(), CategorizationRuleCacheSnapshot.class);

        assertThat(storedSnapshot.getFallbackCategoryId()).isEqualTo(9L);
        assertThat(storedSnapshot.getRules().getFirst().getCategoryId()).isEqualTo(2L);
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
        assertThat(requestCount("miss")).isEqualTo(1.0);
        assertThat(errorCount("deserialization")).isEqualTo(1.0);

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
        assertThat(requestCount("miss")).isEqualTo(1.0);
        assertThat(errorCount("redis_read")).isEqualTo(1.0);

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
        assertThat(requestCount("miss")).isEqualTo(1.0);
        assertThat(errorCount("redis_write")).isEqualTo(1.0);
    }

    @Test
    void getSnapshotRecordsSerializationFailureAndReturnsPostgreSQLSnapshot() {
        Category other = mock(Category.class);
        JsonMapper failingJsonMapper = mock(JsonMapper.class);
        JacksonException serializationException = mock(JacksonException.class);

        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.of(other));
        when(other.getId()).thenReturn(9L);
        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of());
        when(failingJsonMapper.writeValueAsString(any(CategorizationRuleCacheSnapshot.class)))
                .thenThrow(serializationException);

        CategorizationRuleCache cacheWithFailingMapper = new CategorizationRuleCache(
                redisTemplate,
                failingJsonMapper,
                categorizationRuleRepository,
                categoryRepository,
                metrics,
                CACHE_TTL
        );

        CategorizationRuleCacheSnapshot result = cacheWithFailingMapper.getSnapshot();

        assertThat(result.getFallbackCategoryId()).isEqualTo(9L);
        assertThat(requestCount("miss")).isEqualTo(1.0);
        assertThat(errorCount("serialization")).isEqualTo(1.0);
    }

    @Test
    void getSnapshotRecordsEvictionFailureAndStillRebuildsCache() {
        Category other = mock(Category.class);

        when(valueOperations.get(CACHE_KEY)).thenReturn("{invalid-json");
        when(redisTemplate.delete(CACHE_KEY))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.of(other));
        when(other.getId()).thenReturn(9L);
        when(categorizationRuleRepository.findAllByActiveTrueOrderByPriorityAscIdAsc())
                .thenReturn(List.of());

        CategorizationRuleCacheSnapshot result = categorizationRuleCache.getSnapshot();

        assertThat(result.getFallbackCategoryId()).isEqualTo(9L);
        assertThat(requestCount("miss")).isEqualTo(1.0);
        assertThat(errorCount("deserialization")).isEqualTo(1.0);
        assertThat(errorCount("eviction")).isEqualTo(1.0);
    }

    @Test
    void getSnapshotThrowsWhenRequiredFallbackCategoryIsMissing() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(categoryRepository.findByNameIgnoreCase("Other")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categorizationRuleCache.getSnapshot())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Required fallback category 'Other' is missing");

        assertThat(requestCount("miss")).isEqualTo(1.0);

        verifyNoInteractions(categorizationRuleRepository);
        verify(valueOperations, never()).set(eq(CACHE_KEY), anyString(), eq(CACHE_TTL));
    }

    private double requestCount(String outcome) {
        return meterRegistry
                .get("fintrack.categorization.rules.cache.requests")
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private double errorCount(String operation) {
        return meterRegistry
                .get("fintrack.categorization.rules.cache.errors")
                .tag("operation", operation)
                .counter()
                .count();
    }

    @Test
    void evictReturnsTrueWhenCachedSnapshotIsDeleted() {
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(true);

        boolean result = categorizationRuleCache.evict();

        assertThat(result).isTrue();
        assertThat(errorCount("eviction")).isZero();

        verify(redisTemplate).delete(CACHE_KEY);
    }

    @Test
    void evictReturnsFalseWhenCachedSnapshotDoesNotExist() {
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(false);

        boolean result = categorizationRuleCache.evict();

        assertThat(result).isFalse();
        assertThat(errorCount("eviction")).isZero();

        verify(redisTemplate).delete(CACHE_KEY);
    }

    @Test
    void evictReturnsFalseAndRecordsMetricWhenRedisFails() {
        when(redisTemplate.delete(CACHE_KEY))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        boolean result = categorizationRuleCache.evict();

        assertThat(result).isFalse();
        assertThat(errorCount("eviction")).isEqualTo(1.0);
    }
}