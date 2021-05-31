package org.apache.dubbo.rpc;

import org.apache.dubbo.common.extension.SPI;

/**
 * 监听Consumer引用服务时触发的事件
 * InvokerListener. (SPI, Singleton, ThreadSafe)
 */
@SPI
public interface InvokerListener {

    /**
     * 当服务引用的时候，会触发该方法
     * The invoker referred
     *
     * @param invoker
     * @throws RpcException
     * @see org.apache.dubbo.rpc.Protocol#refer(Class, org.apache.dubbo.common.URL)
     */
    void referred(Invoker<?> invoker) throws RpcException;

    /**
     * 当销毁引用服务的时候，会触发该方法
     * The invoker destroyed.
     *
     * @param invoker
     * @see org.apache.dubbo.rpc.Invoker#destroy()
     */
    void destroyed(Invoker<?> invoker);

}