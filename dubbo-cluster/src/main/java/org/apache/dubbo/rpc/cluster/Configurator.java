/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.dubbo.rpc.cluster.Constants.PRIORITY_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;

/**
 * 抽象了一条配置信息，
 * 提供了将配置 URL 解析成 Configurator 对象的工具方法
 *
 * Configurator. (SPI, Prototype, ThreadSafe)
 *
 */
public interface Configurator extends Comparable<Configurator> {

    /**
     * 获取该 Configurator 对象对应的配置 URL，例如前文介绍的 override 协议 URL
     * Get the configurator url.
     *
     * @return configurator url.
     */
    URL getUrl();

    /**
     * configure()方法接收的参数是原始 URL，返回经过 Configurator 修改后的 URL
     * Configure the provider url.
     *
     * @param url - old provider url.
     * @return new provider url.
     */
    URL configure(URL url);


    /**
     * toConfigurators()工具方法可以将多个配置 URL 对象解析成相应的 configurator 对象
     * Convert override urls to map for use when re-refer. Send all rules every time, the urls will be reassembled and
     * calculated
     *
     * URL contract:
     * <ol>
     * <li>override://0.0.0.0/...( or override://ip:port...?anyhost=true)&para1=value1... means global rules
     * (all of the providers take effect)</li>
     * <li>override://ip:port...?anyhost=false Special rules (only for a certain provider)</li>
     * <li>override:// rule is not supported... ,needs to be calculated by registry itself</li>
     * <li>override://0.0.0.0/ without parameters means clearing the override</li>
     * </ol>
     *
     * @param urls URL list to convert
     * @return converted configurator list
     */
    static Optional<List<Configurator>> toConfigurators(List<URL> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            return Optional.empty();
        }

        //创建ConfiguratorFactory适配器
        ConfiguratorFactory configuratorFactory = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                .getAdaptiveExtension();

        List<Configurator> configurators = new ArrayList<>(urls.size());  //记录解析结果
        for (URL url : urls) {
            //遇到 empty 协议，直接清空 configurators 集合，结束解析，返回空集合
            if (EMPTY_PROTOCOL.equals(url.getProtocol())) {
                configurators.clear();
                break;
            }
            Map<String, String> override = new HashMap<>(url.getParameters());
            //The anyhost parameter of override may be added automatically, it can't change the judgement of changing url
            override.remove(ANYHOST_KEY);
            if (override.size() == 0) {  //如果该配置 URL 没有携带任何参数，则跳过该 URL
                configurators.clear();
                continue;
            }
            //通过 ConfiguratorFactory 适配器选择合适 ConfiguratorFactory 扩展，并创建 Configurator 对象
            configurators.add(configuratorFactory.getConfigurator(url));
        }
        Collections.sort(configurators);  //排序
        return Optional.of(configurators);
    }

    /**
     * 排序首先按照 ip 进行排序，所有 ip 的优先级都高于 0.0.0.0，当 ip 相同时，会按照 priority 参数值进行排序
     * Sort by host, then by priority
     * 1. the url with a specific host ip should have higher priority than 0.0.0.0
     * 2. if two url has the same host, compare by priority value；
     */
    @Override
    default int compareTo(Configurator o) {
        if (o == null) {
            return -1;
        }

        int ipCompare = getUrl().getHost().compareTo(o.getUrl().getHost());
        // host is the same, sort by priority
        if (ipCompare == 0) {
            int i = getUrl().getParameter(PRIORITY_KEY, 0);
            int j = o.getUrl().getParameter(PRIORITY_KEY, 0);
            return Integer.compare(i, j);
        } else {
            return ipCompare;
        }
    }
}
