/*
 * Copyright (C) 2007 The Guava Authors
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
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.util.concurrent.Futures.getDone;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

import com.google.common.annotations.Beta;
import com.google.common.annotations.GwtCompatible;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;
import com.google.j2objc.annotations.ReflectionSupport;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An abstract implementation of {@link ListenableFuture}, intended for advanced users only. More
 * common ways to create a {@code ListenableFuture} include instantiating a {@link SettableFuture},
 * submitting a task to a {@link ListeningExecutorService}, and deriving a {@code Future} from an
 * existing one, typically using methods like {@link Futures#transform(ListenableFuture,
 * com.google.common.base.Function, java.util.concurrent.Executor) Futures.transform} and {@link
 * Futures#catching(ListenableFuture, Class, com.google.common.base.Function,
 * java.util.concurrent.Executor) Futures.catching}.
 *
 * <p>This class implements all methods in {@code ListenableFuture}. Subclasses should provide a way
 * to set the result of the computation through the protected methods {@link #set(Object)}, {@link
 * #setFuture(ListenableFuture)} and {@link #setException(Throwable)}. Subclasses may also override
 * {@link #afterDone()}, which will be invoked automatically when the future completes. Subclasses
 * should rarely override other methods.（这一点也解决了我的问题中的第一个。）
 *
 * {@link ListenableFuture}的抽象实现，仅供高级用户使用。创建{@code ListenableFuture}的更常见方法包括实例化{@link SettableFuture}，
 * 将任务提交到{@link ListeningExecutorService}，并从现有的任务中派生{@code Future}，
 * 通常使用{@link等方法链接Futures＃transform（ListenableFuture，com.google.common.base.Function，java.util.concurrent.Executor）Futures.transform}
 * 和{@link Futures＃catch（ListenableFuture，Class，com.google.common.base.Function ，java.util.concurrent.Executor）Futures.catching}。
 *
 * <p>此类实现{@code ListenableFuture}中的所有方法。子类应该提供一种通过受保护的方法{@link #set（Object）}，
 * {@ link #setFuture（ListenableFuture）}和{@link #setException（Throwable）}来设置计算结果的方法（注意这句话，是提供一种基于set以及setFuture的方法，而不是重写这两个方法）。
 * 子类也可以覆盖{@link #afterDone（）}，这将在将来完成时自动调用。子类应该很少覆盖其他方法。
 *
 * @author Sven Mawson
 * @author Luke Sandberg
 * @since 1.0
 */
@SuppressWarnings("ShortCircuitBoolean") // we use non-short circuiting comparisons intentionally 我们故意使用非短路比较
@GwtCompatible(emulated = true)
@ReflectionSupport(value = ReflectionSupport.Level.FULL) // 这个是com.google.j2objc.annotations中的。
public abstract class AbstractFuture<V> extends FluentFuture<V> {
  // NOTE: Whenever both tests are cheap and functional, it's faster to use &, | instead of &&, ||

  private static final boolean GENERATE_CANCELLATION_CAUSES =
      Boolean.parseBoolean(
          System.getProperty("guava.concurrent.generate_cancellation_cause", "false"));

  /**
   * A less abstract subclass of AbstractFuture. This can be used to optimize setFuture by ensuring
   * that {@link #get} calls exactly the implementation of {@link AbstractFuture#get}.
   * AbstractFuture的一个不太抽象的子类。 这可以通过确保{@link #get}完全调用{@link AbstractFuture＃get}的实现来优化setFuture。
   *
   * 不太明白这里的注释是什么意思，跟setFuture有什么关系？ 是这样的，get方法里涉及到SetFuture这个可能的结果对象（原因要从setFuture方法说起，见那里的注释），
   * 这个TrustedFuture方法不改写父类的get方法，也就不需要其子类去处理SetFuture这个可能的结果对象了，用起来比较方便。
   * 算是一种标记说明，并不是什么必须这样实现，其实只要确认AbstractFuture不会改写get方法，也是可以直接继承AbstractFuture的。
   *
   * 其实这个类的其他实现大多也涉及到SetFuture了。
   *
   * 可以看一下TrustedFuture的继承类是如何实现的，都重写了哪些方法。
   */
  abstract static class TrustedFuture<V> extends AbstractFuture<V> {
    @CanIgnoreReturnValue
    @Override
    public final V get() throws InterruptedException, ExecutionException {
      return super.get();
    }

    @CanIgnoreReturnValue
    @Override
    public final V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return super.get(timeout, unit);
    }

    @Override
    public final boolean isDone() {
      return super.isDone();
    }

    @Override
    public final boolean isCancelled() {
      return super.isCancelled();
    }

    @Override
    public final void addListener(Runnable listener, Executor executor) {
      super.addListener(listener, executor);
    }

    @CanIgnoreReturnValue
    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
      return super.cancel(mayInterruptIfRunning);
    }
  }

  // Logger to log exceptions caught when running listeners.
  private static final Logger log = Logger.getLogger(AbstractFuture.class.getName());

  // A heuristic for timed gets. If the remaining timeout is less than this, spin instead of
  // blocking. This value is what AbstractQueuedSynchronizer uses.
  // 时间启发式获取。 如果剩余超时小于此值，则CPU自旋而不是阻塞。 此值是AbstractQueuedSynchronizer使用的值。
  private static final long SPIN_THRESHOLD_NANOS = 1000L;

  private static final AtomicHelper ATOMIC_HELPER;

  static {
    AtomicHelper helper;
    Throwable thrownUnsafeFailure = null;
    Throwable thrownAtomicReferenceFieldUpdaterFailure = null;

    try {
      helper = new UnsafeAtomicHelper();
    } catch (Throwable unsafeFailure) {
      thrownUnsafeFailure = unsafeFailure;
      // catch absolutely everything and fall through to our 'SafeAtomicHelper'
      // The access control checks that ARFU does means the caller class has to be AbstractFuture
      // instead of SafeAtomicHelper, so we annoyingly define these here
      // 绝对抓住所有东西（因为异常类型为Throwable），然后进入我们的'SafeAtomicHelper'。
      // 访问控制检查ARFU是否意味着调用者类必须是AbstractFuture而不是SafeAtomicHelper，所以我们在这里烦人地定义这些
      try {
        helper =
            new SafeAtomicHelper(
                newUpdater(Waiter.class, Thread.class, "thread"),
                newUpdater(Waiter.class, Waiter.class, "next"),
                newUpdater(AbstractFuture.class, Waiter.class, "waiters"),
                newUpdater(AbstractFuture.class, Listener.class, "listeners"),
                newUpdater(AbstractFuture.class, Object.class, "value"));
        // newUpdater可能会抛出一些非受检异常，如ClassCastException、IllegalArgumentException、RuntimeException，见方法的注释。
        // 所以这里捕获处理了一下。
        // 非受检异常（运行时异常）和受检异常的区别等：https://www.cnblogs.com/jimoer/p/6432542.html
        // 注意一个误区：非受检异常是说编译器不强制检查捕获异常的代码，代码里可以捕获也可以不捕获，在声明可能抛出非受检异常的方法时，也可以不使用throws语句。
        // 这就显示注释特别重要了，就像上面的newUpdater方法，在方法注释里详细说明了可能抛出非受检异常。
      } catch (Throwable atomicReferenceFieldUpdaterFailure) {
        // Some Android 5.0.x Samsung devices have bugs in JDK reflection APIs that cause
        // getDeclaredField to throw a NoSuchFieldException when the field is definitely there.
        // For these users fallback to a suboptimal implementation, based on synchronized. This will
        // be a definite performance hit to those users.
        // 一些Android 5.0.x三星设备在JDK反射API中存在错误，导致getDeclaredField在字段肯定存在时抛出NoSuchFieldException。
        // 对于这些用户，基于synchronized，回退到次优实现。 这将对这些用户产生明显的性能影响。
        // 见InterruptibleTask里的注释（那里用AtomicReference来实现了）。
        thrownAtomicReferenceFieldUpdaterFailure = atomicReferenceFieldUpdaterFailure;
        helper = new SynchronizedHelper();
      }
    }
    ATOMIC_HELPER = helper;

    // Prevent rare disastrous classloading in first call to LockSupport.park.
    // See: https://bugs.openjdk.java.net/browse/JDK-8074773
    // LockSupport类的注释里的样例代码里有这行代码的说明。
    @SuppressWarnings("unused")
    Class<?> ensureLoaded = LockSupport.class;

    // Log after all static init is finished; if an installed logger uses any Futures methods, it
    // shouldn't break in cases where reflection is missing/broken.
    // 所有静态init完成后记录; 如果安装的记录器使用任何Futures方法，则在反射丢失/损坏的情况下不应该破坏。
    if (thrownAtomicReferenceFieldUpdaterFailure != null) {
      log.log(Level.SEVERE, "UnsafeAtomicHelper is broken!", thrownUnsafeFailure);
      log.log(
          Level.SEVERE, "SafeAtomicHelper is broken!", thrownAtomicReferenceFieldUpdaterFailure);
    }
  }

  /**
   * 什么是CAS机制？：https://blog.csdn.net/qq_32998153/article/details/79529704
   * Java原子类中CAS的底层实现：http://www.cnblogs.com/noKing/p/9094983.html
   * Treiber Stack介绍：https://www.cnblogs.com/micrari/p/7719408.html
   * FutureTask源码解读：http://www.cnblogs.com/micrari/p/7374513.html
   *
   * 其实我觉得这里的成员变量并不需要原子化更新域而将其设置为volatile类型的，见Listener里就没有设置为volatile。
   * 在get()中，对Waiter链表的操作，其实也是以Waiter为基本单元的，与Waiter里的元素的volatile与否关系不大。
   *
   * 去除这里的volatile变量，去除原子化更新域中的相应操作 TODO. 这样的话，作者注释里的non-volatile write也就可以理解了。
   *
   * 这些东西在《java并发编程实战》第二版15.4.3节中有说明，使用原子化域主要是为了相对于使用AtomicReference细微的性能提升。
   */
  /** Waiter links form a Treiber stack, in the {@link #waiters} field. */
  private static final class Waiter {
    static final Waiter TOMBSTONE = new Waiter(false /* ignored param */); // tombstone有铭牌的意思，相当于一个标记，Waiter与Listener类中都有这个。

    volatile @Nullable Thread thread; // 注意：是volatile类型。
    volatile @Nullable Waiter next; // 注意：是volatile类型。

    /**
     * Constructor for the TOMBSTONE, avoids use of ATOMIC_HELPER in case this class is loaded
     * before the ATOMIC_HELPER. Apparently this is possible on some android platforms.
     *
     * TOMBSTONE的构造函数，在ATOMIC_HELPER之前加载此类时，避免使用ATOMIC_HELPER。 显然在ATOMIC_HELPER之前加载此类在某些Android平台上是可能存在的。
     * 注意：下面有无参构造函数用到ATOMIC_HELPER，所以要避免出现创建TOMBSTONE时，ATOMIC_HELPER还未初始化，所以这里增加了个无用的构造函数。
     */
    Waiter(boolean unused) {}

    Waiter() {
      // avoid volatile write, write is made visible by subsequent CAS on waiters field
      ATOMIC_HELPER.putThread(this, Thread.currentThread());
    }

    // non-volatile write to the next field. Should be made visible by subsequent CAS on waiters
    // field.
    void setNext(Waiter next) {
      ATOMIC_HELPER.putNext(this, next);
    }

    void unpark() { // releaseWaiters方法会调用这里。
      // This is racy with removeWaiter. The consequence of the race is that we may spuriously call
      // unpark even though the thread has already removed itself from the list. But even if we did
      // use a CAS, that race would still exist (it would just be ever so slightly smaller).
      // 那个单词应该是race，应该是作者错打成racy了。
      // 这与removeWaiter之间存在竞争。 竞争的结果可能存在这种情况：即使线程已从列表中删除，我们也可能虚假地调用unpark。
      // 但即使我们确实使用了CAS，这种竞争仍然会存在（它会稍微小一些）。
      Thread w = thread;
      if (w != null) {
        thread = null;
        LockSupport.unpark(w); // get方法里有LockSupport.park操作。
      }
    }
  }

  /**
   * Marks the given node as 'deleted' (null waiter) and then scans the list to unlink all deleted
   * nodes. This is an O(n) operation in the common case (and O(n^2) in the worst), but we are saved
   * by two things.
   *
   * 将给定节点标记为“已删除”（null waiter），然后扫描链表将所有已删除的节点从链表中去除。
   * 这是常见情况下的O（n）操作（最坏情况下为O（n ^ 2）），但我们通过以下两点此方法还是可以接受的：
   *
   * <ul>
   *   <li>This is only called when a waiting thread times out or is interrupted. Both of which
   *       should be rare.
   *       仅在等待线程超时或中断时调用此方法。 两者都应该是罕见的。在get方法里会调用此方法。
   *   <li>The waiters list should be very short.
   * </ul>
   */
  private void removeWaiter(Waiter node) { // 这里的实现是一个正常的删除链表中某元素的操作。
    node.thread = null; // mark as 'deleted'
    restart:
    while (true) {
      Waiter pred = null; // 前驱节点，一开始头节点的前驱节点为null。
      Waiter curr = waiters; // 当前节点
      if (curr == Waiter.TOMBSTONE) {
        return; // give up if someone is calling complete
      }
      Waiter succ; // 后继节点
      while (curr != null) {
        succ = curr.next;
        if (curr.thread != null) { // we aren't unlinking this node, update pred.
          pred = curr;
        } else if (pred != null) { // We are unlinking this node and it has a predecessor.
          pred.next = succ;
          if (pred.thread == null) { // We raced with another node that unlinked pred. Restart. 注意：这些thread、next都是volatile类型的。
            // 为什么只在这里检查一下可能存在的竞争而导致pred.thread为null呢？原因是这样的：因为pred.next=succ之后，就假设pred之前的元素都是有效的、非删除状态了，
            // 所以在这里要把一下关，再检查一下当前的节点是否为有效的。
            // 其实这里再次判断只是为了能在删除无效节点这点上更加有效率而已，而非必须要这样做的，其实这里不检测，也不会有并发问题。
            continue restart;
          }
        } else if (!ATOMIC_HELPER.casWaiters(this, curr, succ)) { // We are unlinking head
          continue restart; // We raced with an add or complete 这个竞争情况考虑的太严谨了
        }
        curr = succ;
      }
      break;
    }
  }

  /** Listeners also form a stack through the {@link #listeners} field. */
  // 这个Listener里的成员变量竟然不是volatile类型的，是不是有问题？
  // 这里的实现与Waiter不同，难道是Waiter里的volatile修饰是多余的？
  // 没有问题，也不是多余的。是这样的：在ATOMIC_HELPER的实现里有Waiter成员变量thread还有next的原子化更新域，所以Waiter里的成员变量要设置为volatile类型的。
  // 而这里没有原子化更新Listener里的变量的域，所以不需要。
  // 在Waiter中，原子化更新域是多作的，见那里的注释，可以改进一下。
  private static final class Listener {
    static final Listener TOMBSTONE = new Listener(null, null); // tombstone有铭牌的意思，相当于一个标记，Waiter与Listener类中都有这个。
    final Runnable task;
    final Executor executor;

    // writes to next are made visible by subsequent CAS's on the listeners field
    @Nullable Listener next;

    Listener(Runnable task, Executor executor) {
      this.task = task;
      this.executor = executor;
    }
  }

  /** A special value to represent {@code null}. */
  private static final Object NULL = new Object();

  /** A special value to represent failure, when {@link #setException} is called successfully. */
  private static final class Failure {
    static final Failure FALLBACK_INSTANCE =
        new Failure(
            new Throwable("Failure occurred while trying to finish a future.") {
              @Override
              public synchronized Throwable fillInStackTrace() {
                return this; // no stack trace
              } // Throwable.fillInStackTrace：https://blog.csdn.net/iceman1952/article/details/8230804
              // java异常分析；剖析printStackTrace和fillInStackTrace：https://blog.csdn.net/yangkai_hudong/article/details/18409007
            });
    final Throwable exception;

    Failure(Throwable exception) {
      this.exception = checkNotNull(exception);
    }
  }

  /** A special value to represent cancellation and the 'wasInterrupted' bit. */
  private static final class Cancellation {
    // constants to use when GENERATE_CANCELLATION_CAUSES = false
    static final Cancellation CAUSELESS_INTERRUPTED;
    static final Cancellation CAUSELESS_CANCELLED;

    static {
      if (GENERATE_CANCELLATION_CAUSES) {
        CAUSELESS_CANCELLED = null;
        CAUSELESS_INTERRUPTED = null;
      } else {
        CAUSELESS_CANCELLED = new Cancellation(false, null);
        CAUSELESS_INTERRUPTED = new Cancellation(true, null);
      }
    }

    final boolean wasInterrupted;
    final @Nullable Throwable cause;

    Cancellation(boolean wasInterrupted, @Nullable Throwable cause) {
      this.wasInterrupted = wasInterrupted;
      this.cause = cause;
    }
  }

  /** A special value that encodes the 'setFuture' state. */
  private static final class SetFuture<V> implements Runnable {
    final AbstractFuture<V> owner;
    final ListenableFuture<? extends V> future;

    SetFuture(AbstractFuture<V> owner, ListenableFuture<? extends V> future) {
      this.owner = owner;
      this.future = future;
    }

    @Override
    public void run() {
      if (owner.value != this) { // 这个value相当于把多个future串起来，而这里不等于，表示没串联关系，不需要处理。
        // nothing to do, we must have been cancelled, don't bother inspecting the future.
        return;
      }
      Object valueToSet = getFutureValue(future);
      if (ATOMIC_HELPER.casValue(owner, this, valueToSet)) { // 注意这个是casValue，如果owner.value等于this，则将其值设置为valueToSet.
        complete(owner);
      }
    }
  }

  // TODO(lukes): investigate using the @Contended annotation on these fields when jdk8 is
  // available.
  // 剖析Disruptor:为什么会这么快？(三)伪共享：http://developer.51cto.com/art/201306/398232.htm
  // 伪共享和缓存行填充，从Java 6, Java 7 到Java 8：http://www.cnblogs.com/Binhua-Liu/p/5620339.html
  // Java8的伪共享和缓存行填充--@Contended注释：https://www.cnblogs.com/Binhua-Liu/p/5623089.html
  /**
   * This field encodes the current state of the future.
   *
   * <p>The valid values are:
   *
   * <ul>
   *   <li>{@code null} initial state, nothing has happened.
   *   <li>{@link Cancellation} terminal state, {@code cancel} was called.
   *   <li>{@link Failure} terminal state, {@code setException} was called.
   *   <li>{@link SetFuture} intermediate state, {@code setFuture} was called.
   *   <li>{@link #NULL} terminal state, {@code set(null)} was called.
   *   <li>Any other non-null value, terminal state, {@code set} was called with a non-null
   *       argument.
   * </ul>
   */
  private volatile @Nullable Object value; // 上面注释中的一些可能的状态，都由这个值来表示。

  /** All listeners. */
  private volatile @Nullable Listener listeners;

  /** All waiting threads. */
  private volatile @Nullable Waiter waiters;

  /** Constructor for use by subclasses. */
  protected AbstractFuture() {}

  // Gets and Timed Gets
  //
  // * Be responsive to interruption
  // * Don't create Waiter nodes if you aren't going to park, this helps reduce contention on the
  //   waiters field.
  // * Future completion is defined by when #value becomes non-null/non SetFuture
  // * Future completion can be observed if the waiters field contains a TOMBSTONE

  // Timed Get
  // There are a few design constraints to consider
  // * We want to be responsive to small timeouts, unpark() has non trivial latency overheads (I
  //   have observed 12 micros on 64 bit linux systems to wake up a parked thread). So if the
  //   timeout is small we shouldn't park(). This needs to be traded off with the cpu overhead of
  //   spinning, so we use SPIN_THRESHOLD_NANOS which is what AbstractQueuedSynchronizer uses for
  //   similar purposes.
  // * We want to behave reasonably for timeouts of 0
  // * We are more responsive to completion than timeouts. This is because parkNanos depends on
  //   system scheduling and as such we could either miss our deadline, or unpark() could be delayed
  //   so that it looks like we timed out even though we didn't. For comparison FutureTask respects
  //   completion preferably and AQS is non-deterministic (depends on where in the queue the waiter
  //   is). If we wanted to be strict about it, we could store the unpark() time in the Waiter node
  //   and we could use that to make a decision about whether or not we timed out prior to being
  //   unparked.
  /**
   * 获取和定时获取
   *
   *    *响应中断
   *    *如果您不打算使用park操作，请不要创建Waiter节点，这有助于减少waiters队列上的争用。
   *    *将在#value变为非null /非SetFuture时定义Future完成。
   *    *当waiters字段包含TOMBSTONE时，可以观察到Future完成情况。（Waiter与Listener类里都有个TOMBSTONE字段）
   *
   *  Timed Get
   *    需要考虑一些设计约束
   *    *我们希望对小超时做出响应，unpark（）具有非平凡的延迟开销（我在64位Linux系统上观察到12个微处理器唤醒停放的线程）。因此，如果超时很小，我们不应该park（）。
   *        这需要与cpu的自旋开销进行折衷，因此我们使用SPIN_THRESHOLD_NANOS，这是AbstractQueuedSynchronizer用于类似目的的。
   *    *我们想要在0的超时时间内合理地行事
   *    *我们对完成的响应速度比超时更快。这是因为parkNanos依赖于系统调度，因此我们可能会错过我们的截止日期，或者unpark（）可能会被延迟，因此即使我们没有超时，
   *        也会因为unpark()被延迟而超时。为了比较，FutureTask表示任务完成，而AQS是非确定性的（取决于waiter在队列中的位置）。如果我们想严格要求它，
   *        我们可以将unpark（）时间存储在Waiter节点中，我们可以使用它来决定我们是否在取消停放之前超时。
   */

  /**
   * {@inheritDoc}
   *
   * <p>The default {@link AbstractFuture} implementation throws {@code InterruptedException} if the
   * current thread is interrupted during the call, even if the value is already available.
   * AbstractFuture的默认实现中如果当前线程被中断了则抛出InterruptedException，即使当前已经得到value了，也会抛出InterruptedException。
   *
   * @throws CancellationException {@inheritDoc}
   */
  @CanIgnoreReturnValue
  @Override
  public V get(long timeout, TimeUnit unit)
      throws InterruptedException, TimeoutException, ExecutionException {
    // NOTE: if timeout < 0, remainingNanos will be < 0 and we will fall into the while(true) loop
    // at the bottom and throw a timeoutexception.
      // 注意：如果timeout <0，remainingNanos将<0，我们将进入底部的while（true）循环并抛出timeoutexception。
      // 如果remainingNanos<0，则这里的实现中，会直接到最下面的if(isDone())那里，将会抛出超时异常。这里的while(true)应该是在形容这样的效果：总是会抛出超时异常，而非实现中有while(true)。
      // 感觉作者的注释不是太详细、恰当。
    long remainingNanos = unit.toNanos(timeout); // we rely on the implicit null check on unit.
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    Object localValue = value;
    if (localValue != null & !(localValue instanceof SetFuture)) {
      return getDoneValue(localValue);
    }
    // we delay calling nanoTime until we know we will need to either park or spin
      // 我们推迟调用nanoTime，直到我们知道我们需要停放或旋转
    final long endNanos = remainingNanos > 0 ? System.nanoTime() + remainingNanos : 0;
    long_wait_loop:
    if (remainingNanos >= SPIN_THRESHOLD_NANOS) {
      Waiter oldHead = waiters;
      if (oldHead != Waiter.TOMBSTONE) {
        Waiter node = new Waiter(); // 这里想要使用park操作，所以创建了一个Waiter，见get方法上的注释：
          // * Don't create Waiter nodes if you aren't going to park, this helps reduce contention on the
          //   waiters field.
          // 注意：Waiter()里的实现并不是空的（不要有主观上的想法），其实现是把当前线程放入node.thread。
          // 注意：执行get的线程是当前线程，而执行Waiter.unpark里的线程是执行AbstractFuture的线程。
          // 所以效果就是AbstractFuture的线程去执行任务，然后其他线程来取数据时可能会被阻塞住以等待任务执行完成或者超时返回。
        do {
          node.setNext(oldHead);
          if (ATOMIC_HELPER.casWaiters(this, oldHead, node)) {
            while (true) {
              LockSupport.parkNanos(this, remainingNanos); // 这个是阻塞在this这个对象上，parkNanos内部会有当前线程，见其实现。
              // Check interruption first, if we woke up due to interruption we need to honor that.
              if (Thread.interrupted()) {
                removeWaiter(node); // park操作已结束，所以移除这个node。
                throw new InterruptedException();
              }
              // parkNanos的注释里说了很多种可能的唤醒原因。

              // Otherwise re-read and check doneness. If we loop then it must have been a spurious
              // wakeup
              localValue = value;
              if (localValue != null & !(localValue instanceof SetFuture)) {
                return getDoneValue(localValue);
              }

              // timed out?
              remainingNanos = endNanos - System.nanoTime();
              if (remainingNanos < SPIN_THRESHOLD_NANOS) {
                // Remove the waiter, one way or another we are done parking this thread.
                removeWaiter(node);
                break long_wait_loop; // jump down to the busy wait loop
              }
              // 继续阻塞等待
            }
          }
          oldHead = waiters; // re-read and loop.
        } while (oldHead != Waiter.TOMBSTONE); // 不断地尝试cas。
      }
      // re-read value, if we get here then we must have observed a TOMBSTONE while trying to add a
      // waiter.
        // 见get方法上面的注释：
        // * Future completion can be observed if the waiters field contains a TOMBSTONE
      return getDoneValue(value);
    }
    // If we get here then we have remainingNanos < SPIN_THRESHOLD_NANOS and there is no node on the
    // waiters list
      // 到这里时，remainingNanos < SPIN_THRESHOLD_NANOS，并用waiters链表里没有node.
      // 对于这个没有node，可以这样理解，增加一个node就是为了调用park方法，如果有多个node，则后一个node会park，然后等待它前面的node的unpark，最后再upnark自己。
      // 所以，到这里只，waiters链表里是没有node的，要不然，还是在park的状态，即阻塞的状态。（注意，上面的循环中有removeWaiter的操作，所以到这里时是没有node的。）
    while (remainingNanos > 0) {
      localValue = value;
      if (localValue != null & !(localValue instanceof SetFuture)) {
        return getDoneValue(localValue);
      }
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      remainingNanos = endNanos - System.nanoTime(); // 继续自旋等待
    }

    String futureToString = toString();
    // It's confusing to see a completed future in a timeout message; if isDone() returns false,
    // then we know it must have given a pending toString value earlier. If not, then the future
    // completed after the timeout expired, and the message might be success.
    // 在超时消息中看到Future的完成会令人产生困惑; 如果isDone()返回false，那么我们知道它必须先提供一个在等待期间的toString值。
    // 如果isDone()返回true，那么Future在超时到期时完成，并且返回的消息提示是成功的。
    if (isDone()) { // 这个其实就是想在超时再检查一次看完成没有。
      throw new TimeoutException(
          "Waited "
              + timeout
              + " "
              + unit.toString().toLowerCase(Locale.ROOT)
              + " but future completed as timeout expired");
    }
    throw new TimeoutException( // 超时了，没有完成
        "Waited "
            + timeout
            + " "
            + unit.toString().toLowerCase(Locale.ROOT)
            + " for "
            + futureToString);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The default {@link AbstractFuture} implementation throws {@code InterruptedException} if the
   * current thread is interrupted during the call, even if the value is already available.
   * AbstractFuture的默认实现中如果当前线程被中断了则抛出InterruptedException，即使当前已经得到value了，也会抛出InterruptedException。
   *
   * @throws CancellationException {@inheritDoc}
   */
  @CanIgnoreReturnValue
  @Override
  public V get() throws InterruptedException, ExecutionException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    Object localValue = value;
    if (localValue != null & !(localValue instanceof SetFuture)) {
      return getDoneValue(localValue);
    }
    Waiter oldHead = waiters;
    if (oldHead != Waiter.TOMBSTONE) {
      Waiter node = new Waiter();
      do {
        node.setNext(oldHead);
        if (ATOMIC_HELPER.casWaiters(this, oldHead, node)) {
          // we are on the stack, now wait for completion.
          while (true) {
            LockSupport.park(this); // releaseWaiters里会最终调用LockSupport.unpark
            // Check interruption first, if we woke up due to interruption we need to honor that.
            if (Thread.interrupted()) {
              removeWaiter(node);
              throw new InterruptedException();
            }
            // Otherwise re-read and check doneness. If we loop then it must have been a spurious
            // wakeup
            localValue = value;
            if (localValue != null & !(localValue instanceof SetFuture)) {
              return getDoneValue(localValue);
            }
          }
        }
        oldHead = waiters; // re-read and loop.
      } while (oldHead != Waiter.TOMBSTONE);
    }
    // re-read value, if we get here then we must have observed a TOMBSTONE while trying to add a
    // waiter.
    return getDoneValue(value);
  }

  /** Unboxes {@code obj}. Assumes that obj is not {@code null} or a {@link SetFuture}. */
  private V getDoneValue(Object obj) throws ExecutionException {
    // While this seems like it might be too branch-y, simple benchmarking proves it to be
    // unmeasurable (comparing done AbstractFutures with immediateFuture)
    if (obj instanceof Cancellation) {
      throw cancellationExceptionWithCause("Task was cancelled.", ((Cancellation) obj).cause);
    } else if (obj instanceof Failure) {
      throw new ExecutionException(((Failure) obj).exception);
    } else if (obj == NULL) {
      return null;
    } else {
      @SuppressWarnings("unchecked") // this is the only other option
      V asV = (V) obj;
      return asV;
    }
  }

  @Override
  public boolean isDone() {
    final Object localValue = value;
    return localValue != null & !(localValue instanceof SetFuture);
  }

  @Override
  public boolean isCancelled() {
    final Object localValue = value;
    return localValue instanceof Cancellation;
  }

  /**
   * {@inheritDoc}
   *
   * <p>If a cancellation attempt succeeds on a {@code Future} that had previously been {@linkplain
   * #setFuture set asynchronously}, then the cancellation will also be propagated to the delegate
   * {@code Future} that was supplied in the {@code setFuture} call.
   *
   * <p>Rather than override this method to perform additional cancellation work or cleanup,
   * subclasses should override {@link #afterDone}, consulting {@link #isCancelled} and {@link
   * #wasInterrupted} as necessary. This ensures that the work is done even if the future is
   * cancelled without a call to {@code cancel}, such as by calling {@code
   * setFuture(cancelledFuture)}.
   */
  @CanIgnoreReturnValue
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    Object localValue = value;
    boolean rValue = false;
    if (localValue == null | localValue instanceof SetFuture) {
      // Try to delay allocating the exception. At this point we may still lose the CAS, but it is
      // certainly less likely.
      Object valueToSet =
          GENERATE_CANCELLATION_CAUSES
              ? new Cancellation(
                  mayInterruptIfRunning, new CancellationException("Future.cancel() was called."))
              : (mayInterruptIfRunning
                  ? Cancellation.CAUSELESS_INTERRUPTED
                  : Cancellation.CAUSELESS_CANCELLED);
      AbstractFuture<?> abstractFuture = this;
      while (true) {
        if (ATOMIC_HELPER.casValue(abstractFuture, localValue, valueToSet)) {
          rValue = true;
          // We call interuptTask before calling complete(), which is consistent with
          // FutureTask
          if (mayInterruptIfRunning) {
            abstractFuture.interruptTask();
          }
          complete(abstractFuture);
          if (localValue instanceof SetFuture) {
            // propagate cancellation to the future set in setfuture, this is racy, and we don't
            // care if we are successful or not.
            ListenableFuture<?> futureToPropagateTo = ((SetFuture) localValue).future;
            if (futureToPropagateTo instanceof TrustedFuture) {
              // If the future is a TrustedFuture then we specifically avoid calling cancel()
              // this has 2 benefits
              // 1. for long chains of futures strung together with setFuture we consume less stack
              // 2. we avoid allocating Cancellation objects at every level of the cancellation
              //    chain
              // We can only do this for TrustedFuture, because TrustedFuture.cancel is final and
              // does nothing but delegate to this method.
                /**
                 * 如果Future是TrustedFuture，那么我们特别避免调用cancel（）
                 *
                 *   这有两个好处
                 *   1.对于与setFuture一起串起的长期期货链，我们消耗的堆栈更少
                 *   2.我们避免在取消链的每个级别分配取消对象。
                 *
                 * 我们只能为TrustedFuture执行此操作，因为TrustedFuture.cancel是最终的，除了委托此方法之外什么都不做。
                 */
              AbstractFuture<?> trusted = (AbstractFuture<?>) futureToPropagateTo;
              localValue = trusted.value;
              if (localValue == null | localValue instanceof SetFuture) {
                abstractFuture = trusted;
                continue; // loop back up and try to complete the new future
              }
            } else {
              // not a TrustedFuture, call cancel directly.
              futureToPropagateTo.cancel(mayInterruptIfRunning);
            }
          }
          break;
        }
        // obj changed, reread
        localValue = abstractFuture.value;
        if (!(localValue instanceof SetFuture)) {
          // obj cannot be null at this point, because value can only change from null to non-null.
          // So if value changed (and it did since we lost the CAS), then it cannot be null and
          // since it isn't a SetFuture, then the future must be done and we should exit the loop
          break;
        }
      }
    }
    return rValue;
  }

  /**
   * Subclasses can override this method to implement interruption of the future's computation. The
   * method is invoked automatically by a successful call to {@link #cancel(boolean) cancel(true)}.
   *
   * <p>The default implementation does nothing.
   *
   * <p>This method is likely to be deprecated. Prefer to override {@link #afterDone}, checking
   * {@link #wasInterrupted} to decide whether to interrupt your task.
   *
   * @since 10.0
   */
  protected void interruptTask() {}

  /**
   * Returns true if this future was cancelled with {@code mayInterruptIfRunning} set to {@code
   * true}.
   *
   * @since 14.0
   */
  protected final boolean wasInterrupted() {
    final Object localValue = value;
    return (localValue instanceof Cancellation) && ((Cancellation) localValue).wasInterrupted;
  }

  /**
   * {@inheritDoc}
   *
   * @since 10.0
   */
  @Override
  public void addListener(Runnable listener, Executor executor) {
    checkNotNull(listener, "Runnable was null.");
    checkNotNull(executor, "Executor was null.");
    Listener oldHead = listeners;
    if (oldHead != Listener.TOMBSTONE) {
      Listener newNode = new Listener(listener, executor);
      do {
        newNode.next = oldHead;
        if (ATOMIC_HELPER.casListeners(this, oldHead, newNode)) {
          return;
        }
        oldHead = listeners; // re-read
      } while (oldHead != Listener.TOMBSTONE);
    }
    // If we get here then the Listener TOMBSTONE was set, which means the future is done, call
    // the listener.
    executeListener(listener, executor);
  }

  /**
   * Sets the result of this {@code Future} unless this {@code Future} has already been cancelled or
   * set (including {@linkplain #setFuture set asynchronously}). When a call to this method returns,
   * the {@code Future} is guaranteed to be {@linkplain #isDone done} <b>only if</b> the call was
   * accepted (in which case it returns {@code true}). If it returns {@code false}, the {@code
   * Future} may have previously been set asynchronously, in which case its result may not be known
   * yet. That result, though not yet known, cannot be overridden by a call to a {@code set*}
   * method, only by a call to {@link #cancel}.
   *
   * 设置此{@code Future}的结果，除非此{@code Future}已被取消或设置（包括{@linkplain #setFuture 异步设置}）。
   * 当对此方法的调用返回时，仅当调用被接受时（在这种情况下它返回{@code true}），{@code Future}保证为{@linkplain #isDone done} 。
   * 如果它返回{@code false}，则{@code Future}可能先前已异步设置，在这种情况下，其结果可能尚未知晓。
   * 这个结果虽然还不知道，但只能通过调用{@link #cancel}来调用{@code set *}方法。
   *
   * 在一个任务执行完后，就会调用这个set方法去设置future的结果。
   *
   * @param value the value to be used as the result
   * @return true if the attempt was accepted, completing the {@code Future}
   */
  @CanIgnoreReturnValue
  protected boolean set(@Nullable V value) {
    Object valueToSet = value == null ? NULL : value;
    if (ATOMIC_HELPER.casValue(this, null, valueToSet)) {
      complete(this);
      return true;
    }
    return false;
  }

  /**
   * Sets the failed result of this {@code Future} unless this {@code Future} has already been
   * cancelled or set (including {@linkplain #setFuture set asynchronously}). When a call to this
   * method returns, the {@code Future} is guaranteed to be {@linkplain #isDone done} <b>only if</b>
   * the call was accepted (in which case it returns {@code true}). If it returns {@code false}, the
   * {@code Future} may have previously been set asynchronously, in which case its result may not be
   * known yet. That result, though not yet known, cannot be overridden by a call to a {@code set*}
   * method, only by a call to {@link #cancel}.
   *
   * @param throwable the exception to be used as the failed result
   * @return true if the attempt was accepted, completing the {@code Future}
   */
  @CanIgnoreReturnValue
  protected boolean setException(Throwable throwable) {
    Object valueToSet = new Failure(checkNotNull(throwable));
    if (ATOMIC_HELPER.casValue(this, null, valueToSet)) {
      complete(this);
      return true;
    }
    return false;
  }

  /**
   * Sets the result of this {@code Future} to match the supplied input {@code Future} once the
   * supplied {@code Future} is done, unless this {@code Future} has already been cancelled or set
   * (including "set asynchronously," defined below).
   *
   * 一旦提供的{@code Future}完成，设置此{@code Future}的结果以匹配提供的输入{@code Future}，
   * 除非此{@code Future}已被取消或设置（包括“异步设置， “定义如下）。
   *
   * <p>If the supplied future is {@linkplain #isDone done} when this method is called and the call
   * is accepted, then this future is guaranteed to have been completed with the supplied future by
   * the time this method returns. If the supplied future is not done and the call is accepted, then
   * the future will be <i>set asynchronously</i>. Note that such a result, though not yet known,
   * cannot be overridden by a call to a {@code set*} method, only by a call to {@link #cancel}.
   *
   * <p>如果在调用此方法并且接受调用时提供的future是{@linkplain #isDone done}，那么在此方法返回时，
   * 将保证使用提供的future完成此future。 如果提供的future没有完成且此调用已被授受，则future将被<i>异步设置</ i>。
   * 请注意，结果目前还不确定，不能通过调用{@code set *}方法来覆盖这样的结果，只能通过调用{@link #cancel}来设置。
   *
   * <p>If the call {@code setFuture(delegate)} is accepted and this {@code Future} is later
   * cancelled, cancellation will be propagated to {@code delegate}. Additionally, any call to
   * {@code setFuture} after any cancellation will propagate cancellation to the supplied {@code
   * Future}.
   *
   * <p>如果{@code setFuture（delegate）}调用被接受了，并且稍后取消此{@code Future}，则取消将传播到{@code delegate}。
   * 此外，任何取消操作后的任何{@code setFuture}调用都会将取消传播到所提供的{@code Future}。
   *
   * <p>Note that, even if the supplied future is cancelled and it causes this future to complete,
   * it will never trigger interruption behavior. In particular, it will not cause this future to
   * invoke the {@link #interruptTask} method, and the {@link #wasInterrupted} method will not
   * return {@code true}.
   *
   * <p>请注意，即使提供的future被取消并且导致此未来完成，也不会触发中断行为。
   * 特别是，它不会导致此future调用{@link #interruptTask}方法，并且{@link #wasInterrupted}方法将不会返回{@code true}。
   *
   * @param future the future to delegate to （就是让this这个future去代理参数future，即从参数future中取得this这个future的最终执行结果。
   * @return true if the attempt was accepted, indicating that the {@code Future} was not previously
   *     cancelled or set.
   * @since 19.0
   */
  @Beta
  @CanIgnoreReturnValue
  protected boolean setFuture(ListenableFuture<? extends V> future) {
    checkNotNull(future);
    Object localValue = value;
    if (localValue == null) {
      if (future.isDone()) {
        Object value = getFutureValue(future);
        if (ATOMIC_HELPER.casValue(this, null, value)) {
          complete(this);
          return true;
        }
        return false;
      }
      SetFuture valueToSet = new SetFuture<V>(this, future);
      if (ATOMIC_HELPER.casValue(this, null, valueToSet)) { // isDone以及get方法里会判断value是否为SetFuture类型的。
        // the listener is responsible for calling completeWithFuture, directExecutor is appropriate
        // since all we are doing is unpacking a completed future which should be fast.
        try {
          future.addListener(valueToSet, directExecutor());
        } catch (Throwable t) {
          // addListener has thrown an exception! SetFuture.run can't throw any exceptions so this
          // must have been caused by addListener itself. The most likely explanation is a
          // misconfigured mock. Try to switch to Failure.
          Failure failure;
          try {
            failure = new Failure(t);
          } catch (Throwable oomMostLikely) {
            failure = Failure.FALLBACK_INSTANCE;
          }
          // Note: The only way this CAS could fail is if cancel() has raced with us. That is ok.
          boolean unused = ATOMIC_HELPER.casValue(this, valueToSet, failure);
        }
        return true;
      }
      localValue = value; // we lost the cas, fall through and maybe cancel
    }
    // The future has already been set to something. If it is cancellation we should cancel the
    // incoming future.
    if (localValue instanceof Cancellation) {
      // we don't care if it fails, this is best-effort.
      future.cancel(((Cancellation) localValue).wasInterrupted);
    }
    return false;
  }

  /**
   * Returns a value that satisfies the contract of the {@link #value} field based on the state of
   * given future.
   *
   * <p>This is approximately the inverse of {@link #getDoneValue(Object)}
   */
  private static Object getFutureValue(ListenableFuture<?> future) {
    Object valueToSet;
    if (future instanceof TrustedFuture) {
      // Break encapsulation for TrustedFuture instances since we know that subclasses cannot
      // override .get() (since it is final) and therefore this is equivalent to calling .get()
      // and unpacking the exceptions like we do below (just much faster because it is a single
      // field read instead of a read, several branches and possibly creating exceptions).
      Object v = ((AbstractFuture<?>) future).value;
      if (v instanceof Cancellation) {
        // If the other future was interrupted, clear the interrupted bit while preserving the cause
        // this will make it consistent with how non-trustedfutures work which cannot propagate the
        // wasInterrupted bit
        Cancellation c = (Cancellation) v;
        if (c.wasInterrupted) {
          v =
              c.cause != null
                  ? new Cancellation(/* wasInterrupted= */ false, c.cause)
                  : Cancellation.CAUSELESS_CANCELLED;
        }
      }
      return v;
    } else {
      // Otherwise calculate valueToSet by calling .get()
      try {
        Object v = getDone(future);
        valueToSet = v == null ? NULL : v;
      } catch (ExecutionException exception) {
        valueToSet = new Failure(exception.getCause());
      } catch (CancellationException cancellation) {
        valueToSet = new Cancellation(false, cancellation);
      } catch (Throwable t) {
        valueToSet = new Failure(t);
      }
    }
    return valueToSet;
  }

  /** Unblocks all threads and runs all listeners. */
  private static void complete(AbstractFuture<?> future) {
    Listener next = null;
    outer:
    while (true) {
      future.releaseWaiters();
      // We call this before the listeners in order to avoid needing to manage a separate stack data
      // structure for them.
      // afterDone() should be generally fast and only used for cleanup work... but in theory can
      // also be recursive and create StackOverflowErrors
      future.afterDone();
      // push the current set of listeners onto next
      next = future.clearListeners(next);
      future = null;
      while (next != null) {
        Listener curr = next;
        next = next.next;
        Runnable task = curr.task;
        if (task instanceof SetFuture) {
          SetFuture<?> setFuture = (SetFuture<?>) task;
          // We unwind setFuture specifically to avoid StackOverflowErrors in the case of long
          // chains of SetFutures
          // Handling this special case is important because there is no way to pass an executor to
          // setFuture, so a user couldn't break the chain by doing this themselves.  It is also
          // potentially common if someone writes a recursive Futures.transformAsync transformer.
          future = setFuture.owner;
          if (future.value == setFuture) {
            Object valueToSet = getFutureValue(setFuture.future);
            if (ATOMIC_HELPER.casValue(future, setFuture, valueToSet)) {
              continue outer;
            }
          }
          // other wise the future we were trying to set is already done.
        } else {
          executeListener(task, curr.executor);
        }
      }
      break;
    }
  }

  /**
   * Callback method that is called exactly once after the future is completed.
   *
   * <p>If {@link #interruptTask} is also run during completion, {@link #afterDone} runs after it.
   *
   * <p>The default implementation of this method in {@code AbstractFuture} does nothing. This is
   * intended for very lightweight cleanup work, for example, timing statistics or clearing fields.
   * If your task does anything heavier consider, just using a listener with an executor.
   *
   * @since 20.0
   */
  @Beta
  @ForOverride
  protected void afterDone() {}

  /**
   * Returns the exception that this {@code Future} completed with. This includes completion through
   * a call to {@link #setException} or {@link #setFuture setFuture}{@code (failedFuture)} but not
   * cancellation.
   *
   * @throws RuntimeException if the {@code Future} has not failed
   */
  final Throwable trustedGetException() {
    return ((Failure) value).exception;
  }

  /**
   * If this future has been cancelled (and possibly interrupted), cancels (and possibly interrupts)
   * the given future (if available).
   */
  final void maybePropagateCancellationTo(@Nullable Future<?> related) {
    if (related != null & isCancelled()) {
      related.cancel(wasInterrupted());
    }
  }

  /** Releases all threads in the {@link #waiters} list, and clears the list. */
  private void releaseWaiters() {
    Waiter head;
    do {
      head = waiters;
    } while (!ATOMIC_HELPER.casWaiters(this, head, Waiter.TOMBSTONE));
    for (Waiter currentWaiter = head; currentWaiter != null; currentWaiter = currentWaiter.next) {
      currentWaiter.unpark(); // get方法里有LockSupport.park操作。
    }
  }

  /**
   * Clears the {@link #listeners} list and prepends its contents to {@code onto}, least recently
   * added first.
   */
  private Listener clearListeners(Listener onto) {
    // We need to
    // 1. atomically swap the listeners with TOMBSTONE, this is because addListener uses that to
    //    to synchronize with us
    // 2. reverse the linked list, because despite our rather clear contract, people depend on us
    //    executing listeners in the order they were added
    // 3. push all the items onto 'onto' and return the new head of the stack
    Listener head;
    do {
      head = listeners;
    } while (!ATOMIC_HELPER.casListeners(this, head, Listener.TOMBSTONE));
    Listener reversedList = onto;
    while (head != null) {
      Listener tmp = head;
      head = head.next;
      tmp.next = reversedList;
      reversedList = tmp;
    }
    return reversedList;
  }

  // TODO(user) move this up into FluentFuture, or parts as a default method on ListenableFuture?
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder().append(super.toString()).append("[status=");
    if (isCancelled()) {
      builder.append("CANCELLED");
    } else if (isDone()) {
      addDoneString(builder);
    } else {
      String pendingDescription;
      try {
        pendingDescription = pendingToString();
      } catch (RuntimeException e) {
        // Don't call getMessage or toString() on the exception, in case the exception thrown by the
        // subclass is implemented with bugs similar to the subclass.
        pendingDescription = "Exception thrown from implementation: " + e.getClass();
      }
      // The future may complete during or before the call to getPendingToString, so we use null
      // as a signal that we should try checking if the future is done again.
      if (!isNullOrEmpty(pendingDescription)) {
        builder.append("PENDING, info=[").append(pendingDescription).append("]");
      } else if (isDone()) {
        addDoneString(builder);
      } else {
        builder.append("PENDING");
      }
    }
    return builder.append("]").toString();
  }

  /**
   * Provide a human-readable explanation of why this future has not yet completed.
   *
   * @return null if an explanation cannot be provided because the future is done.
   * @since 23.0
   */
  protected @Nullable String pendingToString() {
    Object localValue = value;
    if (localValue instanceof SetFuture) {
      return "setFuture=[" + userObjectToString(((SetFuture) localValue).future) + "]";
    } else if (this instanceof ScheduledFuture) {
      return "remaining delay=["
          + ((ScheduledFuture) this).getDelay(TimeUnit.MILLISECONDS)
          + " ms]";
    }
    return null;
  }

  private void addDoneString(StringBuilder builder) {
    try {
      V value = getDone(this);
      builder.append("SUCCESS, result=[").append(userObjectToString(value)).append("]");
    } catch (ExecutionException e) {
      builder.append("FAILURE, cause=[").append(e.getCause()).append("]");
    } catch (CancellationException e) {
      builder.append("CANCELLED"); // shouldn't be reachable
    } catch (RuntimeException e) {
      builder.append("UNKNOWN, cause=[").append(e.getClass()).append(" thrown from get()]");
    }
  }

  /** Helper for printing user supplied objects into our toString method. */
  private String userObjectToString(Object o) {
    // This is some basic recursion detection for when people create cycles via set/setFuture
    // This is however only partial protection though since it only detects self loops.  We could
    // detect arbitrary cycles using a thread local or possibly by catching StackOverflowExceptions
    // but this should be a good enough solution (it is also what jdk collections do in these cases)
    if (o == this) {
      return "this future";
    }
    return String.valueOf(o);
  }

  /**
   * Submits the given runnable to the given {@link Executor} catching and logging all {@linkplain
   * RuntimeException runtime exceptions} thrown by the executor.
   */
  private static void executeListener(Runnable runnable, Executor executor) {
    try {
      executor.execute(runnable);
    } catch (RuntimeException e) {
      // Log it and keep going -- bad runnable and/or executor. Don't punish the other runnables if
      // we're given a bad one. We only catch RuntimeException because we want Errors to propagate
      // up.
      log.log(
          Level.SEVERE,
          "RuntimeException while executing runnable " + runnable + " with executor " + executor,
          e);
    }
  }

  private abstract static class AtomicHelper {
    /** Non volatile write of the thread to the {@link Waiter#thread} field. */
    abstract void putThread(Waiter waiter, Thread newValue);

    /** Non volatile write of the waiter to the {@link Waiter#next} field. */
    abstract void putNext(Waiter waiter, Waiter newValue);

    /** Performs a CAS operation on the {@link #waiters} field. */
    abstract boolean casWaiters(AbstractFuture<?> future, Waiter expect, Waiter update);

    /** Performs a CAS operation on the {@link #listeners} field. */
    abstract boolean casListeners(AbstractFuture<?> future, Listener expect, Listener update);

    /** Performs a CAS operation on the {@link #value} field. */
    abstract boolean casValue(AbstractFuture<?> future, Object expect, Object update);
  }

  /**
   * {@link AtomicHelper} based on {@link sun.misc.Unsafe}.
   *
   * <p>Static initialization of this class will fail if the {@link sun.misc.Unsafe} object cannot
   * be accessed.
   *
   * <p>如果无法访问{@link sun.misc.Unsafe}对象，则此类的静态初始化将失败。
   */
  private static final class UnsafeAtomicHelper extends AtomicHelper {
    static final sun.misc.Unsafe UNSAFE;
    static final long LISTENERS_OFFSET;
    static final long WAITERS_OFFSET;
    static final long VALUE_OFFSET;
    static final long WAITER_THREAD_OFFSET;
    static final long WAITER_NEXT_OFFSET;

    static {
      sun.misc.Unsafe unsafe = null;
      try {
        unsafe = sun.misc.Unsafe.getUnsafe();
      } catch (SecurityException tryReflectionInstead) {
        try {
          unsafe =
              AccessController.doPrivileged(
                  new PrivilegedExceptionAction<sun.misc.Unsafe>() { // AccessController.doPrivileged解释：https://blog.csdn.net/u011703657/article/details/51848700
                    @Override
                    public sun.misc.Unsafe run() throws Exception {
                      Class<sun.misc.Unsafe> k = sun.misc.Unsafe.class;
                      for (java.lang.reflect.Field f : k.getDeclaredFields()) { // sun.misc.Unsafe内部有一个sun.misc.Unsafe类型的单例对象，
                        // 在这里是通过遍历成员变量找到此单例对象的，而且需要做转型后返回。
                        // sun.misc.unsafe类的使用：https://blog.csdn.net/fenglibing/article/details/17138079
                        // 其实简单地参考上面的链接中的反射直接获取并生成此单例。
                        f.setAccessible(true);
                        Object x = f.get(null);
                        if (k.isInstance(x)) { // Java中instanceof和isInstance区别详解：https://www.cnblogs.com/greatfish/p/6096038.html
                          return k.cast(x);
                        }
                      }
                      throw new NoSuchFieldError("the Unsafe");
                    }
                  });
        } catch (PrivilegedActionException e) {
          throw new RuntimeException("Could not initialize intrinsics", e.getCause());
        }
      }
      try {
        Class<?> abstractFuture = AbstractFuture.class;
        WAITERS_OFFSET = unsafe.objectFieldOffset(abstractFuture.getDeclaredField("waiters"));
        LISTENERS_OFFSET = unsafe.objectFieldOffset(abstractFuture.getDeclaredField("listeners"));
        VALUE_OFFSET = unsafe.objectFieldOffset(abstractFuture.getDeclaredField("value"));
        WAITER_THREAD_OFFSET = unsafe.objectFieldOffset(Waiter.class.getDeclaredField("thread"));
        WAITER_NEXT_OFFSET = unsafe.objectFieldOffset(Waiter.class.getDeclaredField("next"));
        UNSAFE = unsafe;
      } catch (Exception e) {
        throwIfUnchecked(e);
        throw new RuntimeException(e);
      }
    }

    @Override
    void putThread(Waiter waiter, Thread newValue) {
      UNSAFE.putObject(waiter, WAITER_THREAD_OFFSET, newValue);
    }

    @Override
    void putNext(Waiter waiter, Waiter newValue) {
      UNSAFE.putObject(waiter, WAITER_NEXT_OFFSET, newValue);
    }

    /** Performs a CAS operation on the {@link #waiters} field. */
    @Override
    boolean casWaiters(AbstractFuture<?> future, Waiter expect, Waiter update) {
      return UNSAFE.compareAndSwapObject(future, WAITERS_OFFSET, expect, update);
    }

    /** Performs a CAS operation on the {@link #listeners} field. */
    @Override
    boolean casListeners(AbstractFuture<?> future, Listener expect, Listener update) {
      return UNSAFE.compareAndSwapObject(future, LISTENERS_OFFSET, expect, update);
    }

    /** Performs a CAS operation on the {@link #value} field. */
    @Override
    boolean casValue(AbstractFuture<?> future, Object expect, Object update) {
      return UNSAFE.compareAndSwapObject(future, VALUE_OFFSET, expect, update);
    }
  }

  /** {@link AtomicHelper} based on {@link AtomicReferenceFieldUpdater}. */
  private static final class SafeAtomicHelper extends AtomicHelper {
    final AtomicReferenceFieldUpdater<Waiter, Thread> waiterThreadUpdater;
    final AtomicReferenceFieldUpdater<Waiter, Waiter> waiterNextUpdater;
    final AtomicReferenceFieldUpdater<AbstractFuture, Waiter> waitersUpdater;
    final AtomicReferenceFieldUpdater<AbstractFuture, Listener> listenersUpdater;
    final AtomicReferenceFieldUpdater<AbstractFuture, Object> valueUpdater;

    SafeAtomicHelper(
        AtomicReferenceFieldUpdater<Waiter, Thread> waiterThreadUpdater,
        AtomicReferenceFieldUpdater<Waiter, Waiter> waiterNextUpdater,
        AtomicReferenceFieldUpdater<AbstractFuture, Waiter> waitersUpdater,
        AtomicReferenceFieldUpdater<AbstractFuture, Listener> listenersUpdater,
        AtomicReferenceFieldUpdater<AbstractFuture, Object> valueUpdater) {
      this.waiterThreadUpdater = waiterThreadUpdater;
      this.waiterNextUpdater = waiterNextUpdater;
      this.waitersUpdater = waitersUpdater;
      this.listenersUpdater = listenersUpdater;
      this.valueUpdater = valueUpdater;
    }

    @Override
    void putThread(Waiter waiter, Thread newValue) {
      waiterThreadUpdater.lazySet(waiter, newValue);
    }

    @Override
    void putNext(Waiter waiter, Waiter newValue) {
      waiterNextUpdater.lazySet(waiter, newValue);
    }

    @Override
    boolean casWaiters(AbstractFuture<?> future, Waiter expect, Waiter update) {
      return waitersUpdater.compareAndSet(future, expect, update);
    }

    @Override
    boolean casListeners(AbstractFuture<?> future, Listener expect, Listener update) {
      return listenersUpdater.compareAndSet(future, expect, update);
    }

    @Override
    boolean casValue(AbstractFuture<?> future, Object expect, Object update) {
      return valueUpdater.compareAndSet(future, expect, update);
    }
  }

  /**
   * {@link AtomicHelper} based on {@code synchronized} and volatile writes.
   *
   * <p>This is an implementation of last resort for when certain basic VM features are broken (like
   * AtomicReferenceFieldUpdater).
   */
  private static final class SynchronizedHelper extends AtomicHelper {
    @Override
    void putThread(Waiter waiter, Thread newValue) {
      waiter.thread = newValue;
    }

    @Override
    void putNext(Waiter waiter, Waiter newValue) {
      waiter.next = newValue;
    }

    @Override
    boolean casWaiters(AbstractFuture<?> future, Waiter expect, Waiter update) {
      synchronized (future) {
        if (future.waiters == expect) {
          future.waiters = update;
          return true;
        }
        return false;
      }
    }

    @Override
    boolean casListeners(AbstractFuture<?> future, Listener expect, Listener update) {
      synchronized (future) {
        if (future.listeners == expect) {
          future.listeners = update;
          return true;
        }
        return false;
      }
    }

    @Override
    boolean casValue(AbstractFuture<?> future, Object expect, Object update) {
      synchronized (future) {
        if (future.value == expect) {
          future.value = update;
          return true;
        }
        return false;
      }
    }
  }

  private static CancellationException cancellationExceptionWithCause(
      @Nullable String message, @Nullable Throwable cause) {
    CancellationException exception = new CancellationException(message);
    exception.initCause(cause);
    return exception;
  }
}
