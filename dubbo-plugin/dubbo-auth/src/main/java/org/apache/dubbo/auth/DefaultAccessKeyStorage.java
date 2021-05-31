package org.apache.dubbo.auth;


import org.apache.dubbo.auth.model.AccessKeyPair;
import org.apache.dubbo.auth.spi.AccessKeyStorage;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;

/**
 *  The default implementation of {@link AccessKeyStorage}
 */
public class DefaultAccessKeyStorage implements AccessKeyStorage {
    @Override
    public AccessKeyPair getAccessKey(URL url, Invocation invocation) {
        AccessKeyPair accessKeyPair = new AccessKeyPair();
        String accessKeyId = url.getParameter(Constants.ACCESS_KEY_ID_KEY);
        String secretAccessKey = url.getParameter(Constants.SECRET_ACCESS_KEY_KEY);
        accessKeyPair.setAccessKey(accessKeyId);
        accessKeyPair.setSecretKey(secretAccessKey);
        return accessKeyPair;
    }
}
