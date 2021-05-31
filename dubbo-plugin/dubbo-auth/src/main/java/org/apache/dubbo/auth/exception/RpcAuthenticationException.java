package org.apache.dubbo.auth.exception;


public class RpcAuthenticationException extends Exception {
    public RpcAuthenticationException() {
    }

    public RpcAuthenticationException(String message) {
        super(message);
    }

    public RpcAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
