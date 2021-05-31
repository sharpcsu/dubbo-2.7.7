package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

import java.util.Set;

import static org.apache.dubbo.rpc.Constants.DEPRECATED_KEY;

/**
 * DeprecatedFilter logs error message if a invoked method has been marked as deprecated. To check whether a method
 * is deprecated or not it looks for <b>deprecated</b> attribute value and consider it is deprecated it value is <b>true</b>
 *
 * @see Filter
 */
@Activate(group = CommonConstants.CONSUMER, value = DEPRECATED_KEY)
public class DeprecatedFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeprecatedFilter.class);

    private static final Set<String> LOGGED = new ConcurrentHashSet<String>();

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String key = invoker.getInterface().getName() + "." + invocation.getMethodName();
        if (!LOGGED.contains(key)) {
            LOGGED.add(key);
            if (invoker.getUrl().getMethodParameter(invocation.getMethodName(), DEPRECATED_KEY, false)) {
                LOGGER.error("The service method " + invoker.getInterface().getName() + "." + getMethodSignature(invocation) + " is DEPRECATED! Declare from " + invoker.getUrl());
            }
        }
        return invoker.invoke(invocation);
    }

    private String getMethodSignature(Invocation invocation) {
        StringBuilder buf = new StringBuilder(invocation.getMethodName());
        buf.append("(");
        Class<?>[] types = invocation.getParameterTypes();
        if (types != null && types.length > 0) {
            boolean first = true;
            for (Class<?> type : types) {
                if (first) {
                    first = false;
                } else {
                    buf.append(", ");
                }
                buf.append(type.getSimpleName());
            }
        }
        buf.append(")");
        return buf.toString();
    }

}
