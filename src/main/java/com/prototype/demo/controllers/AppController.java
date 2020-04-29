package com.prototype.demo.controllers;

import com.prototype.demo.Services.Services;
import com.prototype.demo.Services.TokenServices;
import com.prototype.demo.Exceptions.InvalidInputExceptions;
import com.prototype.demo.bean.JedisBean;
import com.prototype.demo.Services.ElasticSearchService;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Stack;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

import com.prototype.demo.connections.redisConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.security.NoSuchAlgorithmException;

/**
 * Validator used to check whether given string is
 * no longer than the specified amount of characters.
 *
 * @author Divinity
 */
@RestController
public class AppController {

    @Autowired
    Services services;

    @Autowired
    ElasticSearchService elasticSearch;

    Stack<String> eTagStack = new Stack();

    Jedis jedis = redisConnection.getConnection();

    @RequestMapping("/")
    public String index() {
        return "Big-Data indexing - prototype demo!";
    }

    @GetMapping("/token")
    public String generateToken() throws NoSuchAlgorithmException {
       return TokenServices.generateJWTToken();
    }

    @PostMapping(path = "/plan", consumes = "application/json")
    public ResponseEntity addHealthPlan(@RequestHeader HttpHeaders reqHeader, @RequestBody String body, HttpServletResponse response) throws IOException, ProcessingException, JSONException {
        //Verify the bearer token sent along with the request
        Boolean authorizationFlag = OauthValidation(reqHeader);
        if (!authorizationFlag) return new ResponseEntity("Invalid or Expired token", HttpStatus.BAD_REQUEST);

        //If token is verified retrieve json schema
        Boolean isSchemaValid = false;
        String jsonValueString = body;
        File schemaFile = new ClassPathResource("./static/applicationSchema.json").getFile();

        //Validate if the incoming data is in sync with json schema
        final JsonSchema jsonSchema = ValidationsUtil.getSchemaNode(schemaFile);
        final JsonNode jsonNode = ValidationsUtil.getJsonNode(jsonValueString);

        if (ValidationsUtil.isJsonValid(jsonSchema, jsonNode)) {
            isSchemaValid = true;
        }

        //convert request body string to JSON Object
        JSONObject jsonObject = new JSONObject(body);
        Object KEY = jsonObject.getString("objectId");

        if (isSchemaValid) {
            //Check if there is already a plan with same Key
            if (!services.findById(KEY.toString()).isEmpty()) {
                JsonParser parser = new JsonParser();
                JsonObject jOutput = parser.parse("{\"Duplicate\": \"Plan already Exist !! \"}").getAsJsonObject();
                return new ResponseEntity < Object > (jOutput.toString(), HttpStatus.BAD_REQUEST);
            } else {
                JSONObject reObj = JedisBean.insertPlan(jsonObject);
                elasticSearch.indexingPlans();
                if (reObj != null) {
                    return new ResponseEntity<Object>(reObj.toMap(), HttpStatus.CREATED);
                } else {
                    JsonParser parser = new JsonParser();
                    JsonObject errRes = parser.parse("{\"error\": \"Couldn't add a new plan\"}").getAsJsonObject();
                    return new ResponseEntity < > (errRes.toString(), HttpStatus.NOT_FOUND);
                }
            }
        } else {
            throw new InvalidInputExceptions("JSON Schema is invalid");
        }
    }


    @GetMapping("/plan/{planId}")
    public ResponseEntity <Object> getPlanWithId(@RequestHeader HttpHeaders reqHeader, @PathVariable String planId, HttpServletResponse response)  {
       // Verify the bearer token sent along with the request
        Boolean authorizationFlag = OauthValidation(reqHeader);
        if (!authorizationFlag) return new ResponseEntity("Invalid or Expired token", HttpStatus.BAD_REQUEST);

        String ifNoneMatchValue = reqHeader.getFirst("if-none-match");
        if(ifNoneMatchValue != null && ifNoneMatchValue.length()>0) {
            if (eTagStack.size() > 0 && (eTagStack.peek().equals(ifNoneMatchValue)))
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).body("");
        }

        JSONObject responseObj = JedisBean.getHealthPlan(planId);
        if (responseObj != null) {
            UUID uuid = UUID.randomUUID();
            String randomUUIDString = uuid.toString();
            System.out.println(randomUUIDString);
            eTagStack.push("\""+randomUUIDString + "\"");
                return  ResponseEntity.ok().eTag(randomUUIDString).body(responseObj.toMap());
        } else {
            JsonParser parser = new JsonParser();
            JsonObject errRes = parser.parse("{\"error\": \"Plan with this planId is not found\"}").getAsJsonObject();
            return new ResponseEntity < > (errRes.toString(), HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/plan/{planId}")
    public ResponseEntity < Object > deletePlan(@RequestHeader HttpHeaders reqHeader, @PathVariable String planId) throws IOException {
        //Verify the bearer token sent along with the request
        Boolean authorizationFlag = OauthValidation(reqHeader);
        if (!authorizationFlag) return new ResponseEntity("Invalid or Expired token", HttpStatus.BAD_REQUEST);

        //Boolean delSuccess = services.delete(planId);
        Boolean successDelete = JedisBean.deletePlan(planId);
        if (successDelete) {
            JsonParser parser = new JsonParser();
            JsonObject errRes = parser.parse("{\"Success\": \"Deleted the record with plan Id\"}").getAsJsonObject();
            return new ResponseEntity < Object > (errRes.toString(), HttpStatus.NO_CONTENT);

        } else {
            JsonParser parser = new JsonParser();
            JsonObject errRes = parser.parse("{\"Error\": \"Plan with provided ID doesn't exist\"}").getAsJsonObject();
            return new ResponseEntity < > (errRes.toString(), HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/plan/{planId}")
    public ResponseEntity<String> update(@RequestBody String body, @RequestHeader HttpHeaders reqHeader, @PathVariable String planId) throws IOException, ProcessingException {

       //Verify the bearer token sent along with the request
        Boolean authorizationFlag = OauthValidation(reqHeader);
        if (!authorizationFlag) return new ResponseEntity("Invalid or Expired token", HttpStatus.BAD_REQUEST);

        String ifMatchValue = reqHeader.getFirst("if-match");

        if(ifMatchValue != null && ifMatchValue.length()>0) {
            if (eTagStack.size() > 0 && !(eTagStack.peek().equals(ifMatchValue)))
                return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body("Resource has been modified please GET first for new Etag");
        }

        //If token is verified retrieve json schema
        Boolean isSchemaValid = false;
        String jsonValueString = body;
        File schemaFile = new ClassPathResource("./static/applicationSchema.json").getFile();

        //Validate if the incoming data is in sync with json schema
        final JsonSchema jsonSchema = ValidationsUtil.getSchemaNode(schemaFile);
        final JsonNode jsonNode = ValidationsUtil.getJsonNode(jsonValueString);

        if (ValidationsUtil.isJsonValid(jsonSchema, jsonNode)) {
            isSchemaValid = true;
        }

          JSONObject jsonObject = new JSONObject(body);
          String planUUID = "plan"+"____"+planId;

        if(isSchemaValid) {
            if (!JedisBean.updatePlan(jsonObject))
                return new ResponseEntity<String>("Failed to update JSON instance in Redis", HttpStatus.BAD_REQUEST);

            elasticSearch.indexingPlans();
            UUID uuid = UUID.randomUUID();
            String randomUUIDString = uuid.toString();
            System.out.println(randomUUIDString);
            eTagStack.push("\""+randomUUIDString + "\"");
            return  ResponseEntity.status(HttpStatus.NO_CONTENT).eTag(randomUUIDString).body("\"Updated\":\"JSON instance updated in redis\"");
            //return new ResponseEntity<String>("JSON instance updated in redis", HttpStatus.NO_CONTENT);

        }else
            throw new InvalidInputExceptions("JSON Schema is invalid");
    }

    @PatchMapping("/plan/{planId}")
    public ResponseEntity<String> patchUpdate(@RequestBody(required=true) String body, @RequestHeader HttpHeaders reqHeader,@PathVariable String planId) throws IOException, ProcessingException {

        //Verify the bearer token sent along with the request
        Boolean authorizationFlag = OauthValidation(reqHeader);
        if (!authorizationFlag) return new ResponseEntity("Invalid or Expired token", HttpStatus.BAD_REQUEST);

        //No need of validating schema for patch as its partial request
        String ifMatchValue = reqHeader.getFirst("if-match");

        if(ifMatchValue != null && ifMatchValue.length()>0) {
            if (eTagStack.size() > 0 && !(eTagStack.peek().equals(ifMatchValue)))
                return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body("Resource has been modified please GET first for new Etag");
        }

        JSONObject jsonObject = new JSONObject(body);
        Object KEY = jsonObject.getString("objectId");

        if(!JedisBean.patchPlan(jsonObject))
            return new ResponseEntity<String>("There isn't any instance found to patch", HttpStatus.BAD_REQUEST);

        elasticSearch.indexingPlans();
        UUID uuid = UUID.randomUUID();
        String randomUUIDString = uuid.toString();
        System.out.println(randomUUIDString);
        eTagStack.push("\""+randomUUIDString + "\"");
        return  ResponseEntity.status(HttpStatus.NO_CONTENT).eTag(randomUUIDString).body("\"Updated\":\"JSON instance patched with provided data\"");
    }

    @RequestMapping("/*")
    public ResponseEntity < Object > IncorrectPath() {
        JsonParser parser = new JsonParser();
        JsonObject errRes = parser.parse("{\"Error\": \"Path of URL is incorrect !!\"}").getAsJsonObject();
        return new ResponseEntity < > (errRes.toString(), HttpStatus.NOT_FOUND);
    }

    public Boolean OauthValidation(@RequestHeader HttpHeaders headers)
    {
        String authHeader = headers.getFirst("Authorization");
        if(authHeader==null || authHeader.isEmpty() ||!authHeader.contains("Bearer ")) return false;
        String bearerToken = authHeader.substring(7);
        boolean authorized = TokenServices.Authorize(bearerToken);
        return authorized;
    }
}