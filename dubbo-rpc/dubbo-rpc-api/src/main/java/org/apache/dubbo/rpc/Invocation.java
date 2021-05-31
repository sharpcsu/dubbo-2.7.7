package org.apache.dubbo.rpc;

import org.apache.dubbo.common.Experimental;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Invoker.invoke()方法的参数，抽象了一次RPC调用的目标服务和方法信息、相关参数信息、具体的参数值以及一些附加信息
 *
 * Invocation. (API, Prototype, NonThreadSafe)
 *
 * @serial Don't change the class name and package name.
 * @see org.apache.dubbo.rpc.Invoker#invoke(Invocation)
 * @see org.apache.dubbo.rpc.RpcInvocation
 */
public interface Invocation {

    /**
     * 调用Service的唯一标识
     */
    String getTargetServiceUniqueName();

    /**
     * 调用的方法名称
     * get method name.
     *
     * @return method name.
     * @serial
     */
    String getMethodName();


    /**
     * 调用的服务名称
     * get the interface name
     * @return
     */
    String getServiceName();

    /**
     * 参数类型集合
     * get parameter types.
     *
     * @return parameter types.
     * @serial
     */
    Class<?>[] getParameterTypes();

    /**
     * 参数签名集合
     * get parameter's signature, string representation of parameter types.
     *
     * @return parameter's signature
     */
    default String[] getCompatibleParamSignatures() {
        return Stream.of(getParameterTypes())
                .map(Class::getName)
                .toArray(String[]::new);
    }

    /**
     * 此次调用具体的参数值
     * get arguments.
     *
     * @return arguments.
     * @serial
     */
    Object[] getArguments();

    /**
     * Invocation可以携带一个KV信息作为附加信息，一并传递给Provider，注意与attribute的区分
     * get attachments.
     *
     * @return attachments.
     * @serial
     */
    Map<String, String> getAttachments();

    @Experimental("Experiment api for supporting Object transmission")
    Map<String, Object> getObjectAttachments();

    void setAttachment(String key, String value);

    @Experimental("Experiment api for supporting Object transmission")
    void setAttachment(String key, Object value);

    @Experimental("Experiment api for supporting Object transmission")
    void setObjectAttachment(String key, Object value);

    void setAttachmentIfAbsent(String key, String value);

    @Experimental("Experiment api for supporting Object transmission")
    void setAttachmentIfAbsent(String key, Object value);

    @Experimental("Experiment api for supporting Object transmission")
    void setObjectAttachmentIfAbsent(String key, Object value);

    /**
     * get attachment by key.
     *
     * @return attachment value.
     * @serial
     */
    String getAttachment(String key);

    @Experimental("Experiment api for supporting Object transmission")
    Object getObjectAttachment(String key);

    /**
     * get attachment by key with default value.
     *
     * @return attachment value.
     * @serial
     */
    String getAttachment(String key, String defaultValue);

    @Experimental("Experiment api for supporting Object transmission")
    Object getObjectAttachment(String key, Object defaultValue);

    /**
     * 此次调用关联的Invoker对象
     * get the invoker in current context.
     *
     * @return invoker.
     * @transient
     */
    Invoker<?> getInvoker();

    /**
     * Invoker对象可以设置一些KV属性，这些属性不会传递给Provider
     * @param key
     * @param value
     * @return
     */
    Object put(Object key, Object value);

    Object get(Object key);

    Map<Object, Object> getAttributes();
}