/*
 * Copyright (C) 2011 The Guava Authors
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
import com.google.common.annotations.GwtIncompatible;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Abstract {@link ListeningExecutorService} implementation that creates {@link ListenableFuture}
 * instances for each {@link Runnable} and {@link Callable} submitted to it. These tasks are run
 * with the abstract {@link #execute execute(Runnable)} method.
 *
 * <p>In addition to {@link #execute}, subclasses must implement all methods related to shutdown and
 * termination.
 *
 * @author Chris Povirk
 * @since 14.0
 *
 * 见AbstractExecutorService中的类注释：
 * 提供{@link ExecutorService}执行方法的默认实现。 此类使用{@code newTaskFor}返回的{@link RunnableFuture}实现{@code submit}，{@ code invokeAny}和{@code invokeAll}方法，
 * 默认为此中提供的{@link FutureTask}类 包。 例如，{@code submit（Runnable）}的实现会创建一个执行并返回的关联{@code RunnableFuture}。 子类可以覆盖{@code newTaskFor}方法，
 * 以返回{@code FutureTask}以外的{@code RunnableFuture}实现。
 *
 * 这个类最主要的是继承了AbstractExecutorService，然后重写了newTaskFor方法，返回自定义的TrustedListenableFutureTask。
 */
@Beta
@CanIgnoreReturnValue
@GwtIncompatible
public abstract class AbstractListeningExecutorService extends AbstractExecutorService
    implements ListeningExecutorService {

  /** @since 19.0 (present with return type {@code ListenableFutureTask} since 14.0) */
  @Override
  protected final <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
    return TrustedListenableFutureTask.create(runnable, value);
  }

  /** @since 19.0 (present with return type {@code ListenableFutureTask} since 14.0) */
  @Override
  protected final <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
    return TrustedListenableFutureTask.create(callable);
  }

    /**
     * 这里重写了三个submit，其实只是改变了下返回类型，实际操作还是委托给父类实现了。
     * 这里只所以可以改变返回类型，是因为重写了newTaskFor方法，返回的真实类型是ListenableFuture接口的实现类。
     */
  @Override
  public ListenableFuture<?> submit(Runnable task) {
    return (ListenableFuture<?>) super.submit(task);
  }

  @Override
  public <T> ListenableFuture<T> submit(Runnable task, @Nullable T result) {
    return (ListenableFuture<T>) super.submit(task, result);
  }

  @Override
  public <T> ListenableFuture<T> submit(Callable<T> task) {
    return (ListenableFuture<T>) super.submit(task);
  }
}
