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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.context.Lifecycle;
import org.apache.dubbo.common.extension.support.ActivateComparator;
import org.apache.dubbo.common.lang.Prioritized;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.sort;
import static java.util.ServiceLoader.load;
import static java.util.stream.StreamSupport.stream;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOVE_VALUE_PREFIX;

/**
 * {@link org.apache.dubbo.rpc.model.ApplicationModel}, {@code DubboBootstrap} and this class are
 * at present designed to be singleton or static (by itself totally static or uses some static fields).
 * So the instances returned from them are of process or classloader scope. If you want to support
 * multiple dubbo servers in a single process, you may need to refactor these three classes.
 * <p>
 * Load dubbo extensions
 * <ul>
 * <li>auto inject dependency extension </li>
 * <li>auto wrap extension in wrapper </li>
 * <li>default extension is an adaptive instance</li>
 * </ul>
 *
 * @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jar/jar.html#Service%20Provider">Service Provider in Java 5</a>
 * @see org.apache.dubbo.common.extension.SPI
 * @see org.apache.dubbo.common.extension.Adaptive
 * @see org.apache.dubbo.common.extension.Activate
 */
public class ExtensionLoader<T> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    /**
     * dubbo中一个扩展接口对应一个ExtensionLoader实例，该集合缓存了全部ExtensionLoader实例，
     * 其中的key为扩展接口，value为加载其扩展实现的ExtensionLoader实例
     */
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>(64);

    /**
     * 缓存扩展实现类与其实例对象的映射关系，key为Class，value为DubboProtocol对象
     */
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>(64);

    /**
     * 当前ExtensionLoader实例负责加载扩展接口
     */
    private final Class<?> type;

    private final ExtensionFactory objectFactory;

    /**
     * 缓存了该ExtensionLoader加载的扩展实现类与扩展名之间的映射关系
     */
    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<>();

    /**
     * 缓存了该ExtensionLoader加载的扩展名与扩展实现类之间的映射关系。cacheNames集合的反向关系缓存。
     */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    private final Map<String, Object> cachedActivates = new ConcurrentHashMap<>();

    /**
     * 缓存了该ExtensionLoader加载的扩展名与扩展实现对象之间的映射关系
     */
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();
    private final Holder<Object> cachedAdaptiveInstance = new Holder<>();
    private volatile Class<?> cachedAdaptiveClass = null;

    /**
     * 记录了type这个扩展接口上@SPI注解的value值，及默认扩展名
     */
    private String cachedDefaultName;
    private volatile Throwable createAdaptiveInstanceError;

    private Set<Class<?>> cachedWrapperClasses;

    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<>();

    private static volatile LoadingStrategy[] strategies = loadLoadingStrategies();

    public static void setLoadingStrategies(LoadingStrategy... strategies) {
        if (ArrayUtils.isNotEmpty(strategies)) {
            ExtensionLoader.strategies = strategies;
        }
    }

    /**
     * Load all {@link Prioritized prioritized} {@link LoadingStrategy Loading Strategies} via {@link ServiceLoader}
     *
     * @return non-null
     * @since 2.7.7
     */
    private static LoadingStrategy[] loadLoadingStrategies() {
        return stream(load(LoadingStrategy.class).spliterator(), false)
                .sorted()
                .toArray(LoadingStrategy[]::new);
    }

    /**
     * Get all {@link LoadingStrategy Loading Strategies}
     *
     * @return non-null
     * @see LoadingStrategy
     * @see Prioritized
     * @since 2.7.7
     */
    public static List<LoadingStrategy> getLoadingStrategies() {
        return asList(strategies);
    }

    private ExtensionLoader(Class<?> type) {
        this.type = type;
        objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
    }

    private static <T> boolean withExtensionAnnotation(Class<T> type) {
        return type.isAnnotationPresent(SPI.class);
    }

    /**
     * @SPI注解的处理逻辑
     * 根据扩展接口从 EXTENSION_LOADERS 缓存中查找相应的ExtensionLoader实例
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
        }
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type (" + type +
                    ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
        }

        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    // For testing purposes only
    public static void resetExtensionLoader(Class type) {
        ExtensionLoader loader = EXTENSION_LOADERS.get(type);
        if (loader != null) {
            // Remove all instances associated with this loader as well
            Map<String, Class<?>> classes = loader.getExtensionClasses();
            for (Map.Entry<String, Class<?>> entry : classes.entrySet()) {
                EXTENSION_INSTANCES.remove(entry.getValue());
            }
            classes.clear();
            EXTENSION_LOADERS.remove(type);
        }
    }

    public static void destroyAll() {
        EXTENSION_INSTANCES.forEach((_type, instance) -> {
            if (instance instanceof Lifecycle) {
                Lifecycle lifecycle = (Lifecycle) instance;
                try {
                    lifecycle.destroy();
                } catch (Exception e) {
                    logger.error("Error destroying extension " + lifecycle, e);
                }
            }
        });
    }

    private static ClassLoader findClassLoader() {
        return ClassUtils.getClassLoader(ExtensionLoader.class);
    }

    public String getExtensionName(T extensionInstance) {
        return getExtensionName(extensionInstance.getClass());
    }

    public String getExtensionName(Class<?> extensionClass) {
        getExtensionClasses();// load class
        return cachedNames.get(extensionClass);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, key, null)}
     *
     * @param url url 包含配置信息
     * @param key url parameter key which used to get extension point names
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String, String)
     */
    public List<T> getActivateExtension(URL url, String key) {
        return getActivateExtension(url, key, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, values, null)}
     *
     * @param url    url 包含配置信息
     * @param values extension point names 配置中指定的扩展名
     * @return extension list which are activated
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String[] values) {
        return getActivateExtension(url, values, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, url.getParameter(key).split(","), null)}
     *
     * @param url   url 包含配置信息
     * @param key   url parameter key which used to get extension point names
     * @param group group Provider或Consumer
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String key, String group) {
        String value = url.getParameter(key);
        return getActivateExtension(url, StringUtils.isEmpty(value) ? null : COMMA_SPLIT_PATTERN.split(value), group);
    }

    /**
     * Get activate extensions.
     *
     * @param url    包含配置信息
     * @param values 配置中指定的扩展名
     * @param group  Provider或Consumer
     * @return extension list which are activated
     * @see org.apache.dubbo.common.extension.Activate
     */
    public List<T> getActivateExtension(URL url, String[] values, String group) {
        List<T> activateExtensions = new ArrayList<>();
        //values配置就是扩展名
        List<String> names = values == null ? new ArrayList<>(0) : asList(values);
        /*
        获取默认激活的扩展集合。默认激活的扩展实现类有几个条件：
        1. 在cachedActivates集合中存在
        2. @Activate注解指定的group属性与当前group匹配
        3. 扩展名没有出现在values中（未在配置中明确指定，也未在配置中明确指定删除）
        4. url中出现了@Activate注解中指定的key
         */
        if (!names.contains(REMOVE_VALUE_PREFIX + DEFAULT_KEY)) {
            getExtensionClasses();  //触发cachedActivates等缓存字段的加载
            for (Map.Entry<String, Object> entry : cachedActivates.entrySet()) {
                String name = entry.getKey();  //扩展名
                Object activate = entry.getValue();  //@Activate注解

                String[] activateGroup, activateValue;

                if (activate instanceof Activate) {  //@Activate注解中的配置
                    activateGroup = ((Activate) activate).group();
                    activateValue = ((Activate) activate).value();
                } else if (activate instanceof com.alibaba.dubbo.common.extension.Activate) {
                    activateGroup = ((com.alibaba.dubbo.common.extension.Activate) activate).group();
                    activateValue = ((com.alibaba.dubbo.common.extension.Activate) activate).value();
                } else {
                    continue;
                }
                if (isMatchGroup(group, activateGroup)  //匹配group
                        && !names.contains(name)  //没有出现在values配置中的，即为默认激活的扩展实现
                        && !names.contains(REMOVE_VALUE_PREFIX + name)  //通过"-"明确指定不激活该扩展实现
                        && isActive(activateValue, url)) {  //检测url中是否出现了指定的key
                    //加载扩展实现的实例对象，这些都是激活的
                    activateExtensions.add(getExtension(name));
                }
            }
            //排序
            activateExtensions.sort(ActivateComparator.COMPARATOR);
        }
        List<T> loadedExtensions = new ArrayList<>();
        /*
        按序添加自定义扩展实现类的对象
         */
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            //通过"-"开头的配置明确指定不激活的扩展实现，直接忽略了
            if (!name.startsWith(REMOVE_VALUE_PREFIX)
                    && !names.contains(REMOVE_VALUE_PREFIX + name)) {
                if (DEFAULT_KEY.equals(name)) {
                    if (!loadedExtensions.isEmpty()) {
                        //按照顺序，将自定义的扩展添加到默认扩展集合前面
                        activateExtensions.addAll(0, loadedExtensions);
                        loadedExtensions.clear();
                    }
                } else {
                    loadedExtensions.add(getExtension(name));
                }
            }
        }
        if (!loadedExtensions.isEmpty()) {
            //按照顺序，将自定义的扩展添加到默认扩展集合的后面
            activateExtensions.addAll(loadedExtensions);
        }
        return activateExtensions;
    }

    private boolean isMatchGroup(String group, String[] groups) {
        if (StringUtils.isEmpty(group)) {
            return true;
        }
        if (groups != null && groups.length > 0) {
            for (String g : groups) {
                if (group.equals(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isActive(String[] keys, URL url) {
        if (keys.length == 0) {
            return true;
        }
        for (String key : keys) {
            // @Active(value="key1:value1, key2:value2")
            String keyValue = null;
            if (key.contains(":")) {
                String[] arr = key.split(":");
                key = arr[0];
                keyValue = arr[1];
            }

            for (Map.Entry<String, String> entry : url.getParameters().entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if ((k.equals(key) || k.endsWith("." + key))
                        && ((keyValue != null && keyValue.equals(v)) || (keyValue == null && ConfigUtils.isNotEmpty(v)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get extension's instance. Return <code>null</code> if extension is not found or is not initialized. Pls. note
     * that this method will not trigger extension load.
     * <p>
     * In order to trigger extension load, call {@link #getExtension(String)} instead.
     *
     * @see #getExtension(String)
     */
    @SuppressWarnings("unchecked")
    public T getLoadedExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Holder<Object> holder = getOrCreateHolder(name);
        return (T) holder.get();
    }

    /**
     * 封装了查找cachedInstances缓存的逻辑
     */
    private Holder<Object> getOrCreateHolder(String name) {
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }
        return holder;
    }

    /**
     * Return the list of extensions which are already loaded.
     * <p>
     * Usually {@link #getSupportedExtensions()} should be called in order to get all extensions.
     *
     * @see #getSupportedExtensions()
     */
    public Set<String> getLoadedExtensions() {
        return Collections.unmodifiableSet(new TreeSet<>(cachedInstances.keySet()));
    }

    public List<T> getLoadedExtensionInstances() {
        List<T> instances = new ArrayList<>();
        cachedInstances.values().forEach(holder -> instances.add((T) holder.get()));
        return instances;
    }

    public Object getLoadedAdaptiveExtensionInstances() {
        return cachedAdaptiveInstance.get();
    }

//    public T getPrioritizedExtensionInstance() {
//        Set<String> supported = getSupportedExtensions();
//
//        Set<T> instances = new HashSet<>();
//        Set<T> prioritized = new HashSet<>();
//        for (String s : supported) {
//
//        }
//
//    }

    /**
     * Find the extension with the given name. If the specified name is not found, then {@link IllegalStateException}
     * will be thrown.
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        if ("true".equals(name)) {
            return getDefaultExtension();
        }
        //getOrCreateHolder()方法中封装了查找cachedInstances缓存的逻辑
        final Holder<Object> holder = getOrCreateHolder(name);
        Object instance = holder.get();
        if (instance == null) {//  1
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {//  2 双重检查防止并发问题
                    //根据扩展名从SPI配置文件中查找对应的扩展实现类
                    instance = createExtension(name);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * Get the extension by specified name if found, or {@link #getDefaultExtension() returns the default one}
     *
     * @param name the name of extension
     * @return non-null
     */
    public T getOrDefaultExtension(String name) {
        return containsExtension(name) ? getExtension(name) : getDefaultExtension();
    }

    /**
     * Return default extension, return <code>null</code> if it's not configured.
     */
    public T getDefaultExtension() {
        getExtensionClasses();
        if (StringUtils.isBlank(cachedDefaultName) || "true".equals(cachedDefaultName)) {
            return null;
        }
        return getExtension(cachedDefaultName);
    }

    public boolean hasExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Class<?> c = this.getExtensionClass(name);
        return c != null;
    }

    public Set<String> getSupportedExtensions() {
        Map<String, Class<?>> clazzes = getExtensionClasses();
        return Collections.unmodifiableSet(new TreeSet<>(clazzes.keySet()));
    }

    public Set<T> getSupportedExtensionInstances() {
        List<T> instances = new LinkedList<>();
        Set<String> supportedExtensions = getSupportedExtensions();
        if (CollectionUtils.isNotEmpty(supportedExtensions)) {
            for (String name : supportedExtensions) {
                instances.add(getExtension(name));
            }
        }
        // sort the Prioritized instances
        sort(instances, Prioritized.COMPARATOR);
        return new LinkedHashSet<>(instances);
    }

    /**
     * Return default extension name, return <code>null</code> if not configured.
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return cachedDefaultName;
    }

    /**
     * Register new extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension with the same name has already been registered.
     */
    public void addExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement the Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " already exists (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
        } else {
            if (cachedAdaptiveClass != null) {
                throw new IllegalStateException("Adaptive Extension already exists (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
        }
    }

    /**
     * Replace the existing extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension to be placed doesn't exist
     * @deprecated not recommended any longer, and use only when test
     */
    @Deprecated
    public void replaceExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (!cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " doesn't exist (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
            cachedInstances.remove(name);
        } else {
            if (cachedAdaptiveClass == null) {
                throw new IllegalStateException("Adaptive Extension doesn't exist (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
            cachedAdaptiveInstance.set(null);
        }
    }

    /**
     * 获取适配器实例
     */
    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        Object instance = cachedAdaptiveInstance.get();
        if (instance == null) {
            if (createAdaptiveInstanceError != null) {
                throw new IllegalStateException("Failed to create adaptive instance: " +
                        createAdaptiveInstanceError.toString(),
                        createAdaptiveInstanceError);
            }

            synchronized (cachedAdaptiveInstance) {
                instance = cachedAdaptiveInstance.get();
                if (instance == null) {
                    try {
                        instance = createAdaptiveExtension();
                        cachedAdaptiveInstance.set(instance);
                    } catch (Throwable t) {
                        createAdaptiveInstanceError = t;
                        throw new IllegalStateException("Failed to create adaptive instance: " + t.toString(), t);
                    }
                }
            }
        }

        return (T) instance;
    }

    private IllegalStateException findException(String name) {
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (entry.getKey().toLowerCase().contains(name.toLowerCase())) {
                return entry.getValue();
            }
        }
        StringBuilder buf = new StringBuilder("No such extension " + type.getName() + " by name " + name);


        int i = 1;
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (i == 1) {
                buf.append(", possible causes: ");
            }

            buf.append("\r\n(");
            buf.append(i++);
            buf.append(") ");
            buf.append(entry.getKey());
            buf.append(":\r\n");
            buf.append(StringUtils.toString(entry.getValue()));
        }
        return new IllegalStateException(buf.toString());
    }

    /**
     * 完成SPI配置文件的查找以及相应扩展实现类的实例化
     * 还实现了自动装配以及自动wrapper包装等功能
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        /*
        获取cachedClasses缓存，根据扩展名从cachedClasses缓存中获取扩展实现类。
         如果cached未初始化，则会扫描前面介绍的三个SPI目录获取查找相应的SPI配置文件，
         然后加载其中的扩展实现类，
         最后将扩展名和扩展实现类的映射关系记录到cachedClasses缓存中。
         这部分逻辑在loadExtensionClasses()和loadDirectory()方法中
         */
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw findException(name);
        }
        try {
            /*
            根据扩展实现类从EXTENSION_INSTANCES缓存中查找相应的实例。
            如果查找失败，会通过反射创建扩展实现对象。
             */
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            //自动装配扩展实现对象中的属性（调用其setter）
            injectExtension(instance);
            //自动包装扩展实现对象
            Set<Class<?>> wrapperClasses = cachedWrapperClasses;
            if (CollectionUtils.isNotEmpty(wrapperClasses)) {
                for (Class<?> wrapperClass : wrapperClasses) {
                    instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
                }
            }
            initExtension(instance);
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance (name: " + name + ", class: " +
                    type + ") couldn't be instantiated: " + t.getMessage(), t);
        }
    }

    private boolean containsExtension(String name) {
        return getExtensionClasses().containsKey(name);
    }

    /**
     * 自动装配
     * 依赖了ExtensionFactory
     */
    private T injectExtension(T instance) {

        if (objectFactory == null) { //检测objectFactory字段
            return instance;
        }

        try {
            for (Method method : instance.getClass().getMethods()) {
                //如果不是setter方法，忽略该方法
                if (!isSetter(method)) {
                    continue;
                }
                /**
                 * Check {@link DisableInject} to see if we need auto injection for this property
                 * 如果方法上明确标注了@DisableInject注解，忽略该方法
                 */
                if (method.getAnnotation(DisableInject.class) != null) {
                    continue;
                }
                //根据setter方法的参数，确定扩展接口
                Class<?> pt = method.getParameterTypes()[0];
                if (ReflectUtils.isPrimitives(pt)) {
                    continue;//如果参数为简单类型，忽略该方法
                }

                try {
                    //根据setter方法的名称确定属性名称
                    String property = getSetterProperty(method);
                    //加载并实例化扩展实现类
                    Object object = objectFactory.getExtension(pt, property);
                    if (object != null) {
                        method.invoke(instance, object); //调用setter方法进行装配
                    }
                } catch (Exception e) {
                    logger.error("Failed to inject via method " + method.getName()
                            + " of interface " + type.getName() + ": " + e.getMessage(), e);
                }

            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance;
    }

    private void initExtension(T instance) {
        if (instance instanceof Lifecycle) {
            Lifecycle lifecycle = (Lifecycle) instance;
            lifecycle.initialize();
        }
    }

    /**
     * get properties name for setter, for instance: setVersion, return "version"
     * <p>
     * return "", if setter name with length less than 3
     */
    private String getSetterProperty(Method method) {
        return method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
    }

    /**
     * return true if and only if:
     * <p>
     * 1, public
     * <p>
     * 2, name starts with "set"
     * <p>
     * 3, only has one parameter
     */
    private boolean isSetter(Method method) {
        return method.getName().startsWith("set")
                && method.getParameterTypes().length == 1
                && Modifier.isPublic(method.getModifiers());
    }

    private Class<?> getExtensionClass(String name) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (name == null) {
            throw new IllegalArgumentException("Extension name == null");
        }
        return getExtensionClasses().get(name);
    }

    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * synchronized in getExtensionClasses
     */
    private Map<String, Class<?>> loadExtensionClasses() {
        cacheDefaultExtensionName();

        Map<String, Class<?>> extensionClasses = new HashMap<>();

        for (LoadingStrategy strategy : strategies) {
            loadDirectory(extensionClasses, strategy.directory(), type.getName(), strategy.preferExtensionClassLoader(), strategy.overridden(), strategy.excludedPackages());
            loadDirectory(extensionClasses, strategy.directory(), type.getName().replace("org.apache", "com.alibaba"), strategy.preferExtensionClassLoader(), strategy.overridden(), strategy.excludedPackages());
        }

        return extensionClasses;
    }

    /**
     * extract and cache default extension name if exists
     */
    private void cacheDefaultExtensionName() {
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation == null) {
            return;
        }

        String value = defaultAnnotation.value();
        if ((value = value.trim()).length() > 0) {
            String[] names = NAME_SEPARATOR.split(value);
            if (names.length > 1) {
                throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                        + ": " + Arrays.toString(names));
            }
            if (names.length == 1) {
                cachedDefaultName = names[0];
            }
        }
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type) {
        loadDirectory(extensionClasses, dir, type, false, false);
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type,
                               boolean extensionLoaderClassLoaderFirst, boolean overridden, String... excludedPackages) {
        String fileName = dir + type;
        try {
            Enumeration<java.net.URL> urls = null;
            ClassLoader classLoader = findClassLoader();

            // try to load from ExtensionLoader's ClassLoader first
            if (extensionLoaderClassLoaderFirst) {
                ClassLoader extensionLoaderClassLoader = ExtensionLoader.class.getClassLoader();
                if (ClassLoader.getSystemClassLoader() != extensionLoaderClassLoader) {
                    urls = extensionLoaderClassLoader.getResources(fileName);
                }
            }

            if (urls == null || !urls.hasMoreElements()) {
                if (classLoader != null) {
                    urls = classLoader.getResources(fileName);
                } else {
                    urls = ClassLoader.getSystemResources(fileName);
                }
            }

            if (urls != null) {
                while (urls.hasMoreElements()) {
                    java.net.URL resourceURL = urls.nextElement();
                    loadResource(extensionClasses, classLoader, resourceURL, overridden, excludedPackages);
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", description file: " + fileName + ").", t);
        }
    }

    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader,
                              java.net.URL resourceURL, boolean overridden, String... excludedPackages) {
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final int ci = line.indexOf('#');
                    if (ci >= 0) {
                        line = line.substring(0, ci);
                    }
                    line = line.trim();
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            int i = line.indexOf('=');
                            if (i > 0) {
                                name = line.substring(0, i).trim();
                                line = line.substring(i + 1).trim();
                            }
                            if (line.length() > 0 && !isExcluded(line, excludedPackages)) {
                                loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name, overridden);
                            }
                        } catch (Throwable t) {
                            IllegalStateException e = new IllegalStateException("Failed to load extension class (interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }

    private boolean isExcluded(String className, String... excludedPackages) {
        if (excludedPackages != null) {
            for (String excludePackage : excludedPackages) {
                if (className.startsWith(excludePackage + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name,
                           boolean overridden) throws NoSuchMethodException {
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Error occurred when loading extension class (interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    + clazz.getName() + " is not subtype of interface.");
        }
        if (clazz.isAnnotationPresent(Adaptive.class)) {
            //处理@Adaptive注解， 缓存到cachedAdaptiveClass字段
            cacheAdaptiveClass(clazz, overridden);
        } else if (isWrapperClass(clazz)) {  //处理Wrapper类
            cacheWrapperClass(clazz);
        } else {  //处理真正的扩展实现类
            clazz.getConstructor();  //扩展实现类必须有无参构造函数
            if (StringUtils.isEmpty(name)) {
                name = findAnnotationName(clazz);
                if (name.length() == 0) {
                    throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
                }
            }

            //兜底：SPI配置文件中未指定扩展名称，则用类的简单名称作为扩展名
            String[] names = NAME_SEPARATOR.split(name);
            if (ArrayUtils.isNotEmpty(names)) {
                //将包含@Activate注解的实现类缓存到cachedActivates集合中
                cacheActivateClass(clazz, names[0]);
                for (String n : names) {
                    //在cachedNames集合中缓存实现类->扩展名的映射
                    cacheName(clazz, n);
                    //在cachedClasses集合中缓存扩展名->实现类的映射
                    saveInExtensionClass(extensionClasses, clazz, n, overridden);
                }
            }
        }
    }

    /**
     * cache name
     */
    private void cacheName(Class<?> clazz, String name) {
        if (!cachedNames.containsKey(clazz)) {
            cachedNames.put(clazz, name);
        }
    }

    /**
     * put clazz in extensionClasses
     */
    private void saveInExtensionClass(Map<String, Class<?>> extensionClasses, Class<?> clazz, String name, boolean overridden) {
        Class<?> c = extensionClasses.get(name);
        if (c == null || overridden) {
            extensionClasses.put(name, clazz);
        } else if (c != clazz) {
            String duplicateMsg = "Duplicate extension " + type.getName() + " name " + name + " on " + c.getName() + " and " + clazz.getName();
            logger.error(duplicateMsg);
            throw new IllegalStateException(duplicateMsg);
        }
    }

    /**
     * cache Activate class which is annotated with <code>Activate</code>
     * <p>
     * for compatibility, also cache class with old alibaba Activate annotation
     */
    private void cacheActivateClass(Class<?> clazz, String name) {
        Activate activate = clazz.getAnnotation(Activate.class);
        if (activate != null) {
            cachedActivates.put(name, activate);
        } else {
            // support com.alibaba.dubbo.common.extension.Activate
            com.alibaba.dubbo.common.extension.Activate oldActivate = clazz.getAnnotation(com.alibaba.dubbo.common.extension.Activate.class);
            if (oldActivate != null) {
                cachedActivates.put(name, oldActivate);
            }
        }
    }

    /**
     * cache Adaptive class which is annotated with <code>Adaptive</code>
     */
    private void cacheAdaptiveClass(Class<?> clazz, boolean overridden) {
        if (cachedAdaptiveClass == null || overridden) {
            cachedAdaptiveClass = clazz;
        } else if (!cachedAdaptiveClass.equals(clazz)) {
            throw new IllegalStateException("More than 1 adaptive class found: "
                    + cachedAdaptiveClass.getName()
                    + ", " + clazz.getName());
        }
    }

    /**
     * cache wrapper class
     * <p>
     * like: ProtocolFilterWrapper, ProtocolListenerWrapper
     */
    private void cacheWrapperClass(Class<?> clazz) {
        if (cachedWrapperClasses == null) {
            cachedWrapperClasses = new ConcurrentHashSet<>();
        }
        cachedWrapperClasses.add(clazz);
    }

    /**
     * test if clazz is a wrapper class
     * <p>
     * which has Constructor with given class type as its only argument
     */
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> clazz) {
        org.apache.dubbo.common.Extension extension = clazz.getAnnotation(org.apache.dubbo.common.Extension.class);
        if (extension != null) {
            return extension.value();
        }

        String name = clazz.getSimpleName();
        if (name.endsWith(type.getSimpleName())) {
            name = name.substring(0, name.length() - type.getSimpleName().length());
        }
        return name.toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        try {
            return injectExtension((T) getAdaptiveExtensionClass().newInstance());
        } catch (Exception e) {
            throw new IllegalStateException("Can't create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }

    private Class<?> getAdaptiveExtensionClass() {
        getExtensionClasses();
        if (cachedAdaptiveClass != null) {
            return cachedAdaptiveClass;
        }
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }

    private Class<?> createAdaptiveExtensionClass() {
        String code = new AdaptiveClassCodeGenerator(type, cachedDefaultName).generate();
        ClassLoader classLoader = findClassLoader();
        org.apache.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        return compiler.compile(code, classLoader);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }

}
