package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.TimeoutCountDown;

import java.util.Arrays;

import static org.apache.dubbo.common.constants.CommonConstants.TIME_COUNTDOWN_KEY;

/**
 * 超时时间
 * Log any invocation timeout, but don't stop server from running
 */
@Activate(group = CommonConstants.PROVIDER)
public class TimeoutFilter implements Filter, Filter.Listener {

    private static final Logger logger = LoggerFactory.getLogger(TimeoutFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        return invoker.invoke(invocation);
    }

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        Object obj = RpcContext.getContext().get(TIME_COUNTDOWN_KEY);
        if (obj != null) {
            TimeoutCountDown countDown = (TimeoutCountDown) obj;
            if (countDown.isExpired()) {  //检查结果是否超时
                ((AppResponse) appResponse).clear(); //清理结果信息 clear response in case of timeout.
                if (logger.isWarnEnabled()) {
                    logger.warn("invoke timed out. method: " + invocation.getMethodName() + " arguments: " +
                            Arrays.toString(invocation.getArguments()) + " , url is " + invoker.getUrl() +
                            ", invoke elapsed " + countDown.elapsedMillis() + " ms.");
                }
            }
        }
    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {

    }
}
