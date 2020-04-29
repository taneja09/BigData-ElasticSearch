package com.prototype.demo.bean;

import org.apache.http.HttpHost;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.json.JSONException;

import org.json.JSONObject;
import org.json.JSONArray;
import redis.clients.jedis.Jedis;
import com.prototype.demo.connections.redisConnection;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Validator used to check whether given string is
 * no longer than the specified amount of characters.
 *
 * @author Divinity
 */
public class JedisBean {
    private static final String DEL = "____";
    static String MainQueue = "RedisIndexQueue";

    //Redis high level client for elasticsearch
    private static RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200))
            .setRequestConfigCallback(requestConfigBuilder ->
            requestConfigBuilder.setConnectTimeout(5000).setSocketTimeout(5000)));

    //Add Plan to Redis
    public static JSONObject insertPlan(JSONObject jsonObject) throws JSONException {
        Jedis jedis = redisConnection.getConnection();
        String idOne = jsonObject.get("objectType").toString() + DEL + jsonObject.get("objectId").toString();
        String id = null;
        String type = null;
        if(insertPlanUtil(jsonObject, idOne)) {
            jedis.rpush(MainQueue.getBytes(), jsonObject.toString().getBytes(StandardCharsets.UTF_8)); //Pushing into queue after redis addition
            id = jsonObject.get("objectId").toString();
            type = jsonObject.get("objectType").toString();
            JSONObject obj = new JSONObject();
            obj.put("ObjectId",id);
            obj.put("ObjectType",type);
            return obj;
        }
        else
            return null;
    }

    private static boolean insertPlanUtil(JSONObject jsonObject, String uuid) {
        try {
            Jedis jedis = redisConnection.getConnection();
            Map<String,String> simpleMap = new HashMap<String,String>();

            for(Object key : jsonObject.keySet()) {
                String attributeKey = String.valueOf(key);
                Object attributeVal = jsonObject.get(String.valueOf(key));
                String edge = attributeKey;
                if(attributeVal instanceof JSONObject) {

                    JSONObject embdObject = (JSONObject) attributeVal;
                    String setKey = uuid + DEL + edge;
                    String embd_uuid = embdObject.get("objectType") + DEL + embdObject.get("objectId").toString();
                    jedis.sadd(setKey, embd_uuid);
                    insertPlanUtil(embdObject, embd_uuid);

                } else if (attributeVal instanceof JSONArray) {

                    JSONArray jsonArray = (JSONArray) attributeVal;
                    Iterator<Object> jsonIterator = jsonArray.iterator();
                    String setKey = uuid + DEL + edge;

                    while(jsonIterator.hasNext()) {
                        JSONObject embdObject = (JSONObject) jsonIterator.next();
                        String embd_uuid = embdObject.get("objectType") + DEL + embdObject.get("objectId").toString();
                        jedis.sadd(setKey, embd_uuid);
                        insertPlanUtil(embdObject, embd_uuid);
                    }

                } else {
                    simpleMap.put(attributeKey, String.valueOf(attributeVal));
                }
            }
            jedis.hmset(uuid, simpleMap);
            jedis.close();
        }
        catch(JedisException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static JSONObject getHealthPlan(String id) {
        JSONObject jsonObject = getHealthPlanUtil("plan" + DEL + id);
        if(jsonObject != null)
            return jsonObject;
        else
            return null;
    }

    private static JSONObject getHealthPlanUtil(String uuid) {

        Jedis jedis = redisConnection.getConnection();
        JSONObject o = new JSONObject();
        System.out.println("Reading keys from pattern");
        Set<String> keys = jedis.keys(uuid+DEL+"*");

        //Recursively retrieving the use case complex object from Redis
        for(String key : keys) {
            Set<String> jsonKeySet = jedis.smembers(key);
            String kv = key.substring(key.lastIndexOf(DEL)+4);
            if(jsonKeySet.size() > 1) {

                JSONArray ja = new JSONArray();
                Iterator<String> jsonKeySetIterator = jsonKeySet.iterator();
                while(jsonKeySetIterator.hasNext()) {
                    ja.put(getHealthPlanUtil(jsonKeySetIterator.next()));
                }
                o.put(kv, ja);
            } else {
                Iterator<String> jsonKeySetIterator = jsonKeySet.iterator();
                JSONObject embdObject = null;
                while(jsonKeySetIterator.hasNext()) {
                    embdObject = getHealthPlanUtil(jsonKeySetIterator.next());
                }
                o.put(kv, embdObject);
            }
        }

        //retrieving the single objects
        Map<String,String> simpleMap = jedis.hgetAll(uuid);
        for(String simpleKey : simpleMap.keySet()) {
            o.put(simpleKey, simpleMap.get(simpleKey));
        }

        jedis.close();
        return o;
    }

    public static boolean deletePlan(String plan_Id) throws IOException {
        //Delete the index from elasticsearch
        restHighLevelClient.delete(new DeleteRequest("plan_index", "plan", plan_Id));
        return deletePlanUtil("plan" + DEL + plan_Id);
    }

    public static boolean deletePlanUtil(String uuid) {
        try {
            Jedis jedis = redisConnection.getConnection();
            // recursively deleting all complex json objects
            Set<String> keys = jedis.keys(uuid+DEL+"*");
            for(String key : keys) {
                Set<String> jsonKeySet = jedis.smembers(key);
                for(String embd_uuid : jsonKeySet) {
                    deletePlanUtil(embd_uuid);
                }
                jedis.del(key);
            }
            // deleting single mappings
            jedis.del(uuid);
            jedis.close();
            return true;
        } catch(JedisException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean updatePlan(JSONObject jsonObject) throws IOException {
        Jedis jedis = redisConnection.getConnection();
        if(updatePlanUtil(jsonObject)){
            String plan_Id = jsonObject.getString("objectId");
            //If plan is successfully updated then delete the existing index from elastic search and push the new case to Jedis queue
            restHighLevelClient.delete(new DeleteRequest("plan_index", "plan", plan_Id));
            jedis.rpush(MainQueue.getBytes(), jsonObject.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        }else
            return false;
    }


    public static boolean updatePlanUtil(JSONObject jsonObject) {
        try {
            Jedis jedis = redisConnection.getConnection();
            String uuid = jsonObject.getString("objectType") + DEL + jsonObject.getString("objectId");
            Map<String,String> simpleMap = jedis.hgetAll(uuid);
            if(simpleMap.isEmpty()) {
                simpleMap = new HashMap<>();
            }

            for(Object key : jsonObject.keySet()) {
                String attributeKey = String.valueOf(key);
                Object attributeVal = jsonObject.get(String.valueOf(key));
                String edge = attributeKey;

                if(attributeVal instanceof JSONObject) {

                    JSONObject embdObject = (JSONObject) attributeVal;
                    String setKey = uuid + DEL + edge;
                    String embd_uuid = embdObject.get("objectType") + DEL + embdObject.getString("objectId");
                    jedis.sadd(setKey, embd_uuid);
                    updatePlanUtil(embdObject);

                } else if (attributeVal instanceof JSONArray) {

                    JSONArray jsonArray = (JSONArray) attributeVal;
                    Iterator<Object> jsonIterator = jsonArray.iterator();
                    String setKey = uuid + DEL + edge;

                    while(jsonIterator.hasNext()) {
                        JSONObject embdObject = (JSONObject) jsonIterator.next();
                        String embd_uuid = embdObject.get("objectType") + DEL + embdObject.getString("objectId");
                        jedis.sadd(setKey, embd_uuid);
                        updatePlanUtil(embdObject);
                    }

                } else {
                    simpleMap.put(attributeKey, String.valueOf(attributeVal));
                }
            }
            jedis.hmset(uuid, simpleMap);
            jedis.close();
            return true;

        } catch(JedisException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean patchPlan(JSONObject jsonObject) {
        try {
            Jedis jedis = redisConnection.getConnection();
            String uuid = jsonObject.getString("objectType") + DEL + jsonObject.getString("objectId");
            Map<String, String> simpleMap = jedis.hgetAll(uuid);
            if (simpleMap.isEmpty()) {
                return false;
            }
            if(patchUpdatePlan(jsonObject)) {
                String plan_Id = jsonObject.getString("objectId");
                //restHighLevelClient.delete(new DeleteRequest("plan_index", "plan", plan_Id));
                //If the plan is successfully patched, push the patched plan to redis queue.
                jedis.rpush(MainQueue.getBytes(), jsonObject.toString().getBytes(StandardCharsets.UTF_8));
                return true;
            }else
                return false;

        }catch(JedisException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean patchUpdatePlan(JSONObject jsonObject){
        try {
            Jedis jedis = redisConnection.getConnection();
            String uuid = jsonObject.getString("objectType") + DEL + jsonObject.getString("objectId");
            Map<String, String> simpleMap = jedis.hgetAll(uuid);
            if(simpleMap.isEmpty()) {
                simpleMap = new HashMap<>();
            }

            for(Object key : jsonObject.keySet()) {
                String attributeKey = String.valueOf(key);
                Object attributeVal = jsonObject.get(String.valueOf(key));
                String edge = attributeKey;

                if(attributeVal instanceof JSONObject) {
                    JSONObject embdObject = (JSONObject) attributeVal;
                    String setKey = uuid + DEL + edge;
                    String embd_uuid = embdObject.get("objectType") + DEL + embdObject.getString("objectId");
                    jedis.sadd(setKey, embd_uuid);
                    patchUpdatePlan(embdObject);

                } else if (attributeVal instanceof JSONArray) {

                    JSONArray jsonArray = (JSONArray) attributeVal;
                    Iterator<Object> jsonIterator = jsonArray.iterator();
                    String setKey = uuid + DEL + edge;

                    while(jsonIterator.hasNext()) {
                        JSONObject embdObject = (JSONObject) jsonIterator.next();
                        String embd_uuid = embdObject.get("objectType") + DEL + embdObject.getString("objectId");
                        jedis.sadd(setKey, embd_uuid);
                        patchUpdatePlan(embdObject);
                    }

                } else {
                    simpleMap.put(attributeKey, String.valueOf(attributeVal));
                }
            }
            jedis.hmset(uuid, simpleMap);
            jedis.close();
            return true;

        }catch(JedisException e) {
            e.printStackTrace();
            return false;
        }

    }
}
