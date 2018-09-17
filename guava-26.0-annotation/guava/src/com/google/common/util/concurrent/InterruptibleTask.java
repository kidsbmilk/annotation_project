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

import com.google.common.annotations.GwtCompatible;
import com.google.j2objc.annotations.ReflectionSupport;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.Nullable;

@GwtCompatible(emulated = true)
@ReflectionSupport(value = ReflectionSupport.Level.FULL)
// Some Android 5.0.x Samsung devices have bugs in JDK reflection APIs that cause
// getDeclaredField to throw a NoSuchFieldException when the field is definitely there.
// Since this class only needs CAS on one field, we can avoid this bug by extending AtomicReference
// instead of using an AtomicReferenceFieldUpdater. This reference stores Thread instances
// and DONE/INTERRUPTED - they have a common ancestor of Runnable.
/**
 * 一些Android 5.0.x三星设备在JDK反射API中存在错误，导致getDeclaredField在字段肯定存在时抛出NoSuchFieldException。
 * 由于此类仅在一个字段上需要CAS，因此我们可以通过扩展AtomicReference而不是使用AtomicReferenceFieldUpdater来避免此错误。
 * 此引用存储Thread实例和DONE / INTERRUPTED - 它们具有Runnable的共同祖先。
 *
 * 不光是在这里做了防止上面出错的措施，在AbstractFuture里做判断了，不过那里字段太多，使用了synchronized来解决问题。
 * 那里只所以不使用AtomicReferenceFieldUpdater，是因为那里的AtomicReferenceFieldUpdater是基于反射来得到变量的，而根本问题就出在反射有问题上。所以使用了synchronized。
 *
 * 注意：这个类继承自AtomicReference<Runnable>，所以在下面可以使用：compareAndSet(currentThread, DONE)，因为Thread和DONE都实现了Runnable接口。
 *
 * 注意：上面说“此类仅有一个字段”，是指这个类是用来封装是否可中断的，来描述任务的中断状态的，正常情况下只需要一个字段就行，
 * 但是由于上面的原因，所以这里扩展了AtomicReference，用两个字段来描述是否中断状态的。
 */
abstract class InterruptibleTask<T> extends AtomicReference<Runnable> implements Runnable {
  private static final class DoNothingRunnable implements Runnable {
    @Override
    public void run() {}
  }
  // The thread executing the task publishes itself to the superclass' reference and the thread
  // interrupting sets DONE when it has finished interrupting.
  private static final Runnable DONE = new DoNothingRunnable();
  private static final Runnable INTERRUPTING = new DoNothingRunnable();

  @Override
  public final void run() {
    /*
     * Set runner thread before checking isDone(). If we were to check isDone() first, the task
     * might be cancelled before we set the runner thread. That would make it impossible to
     * interrupt, yet it will still run, since interruptTask will leave the runner value null,
     * allowing the CAS below to succeed.
     */
    Thread currentThread = Thread.currentThread();
    if (!compareAndSet(null, currentThread)) {
      return; // someone else has run or is running.
    }

    boolean run = !isDone();
    T result = null;
    Throwable error = null;
    try {
      if (run) {
        result = runInterruptibly();
      }
    } catch (Throwable t) {
      error = t;
    } finally {
      // Attempt to set the task as done so that further attempts to interrupt will fail.
      if (!compareAndSet(currentThread, DONE)) {
        // If we were interrupted, it is possible that the interrupted bit hasn't been set yet. Wait
        // for the interrupting thread to set DONE. See interruptTask().
        // We want to wait so that we don't interrupt the _next_ thing run on the thread.
        // Note: We don't reset the interrupted bit, just wait for it to be set.
        // If this is a thread pool thread, the thread pool will reset it for us. Otherwise, the
        // interrupted bit may have been intended for something else, so don't clear it.
        while (get() == INTERRUPTING) {
          Thread.yield();
        }
        /*
         * TODO(cpovirk): Clear interrupt status here? We currently don't, which means that an
         * interrupt before, during, or after runInterruptibly() (unless it produced an
         * InterruptedException caught above) can linger and affect listeners.
         */
      }
      if (run) {
        afterRanInterruptibly(result, error);
      }
    }
  }

  /**
   * Called before runInterruptibly - if true, runInterruptibly and afterRanInterruptibly will not
   * be called.
   */
  abstract boolean isDone();

  /**
   * Do interruptible work here - do not complete Futures here, as their listeners could be
   * interrupted.
   */
  abstract T runInterruptibly() throws Exception;

  /**
   * Any interruption that happens as a result of calling interruptTask will arrive before this
   * method is called. Complete Futures here.
   */
  abstract void afterRanInterruptibly(@Nullable T result, @Nullable Throwable error);

  final void interruptTask() {
    // Since the Thread is replaced by DONE before run() invokes listeners or returns, if we succeed
    // in this CAS, there's no risk of interrupting the wrong thread or interrupting a thread that
    // isn't currently executing this task.
      // 由于在run（）调用侦听器或返回之前，Thread被DONE替换，如果我们在此CAS中成功，则不存在中断错误线程或中断当前未执行此任务的线程的风险。
    Runnable currentRunner = get();
    if (currentRunner instanceof Thread && compareAndSet(currentRunner, INTERRUPTING)) {
      ((Thread) currentRunner).interrupt();
      set(DONE);
    }
  }

  @Override
  public final String toString() {
    Runnable state = get();
    final String result;
    if (state == DONE) {
      result = "running=[DONE]";
    } else if (state == INTERRUPTING) {
      result = "running=[INTERRUPTED]";
    } else if (state instanceof Thread) {
      // getName is final on Thread, no need to worry about exceptions
      result = "running=[RUNNING ON " + ((Thread) state).getName() + "]";
    } else {
      result = "running=[NOT STARTED YET]";
    }
    return result + ", " + toPendingString();
  }

  abstract String toPendingString();
}
