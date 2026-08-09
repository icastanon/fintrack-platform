package com.fintrack.workerservice.category.cache;

import com.fintrack.workerservice.category.cache.model.CachedCategorizationRule;
import com.fintrack.workerservice.category.cache.model.CategorizationRuleCacheSnapshot;
import com.fintrack.workerservice.category.entity.Category;
import com.fintrack.workerservice.category.repository.CategoryRepository;
import com.fintrack.workerservice.category.repository.CategorizationRuleRepository;
import com.fintrack.workerservice.category.util.MerchantNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Component
public class CategorizationRuleCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategorizationRuleCache.class);
    private static final String CACHE_KEY = "fintrack:categorization-rules:v1";
    private static final String FALLBACK_CATEGORY_NAME = "Other";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final CategorizationRuleRepository categorizationRuleRepository;
    private final CategoryRepository categoryRepository;
    private final Duration cacheTtl;

    public CategorizationRuleCache(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            CategorizationRuleRepository categorizationRuleRepository,
            CategoryRepository categoryRepository,
            @Value("${fintrack.redis.categorization-rules-ttl}") Duration cacheTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.categorizationRuleRepository = categorizationRuleRepository;
        this.categoryRepository = categoryRepository;
        this.cacheTtl = cacheTtl;
    }

    public CategorizationRuleCacheSnapshot getSnapshot() {
        CategorizationRuleCacheSnapshot cachedSnapshot = readFromRedis();

        if (cachedSnapshot != null) {
            LOGGER.debug("Categorization-rule cache hit");
            return cachedSnapshot;
        }

        LOGGER.debug("Categorization-rule cache miss");

        CategorizationRuleCacheSnapshot databaseSnapshot = loadFromDatabase();

        writeToRedis(databaseSnapshot);

        return databaseSnapshot;
    }

    private CategorizationRuleCacheSnapshot readFromRedis() {
        try {
            String cachedJson = redisTemplate.opsForValue().get(CACHE_KEY);

            if (cachedJson == null) {
                return null;
            }

            try {
                return jsonMapper.readValue(cachedJson, CategorizationRuleCacheSnapshot.class);
            } catch (JacksonException exception) {
                LOGGER.warn("Cached categorization rules could not be deserialized; rebuilding the cache");
                evictQuietly();
                return null;
            }
        } catch (DataAccessException exception) {
            LOGGER.warn("Redis read failed; using PostgreSQL categorization rules: {}", exception.getMessage());
            return null;
        }
    }

    private CategorizationRuleCacheSnapshot loadFromDatabase() {
        Category fallbackCategory = categoryRepository
                .findByNameIgnoreCase(FALLBACK_CATEGORY_NAME)
                .orElseThrow(() -> new IllegalStateException("Required fallback category 'Other' is missing"));

        List<CachedCategorizationRule> cachedRules = categorizationRuleRepository
                .findAllByActiveTrueOrderByPriorityAscIdAsc()
                .stream()
                .map(rule -> new CachedCategorizationRule(
                        rule.getId(),
                        MerchantNormalizer.normalize(rule.getMerchantPattern()),
                        rule.getCategoryId(),
                        rule.getPriority()
                ))
                .toList();

        return new CategorizationRuleCacheSnapshot(fallbackCategory.getId(), cachedRules);
    }

    private void writeToRedis(CategorizationRuleCacheSnapshot snapshot) {
        try {
            String serializedSnapshot = jsonMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(CACHE_KEY, serializedSnapshot, cacheTtl);
        } catch (JacksonException exception) {
            LOGGER.warn("Categorization-rule snapshot could not be serialized; continuing without caching");
        } catch (DataAccessException exception) {
            LOGGER.warn("Redis write failed; continuing with PostgreSQL categorization rules: {}", exception.getMessage());
        }
    }

    private void evictQuietly() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (DataAccessException exception) {
            LOGGER.warn("Corrupt categorization-rule cache entry could not be deleted: {}", exception.getMessage());
        }
    }
}