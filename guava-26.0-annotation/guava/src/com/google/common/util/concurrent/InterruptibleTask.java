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
  // 执行任务的线程将自己发布到超类的引用，线程中断在完成中断时设置DONE。
  // 下面这个两量是用于表示线程是否中断的，如果是DONE，则表示中断了，如果是INTERRUPTING，则是正在设置中断中。
  // 这两个量设置在父类AtomicReference的volatile类型的value字段里。
  private static final Runnable DONE = new DoNothingRunnable();
  private static final Runnable INTERRUPTING = new DoNothingRunnable();

  @Override
  public final void run() {
    /*
     * Set runner thread before checking isDone(). If we were to check isDone() first, the task
     * might be cancelled before we set the runner thread. That would make it impossible to
     * interrupt, yet it will still run, since interruptTask will leave the runner value null,
     * allowing the CAS below to succeed.
     *
     * 在检查isDone（）之前设置runner线程。 如果我们首先检查isDone（），则在设置runner线程之前可能会取消该任务。
     * 这将使得它无法中断，但它仍将运行，因为interruptTask将使runner值保持为null，从而允许下面的CAS成功。
     *
     * 注意：这里才是开始执行提交的任务的地方，isDone中只可能出现两种情况：任务取消或者任务还没完成。
     *
     * 如果先检查isDone，则有可能检查的时候任务没有取消，但是设置cas前任务取消了，那样的话，cas依然会成功，而且run为true,
     * task依然会执行，只是最后task要设置结果时，会设置不成功。这样会让程序做无用功，所以是先设置cas，然后再检查是否已取消。
     *
     * 如果cas时任务也没取消，在任务运行中取消了，那么就要看AbstractFuture.cancel里的实现了。
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
      // 尝试将任务设置为已完成，以使后面尝试中断的操作失败。
      if (!compareAndSet(currentThread, DONE)) { // 见这个变量的注释。
        // If we were interrupted, it is possible that the interrupted bit hasn't been set yet. Wait
        // for the interrupting thread to set DONE. See interruptTask().
        // We want to wait so that we don't interrupt the _next_ thing run on the thread.
        // Note: We don't reset the interrupted bit, just wait for it to be set.
        // If this is a thread pool thread, the thread pool will reset it for us. Otherwise, the
        // interrupted bit may have been intended for something else, so don't clear it.
        // 如果我们被中断，则可能尚未设置被中断的位。 等待中断线程设置DONE。 请参阅interruptTask（）。
        // 我们要等待当前任务中断完成，以便我们不会错误地中断此线程上后续运行的_next_任务。（这个翻译一定要准确，非常关键！！！）
        // 注意：我们不会重置被中断的位，只需等待它被设置即可。
        // 如果这是一个线程池线程，则线程池将为我们重置它。 否则，被中断的位可能是用于其他的东西，所以不要清除它。

        // 具体执行中断的操作在下面的interruptTask()中。
        while (get() == INTERRUPTING) { // 见这个变量的注释。
          Thread.yield(); // 让出cpu，等待线程被设置为中断状态。（注意：不要把java中的Thread对象与cpu中的执行线程搞混了，
          // java中的Thread对象是映射到cpu中的执行线程上去运行的。
          // 而任务是分配在Thread对象上执行的，所以一般可以把Thread对象看成cpu中的执行线程。
          // 这里只是此Thread对象让出cpu，如果此Thread上绑定了多个任务，则这几个任务都让出cpu了）
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
   * 在这里做可中断的工作 - 不要在这里完成future，因为他们的listener可能会被打断。
   */
  abstract T runInterruptibly() throws Exception;

  /**
   * Any interruption that happens as a result of calling interruptTask will arrive before this
   * method is called. Complete Futures here.
   * 由于调用interruptTask而发生的任何中断都将在调用此方法之前到达。 在这里完成future。
   *
   * 可以看下TrustedFutureInterruptibleAsyncTask.afterRanInterruptibly以及TrustedFutureInterruptibleTask.afterRanInterruptibly里的实现。
   */
  abstract void afterRanInterruptibly(@Nullable T result, @Nullable Throwable error);

  final void interruptTask() {
    // Since the Thread is replaced by DONE before run() invokes listeners or returns, if we succeed
    // in this CAS, there's no risk of interrupting the wrong thread or interrupting a thread that
    // isn't currently executing this task.
    // 由于Thread是在run()调用侦听器或返回之前被替换为DONE（这里是在考虑interruptTask()被调用的时机），
    // 所以，如果我们在此CAS中成功，则不存在中断错误线程或中断当前未执行此任务的线程的风险。
    Runnable currentRunner = get(); // 得到AtomicReference里保存的值。在run()方法里会设置这个值。
    if (currentRunner instanceof Thread && compareAndSet(currentRunner, INTERRUPTING)) { // 这个设置AtomicReference里值。
      ((Thread) currentRunner).interrupt(); // 这个才是真正的中断线程（其实是中断线程上当前在执行的任务，
      // 为了防止错误中断此线程上执行的其他任务，run方法里会让出cpu循环等待，见run里的实现。
      set(DONE); // 这个设置AtomicReference里值，表明InterruptibleTask中断标记设置完成。
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
