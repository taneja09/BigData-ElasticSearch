package com.prototype.demo.Services;

import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import com.prototype.demo.connections.redisConnection;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Validator used to check whether given string is
 * no longer than the specified amount of characters.
 *
 * @author Divinity
 */
@Service
public class ElasticSearchService {
    private RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200))
            .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder.setConnectTimeout(5000).setSocketTimeout(5000))
    );

    @Autowired
    static String MainQueue = "RedisIndexQueue";
    static String BackupQueue = "BackUpQueue";

    public void indexingPlans() {
            Jedis jedis = redisConnection.getConnection();
            byte[] bytes = jedis.rpoplpush(MainQueue.getBytes(), BackupQueue.getBytes());
            if (bytes != null && bytes.length !=0) {
                try {
                    JSONObject jo = new JSONObject(new String(bytes));
                    String objectType = jo.getString("objectType");
                    String objectId = jo.getString("objectId");
                    restHighLevelClient.index(new IndexRequest("plan_index", objectType, objectId).source(bytes, XContentType.JSON));
                } catch (IOException e) {
                    System.out.println("Error Caused"+e);
                }
                String message = new String(bytes, StandardCharsets.UTF_8);
                System.out.println("Json Object Posted: "+message);
            }
            jedis.close();
    }
}

