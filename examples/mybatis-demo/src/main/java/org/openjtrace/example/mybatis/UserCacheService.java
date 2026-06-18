package org.openjtrace.example.mybatis;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserCacheService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Cacheable(value = "user-cache")
    public String getUserCached(int id) {
        return "user:" + id;
    }

    public void updateSession(String token, String data) {
        // 静态分析器将提取 "user:session" 这个前缀字面量
        redisTemplate.opsForValue().set("user:session:" + token, data);
    }
}
