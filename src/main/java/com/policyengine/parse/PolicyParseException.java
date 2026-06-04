package com.policyengine.parse;

public class PolicyParseException extends RuntimeException {

    public PolicyParseException(String message) {
        super(message);
    }

    public PolicyParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
