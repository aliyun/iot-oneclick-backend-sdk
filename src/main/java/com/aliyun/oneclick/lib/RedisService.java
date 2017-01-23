package com.aliyun.oneclick.lib;

import com.alibaba.fastjson.JSON;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Created by zhirui.rzr on 2016/11/8.
 */
public class RedisService {
    JedisPool jedisPool;
    String prefix;

    public RedisService() {

    }

    public RedisService(JedisPool jedisPool, String prefix) {
        this.jedisPool = jedisPool;
        this.prefix = prefix;
    }


    public RedisService(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.prefix = "";
    }

    public void pushQueue(final String key, final String value) {
        new RedisOp<Long>(){
            @Override
            public Long operate(Jedis redis) {
                return redis.lpush(getRedisKey(key), value.getBytes());
            }
        }.run();
    }

    public String popQueue(final String key) {
        return new RedisOp<String>() {
            @Override
            public String operate(Jedis redis) {
                byte[] data = redis.brpop(0, getRedisKey(key)).get(1);
                return data == null ? null : new String(data);
            }
        }.run();
    }

    public String get(final String key) {
        return new RedisOp<String>() {
            @Override
            public String operate(Jedis redis) {
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

    public void set(final String key, final Object value, final int expire) {
        new RedisOp() {
            @Override
            public Object operate(Jedis redis) {
                if (expire > 0) {
                    redis.setex(key, expire, String.valueOf(value));
                } else {
                    redis.set(key, String.valueOf(value));
                }
                return null;
            }
        }.run();
    }

    public void set(final String key, final Object value) {
        set(key, value, 0);
    }

    public void setObject(final String key, final Object obj, final int expire) {
        set(key, JSON.toJSONString(obj), expire);
    }

    public void setObject(final String key, final Object obj) {
        setObject(key, obj, 0);
    }

    public void del(final String key) {
        new RedisOp() {
            @Override
            protected Object operate(Jedis redis) {
                return redis.del(getRedisKey(key));
            }
        }.run();
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void close() {
        jedisPool.destroy();
    }

    abstract class RedisOp<T> {
        protected abstract T operate(Jedis redis);
        public T run() {
            Jedis redis = jedisPool.getResource();
            try {
                return operate(redis);
            } finally {
                redis.close();
            }
        }
    }
}
