package org.apache.dubbo.configcenter.support.apollo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.AbstractDynamicConfigurationFactory;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;

/**
 *
 */
public class ApolloDynamicConfigurationFactory extends AbstractDynamicConfigurationFactory {
    @Override
    protected DynamicConfiguration createDynamicConfiguration(URL url) {
        return new ApolloDynamicConfiguration(url);
    }
}
