/*
 * Copyright (C) 2015 The Guava Authors
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

import static com.google.common.collect.Sets.newConcurrentHashSet;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

import com.google.common.annotations.GwtCompatible;
import com.google.j2objc.annotations.ReflectionSupport;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A helper which does some thread-safe operations for aggregate futures, which must be implemented
 * differently in GWT. Namely:
 * 一个为聚合future做一些线程安全操作的帮助类，必须在GWT中以不同方式实现。即：
 *
 * <ul>
 *   <li>Lazily initializes a set of seen exceptions
 *       Lazily初始化了一组看到的异常
 *   <li>Decrements a counter atomically
 *       原子地减少计数器
 * </ul>
 *
 * 见RunningState类前的注释。
 */
@GwtCompatible(emulated = true)
@ReflectionSupport(value = ReflectionSupport.Level.FULL)
abstract class AggregateFutureState {
  // Lazily initialized the first time we see an exception; not released until all the input futures
  // & this future completes. Released when the future releases the reference to the running state
  // 延迟初始化，直到我们第一次看到异常时才初始化它们; 直到所有输入future和这个future完成后才会释放它们。
  // 在future释放对运行状态的引用时才会释放此对象。
  private volatile Set<Throwable> seenExceptions = null;

  private volatile int remaining;

  private static final AtomicHelper ATOMIC_HELPER;

  private static final Logger log = Logger.getLogger(AggregateFutureState.class.getName());

  static {
    AtomicHelper helper;
    Throwable thrownReflectionFailure = null;
    try {
      helper =
          new SafeAtomicHelper(
              newUpdater(AggregateFutureState.class, (Class) Set.class, "seenExceptions"),
              newUpdater(AggregateFutureState.class, "remaining"));
    } catch (Throwable reflectionFailure) {
      // Some Android 5.0.x Samsung devices have bugs in JDK reflection APIs that cause
      // getDeclaredField to throw a NoSuchFieldException when the field is definitely there.
      // For these users fallback to a suboptimal implementation, based on synchronized. This will
      // be a definite performance hit to those users.
        // 这里的设计参考AbstracfFuture中的静态初始化块。
      thrownReflectionFailure = reflectionFailure;
      helper = new SynchronizedAtomicHelper();
    }
    ATOMIC_HELPER = helper;
    // Log after all static init is finished; if an installed logger uses any Futures methods, it
    // shouldn't break in cases where reflection is missing/broken.
      // 所有静态init完成后记录; 如果安装的记录器使用任何Futures方法，则在反射丢失/损坏的情况下不应该破坏。
    if (thrownReflectionFailure != null) {
      log.log(Level.SEVERE, "SafeAtomicHelper is broken!", thrownReflectionFailure);
    }
  }

  AggregateFutureState(int remainingFutures) {
    this.remaining = remainingFutures;
  }

  final Set<Throwable> getOrInitSeenExceptions() {
    /*
     * The initialization of seenExceptions has to be more complicated than we'd like. The simple
     * approach would be for each caller CAS it from null to a Set populated with its exception. But
     * there's another race: If the first thread fails with an exception and a second thread
     * immediately fails with the same exception:
     * seenExceptions的初始化必须比我们想要的更复杂。 简单的方法是每个调用者CAS从null到填充了它的异常的Set。
     * 但还有另一种竞争：如果第一个线程因异常而失败，第二个线程立即失败并出现相同的异常：
     *
     * Thread1: calls setException(), which returns true, context switch before it can CAS
     * seenExceptions to its exception
     * Thread1：调用setException()，它返回true，在CAS操作将seenExceptions设置为它的异常之前发生了上下文切换。
     *
     * Thread2: calls setException(), which returns false, CASes seenExceptions to its exception,
     * and wrongly believes that its exception is new (leading it to logging it when it shouldn't)
     * Thread2：调用setException()，返回false，CAS操作将seenExceptions设置为它的异常，并错误地认为它的异常是新的（导致它在不应该的时候记录它）
     *
     * Our solution is for threads to CAS seenExceptions from null to a Set population with _the
     * initial exception_, no matter which thread does the work. This ensures that seenExceptions
     * always contains not just the current thread's exception but also the initial thread's.
     * 我们的解决方案是针对CAS操作seenExceptions的线程，将seenExceptions从null设置为初始异常，无论哪个线程都起作用。
     * 这可以确保seenExceptions不仅包含当前线程的异常，还包含初始线程的异常。
     */
    Set<Throwable> seenExceptionsLocal = seenExceptions;
    if (seenExceptionsLocal == null) {
      seenExceptionsLocal = newConcurrentHashSet();
      /*
       * Other handleException() callers may see this as soon as we publish it. We need to populate
       * it with the initial failure before we do, or else they may think that the initial failure
       * has never been seen before.
       * 其他handleException（）调用者可以在我们发布它时立即看到它。 在我们做之前，我们需要用初始失败来填充它，否则他们可能会认为以前从未见过初始失败。
       */
      addInitialException(seenExceptionsLocal);

      ATOMIC_HELPER.compareAndSetSeenExceptions(this, null, seenExceptionsLocal);
      /*
       * If another handleException() caller created the set, we need to use that copy in case yet
       * other callers have added to it.
       * 如果另一个handleException（）调用者创建了该集合，我们需要使用该副本，以防其他调用者添加到该集合中。
       *
       * This read is guaranteed to get us the right value because we only set this once (here).
       * 这个读取保证给我们正确的值，因为我们只设置了一次（这里）。
       */
      seenExceptionsLocal = seenExceptions;
    }
    return seenExceptionsLocal;
  }

  /** Populates {@code seen} with the exception that was passed to {@code setException}. */
  abstract void addInitialException(Set<Throwable> seen); // 见getOrInitSeenExceptions里对此方法的说明。

  final int decrementRemainingAndGet() {
    return ATOMIC_HELPER.decrementAndGetRemainingCount(this);
  }

  private abstract static class AtomicHelper {
    /** Atomic compare-and-set of the {@link AggregateFutureState#seenExceptions} field. */
    abstract void compareAndSetSeenExceptions(
        AggregateFutureState state, Set<Throwable> expect, Set<Throwable> update);

    /** Atomic decrement-and-get of the {@link AggregateFutureState#remaining} field. */
    abstract int decrementAndGetRemainingCount(AggregateFutureState state);
  }

  private static final class SafeAtomicHelper extends AtomicHelper {
    final AtomicReferenceFieldUpdater<AggregateFutureState, Set<Throwable>> seenExceptionsUpdater;

    final AtomicIntegerFieldUpdater<AggregateFutureState> remainingCountUpdater;

    SafeAtomicHelper(
        AtomicReferenceFieldUpdater seenExceptionsUpdater,
        AtomicIntegerFieldUpdater remainingCountUpdater) {
      this.seenExceptionsUpdater = seenExceptionsUpdater;
      this.remainingCountUpdater = remainingCountUpdater;
    }

    @Override
    void compareAndSetSeenExceptions(
        AggregateFutureState state, Set<Throwable> expect, Set<Throwable> update) {
      seenExceptionsUpdater.compareAndSet(state, expect, update);
    }

    @Override
    int decrementAndGetRemainingCount(AggregateFutureState state) {
      return remainingCountUpdater.decrementAndGet(state);
    }
  }

  private static final class SynchronizedAtomicHelper extends AtomicHelper {
    @Override
    void compareAndSetSeenExceptions(
        AggregateFutureState state, Set<Throwable> expect, Set<Throwable> update) {
      synchronized (state) {
        if (state.seenExceptions == expect) {
          state.seenExceptions = update;
        }
      }
    }

    @Override
    int decrementAndGetRemainingCount(AggregateFutureState state) {
      synchronized (state) {
        state.remaining--;
        return state.remaining;
      }
    }
  }
}
