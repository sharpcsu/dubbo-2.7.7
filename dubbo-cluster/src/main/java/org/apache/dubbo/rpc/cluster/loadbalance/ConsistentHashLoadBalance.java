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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;

/**
 * 使用一致性 Hash 算法实现负载均衡
 * 可以让参数相同的请求每次都路由到相同的服务节点
 * ConsistentHashLoadBalance
 */
public class ConsistentHashLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "consistenthash";

    /**
     * Hash nodes name
     */
    public static final String HASH_NODES = "hash.nodes";

    /**
     * Hash arguments name
     */
    public static final String HASH_ARGUMENTS = "hash.arguments";

    private final ConcurrentMap<String, ConsistentHashSelector<?>> selectors = new ConcurrentHashMap<String, ConsistentHashSelector<?>>();

    /**
     * 根据 ServiceKey 和 methodName 选择一个 ConsistentHashSelector对象
     */
    @SuppressWarnings("unchecked")
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        //获取调用的方法名称
        String methodName = RpcUtils.getMethodName(invocation);
        //将 ServiceKey 和方法拼接起来，构成一个 key
        String key = invokers.get(0).getUrl().getServiceKey() + "." + methodName;
        // using the hashcode of list to compute the hash only pay attention to the elements in the list
        //注意：这是为了在 invokers 列表发生变化时都会重新生成 ConsistentHashSelector 对象
        int invokersHashCode = invokers.hashCode();
        //根据 key 获取对应的 ConsistentHashSelector 对象，selectors 是一个 ConcurrentMap<String, ConsistentHashSelector> 集合
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectors.get(key);
        if (selector == null || selector.identityHashCode != invokersHashCode) {  //未查到 ConsistentHashSelector 对象，则进行创建
            selectors.put(key, new ConsistentHashSelector<T>(invokers, methodName, invokersHashCode));
            selector = (ConsistentHashSelector<T>) selectors.get(key);
        }
        //通过 ConsistentHashSelector 对象选择一个 Invoker 对象
        return selector.select(invocation);
    }

    private static final class ConsistentHashSelector<T> {

        /**
         * 用于记录虚拟 Invoker 对象的 Hash 环，使用 TreeMap 实现 Hash 环
         */
        private final TreeMap<Long, Invoker<T>> virtualInvokers;

        /**
         * 虚拟 Invoker 个数
         */
        private final int replicaNumber;

        /**
         * Invoker 集合的 HashCode 值
         */
        private final int identityHashCode;

        /**
         * 需要参数 Hash 计算的参数索引
         */
        private final int[] argumentIndex;

        /**
         * 构造方法
         * 1. 构建 Hash 槽
         * 2. 确认参与一致性 Hash 计算的参数，默认是第一个参数
         */
        ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) {
            //初始化 virtualInvokers字段，即虚拟 Hash 槽
            this.virtualInvokers = new TreeMap<Long, Invoker<T>>();
            //记录 Invoker 集合的hashCode，用该 hashCode 值来判断 Provider列表是否发生了变化
            this.identityHashCode = identityHashCode;
            URL url = invokers.get(0).getUrl();
            //从 hash.nodes 参数中获取虚拟节点的个数
            this.replicaNumber = url.getMethodParameter(methodName, HASH_NODES, 160);
            //获取参与 Hash 计算的参数下标值，默认对第一个参数进行 Hash 运算
            String[] index = COMMA_SPLIT_PATTERN.split(url.getMethodParameter(methodName, HASH_ARGUMENTS, "0"));
            argumentIndex = new int[index.length];
            for (int i = 0; i < index.length; i++) {
                argumentIndex[i] = Integer.parseInt(index[i]);
            }
            //构建虚拟 Hash 槽，默认 replicaNumber = 160，相当于在 Hash 槽上放 160 个槽位
            //外层轮询40次，内层轮询4次，共40 * 4 = 160次，即同一个节点虚拟出160个槽位
            for (Invoker<T> invoker : invokers) {
                String address = invoker.getUrl().getAddress();
                for (int i = 0; i < replicaNumber / 4; i++) {
                    //对 address + i 进行 md5 运算，得到一个长度为16字节的数组
                    byte[] digest = md5(address + i);
                    //对 digest 部分字节进行4次 Hash 运算，得到4个不同的long型正整数
                    for (int h = 0; h < 4; h++) {
                        //h = 0时，取 digest 中下标为 0~3的4个字节进行位运算
                        //h = 1时，去 digest 中下标为 4~7的4个字节进行位运算
                        //h = 2和 h = 3时，过程同上
                        long m = hash(digest, h);
                        virtualInvokers.put(m, invoker);
                    }
                }
            }
        }

        public Invoker<T> select(Invocation invocation) {
            //将参与一致性 Hash 的参数拼接到一起
            String key = toKey(invocation.getArguments());
            //计算 key 的 Hash 值
            byte[] digest = md5(key);
            //匹配 Invoker 对象
            return selectForKey(hash(digest, 0));
        }

        private String toKey(Object[] args) {
            StringBuilder buf = new StringBuilder();
            for (int i : argumentIndex) {
                if (i >= 0 && i < args.length) {
                    buf.append(args[i]);
                }
            }
            return buf.toString();
        }

        private Invoker<T> selectForKey(long hash) {
            //从 virtualInvokers 集合（TreeMap是按照 Key 排序的）中查找第一个节点值大于或等于传入 Hash 值得 Invoker 对象
            Map.Entry<Long, Invoker<T>> entry = virtualInvokers.ceilingEntry(hash);
            //如果 Hash 值大于 hash 环中的所有 Invoker，则回到Hash环的开头，返回第一个Invoker对象
            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }

        private long hash(byte[] digest, int number) {
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }

        private byte[] md5(String value) {
            MessageDigest md5;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            md5.reset();
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            md5.update(bytes);
            return md5.digest();
        }

    }

}
