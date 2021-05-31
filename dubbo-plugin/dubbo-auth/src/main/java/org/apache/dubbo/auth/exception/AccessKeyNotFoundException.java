package org.apache.dubbo.auth.exception;


import org.apache.dubbo.auth.model.AccessKeyPair;

/**
 * Signals that an attempt to get the {@link AccessKeyPair} has failed.
 */
public class AccessKeyNotFoundException extends Exception {
    private static final long serialVersionUID = 7106108446396804404L;

    public AccessKeyNotFoundException() {
    }

    public AccessKeyNotFoundException(String message) {
        super(message);
    }


}
