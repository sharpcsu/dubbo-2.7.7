package org.apache.dubbo.rpc;

import org.apache.dubbo.common.extension.SPI;

/**
 * 监听服务发布事件以及取消暴露事件
 * ExporterListener. (SPI, Singleton, ThreadSafe)
 */
@SPI
public interface ExporterListener {

    /**
     * 当有服务发布的时候，会触发该方法
     * The exporter exported.
     *
     * @param exporter
     * @throws RpcException
     * @see org.apache.dubbo.rpc.Protocol#export(Invoker)
     */
    void exported(Exporter<?> exporter) throws RpcException;

    /**
     * 当有服务取消发布的时候，会触发该方法
     * The exporter unexported.
     *
     * @param exporter
     * @throws RpcException
     * @see org.apache.dubbo.rpc.Exporter#unexport()
     */
    void unexported(Exporter<?> exporter);

}