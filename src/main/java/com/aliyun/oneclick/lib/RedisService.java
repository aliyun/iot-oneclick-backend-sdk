package com.aliyun.oneclick.lib;

import com.alibaba.fastjson.JSON;
import org.springframework.data.redis.connection.jedis.JedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

/**
 * Created by zhirui.rzr on 2016/11/8.
 */
public class RedisService {
    JedisConnectionFactory connectionFactory;
    String prefix;

    public RedisService() {

    }

    public RedisService(JedisConnectionFactory connectionFactory, String prefix) {
        this.connectionFactory = connectionFactory;
        this.prefix = "";
    }

    public RedisService(JedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void pushQueue(final String key, final String value) {
        new RedisOp<Long>(){
            @Override
            public Long operate(JedisConnection redis) {
                return redis.lPush(getRedisKey(key), value.getBytes());
            }
        }.run();
    }

    public String popQueue(final String key) {
        return new RedisOp<String>() {
            @Override
            public String operate(JedisConnection redis) {
                byte[] data = redis.bRPop(0, getRedisKey(key)).get(1);
                return data == null ? null : new String(data);
            }
        }.run();
    }

    public String get(final String key) {
        return new RedisOp<String>() {
            @Override
            public String operate(JedisConnection redis) {
                byte[] data = redis.get(getRedisKey(key));
                return data == null ? null : new String(data);
            }
        }.run();
    }

    public String get(final String key, final String defVal) {
        String val = get(key);
        if (val == null) return defVal;
        return val;
    }

    public <T> T get(final String key, Class<T> clazz) {
        String val = get(key);
        if (val == null) return null;
        return JSON.parseObject(val, clazz);
    }

    public Long getLong(final String key) {
        String val = get(key);
        if (val == null) return null;
        return Long.parseLong(val);
    }

    public long get(final String key, final long defVal) {
        Long val = getLong(key);
        if (val == null) return defVal;
        return val;
    }

    private byte[] getRedisKey(String key) {
        return (prefix + key).getBytes();
    }

    public void set(final String key, final Object value, final long expire) {
        new RedisOp() {
            @Override
            public Object operate(JedisConnection redis) {
                byte[] keyBytes = getRedisKey(key);
                redis.set(keyBytes, String.valueOf(value).getBytes());
                if (expire > 0) redis.expire(keyBytes, expire);
                return null;
            }
        }.run();
    }

    public void set(final String key, final Object value) {
        set(key, value, 0);
    }

    public void setObject(final String key, final Object obj, final long expire) {
        set(key, JSON.toJSONString(obj), expire);
    }

    public void setObject(final String key, final Object obj) {
        setObject(key, obj, 0);
    }

    public void del(final String key) {
        new RedisOp() {
            @Override
            protected Object operate(JedisConnection redis) {
                return redis.del(getRedisKey(key));
            }
        }.run();
    }

    public JedisConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(JedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    abstract class RedisOp<T> {
        protected abstract T operate(JedisConnection redis);
        public T run() {
            JedisConnection redis = connectionFactory.getConnection();
            try {
                return operate(redis);
            } finally {
                redis.close();
            }
        }
    }
}
