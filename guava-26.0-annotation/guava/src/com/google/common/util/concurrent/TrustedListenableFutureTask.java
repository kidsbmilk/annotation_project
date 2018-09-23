/*
 * Copyright (C) 2014 The Guava Authors
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtCompatible;
import com.google.j2objc.annotations.WeakOuter;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RunnableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A {@link RunnableFuture} that also implements the {@link ListenableFuture} interface.
 *
 * <p>This should be used in preference to {@link ListenableFutureTask} when possible for
 * performance reasons.
 *
 * 这个类要实现RunnableFuture，见AbstractListeningExecutorService以及AbstractExecutorService中的注释。
 *
 * 阅读源码要注意类所实现的接口以及继承的抽象类，一般继承的抽象类里会对子类必须实现的方法做注释、说明。
 * 在这个类中，继承自AbstractFuture，在AbstractFuture的类注释中已经明确说明了：Subclasses may also override
 * {@link #afterDone()}, which will be invoked automatically when the future completes. Subclasses
 * should rarely override other methods.（这一点也解决了我的问题中的第一个。）
 *
 * 这个类也继承了RunnableFuture，所以要实现run方法。
 *
 * 面向对象中的面向接口编程，我对其的理解又更进了一步。
 *
 * 明白类间的继承与实现关系后，再去自己造轮子就知道流程了，就能更好地理解实现细节了。
 */

/**
 * 明白了InterruptibleTask.isDone与AbstractFuture.isDone的关系了。
 * 代码关系为:比如InterruptibleTask的实现类TrustedFutureInterruptibleTask，由于这个实现类为TrustedListenableFutureTask的私有内部类，而这个私有内部类的idDone的实现是委托给外部类（也即TrustedListenableFutureTask，是一个AbstractFuture.TrustedFuture类型），所以最终的InterruptibleTask.isDone其实就是AbstractFuture.isDone。这是代码上的关系。
 * 设计思想上的关系是：task描述了一个任务，而这个任务在执行时会返回一个future给调用者，所以Task的完成与否与future的完成与否是等价的。
 *
 * 整体流程是这样的：
 * MoreExecutors.listeningDecorator -> ListeningDecorator，继承自AbstractListeningExecutorService，覆盖了execute方法 -> 在用户submit任务时，AbstractListeningExecutorService重写了newTaskFor，并且AbstractListeningExecutorService的submit是转到了AbstractExecutorService里的submit，AbstractListeningExecutorService.newTaskFor引出TrustedFutureInterruptibleTask
 * 这里说一下TrustedListenableFutureTask与TrustedFutureInterruptibleTask的关系，TrustedFutureInterruptibleTask是TrustedListenableFutureTask的私有内部类，TrustedListenableFutureTask中持有TrustedFutureInterruptibleTask对象。
 * TrustedListenableFutureTask的run最终转到TrustedFutureInterruptibleTask的run方法，而TrustedFutureInterruptibleTask的run是继承自InterruptibleTask的。
 * ，TrustedFutureInterruptibleTask的isDone会转到调用TrustedListenableFutureTask的isDone（继承自AbstractFuture），TrustedFutureInterruptibleTask的afterRanInterruptibly会调用TrustedListenableFutureTask的set方法（继承自AbstractFuture）。
 *
 * 这样，在TrustedFutureInterruptibleTask执行完后会调用AbstractFuture的结果，并处理等待线程以及监听器。
 */
@GwtCompatible
class TrustedListenableFutureTask<V> extends AbstractFuture.TrustedFuture<V>
    implements RunnableFuture<V> { // java没有多重继承，这个TrustedListenableFutureTask要描述一个任务，
  // 同时要具有对ListenableFuture的相关操作功能，所以，这里的实现方式是继承AbstractFuture.TrustedFuture，
  // 同时，实现RunnableFuture，将RunnableFuture.run方法转到TrustedListenableFutureTask持有的InterruptibleTask类型成员变量的run方法里。
  // 算是明白这里的设计思想了。
  // 注意：这里使用了内部类，可以加深对内部类的使用场景的理解。TrustedListenableFutureTask要持有InterruptibleTask类型的成员变量，
  // 而这个成员变量类型只在TrustedListenableFutureTask里被使用了，所以，TrustedFutureInterruptibleTask以及
  // TrustedFutureInterruptibleAsyncTask被设计为TrustedListenableFutureTask的私有内部类。
  // 类似的还有，比如AbstractFutur中，AtomicHelper实现类的实现方式。

  static <V> TrustedListenableFutureTask<V> create(AsyncCallable<V> callable) { // 注意这是静态方法
    return new TrustedListenableFutureTask<V>(callable); // 这个是调用的非静态方法，最终转到带有是否中断状态的task上。
  }

  static <V> TrustedListenableFutureTask<V> create(Callable<V> callable) { // 注意这是静态方法
    return new TrustedListenableFutureTask<V>(callable); // 这个是调用的非静态方法，最终转到带有是否中断状态的task上。
  }

  /**
   * Creates a {@code ListenableFutureTask} that will upon running, execute the given {@code
   * Runnable}, and arrange that {@code get} will return the given result on successful completion.
   *
   * @param runnable the runnable task
   * @param result the result to return on successful completion. If you don't need a particular
   *     result, consider using constructions of the form: {@code ListenableFuture<?> f =
   *     ListenableFutureTask.create(runnable, null)}
   */
  static <V> TrustedListenableFutureTask<V> create(Runnable runnable, @Nullable V result) {
    return new TrustedListenableFutureTask<V>(Executors.callable(runnable, result));
  }

  /*
   * In certain circumstances, this field might theoretically not be visible to an afterDone() call
   * triggered by cancel(). For details, see the comments on the fields of TimeoutFuture.
   *
   * <p>{@code volatile} is required for j2objc transpiling:
   * https://developers.google.com/j2objc/guides/j2objc-memory-model#atomicity
   */
  private volatile InterruptibleTask<?> task;

  TrustedListenableFutureTask(Callable<V> callable) {
    this.task = new TrustedFutureInterruptibleTask(callable); // 这个是组合了可中断的特点。
  }

  TrustedListenableFutureTask(AsyncCallable<V> callable) {
    this.task = new TrustedFutureInterruptibleAsyncTask(callable); // 这个是组合了可中断的特点。
  }

  @Override
  public void run() {
    InterruptibleTask localTask = task;
    if (localTask != null) {
      localTask.run(); // 这个里会调用isDone，runInterruptibly，afterRanInterruptibly，也是InterruptibleTask需要实现的方法。
    }
    /*
     * In the Async case, we may have called setFuture(pendingFuture), in which case afterDone()
     * won't have been called yet.
     */
    this.task = null;
  }

  @Override
  protected void afterDone() {
    super.afterDone();

    if (wasInterrupted()) {
      InterruptibleTask localTask = task;
      if (localTask != null) {
        localTask.interruptTask();
      }
    }

    this.task = null;
  }

  @Override
  protected String pendingToString() {
    InterruptibleTask localTask = task;
    if (localTask != null) {
      return "task=[" + localTask + "]";
    }
    return super.pendingToString();
  }

  // 这个私有内部类继承了InterruptibleTask，要实现其中的三个抽象方法。
    // InterruptibleTask相当于一个组件、成员变量，就是是否中断了，这个TrustedFutureInterruptibleTask结合了是否中断信息以及TrustedListenableFutureTask。
  @WeakOuter
  private final class TrustedFutureInterruptibleTask extends InterruptibleTask<V> {
    private final Callable<V> callable;

    TrustedFutureInterruptibleTask(Callable<V> callable) {
      this.callable = checkNotNull(callable);
    }

    @Override
    final boolean isDone() {
      return TrustedListenableFutureTask.this.isDone(); // 注意：这里用到了TrustedListenableFutureTask.this，此类是TrustedListenableFutureTask的内部类，它要调用TrustedListenableFutureTask的isDone方法，所以这样写了，
        // 先取出当前的实例，然后调用其isDone方法。
      // 注意：这里这所以通过this来访问外部类的isDone，是因为当前类中也有个isDone。
    }

    @Override
    V runInterruptibly() throws Exception {
      return callable.call();
    }

    @Override
    void afterRanInterruptibly(V result, Throwable error) {
      if (error == null) {
        TrustedListenableFutureTask.this.set(result);
        // 注意：这里这所以通过this来访问外部类的set，是因为当前类中也有个set，因为InterruptibleTask继承自AtomicReference。
      } else {
        setException(error); // 访问外部类中的方法，此方法是外部类的父类AbstractFuture中的。
      }
    }

    @Override
    String toPendingString() {
      return callable.toString();
    }
  }

  @WeakOuter
  private final class TrustedFutureInterruptibleAsyncTask
      extends InterruptibleTask<ListenableFuture<V>> {
    private final AsyncCallable<V> callable;

    TrustedFutureInterruptibleAsyncTask(AsyncCallable<V> callable) {
      this.callable = checkNotNull(callable);
    }

    @Override
    final boolean isDone() {
      return TrustedListenableFutureTask.this.isDone();
    }

    @Override
    ListenableFuture<V> runInterruptibly() throws Exception {
      return checkNotNull(
          callable.call(),
          "AsyncCallable.call returned null instead of a Future. "
              + "Did you mean to return immediateFuture(null)?");
    }

    @Override
    void afterRanInterruptibly(ListenableFuture<V> result, Throwable error) {
      if (error == null) {
        setFuture(result);
      } else {
        setException(error);
      }
    }

    @Override
    String toPendingString() {
      return callable.toString();
    }
  }
}
