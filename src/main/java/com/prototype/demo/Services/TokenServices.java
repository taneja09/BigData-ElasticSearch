package com.prototype.demo.Services;

import org.springframework.stereotype.Service;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import io.jsonwebtoken.*;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

/**
 * Validator used to check whether given string is
 * no longer than the specified amount of characters.
 *
 * @author Divinity
 */
@Service
public class TokenServices {

    private static String SecretKey = "9a8b7c6d5e4f3g2h1i090909";

    public static String generateJWTToken(){
        UUID uuid = UUID.randomUUID();
        String id = uuid.toString();  //random string to generate a different token every time
        String subject = "InsurancePlan"; //subject to build jwt token
        String iss = "ABCHealthInsurance";  //issuer for the jwt token
        long ttl = 120000; //2 minutes of expiration duration for jwt token

        //setting the signature algorithm to have HMAC (hash + SH256)
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

        //generating signing key using the secret key provided
        byte[] apiKeySecretBytes = DatatypeConverter.parseBase64Binary(SecretKey);
        Key signingKey = new SecretKeySpec(apiKeySecretBytes, signatureAlgorithm.getJcaName());

        //Setting the JWT Claims with ID, timestamp, subject, issuer and signature key + signing key
        Date now = new Date();
        JwtBuilder builder = Jwts.builder().setId(id)
                .setIssuedAt(now)
                .setSubject(subject)
                .setIssuer(iss)
                .signWith(signatureAlgorithm, signingKey);

        //setting expiration for the token with jwt builder
        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis + ttl;
        Date exp = new Date(expMillis);
        builder.setExpiration(exp);

        return builder.compact();

    }

    //Getting the claims back
    public static Claims decodeJWT(String jwt) {
        //This line will throw an exception if it is not a signed JWS
        Claims claims = null;
        try {
             claims = Jwts.parser()
                    .setSigningKey(DatatypeConverter.parseBase64Binary(SecretKey))
                    .parseClaimsJws(jwt).getBody();
        }catch(Exception e) {
            System.out.println("No Signed claim is found");
        }

        return claims;
    }

    //authorizing the claims as set
    public static Boolean Authorize(String jwt){
        Claims claim = decodeJWT(jwt);
        if(claim != null){
            if(claim.getSubject().equals("InsurancePlan") && claim.getIssuer().equals("ABCHealthInsurance"))
                return true;
        }
        return false;
    }
}
