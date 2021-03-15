package org.apache.dubbo.metadata.report;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public interface MetadataReport {

    /**
     * 存储Provider元数据
     */
    void storeProviderMetadata(MetadataIdentifier providerMetadataIdentifier, ServiceDefinition serviceDefinition);

    /**
     * 存储Consumer元数据
     */
    void storeConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, Map<String, String> serviceParameterMap);

    /**
     * 存储、删除Service元数据
     */
    void saveServiceMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url);

    /**
     *
     */
    void removeServiceMetadata(ServiceMetadataIdentifier metadataIdentifier);

    /**
     * 查询暴露的URL
     */
    List<String> getExportedURLs(ServiceMetadataIdentifier metadataIdentifier);

    /**
     * 查询订阅数据
     */
    void saveSubscribedData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, Set<String> urls);

    /**
     *
     */
    List<String> getSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier);

    /**
     * 查询ServiceDefinition
     */
    String getServiceDefinition(MetadataIdentifier metadataIdentifier);
}
