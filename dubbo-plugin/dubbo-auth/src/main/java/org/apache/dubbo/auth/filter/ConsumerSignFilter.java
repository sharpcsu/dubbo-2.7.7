package org.apache.dubbo.auth.filter;

import org.apache.dubbo.auth.Constants;
import org.apache.dubbo.auth.spi.Authenticator;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

/**
 * The ConsumerSignFilter
 *
 * @see org.apache.dubbo.rpc.Filter
 */
@Activate(group = CommonConstants.CONSUMER, order = -10000)
public class ConsumerSignFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        URL url = invoker.getUrl();
        boolean shouldAuth = url.getParameter(Constants.SERVICE_AUTH, false);
        if (shouldAuth) {
            Authenticator authenticator = ExtensionLoader.getExtensionLoader(Authenticator.class)
                    .getExtension(url.getParameter(Constants.AUTHENTICATOR, Constants.DEFAULT_AUTHENTICATOR));
            authenticator.sign(invocation, url);
        }
        return invoker.invoke(invocation);
    }
}
