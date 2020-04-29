package com.prototype.demo.Services;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
/**
 * Validator used to check whether given string is
 * no longer than the specified amount of characters.
 *
 * @author Divinity
 */

@Service
public class Services {

    private RedisTemplate<String,String> template;
    private ValueOperations valOperations;

    public Services(RedisTemplate<String, String> redisTemplate) {
        this.template = redisTemplate;
        valOperations = template.opsForValue();

    }

    public void save(Object planKey, Object plan) {
        valOperations.set(planKey.toString(),plan.toString());
    }

    public String findById(String planKey) {
       Object ob =  valOperations.get(planKey);
       if(ob == null ){
           return  "";
       }else {
           return ob.toString();
       }
    }

    public boolean delete(String planKey) {
      Boolean delSuccess  = template.delete(planKey);
      return delSuccess;
    }

}
