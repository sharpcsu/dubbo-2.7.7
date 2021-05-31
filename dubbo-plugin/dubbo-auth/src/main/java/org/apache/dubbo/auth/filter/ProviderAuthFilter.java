package org.apache.dubbo.auth.filter;

import org.apache.dubbo.auth.Constants;
import org.apache.dubbo.auth.spi.Authenticator;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

@Activate(group = CommonConstants.PROVIDER, order = -10000)
public class ProviderAuthFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        URL url = invoker.getUrl();
        boolean shouldAuth = url.getParameter(Constants.SERVICE_AUTH, false);
        if (shouldAuth) {
            Authenticator authenticator = ExtensionLoader.getExtensionLoader(Authenticator.class)
                    .getExtension(url.getParameter(Constants.AUTHENTICATOR, Constants.DEFAULT_AUTHENTICATOR));
            try {
                authenticator.authenticate(invocation, url);
            } catch (Exception e) {
                return AsyncRpcResult.newDefaultAsyncResult(e, invocation);
            }
        }
        return invoker.invoke(invocation);
    }


}
