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

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;


/**
 * The result of an asynchronous operation.（返回异步操作的结果）
 */

/**
 * Future, 在Netty中所有的IO操作都是异步的，因此，你不能立刻得知消息是否被正确处理，但是我们可以过一会儿等它执行完成或者直接注册一个监听，具体的实现是通过
 * Future和ChannelFuture，他们可以注册一个监听，当操作执行成功或失败时监听会自动触发。总之，所有的操作都会返回一个ChannelFuture。
 *
 * Netty中的异步，就不得不提ChannelFuture。Netty中的IO操作是异步的，包括bind、write、connect等操作会简单的返回一个ChannelFuture，调用者并不能立刻获得结果。
 *
 * 在Netty中所有的IO操作都是异步的，也就是说我们在发送完消息后，Netty内部是采用线程池去处理，方法立即返回了，但有时候我们需要外部方法等待服务器的响应，
 * 整个过程需要同步处理，那么就需要将异步调用转为同步调用，原理很简单，就是在调用异步方法后，主线程阻塞，直到异步方法返回结果。
 *
 * 在Netty中所有的IO操作都是异步，这意味着Netty提供的IO方法调用都将立即返回，会返回一个ChannelFuture对象的实例，它将会给你一些信息，
 * 关于IO执行状态的结果，但此时不能保证真正的IO操作已经完成。
 *
 * 推荐使用addListener(ChannelFutureListener)异步得到通知当一个IO操作完成后，做任何后续任务，而不是通过调用await方法（降低吞吐量）。
 * 但如果你想要业务场景是必须先执行A，然后同步执行B（异步通知不合适的场景），使用await是比较方便的。但await有一个限制，
 * 调用await方法的线程不能是IO线程（work线程），否则会抛出一个异常，避免死锁。
 *
 * 作为一个异步NIO框架，Netty的所有IO操作都是异步非阻塞的，通过Future-Listener机制，用户可以方便的主动获取或者通过通知机制获得IO操作结果。
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
 * Netty扩展了Java的Future，最主要的改进就是增加了监听器Listener接口，通过监听器可以让异步执行更加有效率，不需要通过get来等待异步执行结束，
 * 而是通过监听器回调来精确地控制异步执行结束的时间点。
 */

/**
 * Netty推荐使用addListener的方式来回调异步执行的结果，这种方式优于Future.get，能够更精确地把握异步执行结束的时间。
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
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Future<V> extends java.util.concurrent.Future<V> { // Netty的Future继承自java.util.concurrent.Future接口。

    /**
     * Returns {@code true} if and only if the I/O operation was completed
     * successfully.
     */
    boolean isSuccess();

    /**
     * returns {@code true} if and only if the operation can be cancelled via {@link #cancel(boolean)}.
     */
    boolean isCancellable();

    /**
     * Returns the cause of the failed I/O operation if the I/O operation has
     * failed.
     *
     * @return the cause of the failure.
     *         {@code null} if succeeded or this future is not
     *         completed yet.
     */
    Throwable cause();

    /**
     * Adds the specified listener to this future.  The
     * specified listener is notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listener is notified immediately.
     *
     * 看上面的注释：对某个future添加一个监视器。
     */
    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * Adds the specified listeners to this future.  The
     * specified listeners are notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listeners are notified immediately.
     *
     * 对某个future添加多个监视器
     */
    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * Removes the first occurrence of the specified listener from this future.
     * The specified listener is no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listener is not associated with this future, this method
     * does nothing and returns silently.
     *
     * 此特定的listener可能会多次出现，只移除第一个出现的。如果不存在，则什么也不做，安静地返回。
     */
    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * Removes the first occurrence for each of the listeners from this future.
     * The specified listeners are no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listeners are not associated with this future, this method
     * does nothing and returns silently.
     *
     * 对于listeners中的每一个特定的listener，都只移除其第一次出现的。
     */
    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     *
     * 等待future执行完毕
     */
    Future<V> sync() throws InterruptedException;

    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     *
     * 这个相比于上面的区别在于不可中断。
     */
    Future<V> syncUninterruptibly();

    /**
     * Waits for this future to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     *
     * 这个方法与sync()有什么区别 ?zz?
     */
    Future<V> await() throws InterruptedException;

    /**
     * Waits for this future to be completed without
     * interruption.  This method catches an {@link InterruptedException} and
     * discards it silently.
     */
    Future<V> awaitUninterruptibly();

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeoutMillis);

    /**
     * Return the result without blocking. If the future is not done yet this will return {@code null}.
     *
     * As it is possible that a {@code null} value is used to mark the future as successful you also need to check
     * if the future is really done with {@link #isDone()} and not relay on the returned {@code null} value.
     *
     * 非阻塞立刻返回结果，如果future运行完了，就返回结果；如果没有运行完，就返回null。
     *
     * 假如有的方法以返回null来标记成功执行，则需要使用isDone方法来检查一下future是否真的执行完毕。
     */
    V getNow();

    /**
     * {@inheritDoc}
     *
     * If the cancellation was successful it will fail the future with an {@link CancellationException}.
     */
    @Override
    boolean cancel(boolean mayInterruptIfRunning);
}
