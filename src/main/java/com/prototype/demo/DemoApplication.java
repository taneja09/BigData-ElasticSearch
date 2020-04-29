package com.prototype.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import javax.servlet.Filter;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }


//    @Bean
//    public Filter shallowEtagFilter(){
//
//        return new ShallowEtagHeaderFilter();
//    }

    @Bean
    JedisConnectionFactory jedisConnectionFactory(){
        return new JedisConnectionFactory();
    }

    @Bean
    public RedisTemplate<?, ?> redisTemplate(){
        RedisTemplate<?, ?> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(jedisConnectionFactory());
        return redisTemplate;
    }

//    @Bean
//    public Filter filter(){
//        ShallowEtagHeaderFilter filter=new ShallowEtagHeaderFilter();
//        return filter;
//
//    }

}
