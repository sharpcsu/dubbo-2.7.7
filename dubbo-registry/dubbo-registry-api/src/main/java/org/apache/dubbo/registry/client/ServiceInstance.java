package org.apache.dubbo.registry.client;

import java.io.Serializable;
import java.util.Map;

/**
 * The model class of an instance of a service, which is used for service registration and discovery.
 * <p>
 *
 * 唯一标识一个服务实例
 * @since 2.7.5
 */
public interface ServiceInstance extends Serializable {

    /**
     * 唯一标识
     */
    String getId();

    /**
     * 获取当前ServiceInstance所属的Service Name
     */
    String getServiceName();

    /**
     * 获取当前ServiceInstance的host
     */
    String getHost();

    /**
     * 获取当前ServiceInstance的port
     */
    Integer getPort();

    /**
     * 当前ServiceInstance的状态
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 检测当前ServiceInstance的状态
     */
    default boolean isHealthy() {
        return true;
    }

    /**
     * 获取当前ServiceInstance关联的元数据，这些元数据以KV格式存储
     */
    Map<String, String> getMetadata();

    /**
     * 计算当前ServiceInstance对象的hashCode值
     */
    int hashCode();

    /**
     * 比较两个ServiceInstance对象
     */
    boolean equals(Object another);

}
