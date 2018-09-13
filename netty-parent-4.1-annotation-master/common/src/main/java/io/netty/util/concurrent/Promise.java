/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

/**
 * Special {@link Future} which is writable.
 *
 * Promise是一个特殊的Future。
 */

/**
 * 在并发编程中，我们通常会用到一组非阻塞的模型：Promise，Future 和 Callback。其中的 Future 表示一个可能还没有实际完成的异步任务的结果，针对这个结果可以添加 Callback 以便在任务执行成功或失败后做出对应的操作，而 Promise 交由任务执行者，任务执行者通过 Promise 可以标记任务完成或者失败。 可以说这一套模型是很多异步非阻塞架构的基础。Netty 4中正提供了这种Future/Promise异步模型。
 Netty文档说明Netty的网络操作都是异步的， 在源码上大量使用了Future/Promise模型，在Netty里面也是这样定义的：
 Future接口定义了isSuccess(),isCancellable(),cause(),这些判断异步执行状态的方法。（read-only）
 Promise接口在extneds future的基础上增加了setSuccess(), setFailure()这些方法。（writable）
 java.util.concurrent.Future是Java提供的接口，表示异步执行的状态，Future的get方法会判断任务是否执行完成，如果完成就返回结果，否则阻塞线程，直到任务完成。
 *
 */

/**
 * Promise接口也扩展了Future接口，它表示一种可写的Future，就是可以设置异步执行的结果。
 */


/**
 * Future用于获取异步操作的结果
 *
 * ChannelFuture有两种状态：upcompleted和completed；ChannelFuture可以同时增加一个或多个GenericFutureListener。
 *
 * 需要注意的是：不要在ChannelHandler中调用ChannelFuture的await()方法，否则会导致死锁。如果I/O线程和用户线程是同一个线程，
 * 就会导致I/O线程等待自已通知操作系统，属于自已把自己挂死。
 *
 * Promise是可写的Future，对Future进行扩展，用于设置I/O操作的结果。强烈建议通过增加监听器Listener的方式接受异步I/O操作结果的通知，
 * 而不是调用wait或者sync阻塞用户线程。
 */
public interface Promise<V> extends Future<V> {

    /**
     * Marks this future as a success and notifies all
     * listeners.
     *
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     *
     * 如果future已经成功（已经被标记过）或者失败，它将抛出IllegalStateException异常。
     */
    Promise<V> setSuccess(V result);

    /**
     * Marks this future as a success and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a success. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean trySuccess(V result);

    /**
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setFailure(Throwable cause);

    /**
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a failure. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean tryFailure(Throwable cause);

    /**
     * Make this future impossible to cancel.
     *
     * @return {@code true} if and only if successfully marked this future as uncancellable or it is already done
     *         without being cancelled.  {@code false} if this future has been cancelled already.
     */
    boolean setUncancellable();

    @Override
    Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> await() throws InterruptedException;

    @Override
    Promise<V> awaitUninterruptibly();

    @Override
    Promise<V> sync() throws InterruptedException;

    @Override
    Promise<V> syncUninterruptibly();
}
