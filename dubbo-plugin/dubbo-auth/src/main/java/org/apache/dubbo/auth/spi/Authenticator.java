package org.apache.dubbo.auth.spi;


import org.apache.dubbo.auth.exception.RpcAuthenticationException;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.rpc.Invocation;

@SPI("accessKey")
public interface Authenticator {

    /**
     * give a sign to request
     *
     * @param invocation
     * @param url
     */
    void sign(Invocation invocation, URL url);


    /**
     * verify the signature of the request is valid or not
     * @param invocation
     * @param url
     * @throws RpcAuthenticationException when failed to authenticate current invocation
     */
    void authenticate(Invocation invocation, URL url) throws RpcAuthenticationException;
}
