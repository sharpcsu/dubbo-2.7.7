package org.apache.dubbo.rpc;

/**
 * AsyncContext works like {@see javax.servlet.AsyncContext} in the Servlet 3.0.
 * An AsyncContext is stated by a call to {@link RpcContext#startAsync()}.
 * <p>
 * The demo is {@see com.alibaba.dubbo.examples.async.AsyncConsumer}
 * and {@see com.alibaba.dubbo.examples.async.AsyncProvider}
 */
public interface AsyncContext {

    /**
     * write value and complete the async context.
     *
     * @param value invoke result
     */
    void write(Object value);

    /**
     * @return true if the async context is started
     */
    boolean isAsyncStarted();

    /**
     * change the context state to stop
     */
    boolean stop();

    /**
     * change the context state to start
     */
    void start();

    /**
     * Signal RpcContext switch.
     * Use this method to switch RpcContext from a Dubbo thread to a new thread created by the user.
     *
     * Note that you should use it in a new thread like this:
     * <code>
     * public class AsyncServiceImpl implements AsyncService {
     *     public String sayHello(String name) {
     *         final AsyncContext asyncContext = RpcContext.startAsync();
     *         new Thread(() -> {
     *
     *             // right place to use this method
     *             asyncContext.signalContextSwitch();
     *
     *             try {
     *                 Thread.sleep(500);
     *             } catch (InterruptedException e) {
     *                 e.printStackTrace();
     *             }
     *             asyncContext.write("Hello " + name + ", response from provider.");
     *         }).start();
     *         return null;
     *     }
     * }
     * </code>
     */
    void signalContextSwitch();
}
