package org.apache.dubbo.rpc;

/**
 * Exporter. (API/SPI, Prototype, ThreadSafe)
 *
 * @see org.apache.dubbo.rpc.Protocol#export(Invoker)
 * @see org.apache.dubbo.rpc.ExporterListener
 * @see org.apache.dubbo.rpc.protocol.AbstractExporter
 */
public interface Exporter<T> {

    /**
     * 获取底层封装的Invoker对象
     * get invoker.
     *
     * @return invoker
     */
    Invoker<T> getInvoker();

    /**
     * 取消发布底层的Invoker对象
     * unexport.
     * <p>
     * <code>
     * getInvoker().destroy();
     * </code>
     */
    void unexport();

}