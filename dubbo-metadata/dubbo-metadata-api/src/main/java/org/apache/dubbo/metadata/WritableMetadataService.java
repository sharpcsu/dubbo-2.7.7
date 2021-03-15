package org.apache.dubbo.metadata;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.metadata.store.InMemoryWritableMetadataService;
import org.apache.dubbo.rpc.model.ApplicationModel;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;

/**
 *
 */
@SPI(DEFAULT_METADATA_STORAGE_TYPE)
public interface WritableMetadataService extends MetadataService {
    /**
     *
     */
    @Override
    default String serviceName() {
        // ServiceName默认是从ApplicationModel中获取
        // ExtensionLoader、DubboBootstrap以及ApplicationModel是单个Dubbo进程范围内的单例对象，
        // ExtensionLoader用于Dubbo SPI机制加载扩展实现，DubboBootstrap用于启动Dubbo进程，
        // ApplicationModel用于表示一个Dubbo实例，其中维护了多个ProviderModel对象表示当前Dubbo实例发布的服务，
        // 维护了多个ConsumerModel对象表示当前Dubbo实例引用的服务。
        return ApplicationModel.getApplication();
    }

    /**
     * 发布该URL所代表的服务
     */
    boolean exportURL(URL url);

    /**
     * 注销该URL所代表的服务
     */
    boolean unexportURL(URL url);

    /**
     * 刷新元数据
     */
    default boolean refreshMetadata(String exportedRevision, String subscribedRevision) {
        return true;
    }

    /**
     * 订阅该URL所代表的服务
     */
    boolean subscribeURL(URL url);

    /**
     * 取消订阅该URL所代表的服务
     */
    boolean unsubscribeURL(URL url);

    /**
     * 发布Provider端的ServiceDefinition
     */
    void publishServiceDefinition(URL providerUrl);

    /**
     * 获取WritableMetadataService的默认扩展实现
     */
    static WritableMetadataService getDefaultExtension() {
        return getExtensionLoader(WritableMetadataService.class).getDefaultExtension();
    }

    /**
     * 获取WritableMetadataService接口指定的扩展实现（无指定扩展名称，则返回默认扩展实现）
     */
    static WritableMetadataService getExtension(String name) {
        return getExtensionLoader(WritableMetadataService.class).getOrDefaultExtension(name);
    }
}
