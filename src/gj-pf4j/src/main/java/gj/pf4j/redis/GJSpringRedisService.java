package gj.pf4j.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
@ConditionalOnBean({RedisConnectionFactory.class, RedisMessageListenerContainer.class})
public class GJSpringRedisService implements IGJRedisService, IGJRedisBusService {

    private static final Logger log = LoggerFactory.getLogger(GJSpringRedisService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;

    public GJSpringRedisService(RedisTemplate<String, Object> redisTemplate,
                                RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.redisTemplate.setKeySerializer(new StringRedisSerializer());
        this.redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    }

    @Override
    public void setWithTtl(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis SET failed: key={}", key, e);
        }
    }

    @Override
    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis GET failed: key={}", key, e);
            return null;
        }
    }

    @Override
    public void del(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis DEL failed: key={}", key, e);
        }
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
        } catch (Exception e) {
            log.warn("Redis SETNX failed: key={}", key, e);
            return false;
        }
    }

    @Override
    public void sadd(String key, String... members) {
        try {
            redisTemplate.opsForSet().add(key, members);
        } catch (Exception e) {
            log.warn("Redis SADD failed: key={}", key, e);
        }
    }

    @Override
    public void srem(String key, String... members) {
        try {
            redisTemplate.opsForSet().remove(key, (Object[]) members);
        } catch (Exception e) {
            log.warn("Redis SREM failed: key={}", key, e);
        }
    }

    @Override
    public Set<String> smembers(String key) {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(key);
            if (members == null) {
                return Collections.emptySet();
            }
            return members.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Redis SMEMBERS failed: key={}", key, e);
            return Collections.emptySet();
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis EXISTS failed: key={}", key, e);
            return false;
        }
    }

    @Override
    public Set<String> keys(String pattern) {
        try {
            return redisTemplate.keys(pattern);
        } catch (Exception e) {
            log.warn("Redis KEYS failed: pattern={}", pattern, e);
            return Collections.emptySet();
        }
    }

    @Override
    public void publishAsync(String channel, Object message) {
        try {
            redisTemplate.convertAndSend(channel, message);
        } catch (Exception e) {
            log.warn("Redis PUBLISH failed: channel={}", channel, e);
        }
    }

    @Override
    public void subscribe(String channel, MessageListener listener) {
        listenerContainer.addMessageListener(listener, new ChannelTopic(channel));
        log.info("Subscribed to Redis channel: {}", channel);
    }

    @Override
    public void unsubscribe(String channel, MessageListener listener) {
        listenerContainer.removeMessageListener(listener, new ChannelTopic(channel));
        log.info("Unsubscribed from Redis channel: {}", channel);
    }
}
