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
/**
 * 注意：读这个类的代码时，先不要管每个方法都是由什么线程去执行的，这个类本身就是一个抽象类。只要把这些方法串起来能合理完成任务就行。
 */
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
   *
   * 注意：TrustedFuture的子类不能重写其方法，因为TrustedFuture里的几个方法是final的。getFutureValue里也有这个说明。
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

//  /**
//   * 什么是CAS机制？：https://blog.csdn.net/qq_32998153/article/details/79529704
//   * Java原子类中CAS的底层实现：http://www.cnblogs.com/noKing/p/9094983.html
//   * Treiber Stack介绍：https://www.cnblogs.com/micrari/p/7719408.html
//   * FutureTask源码解读：http://www.cnblogs.com/micrari/p/7374513.html
//   *
//   * 其实我觉得这里的成员变量并不需要原子化更新域而将其设置为volatile类型的，见Listener里就没有设置为volatile。
//   * 在get()中，对Waiter链表的操作，其实也是以Waiter为基本单元的，与Waiter里的元素的volatile与否关系不大。
//   *
//   * 去除这里的volatile变量，去除原子化更新域中的相应操作 TODO. 这样的话，作者注释里的non-volatile write也就可以理解了。
//   *
//   * 这些东西在《java并发编程实战》第二版15.4.3节中有说明，使用原子化域主要是为了相对于使用AtomicReference细微的性能提升。
//   */
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

    /**
     * 这个run方法应该是没有调用到，为什么会出现在这里呢？
     * 一种猜测：这个方法是老版本，以前在用，就是处理到complete时，
     * 使用额外的executor来执行此SetFuture.run来设置原future的value。
     * 具体见setFuture方法里，会将SetFuture封闭为listener加入到监听器链里。
     * 现在，complete里特意检测了一个task是否为SetFuture，然后做非递归处理了，
     * 见complete里的实现。
     */
    @Override
    public void run() {
      if (owner.value != this) { // 目的是为了判断future是否取消了，complete有类似的判断逻辑。
        // nothing to do, we must have been cancelled, don't bother inspecting the future.
        return;
      }
      Object valueToSet = getFutureValue(future);
      if (ATOMIC_HELPER.casValue(owner, this, valueToSet)) { // 注意这个是casValue，如果owner.value等于this，则将其值设置为valueToSet.
        // 这里体现了SetFuture类名的含义，是来设置future的，而AbstractFuture中的值是用value表示的，所以这里就是设置为getFutureValue的返回值。
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
//  /**
//   * 获取和定时获取
//   *
//   *    *响应中断
//   *    *如果您不打算使用park操作，请不要创建Waiter节点，这有助于减少waiters队列上的争用。
//   *    *将在#value变为非null /非SetFuture时定义Future完成。
//   *    *当waiters字段包含TOMBSTONE时，可以观察到Future完成情况。（Waiter与Listener类里都有个TOMBSTONE字段）
//   *
//   *  Timed Get
//   *    需要考虑一些设计约束
//   *    *我们希望对小超时做出响应，unpark（）具有非平凡的延迟开销（我在64位Linux系统上观察到12个微处理器唤醒停放的线程）。因此，如果超时很小，我们不应该park（）。
//   *        这需要与cpu的自旋开销进行折衷，因此我们使用SPIN_THRESHOLD_NANOS，这是AbstractQueuedSynchronizer用于类似目的的。
//   *    *我们想要在0的超时时间内合理地行事
//   *    *我们对完成的响应速度比超时更快。这是因为parkNanos依赖于系统调度，因此我们可能会错过我们的截止日期，或者unpark（）可能会被延迟，因此即使我们没有超时，
//   *        也会因为unpark()被延迟而超时。为了比较，FutureTask表示任务完成，而AQS是非确定性的（取决于waiter在队列中的位置）。如果我们想严格要求它，
//   *        我们可以将unpark（）时间存储在Waiter节点中，我们可以使用它来决定我们是否在取消停放之前超时。
//   */

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
              if (Thread.interrupted()) { // 对Thread.interrupt()方法很详细的介绍：https://blog.csdn.net/yonghumingshishenme/article/details/6285259
                // 理解java线程的中断(interrupt)：https://blog.csdn.net/canot/article/details/51087772
                // https://www.cnblogs.com/bingscode/p/6211837.html：https://www.cnblogs.com/bingscode/p/6211837.html
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
  public V get() throws InterruptedException, ExecutionException { // get方法是获取future的结果，可能需要调用park操作阻塞获取线程，
    // 所以可能会增加Waiter。
    // 而
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
  // 这个方法主要是在get方法里被调用，在调用这个方法之前会判断obj不为null，且不是SetFuture.
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
   * 如果此future取消了，则也会将取消传播到代理future，这个代理future是通过setFuture设置的。（见setFuture方法的注释）
   *
   * <p>Rather than override this method to perform additional cancellation work or cleanup,
   * subclasses should override {@link #afterDone}, consulting {@link #isCancelled} and {@link
   * #wasInterrupted} as necessary. This ensures that the work is done even if the future is
   * cancelled without a call to {@code cancel}, such as by calling {@code
   * setFuture(cancelledFuture)}.
   * 不要覆盖此方法来执行其他取消工作或清理， 子类应覆盖{@link #afterDone}，根据需要咨询{@link #isCancelled}和{@link #wasInterrupted}。
   * 这确保即使在未调用{@code cancel}的情况下取消future也可以完成工作，例如通过调用{@code setFuture（cancelledFuture）}。
   */
  @CanIgnoreReturnValue
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    Object localValue = value;
    boolean rValue = false;
    if (localValue == null | localValue instanceof SetFuture) { // 注意：这是一个位运算，两个都会判断
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
          rValue = true; // 这个指示取消操作是否执行成功了。如果在执行取消时future已经完成了，则执行取消操作失败。
          // We call interuptTask before calling complete(), which is consistent with
          // FutureTask
          if (mayInterruptIfRunning) {
            abstractFuture.interruptTask();
          }
          complete(abstractFuture);
          if (localValue instanceof SetFuture) {
            // propagate cancellation to the future set in setFuture, this is racy, and we don't
            // care if we are successful or not.
            // 传播cancellation，可能存在竞争，但是不管是否成功
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
                 *   1.对于通过setFuture串起来的长的future链，我们消耗的堆栈更少
                 *   2.我们避免在取消链的每个级别分配取消对象。
                 *
                 * 我们只能为TrustedFuture执行此操作，因为TrustedFuture.cancel是final，除了委托此方法之外什么都不做。
                 */
              AbstractFuture<?> trusted = (AbstractFuture<?>) futureToPropagateTo;
              localValue = trusted.value;
              if (localValue == null | localValue instanceof SetFuture) {
                abstractFuture = trusted;
                continue; // loop back up and try to complete the new future // 这也算是一个非递归的写法，只不过这个setFuture的委托链是单一的一条链，
                // 不像listener一样是像二叉树一样的东西。见complete里的注释。
              }
            } else {
              // not a TrustedFuture, call cancel directly.
              futureToPropagateTo.cancel(mayInterruptIfRunning); // 注意，这个是方法调用，目前还在while循环里。
            }
          }
          break;
        }
        // obj changed, reread
        localValue = abstractFuture.value; // 更新localValue。
        if (!(localValue instanceof SetFuture)) {
          // obj cannot be null at this point, because value can only change from null to non-null.
          // So if value changed (and it did since we lost the CAS), then it cannot be null and
          // since it isn't a SetFuture, then the future must be done and we should exit the loop
          // 此时obj不能为null，因为value只能从null更改为非null。 因此，如果值发生了变化（并且自从我们丢失了CAS之后就发生了变化），那么它不能为空，
          // 并且因为它不是SetFuture，那么必定是完成future了，我们应该退出循环
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
   * 子类可以覆盖此方法以实现对future计算的中断。 通过成功调用{@link #cancel（boolean）cancel（true）}自动调用该方法。
   *
   * <p>The default implementation does nothing.
   *
   * <p>This method is likely to be deprecated. Prefer to override {@link #afterDone}, checking
   * {@link #wasInterrupted} to decide whether to interrupt your task.
   *
   * 此方法可能已弃用。 希望覆盖{@link #afterDone}，检查{@link #wasInterrupted}以决定是否中断您的任务。
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
   * 在一个任务执行完后，就会调用这个set方法去设置future的结果。比如：TrustedListenableFutureTask.run -> InterruptibleTask.run ->
   * TrustedListenableFutureTask.afterRanInterruptibly里会调用set
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
   * 设置此{@code Future}的失败结果，除非此{@code Future}已被取消或设置（包括{@linkplain #setFuture 异步设置}）。
   * 当对此方法的调用返回时，{@code Future}保证为{@linkplain #isDone done} <b>仅当</ b>调用被接受时（在这种情况下它返回{@code true}）。
   * 如果它返回{@code false}，则{@code Future}可能先前已异步设置，在这种情况下，其结果可能尚未知晓。
   * 这个结果虽然还不知道，但只能通过调用{@link #cancel}来重写，不能调用{@code set *}方法重写。
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
   * 此外，任何取消操作后的任何{@code setFuture}调用都会将取消传播到所提供的{@code Future}。（这个的实现见setFuturn方法的最后面，
   * 这里的传播时指要设置时发现已被取消，就直接传播到参数future，而cancel方法里的传播动作则是在setFuture后引起的传播。）
   *
   * <p>Note that, even if the supplied future is cancelled and it causes this future to complete,
   * it will never trigger interruption behavior. In particular, it will not cause this future to
   * invoke the {@link #interruptTask} method, and the {@link #wasInterrupted} method will not
   * return {@code true}.
   *
   * <p>请注意，即使提供的future被取消并且导致此future完成，也不会触发中断行为。
   * 特别是，它不会导致此future调用{@link #interruptTask}方法，并且{@link #wasInterrupted}方法将不会返回{@code true}。
   * （这个见SetFuture.run() -> getFutureValue方法里的实现。）
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
        // 监听器负责调用completeWithFuture，directExecutor是合适的，因为我们所做的就是解包一个完成的future，这是非常快的操作。
        try {
          future.addListener(valueToSet, directExecutor()); // 这个是MoreExecutors里的方法
          // 当future完成时，就会去执行valueToSet（也是一个listener）里的run方法，然后设置this的value值。
          // 上面是future主动完成，然后通过this的，如果future取消了，不会导致this的中断方法的调用，见setFuture前面的方法注释。
          // 这个在this上调用setFuture后，只能再去调用cancel方法，而不能再次调用set方法，见setFuture前面的方法注释。
          // （其实这个SetFuture值，也算是this的一种完成状态，只不过最终的正常结果通知是由future完成后调用listener，
          // 然后调用SetFuture里的run方法最终完成this的value值的设置。）
          // 如果this上调用cancel方法，则是this主动了，然后cancel会传播到future上，见setFuture前面的方法注释。
        } catch (Throwable t) {
          // addListener has thrown an exception! SetFuture.run can't throw any exceptions so this
          // must have been caused by addListener itself. The most likely explanation is a
          // misconfigured mock. Try to switch to Failure.
          // addListener抛出异常！ SetFuture.run不能抛出任何异常，因此这必须是由addListener本身引起的。 最可能的解释是错误配置的模拟。 尝试切换到失败。
          // 如果设置监听器时任务future已完成，则会去直接执行，在直接执行时，会调用SetFuture.run，所以上面会提到这个方法。
          // 可以看ListenableFuture.addListener方法的注释，可能会抛出RejectedExecutionException，继承自RuntimeException，是免检异常。
          Failure failure;
          try {
            failure = new Failure(t);
          } catch (Throwable oomMostLikely) {
            failure = Failure.FALLBACK_INSTANCE;
          }
          // Note: The only way this CAS could fail is if cancel() has raced with us. That is ok.
          // 注意：此CAS失败的唯一方法是cancel（）与我们竞争。 那没问题。
          // 这里只所以说中唯一的可能竞争的方法是cancel，是因为上面已经用cas成功设置value了，所以其他set方法是不可用的，只有cancel可能重写这个value字段。见此方法前的注释。
          // 这里只所以失败也没问题是因为：上面要设置addListener，出现了异常，这里要设置出错的异常，而由于cancel的竞争导致设置出错异常失败，
          // 反正addListener没设置成功同时cancel设置取消了，则效果上没什么问题。
          boolean unused = ATOMIC_HELPER.casValue(this, valueToSet, failure);
        }
        return true; // 到这里时，value为SetFuture、Cancellation、Failure三者中的一个。
      }
      localValue = value; // we lost the cas, fall through and maybe cancel
    }
    // The future has already been set to something. If it is cancellation we should cancel the
    // incoming future.
    // 此future（this）已经被设置为某种对象，如果它是Cancellation类型的，则取消将传播到输入的future，见下面的实现，还有此方法前的注释说明。
    if (localValue instanceof Cancellation) {
      // we don't care if it fails, this is best-effort.
      future.cancel(((Cancellation) localValue).wasInterrupted);
    }
    return false;
  }

  /**
   * Returns a value that satisfies the contract of the {@link #value} field based on the state of
   * given future.
   * 根据给定的future的状态返回一个满足{@link #value}字段的约定（value字段有多种可能的情况，见其注释）的值。
   *
   * <p>This is approximately the inverse of {@link #getDoneValue(Object)}
   * 这与{@link #getDoneValue（Object）}大致相反。
   */
  private static Object getFutureValue(ListenableFuture<?> future) {
    Object valueToSet;
    if (future instanceof TrustedFuture) {
      // Break encapsulation for TrustedFuture instances since we know that subclasses cannot
      // override .get() (since it is final) and therefore this is equivalent to calling .get()
      // and unpacking the exceptions like we do below (just much faster because it is a single
      // field read instead of a read, several branches and possibly creating exceptions).
      // 打破TrustedFuture实例的封装，因为我们知道子类不能覆盖.get（）（因为它是final），因此这相当于调用.get（）并像下面那样解压缩异常
      // （因为它是单个字段读取而不是读取几个分支并且是可能产生异常的几个分支，所以更快）。
      Object v = ((AbstractFuture<?>) future).value;
      if (v instanceof Cancellation) {
        // If the other future was interrupted, clear the interrupted bit while preserving the cause
        // this will make it consistent with how non-trusted futures work which cannot propagate the
        // wasInterrupted bit
        // 如果另一个future被中断，在保留原因的同时清除被中断的位，这将使其与不可信的future的工作方式一致，即不传播isInterrupted位。
        // 这里清除了中断位，由外部调用者根据cause去检测是否为中断异常，然后可以根据需要再次抛出异常。
        // 这个不可信的future是指，不能保证外部的所有future都会传播这个中断位，所以只能放宽约定，不要依赖这个中断位的传递情况，只传递原因。
        // 这个见setFuture方法前的注释。
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
        Object v = getDone(future); // 这个是Futures里的方法，在具体实现中是先检查是否已完成的，是不会阻塞线程的。
        valueToSet = v == null ? NULL : v;
      } catch (ExecutionException exception) { // 这些异常，见getDone里的方法注释。
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
      // 我们在侦听器之前调用它，以避免需要为它们管理单独的堆栈数据结构。
      // afterDone（）通常应该很快并且仅用于清理工作......但理论上也可以递归并创建StackOverflowErrors。
      future.afterDone();
      // push the current set of listeners onto next
      next = future.clearListeners(next); // 注意这个next!!!
      future = null;
      while (next != null) {
        Listener curr = next;
        next = next.next; // 注意这个next!!!
        Runnable task = curr.task;
        if (task instanceof SetFuture) { // 具体见setFuture方法里，会将SetFuture封闭为listener加入到监听器链里。
          SetFuture<?> setFuture = (SetFuture<?>) task;
          // We unwind setFuture specifically to avoid StackOverflowErrors in the case of long
          // chains of SetFutures
          // Handling this special case is important because there is no way to pass an executor to
          // setFuture, so a user couldn't break the chain by doing this themselves.  It is also
          // potentially common if someone writes a recursive Futures.transformAsync transformer.
          // 我们特别解开setFuture以避免在SetFutures长链的情况下出现StackOverflowErrors。处理这种特殊情况很重要，
          // 因为无法将executor传递给setFuture，因此用户无法通过自己执行此操作来破坏链。
          // 如果有人编写递归的Futures.transformAsync转换器，这也是很常见的。
          future = setFuture.owner;
          if (future.value == setFuture) { // 目的是为了判断future是否取消了，跟SetFuture.run里的作用一样。
            Object valueToSet = getFutureValue(setFuture.future);
            if (ATOMIC_HELPER.casValue(future, setFuture, valueToSet)) { // 注意这个future，如果这里操作成功，则这个future算是完成了，
              // 然后开始跳到外层去继续处理此future上的waiters以及listener。注意：在处理这个future时，并不会丢弃这个future所属的setFuture所属的listener后面的listener，
              // 因为后面的listener已经存放在next里了，然后在continue outer后，会把next放入此future的listener链表的后面。
              // 这里的奇葩实现其实是非递归写法，如果递归写法会比较好理解，但是效率可能会低一些。
              // 这里listener的形状如同是二叉树一样，比如future1有三个listener，listenerA, listenerB, listenerC，而listenerB里的任务可能是一个SetFuture，
              // 然后这个SetFuture属于future2，那么从future1的listener链表处理到listenerB时，就会去处理listenerB的future2上的listener链表，比如为listenerD,listenerF，
              // 则continue后的总的listener链表为：listenerD, listenerF, listenerC。
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
   * 在完成未来之后完全调用一次的回调方法。
   * 如果在完成期间也运行{@link #interruptTask}，则{@link #afterDone}将在其后运行。
   * @code AbstractFuture}中此方法的默认实现不执行任何操作。
   * 这适用于非常轻量级的清理工作，例如，计时统计或清除字段。
   * 如果你的任务做了更重的考虑，只需使用一个带executor的监听器。
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
   * 返回此{@code Future}完成时的异常。 这包括通过调用{@link #setException}或{@link #setFuture setFuture} {@code (failedFuture)}设置的完成但没有取消。
   *
   * @throws RuntimeException if the {@code Future} has not failed
   */
  final Throwable trustedGetException() {
    return ((Failure) value).exception;
  }

  /**
   * If this future has been cancelled (and possibly interrupted), cancels (and possibly interrupts)
   * the given future (if available).
   * 如果此future已取消（并可能中断），则取消（并可能中断）给定的future（如果可用）。
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
    } while (!ATOMIC_HELPER.casWaiters(this, head, Waiter.TOMBSTONE));// 这个是如果一直不成功，就一直尝试，如何理解呢？
    // 这样子理解：Waiter表示要操作park动作，要阻塞线程，如果当前的线程一直不能成功，则表示一直有新的线程在阻塞等待结果。只有当当前线程操作成功，
    // 成功将waiters设置为Waiter.TOMBSTONE，表示此AbstractFuture已经完成，可以去取value了。见get方法上面的注释。
    for (Waiter currentWaiter = head; currentWaiter != null; currentWaiter = currentWaiter.next) { // 挨个释放Waiter。
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
    } while (!ATOMIC_HELPER.casListeners(this, head, Listener.TOMBSTONE)); // 这里的解释参考releaseWaiters的注释。
    Listener reversedList = onto;
    while (head != null) { // 反转链表
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
        // 不要在异常上调用getMessage或toString（），以防子类抛出的异常是由于子类的bug造成的。
        pendingDescription = "Exception thrown from implementation: " + e.getClass();
      }
      // The future may complete during or before the call to getPendingToString, so we use null
      // as a signal that we should try checking if the future is done again.
      // future可能在调用getPendingToString期间或之前完成，因此我们使用null作为信号，我们应该尝试再次检查future是否完成。
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
   * 提供一个人类可读的解释，说明为什么这个future还没有完成。
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
      V value = getDone(this); // 这个是Futures里的方法
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
    // 这是人们通过set / setFuture创建周期时的一些基本递归检测
    //
    //然而，这只是部分保护，因为它只检测自循环。 我们可以使用本地线程或可能通过捕获StackOverflowExceptions来检测任意循环，但这应该是一个足够好的解决方案（它也是jdk集合在这些情况下所做的）
    if (o == this) {
      return "this future";
    }
    return String.valueOf(o);
  }

  /**
   * Submits the given runnable to the given {@link Executor} catching and logging all {@linkplain
   * RuntimeException runtime exceptions} thrown by the executor.
   *
   * 将给定的runnable提交给给定的{@link Executor}捕获并记录执行程序抛出的所有{@linkplain RuntimeException 运行时异常}。
   */
  private static void executeListener(Runnable runnable, Executor executor) {
    try {
      executor.execute(runnable);
    } catch (RuntimeException e) {
      // Log it and keep going -- bad runnable and/or executor. Don't punish the other runnables if
      // we're given a bad one. We only catch RuntimeException because we want Errors to propagate
      // up.
      // 记录并继续 - 糟糕的runnable和/或executor。 如果我们被给予一个坏的，那么不要惩罚其他的runnables。
      // 我们只捕获RuntimeException，因为我们希望Errors传播。
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
