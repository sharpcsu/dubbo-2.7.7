package org.apache.dubbo.rpc;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * It's recommended to implement Filter.Listener directly for callback registration, check the default implementation,
 * see {@link org.apache.dubbo.rpc.filter.ExceptionFilter}, for example.
 * <p>
 * If you do not want to share Listener instance between RPC calls. You can use ListenableFilter
 * to keep a 'one Listener each RPC call' model.
 */
public abstract class ListenableFilter implements Filter {

    protected Listener listener = null;
    protected final ConcurrentMap<Invocation, Listener> listeners = new ConcurrentHashMap<>();

    public Listener listener() {
        return listener;
    }

    public Listener listener(Invocation invocation) {
        Listener invListener = listeners.get(invocation);
        if (invListener == null) {
            invListener = listener;
        }
        return invListener;
    }

    public void addListener(Invocation invocation, Listener listener) {
        listeners.putIfAbsent(invocation, listener);
    }

    public void removeListener(Invocation invocation) {
        listeners.remove(invocation);
    }
}
