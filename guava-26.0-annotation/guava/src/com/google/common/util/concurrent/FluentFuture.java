/*
 * Copyright (C) 2006 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.util.concurrent;

import com.google.common.annotations.Beta;
import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Function;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link ListenableFuture} that supports fluent chains of operations. For example:
 *
 * <pre>{@code
 * ListenableFuture<Boolean> adminIsLoggedIn =
 *     FluentFuture.from(usersDatabase.getAdminUser())
 *         .transform(User::getId, directExecutor())
 *         .transform(ActivityService::isLoggedIn, threadPool)
 *         .catching(RpcException.class, e -> false, directExecutor());
 * }</pre>
 *
 * <h3>Alternatives</h3>
 *
 * <h4>Frameworks</h4>
 *
 * <p>When chaining together a graph of asynchronous operations, you will often find it easier to
 * use a framework. Frameworks automate the process, often adding features like monitoring,
 * debugging, and cancellation. Examples of frameworks include:
 *
 * <ul>
 *   <li><a href="http://google.github.io/dagger/producers.html">Dagger Producers</a>
 * </ul>
 *
 * <h4>{@link java.util.concurrent.CompletableFuture} / {@link java.util.concurrent.CompletionStage}
 * </h4>
 *
 * <p>Users of {@code CompletableFuture} will likely want to continue using {@code
 * CompletableFuture}. {@code FluentFuture} is targeted at people who use {@code ListenableFuture},
 * who can't use Java 8, or who want an API more focused than {@code CompletableFuture}. (If you
 * need to adapt between {@code CompletableFuture} and {@code ListenableFuture}, consider <a
 * href="https://github.com/lukas-krecan/future-converter">Future Converter</a>.)
 *
 * <p> {@code CompletableFuture}的用户可能想要继续使用{@code CompletableFuture}。
 * {@code FluentFuture}是面向使用{@code ListenableFuture}、无法使用Java 8、或者想要比{@code CompletableFuture}拥有更专注API的人。
 * （如果您需要在{@code CompletableFuture}和{@code ListenableFuture}之间进行适配，
 * 请考虑<a href="https://github.com/lukas-krecan/future-converter"> Future Converter </a>。）
 *
 * <h3>Extension</h3>
 *
 * If you want a class like {@code FluentFuture} but with extra methods, we recommend declaring your
 * own subclass of {@link ListenableFuture}, complete with a method like {@link #from} to adapt an
 * existing {@code ListenableFuture}, implemented atop a {@link ForwardingListenableFuture} that
 * forwards to that future and adds the desired methods.
 *
 * 如果你想要一个像{@code FluentFuture}这样的类但有额外的方法，我们建议你声明自己的{@link ListenableFuture}子类，
 * 并使用类似{@link #from}的方法来修改现有的{@code ListenableFuture}， 基于{@link ForwardingListenableFuture}实现转发future并添加所需的方法。
 *
 * FluentFuture与Futures的区别，两者的区别只是前者用于实现流式调用，后者是大杂烩，前者用到后者的方法了。
 * @since 23.0
 */
@Beta
@GwtCompatible(emulated = true)
public abstract class FluentFuture<V> extends GwtFluentFutureCatchingSpecialization<V> {
  FluentFuture() {}

  /**
   * Converts the given {@code ListenableFuture} to an equivalent {@code FluentFuture}.
   * 将给定的{@code ListenableFuture}转换为等效的{@code FluentFuture}。
   * <p>If the given {@code ListenableFuture} is already a {@code FluentFuture}, it is returned
   * directly. If not, it is wrapped in a {@code FluentFuture} that delegates all calls to the
   * original {@code ListenableFuture}.
   * <p>如果给定的{@code ListenableFuture}已经是{@code FluentFuture}，则会直接返回。
   * 如果没有，它将被包含在{@code FluentFuture}中，该代理将所有调用委托给原始的{@code ListenableFuture}。
   */
  public static <V> FluentFuture<V> from(ListenableFuture<V> future) { // 这个方法的用途，参考类前面的小例子。
    return future instanceof FluentFuture
        ? (FluentFuture<V>) future
        : new ForwardingFluentFuture<V>(future);
  }

  /**
   * Returns a {@code Future} whose result is taken from this {@code Future} or, if this {@code
   * Future} fails with the given {@code exceptionType}, from the result provided by the {@code
   * fallback}. {@link Function#apply} is not invoked until the primary input has failed, so if the
   * primary input succeeds, it is never invoked. If, during the invocation of {@code fallback}, an
   * exception is thrown, this exception is used as the result of the output {@code Future}.
   * 返回{@code Future}，其结果取自此{@code Future}，或者，如果此{@code Future}失败并且抛出给定的{@code exceptionType}，则结果取自{@code fallback}。
   * 在原始future失败之前，不会调用{@link Function#apply}，因此如果原始future成功，则永远不会调用它。 如果在调用{@code fallback}期间抛出异常，
   * 则此异常将用作输出{@code Future}的结果。
   *
   * <p>Usage example:
   *
   * <pre>{@code
   * // Falling back to a zero counter in case an exception happens when processing the RPC to fetch
   * // counters.
   * ListenableFuture<Integer> faultTolerantFuture =
   *     fetchCounters().catching(FetchException.class, x -> 0, directExecutor());
   * }</pre>
   *
   * <p>When selecting an executor, note that {@code directExecutor} is dangerous in some cases. See
   * the discussion in the {@link #addListener} documentation. All its warnings about heavyweight
   * listeners are also applicable to heavyweight functions passed to this method.
   * 选择executor时，请注意{@code directExecutor}在某些情况下是危险的。
   * 请参阅{@link #addListener}文档中的讨论。 关于重量级侦听器的所有警告也适用于传递给此方法的重量级函数。
   *
   * <p>This method is similar to {@link java.util.concurrent.CompletableFuture#exceptionally}. It
   * can also serve some of the use cases of {@link java.util.concurrent.CompletableFuture#handle}
   * and {@link java.util.concurrent.CompletableFuture#handleAsync} when used along with {@link
   * #transform}.
   *
   * <p>此方法类似于{@link java.util.concurrent.CompletableFuture＃exceptionally}。 当与{@link #transform}一起使用时，
   * 它还可以提供{@link java.util.concurrent.CompletableFuture＃handle}和{@link java.util.concurrent.CompletableFuture＃handleAsync}的一些用例。
   *
   * @param exceptionType the exception type that triggers use of {@code fallback}. The exception
   *     type is matched against the input's exception. "The input's exception" means the cause of
   *     the {@link ExecutionException} thrown by {@code input.get()} or, if {@code get()} throws a
   *     different kind of exception, that exception itself. To avoid hiding bugs and other
   *     unrecoverable errors, callers should prefer more specific types, avoiding {@code
   *     Throwable.class} in particular.
   * @param fallback the {@link Function} to be called if the input fails with the expected
   *     exception type. The function's argument is the input's exception. "The input's exception"
   *     means the cause of the {@link ExecutionException} thrown by {@code this.get()} or, if
   *     {@code get()} throws a different kind of exception, that exception itself.
   * @param executor the executor that runs {@code fallback} if the input fails
   */
  @Partially.GwtIncompatible("AVAILABLE but requires exceptionType to be Throwable.class")
  public final <X extends Throwable> FluentFuture<V> catching(
      Class<X> exceptionType, Function<? super X, ? extends V> fallback, Executor executor) {
    return (FluentFuture<V>) Futures.catching(this, exceptionType, fallback, executor);
  }

  /**
   * Returns a {@code Future} whose result is taken from this {@code Future} or, if this {@code
   * Future} fails with the given {@code exceptionType}, from the result provided by the {@code
   * fallback}. {@link AsyncFunction#apply} is not invoked until the primary input has failed, so if
   * the primary input succeeds, it is never invoked. If, during the invocation of {@code fallback},
   * an exception is thrown, this exception is used as the result of the output {@code Future}.
   *
   * <p>Usage examples:
   *
   * <pre>{@code
   * // Falling back to a zero counter in case an exception happens when processing the RPC to fetch
   * // counters.
   * ListenableFuture<Integer> faultTolerantFuture =
   *     fetchCounters().catchingAsync(
   *         FetchException.class, x -> immediateFuture(0), directExecutor());
   * }</pre>
   *
   * <p>The fallback can also choose to propagate the original exception when desired:
   *
   * <pre>{@code
   * // Falling back to a zero counter only in case the exception was a
   * // TimeoutException.
   * ListenableFuture<Integer> faultTolerantFuture =
   *     fetchCounters().catchingAsync(
   *         FetchException.class,
   *         e -> {
   *           if (omitDataOnFetchFailure) {
   *             return immediateFuture(0);
   *           }
   *           throw e;
   *         },
   *         directExecutor());
   * }</pre>
   *
   * <p>When selecting an executor, note that {@code directExecutor} is dangerous in some cases. See
   * the discussion in the {@link #addListener} documentation. All its warnings about heavyweight
   * listeners are also applicable to heavyweight functions passed to this method. (Specifically,
   * {@code directExecutor} functions should avoid heavyweight operations inside {@code
   * AsyncFunction.apply}. Any heavyweight operations should occur in other threads responsible for
   * completing the returned {@code Future}.)
   *
   * <p>This method is similar to {@link java.util.concurrent.CompletableFuture#exceptionally}. It
   * can also serve some of the use cases of {@link java.util.concurrent.CompletableFuture#handle}
   * and {@link java.util.concurrent.CompletableFuture#handleAsync} when used along with {@link
   * #transform}.
   *
   * @param exceptionType the exception type that triggers use of {@code fallback}. The exception
   *     type is matched against the input's exception. "The input's exception" means the cause of
   *     the {@link ExecutionException} thrown by {@code this.get()} or, if {@code get()} throws a
   *     different kind of exception, that exception itself. To avoid hiding bugs and other
   *     unrecoverable errors, callers should prefer more specific types, avoiding {@code
   *     Throwable.class} in particular.
   * @param fallback the {@link AsyncFunction} to be called if the input fails with the expected
   *     exception type. The function's argument is the input's exception. "The input's exception"
   *     means the cause of the {@link ExecutionException} thrown by {@code input.get()} or, if
   *     {@code get()} throws a different kind of exception, that exception itself.
   * @param executor the executor that runs {@code fallback} if the input fails
   */
  @Partially.GwtIncompatible("AVAILABLE but requires exceptionType to be Throwable.class")
  public final <X extends Throwable> FluentFuture<V> catchingAsync(
      Class<X> exceptionType, AsyncFunction<? super X, ? extends V> fallback, Executor executor) {
    return (FluentFuture<V>) Futures.catchingAsync(this, exceptionType, fallback, executor);
  }

  /**
   * Returns a future that delegates to this future but will finish early (via a {@link
   * TimeoutException} wrapped in an {@link ExecutionException}) if the specified timeout expires.
   * If the timeout expires, not only will the output future finish, but also the input future
   * ({@code this}) will be cancelled and interrupted.
   * 返回一个future，这个返回的future将所有操作都委托给this这个future，同时可能会提前超时返回，超时时间内timeout以及unit指定，
   * 超时时，会抛出TimeoutException异常，但是这个是包装在ExecutionException里返回的（具体封装处见：AbstractFuture.getDoneValue里的代码）。
   * 如果超时返回，则不仅会使返回的future结束，也会使输入future（即this future）被取消或者被中断。
   *
   * @param timeout when to time out the future
   * @param unit the time unit of the time parameter
   * @param scheduledExecutor The executor service to enforce the timeout.
   */
  @GwtIncompatible // ScheduledExecutorService
  public final FluentFuture<V> withTimeout(
      long timeout, TimeUnit unit, ScheduledExecutorService scheduledExecutor) {
    return (FluentFuture<V>) Futures.withTimeout(this, timeout, unit, scheduledExecutor);
  }

  /**
   * Returns a new {@code Future} whose result is asynchronously derived from the result of this
   * {@code Future}. If the input {@code Future} fails, the returned {@code Future} fails with the
   * same exception (and the function is not invoked).
   *
   * <p>More precisely, the returned {@code Future} takes its result from a {@code Future} produced
   * by applying the given {@code AsyncFunction} to the result of the original {@code Future}.
   * Example usage:
   *
   * <pre>{@code
   * FluentFuture<RowKey> rowKeyFuture = FluentFuture.from(indexService.lookUp(query));
   * ListenableFuture<QueryResult> queryFuture =
   *     rowKeyFuture.transformAsync(dataService::readFuture, executor);
   * }</pre>
   *
   * <p>When selecting an executor, note that {@code directExecutor} is dangerous in some cases. See
   * the discussion in the {@link #addListener} documentation. All its warnings about heavyweight
   * listeners are also applicable to heavyweight functions passed to this method. (Specifically,
   * {@code directExecutor} functions should avoid heavyweight operations inside {@code
   * AsyncFunction.apply}. Any heavyweight operations should occur in other threads responsible for
   * completing the returned {@code Future}.)
   *
   * <p>The returned {@code Future} attempts to keep its cancellation state in sync with that of the
   * input future and that of the future returned by the chain function. That is, if the returned
   * {@code Future} is cancelled, it will attempt to cancel the other two, and if either of the
   * other two is cancelled, the returned {@code Future} will receive a callback in which it will
   * attempt to cancel itself.
   *
   * <p>This method is similar to {@link java.util.concurrent.CompletableFuture#thenCompose} and
   * {@link java.util.concurrent.CompletableFuture#thenComposeAsync}. It can also serve some of the
   * use cases of {@link java.util.concurrent.CompletableFuture#handle} and {@link
   * java.util.concurrent.CompletableFuture#handleAsync} when used along with {@link #catching}.
   *
   * @param function A function to transform the result of this future to the result of the output
   *     future
   * @param executor Executor to run the function in.
   * @return A future that holds result of the function (if the input succeeded) or the original
   *     input's failure (if not)
   */
  public final <T> FluentFuture<T> transformAsync(
      AsyncFunction<? super V, T> function, Executor executor) {
    return (FluentFuture<T>) Futures.transformAsync(this, function, executor);
  }

  /**
   * Returns a new {@code Future} whose result is derived from the result of this {@code Future}. If
   * this input {@code Future} fails, the returned {@code Future} fails with the same exception (and
   * the function is not invoked). Example usage:
   * 返回一个新的{@code Future}，其结果派生自this{@code Future}的结果。 如果此输入{@code Future}失败，
   * 则返回的{@code Future}将失败并返回相同的异常（并且不会调用该函数）。 用法示例：
   *
   * <pre>{@code
   * ListenableFuture<List<Row>> rowsFuture =
   *     queryFuture.transform(QueryResult::getRows, executor);
   * }</pre>
   *
   * <p>When selecting an executor, note that {@code directExecutor} is dangerous in some cases. See
   * the discussion in the {@link #addListener} documentation. All its warnings about heavyweight
   * listeners are also applicable to heavyweight functions passed to this method.
   * 选择executor时，请注意{@code directExecutor}在某些情况下是危险的。
   * 请参阅{@link #addListener}文档中的讨论。 关于重量级侦听器的所有警告也适用于传递给此方法的重量级函数。
   *
   * <p>The returned {@code Future} attempts to keep its cancellation state in sync with that of the
   * input future. That is, if the returned {@code Future} is cancelled, it will attempt to cancel
   * the input, and if the input is cancelled, the returned {@code Future} will receive a callback
   * in which it will attempt to cancel itself.
   * <p>返回的{@code Future}尝试在取消状态上与输入future保持同步。 也就是说，如果返回的{@code Future}被取消，
   * 它将尝试取消输入future，如果输入future被取消，返回的{@code Future}将收到一个回调函数，它将尝试取消它自己。
   *
   * 保持同步的两个关键点是：
   * 一、如果输入future取消了，则在run方法里设置返回future的取消，见AbstractTransformFuture.run
   * 二、如果返回future取消了，则在afterDone里设置返回future的取消，最关键的是AbstractFuture.maybePropagateCancellationTo的调用，
   * 见AbstractTransformFuture.afterDone。
   *
   * <p>An example use of this method is to convert a serializable object returned from an RPC into
   * a POJO.
   * <p>此方法的一个示例用法是将从RPC返回的可序列化对象转换为POJO。
   *
   * <p>This method is similar to {@link java.util.concurrent.CompletableFuture#thenApply} and
   * {@link java.util.concurrent.CompletableFuture#thenApplyAsync}. It can also serve some of the
   * use cases of {@link java.util.concurrent.CompletableFuture#handle} and {@link
   * java.util.concurrent.CompletableFuture#handleAsync} when used along with {@link #catching}.
   * <p>此方法类似于{@link java.util.concurrent.CompletableFuture＃thenApply}和{@link java.util.concurrent.CompletableFuture＃thenApplyAsync}。
   * 当与{@link #catching}一起使用时，它还可以提供{@link java.util.concurrent.CompletableFuture＃handle}和
   * {@link java.util.concurrent.CompletableFuture＃handleAsync}的一些用例。
   *
   * @param function A Function to transform the results of this future to the results of the
   *     returned future.
   * @param executor Executor to run the function in.
   * @return A future that holds result of the transformation.
   */
  public final <T> FluentFuture<T> transform(Function<? super V, T> function, Executor executor) {
    return (FluentFuture<T>) Futures.transform(this, function, executor);
  }

  /**
   * Registers separate success and failure callbacks to be run when this {@code Future}'s
   * computation is {@linkplain java.util.concurrent.Future#isDone() complete} or, if the
   * computation is already complete, immediately.
   * 给this这个future注册一个回调，包括成功回调方法以及失败回调方法，当this这个future完成后，
   * 会执行回调里的方法，如果this这个future已经完成了，则回调会立即执行。
   *
   * <p>The callback is run on {@code executor}. There is no guaranteed ordering of execution of
   * callbacks, but any callback added through this method is guaranteed to be called once the
   * computation is complete.
   * <p>回调在{@code executor}上运行。 执行回调的顺序没有任何保证，但是保证在计算完成后调用通过此方法添加的任何回调。
   *
   * <p>Example:
   *
   * <pre>{@code
   * future.addCallback(
   *     new FutureCallback<QueryResult>() {
   *       public void onSuccess(QueryResult result) {
   *         storeInCache(result);
   *       }
   *       public void onFailure(Throwable t) {
   *         reportError(t);
   *       }
   *     }, executor);
   * }</pre>
   *
   * <p>When selecting an executor, note that {@code directExecutor} is dangerous in some cases. See
   * the discussion in the {@link #addListener} documentation. All its warnings about heavyweight
   * listeners are also applicable to heavyweight callbacks passed to this method.
   * 选择executor时，请注意{@code directExecutor}在某些情况下是危险的。
   * 请参阅{@link #addListener}文档中的讨论。 关于重量级侦听器的所有警告也适用于传递给此方法的重量级函数。
   *
   * <p>For a more general interface to attach a completion listener, see {@link #addListener}.
   * <p>有关附加完成侦听器的更通用的接口，请参阅{@link #addListener}。
   *
   * <p>This method is similar to {@link java.util.concurrent.CompletableFuture#whenComplete} and
   * {@link java.util.concurrent.CompletableFuture#whenCompleteAsync}. It also serves the use case
   * of {@link java.util.concurrent.CompletableFuture#thenAccept} and {@link
   * java.util.concurrent.CompletableFuture#thenAcceptAsync}.
   * <p>此方法类似于{@link java.util.concurrent.CompletableFuture＃whenComplete}和{@link java.util.concurrent.CompletableFuture＃whenCompleteAsync}。
   * 它还提供了{@link java.util.concurrent.CompletableFuture＃thenAccept}和{@link java.util.concurrent.CompletableFuture＃thenAcceptAsync}的用例。
   *
   * @param callback The callback to invoke when this {@code Future} is completed.
   * @param executor The executor to run {@code callback} when the future completes.
   */
  public final void addCallback(FutureCallback<? super V> callback, Executor executor) {
    Futures.addCallback(this, callback, executor);
  }
}
