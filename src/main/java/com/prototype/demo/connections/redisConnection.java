package com.prototype.demo.connections;
import redis.clients.jedis.Jedis;

/**
 * Validator used to check whether given string is
 * no longer than the specified amount of characters.
 *
 * @author Divinity
 */
public class redisConnection {

    private static Jedis jedis;

    public static Jedis getConnection() {
         jedis = new Jedis();
        System.out.println("Connected to server sucessfully");
        return jedis;
    }
}
