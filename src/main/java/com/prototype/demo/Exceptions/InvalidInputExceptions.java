package com.prototype.demo.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
/**
 * Validator used to check whether given string is
 * no longer than the specified amount of characters.
 *
 * @author Divinity
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidInputExceptions extends RuntimeException {
        public InvalidInputExceptions(String message) {
                super(message);
        }
    }
