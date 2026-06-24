package gj.pf4j.redis;

import org.springframework.data.redis.connection.MessageListener;

public interface IGJRedisBusService {

    void publishAsync(String channel, Object message);

    void subscribe(String channel, MessageListener listener);

    void unsubscribe(String channel, MessageListener listener);
}
