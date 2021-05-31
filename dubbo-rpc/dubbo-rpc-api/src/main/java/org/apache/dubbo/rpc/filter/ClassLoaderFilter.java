package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

/**
 * Provider端的Filter实现，
 * 主要功能是切换类加载器
 * Set the current execution thread class loader to service interface's class loader.
 */
@Activate(group = CommonConstants.PROVIDER, order = -30000)
public class ClassLoaderFilter implements Filter {

    /**
     * 首先获取当前线程关联的 contextClassLoader，
     * 然后将其 ContextClassLoader 设置为 invoker.getInterface().getClassLoader()，也就是加载服务接口类的类加载器；
     * 之后执行 invoker.invoke() 方法，执行后续的 Filter 逻辑以及业务逻辑；
     * 最后，将当前线程关联的 contextClassLoader 重置为原来的 contextClassLoader
     */
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        //更新当前线程绑定的ClassLoader
        ClassLoader ocl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(invoker.getInterface().getClassLoader());
        try {
            return invoker.invoke(invocation);
        } finally {
            Thread.currentThread().setContextClassLoader(ocl);
        }
    }

}
