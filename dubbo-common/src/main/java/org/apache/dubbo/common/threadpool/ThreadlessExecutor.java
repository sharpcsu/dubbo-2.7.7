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
package org.apache.dubbo.common.threadpool;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 内部不管理任何线程，提交的任务存储在阻塞队列中，当其他线程调用ThreadlessExecutor.waitAndDrain()方法时才会真正执行。
 * 执行任务与调用waitAndDrain()方法是同一个线程。
 *
 * The most important difference between this Executor and other normal Executor is that this one doesn't manage
 * any thread.
 *
 * Tasks submitted to this executor through {@link #execute(Runnable)} will not get scheduled to a specific thread, though normal executors always do the schedule.
 * Those tasks are stored in a blocking queue and will only be executed when a thread calls {@link #waitAndDrain()}, the thread executing the task
 * is exactly the same as the one calling waitAndDrain.
 */
public class ThreadlessExecutor extends AbstractExecutorService {
    private static final Logger logger = LoggerFactory.getLogger(ThreadlessExecutor.class.getName());

    /**
     * 阻塞队列，用来在IO线程和业务线程之间传递任务
     */
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    /**
     * ThreadlessExecutor底层关联的共享线程池，当业务线程不再等待响应时，会由该共享线程执行提交的任务。
     */
    private ExecutorService sharedExecutor;

    /**
     * 指向请求对应的DefaultFuture对象
     */
    private CompletableFuture<?> waitingFuture;

    /**
     * ThreadlessExecutor中的waitAndDrain()方法一般与一次RPC调用绑定，只会执行一次。
     * 当后续再次调用waitAndDrain()方法时，会检查finished字段，若为true，则此次调用直接返回。
     * 当后续再次调用execute()方法提交任务时，会根据waiting字段决定任务时放入queue队列等待业务线程执行，还是直接由sharedExecutor线程池执行。
     */
    private boolean finished = false;

    private volatile boolean waiting = true;

    private final Object lock = new Object();

    public ThreadlessExecutor(ExecutorService sharedExecutor) {
        this.sharedExecutor = sharedExecutor;
    }

    public CompletableFuture<?> getWaitingFuture() {
        return waitingFuture;
    }

    public void setWaitingFuture(CompletableFuture<?> waitingFuture) {
        this.waitingFuture = waitingFuture;
    }

    public boolean isWaiting() {
        return waiting;
    }

    /**
     * 首先检测finished字段值，然后获取阻塞队列中的全部任务并执行，
     * 执行完成之后会修改finished和waiting字段，标志当前ThreadlessExecutor已使用完毕，无业务线程等待。
     *
     * Waits until there is a task, executes the task and all queued tasks (if there're any). The task is either a normal
     * response or a timeout response.
     */
    public void waitAndDrain() throws InterruptedException {
        /**
         * Usually, {@link #waitAndDrain()} will only get called once. It blocks for the response for the first time,
         * once the response (the task) reached and being executed waitAndDrain will return, the whole request process
         * then finishes. Subsequent calls on {@link #waitAndDrain()} (if there're any) should return immediately.
         *
         * There's no need to worry that {@link #finished} is not thread-safe. Checking and updating of
         * 'finished' only appear in waitAndDrain, since waitAndDrain is binding to one RPC call (one thread), the call
         * of it is totally sequential.
         */
        if (finished) {  //检查当前ThreadlessExecutor状态
            return;
        }

        //获取阻塞队列中的等待任务
        Runnable runnable = queue.take();

        synchronized (lock) {
            //修改waiting状态
            waiting = false;
            //执行任务
            runnable.run();
        }

        //如果阻塞队列中还有其他任务，需要一并执行
        runnable = queue.poll();
        while (runnable != null) {
            try {
                runnable.run();
            } catch (Throwable t) {
                logger.info(t);

            }
            runnable = queue.poll();
        }
        // mark the status of ThreadlessExecutor as finished.
        //修改finished状态
        finished = true;
    }

    public long waitAndDrain(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        /*long startInMs = System.currentTimeMillis();
        Runnable runnable = queue.poll(timeout, unit);
        if (runnable == null) {
            throw new TimeoutException();
        }
        runnable.run();
        long elapsedInMs = System.currentTimeMillis() - startInMs;
        long timeLeft = timeout - elapsedInMs;
        if (timeLeft < 0) {
            throw new TimeoutException();
        }
        return timeLeft;*/
        throw new UnsupportedOperationException();
    }

    /**
     * 根据waiting状态决定任务提交到哪里
     * If the calling thread is still waiting for a callback task, add the task into the blocking queue to wait for schedule.
     * Otherwise, submit to shared callback executor directly.
     *
     * @param runnable
     */
    @Override
    public void execute(Runnable runnable) {
        synchronized (lock) {
            if (!waiting) {  //判断业务线程是否还在等待响应结果
                //不等待，直接交给共享线程池处理任务
                sharedExecutor.execute(runnable);
            } else {  //业务线程还在等待，则将任务写入队列然后由业务线程自己执行
                queue.add(runnable);
            }
        }
    }

    /**
     * tells the thread blocking on {@link #waitAndDrain()} to return, despite of the current status, to avoid endless waiting.
     */
    public void notifyReturn(Throwable t) {
        // an empty runnable task.
        execute(() -> {
            waitingFuture.completeExceptionally(t);
        });
    }

    /**
     * The following methods are still not supported
     */

    @Override
    public void shutdown() {
        shutdownNow();
    }

    @Override
    public List<Runnable> shutdownNow() {
        notifyReturn(new IllegalStateException("Consumer is shutting down and this call is going to be stopped without " +
                "receiving any result, usually this is called by a slow provider instance or bad service implementation."));
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }
}
