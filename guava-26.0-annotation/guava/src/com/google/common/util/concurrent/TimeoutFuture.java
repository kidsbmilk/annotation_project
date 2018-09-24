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

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implementation of {@code Futures#withTimeout}.
 *
 * <p>Future that delegates to another but will finish early (via a {@link TimeoutException} wrapped
 * in an {@link ExecutionException}) if the specified duration expires. The delegate future is
 * interrupted and cancelled if it times out.
 * 这个的翻译见FluentFuture.withTimeout方法的注释。
 */

/**
 * 这个类的设计方式与AbstractCatchingFuture以及TrustedListenableFutureTask不同，
 * 感觉不是同一个人写的。
 * 这里只所以与其他的设计不同，原因参考：Fire.run里的注释。
 * 但是，还是觉得其他的设计也可以实现同样的功能。
 */
@GwtIncompatible
final class TimeoutFuture<V> extends AbstractFuture.TrustedFuture<V> {
  static <V> ListenableFuture<V> create(
      ListenableFuture<V> delegate,
      long time,
      TimeUnit unit,
      ScheduledExecutorService scheduledExecutor) {
    TimeoutFuture<V> result = new TimeoutFuture<>(delegate);
    Fire<V> fire = new Fire<>(result);
    result.timer = scheduledExecutor.schedule(fire, time, unit);
    delegate.addListener(fire, directExecutor());
    return result;
  }

  /*
   * Memory visibility of these fields. There are two cases to consider.
   * 这些字段的内存可见性。 有两种情况需要考虑。
   *
   * 1. visibility of the writes to these fields to Fire.run:
   * 1.写入这些字段对Fire.run的可见性：
   *
   * The initial write to delegateRef is made definitely visible via the semantics of
   * addListener/SES.schedule. The later racy write in cancel() is not guaranteed to be observed,
   * however that is fine since the correctness is based on the atomic state in our base class. The
   * initial write to timer is never definitely visible to Fire.run since it is assigned after
   * SES.schedule is called. Therefore Fire.run has to check for null. However, it should be visible
   * if Fire.run is called by delegate.addListener since addListener is called after the assignment
   * to timer, and importantly this is the main situation in which we need to be able to see the
   * write.
    通过addListener / SES.schedule的语义，可以清楚地看到对delegateRef的初始写入。
    不能保证在cancel（）中的后续racy写入被观察到，但是这很好，因为正确性基于我们基类中的原子状态。
    计时器的初始写入对于Fire.run并不是绝对可见，因为它是在调用SES.schedule之后分配的。
    因此Fire.run必须检查计时器是否为null。 但是，如果Fire.run是由delegate.addListener调用的，
    它应该是可见的，这是因为是在分配给定时器之后调用addListener，重要的是这是我们使用它的主要场景，
    在这些场景中我们需要能够看到定时器的初始写入。
   * 2. visibility of the writes to an afterDone() call triggered by cancel():
   * 2.变量写入对cancel()触发的afterDone()调用的可见性：
   *
   * Since these fields are non-final that means that TimeoutFuture is not being 'safely published',
   * thus a motivated caller may be able to expose the reference to another thread that would then
   * call cancel() and be unable to cancel the delegate.
   * There are a number of ways to solve this, none of which are very pretty, and it is currently
   * believed to be a purely theoretical problem (since the other actions should supply sufficient
   * write-barriers).
   * 由于这些字段是非final的，这意味着TimeoutFuture没有被“安全发布”，因此别有用心的调用者可能会将引用暴露给另一个线程，
   * 然后这个另一个线程可能调用cancel（）并且无法取消该委托。有许多方法可以解决这个问题，没有一个非常漂亮，
   * 而且目前认为这是一个纯粹的理论问题（因为其他行动应该提供足够的写屏障）。
   */

  private @Nullable ListenableFuture<V> delegateRef;
  private @Nullable Future<?> timer;

  private TimeoutFuture(ListenableFuture<V> delegate) {
    this.delegateRef = Preconditions.checkNotNull(delegate);
  }

  /** A runnable that is called when the delegate or the timer completes. */
  private static final class Fire<V> implements Runnable {
    @Nullable TimeoutFuture<V> timeoutFutureRef;

    Fire(TimeoutFuture<V> timeoutFuture) {
      this.timeoutFutureRef = timeoutFuture;
    }

    @Override
    public void run() {
      // If either of these reads return null then we must be after a successful cancel or another
      // call to this method.
      // 如果这些读取中的任何一个返回null，那么我们必定处在成功取消或对此方法的另一次调用之后的情况。
      TimeoutFuture<V> timeoutFuture = timeoutFutureRef;
      if (timeoutFuture == null) {
        return;
      }
      ListenableFuture<V> delegate = timeoutFuture.delegateRef;
      if (delegate == null) {
        return;
      }

      /*
       * If we're about to complete the TimeoutFuture, we want to release our reference to it.
       * Otherwise, we'll pin it (and its result) in memory until the timeout task is GCed. (The
       * need to clear our reference to the TimeoutFuture is the reason we use a *static* nested
       * class with a manual reference back to the "containing" class.)
       * 如果我们即将完成TimeoutFuture，我们希望释放我们对它的引用。否则，我们会将它（及其结果）固定在内存中，直到超时任务被GC为止。
       * （需要清除我们对TimeoutFuture的引用是我们使用* static *嵌套类和手动引用回到“包含”类的原因。）
       *
       * This has the nice-ish side effect of limiting reentrancy: run() calls
       * timeoutFuture.setException() calls run(). That reentrancy would already be harmless, since
       * timeoutFuture can be set (and delegate cancelled) only once. (And "set only once" is
       * important for other reasons: run() can still be invoked concurrently in different threads,
       * even with the above null checks.)
       * 这具有限制重入的良好副作用：run()调用timeoutFuture.setException()再调用run()。 这种重入已经是无害的，
       * 因为timeoutFuture只能设置（并且委托取消）一次。 （“仅设置一次”对于其他原因很重要，比如：即使使用上述空检查，
       * 仍然可以在不同的线程中同时调用run()。）
       */
      timeoutFutureRef = null;
      if (delegate.isDone()) {
        timeoutFuture.setFuture(delegate); // 其实这里已经确定了delegate已经完成了，可以使用使用set(getFutureValue(delegate))
        // 不行，因为getFutureValue是私有方法。
      } else {
        try {
          // TODO(lukes): this stack trace is particularly useless (all it does is point at the
          // scheduledexecutorservice thread), consider eliminating it altogether?
          timeoutFuture.setException(new TimeoutException("Future timed out: " + delegate));
        } finally {
          delegate.cancel(true);
        }
      }
    }
  }

  @Override
  protected String pendingToString() {
    ListenableFuture<? extends V> localInputFuture = delegateRef;
    if (localInputFuture != null) {
      return "inputFuture=[" + localInputFuture + "]";
    }
    return null;
  }

  @Override
  protected void afterDone() {
    maybePropagateCancellationTo(delegateRef);

    Future<?> localTimer = timer;
    // Try to cancel the timer as an optimization.
    // timer may be null if this call to run was by the timer task since there is no happens-before
    // edge between the assignment to timer and an execution of the timer task.
    if (localTimer != null) {
      localTimer.cancel(false);
    }

    delegateRef = null;
    timer = null;
  }
}
