package gj.pf4j.redis;

import java.time.Duration;
import java.util.Set;

public interface IGJRedisService {

    void setWithTtl(String key, Object value, Duration ttl);

    Object get(String key);

    void del(String key);

    boolean setIfAbsent(String key, String value, Duration ttl);

    void sadd(String key, String... members);

    void srem(String key, String... members);

    Set<String> smembers(String key);

    boolean exists(String key);

    Set<String> keys(String pattern);
}
