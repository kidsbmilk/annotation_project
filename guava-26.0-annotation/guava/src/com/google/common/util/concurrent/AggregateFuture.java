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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Futures.getDone;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.ImmutableCollection;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A future made up of a collection of sub-futures.
 *
 * @param <InputT> the type of the individual inputs
 * @param <OutputT> the type of the output (i.e. this) future
 */
@GwtCompatible
abstract class AggregateFuture<InputT, OutputT> extends AbstractFuture.TrustedFuture<OutputT> {
  private static final Logger logger = Logger.getLogger(AggregateFuture.class.getName());

  /*
   * In certain circumstances, this field might theoretically not be visible to an afterDone() call
   * triggered by cancel(). For details, see the comments on the fields of TimeoutFuture.
   * 在某些情况下，理论上这个字段在cancel（）触发的afterDone（）调用中可能不可见。
   * 有关详细信息，请参阅TimeoutFuture字段的注释。
   */
  private @Nullable RunningState runningState;

  @Override
  protected final void afterDone() {
    super.afterDone();
    RunningState localRunningState = runningState;
    if (localRunningState != null) {
      // Let go of the memory held by the running state
      this.runningState = null;
      ImmutableCollection<? extends ListenableFuture<? extends InputT>> futures =
          localRunningState.futures;
      boolean wasInterrupted = wasInterrupted();

      if (wasInterrupted) {
        localRunningState.interruptTask();
      }

      if (isCancelled() & futures != null) {
        for (ListenableFuture<?> future : futures) {
          future.cancel(wasInterrupted);
        }
      }
    }
  }

  @Override
  protected String pendingToString() {
    RunningState localRunningState = runningState;
    if (localRunningState == null) {
      return null;
    }
    ImmutableCollection<? extends ListenableFuture<? extends InputT>> localFutures =
        localRunningState.futures;
    if (localFutures != null) {
      return "futures=[" + localFutures + "]";
    }
    return null;
  }

  /** Must be called at the end of each sub-class's constructor. */
  final void init(RunningState runningState) {
    this.runningState = runningState;
    runningState.init();
  }

  /**
   * 说一下为什么这个类叫state，其实就是管理一下聚合future中多个future的状态信息，
   * AggregateFutureState是最基础的类，当然里面也包含最基本的必不可少的多个future的状态信息，
   * 比如future是否出现异常以及多少个已完成。
   * 而这个RunnState扩展了AggregateFutureState，增加了多个future的原始信息，以及是否必须全部完成、是否收集结果等等。
   * 再接下来的一些更具体的类，比如CollectionFutureRunningState，继承自RunningState，增加了成员变量values，
   * 用于保存收集的多个future的结果，同时实现了collectOneValue方法，用于将结果写入到values中，也实现了handleAllCompleted方法，
   * 并引出了一个新的抽象方法combine，用于转换vlaues为最终聚合future的结果，这个要见ListFutureRunningState类，
   * 它继承了CollectionFutureRunningState，实现了combine方法，将vlaue转为不要变的列表，设置为最终的聚合future的结果。
   *
   * 这一套继承下来，非常漂亮。
   */
  abstract class RunningState extends AggregateFutureState implements Runnable {
    private ImmutableCollection<? extends ListenableFuture<? extends InputT>> futures;
    private final boolean allMustSucceed;
    private final boolean collectsValues;

    RunningState(
        ImmutableCollection<? extends ListenableFuture<? extends InputT>> futures,
        boolean allMustSucceed,
        boolean collectsValues) {
      super(futures.size());
      this.futures = checkNotNull(futures);
      this.allMustSucceed = allMustSucceed;
      this.collectsValues = collectsValues;
    }

    /* Used in the !allMustSucceed case so we don't have to instantiate a listener.
     * 在!allMustSucceed案例中使用，因此我们不必实例化一个监听器。
     */
    @Override
    public final void run() {
      decrementCountAndMaybeComplete();
    }

    /**
     * The "real" initialization; we can't put this in the constructor because, in the case where
     * futures are already complete, we would not initialize the subclass before calling {@link
     * #handleOneInputDone}. As this is called after the subclass is constructed, we're guaranteed
     * to have properly initialized the subclass.
     * “真正的”初始化; 我们不能把它放在构造函数中，因为在futures已经完成的情况下，
     * 我们不会在调用{@link #handleOneInputDone}之前初始化子类。 因为在构造子类之后调用此方法，
     * 所以我们保证在调用此方法之前已经正确初始化子类了。
     */
    private void init() {
      // Corner case: List is empty.
      if (futures.isEmpty()) {
        handleAllCompleted();
        return;
      }

      // NOTE: If we ever want to use a custom executor here, have a look at CombinedFuture as we'll
      // need to handle RejectedExecutionException
      // 注意：如果我们想在这里使用自定义executor，请查看CombinedFuture，因为我们需要处理RejectedExecutionException

      if (allMustSucceed) {
        // We need fail fast, so we have to keep track of which future failed so we can propagate
        // the exception immediately
        // 我们需要快速失败，因此我们必须跟踪哪个future失败，以便我们可以立即传播异常

        // Register a listener on each Future in the list to update the state of this future.
        // 在列表中的每个Future上注册一个侦听器，以更新此future的状态。
        // Note that if all the futures on the list are done prior to completing this loop, the last
        // call to addListener() will callback to setOneValue(), transitively call our cleanup
        // listener, and set this.futures to null.
        // 请注意，如果列表中的所有future都在完成此循环之前完成，则对addListener()的最后一次调用将回调到setOneValue()，
        // 传递调用我们的清理侦听器，并将this.futures设置为null。
        // This is not actually a problem, since the foreach only needs this.futures to be non-null
        // at the beginning of the loop.
        // 这实际上不是一个问题，因为foreach只需要this.futures在循环开始时为非null。
        int i = 0;
        for (final ListenableFuture<? extends InputT> listenable : futures) {
          final int index = i++; // 注意这个：匿名内部类需要传递一个表示下标的值，此值需要为final的，但是此值又需要变化，所以这里就先用一个可变量的量去初始化创建一个final的量。
          listenable.addListener( // 注意：这个是加入listener后立即就返回了，这个init也就结束了。
              new Runnable() {
                @Override
                public void run() {
                  try {
                    handleOneInputDone(index, listenable);
                  } finally {
                    decrementCountAndMaybeComplete();
                  }
                }
              },
              directExecutor());
        }
      } else {
        // We'll only call the callback when all futures complete, regardless of whether some failed
        // 我们只会在所有futures完成时调用回调，无论是否有一些失败
        // Hold off on calling setOneValue until all complete, so we can share the same listener
        // 暂停调用setOneValue直到全部完成，这样我们就可以共享同一个监听器
        for (ListenableFuture<? extends InputT> listenable : futures) {
          listenable.addListener(this, directExecutor());
        }
      }
    }

    /**
     * Fails this future with the given Throwable if {@link #allMustSucceed} is true. Also, logs the
     * throwable if it is an {@link Error} or if {@link #allMustSucceed} is {@code true}, the
     * throwable did not cause this future to fail, and it is the first time we've seen that
     * particular Throwable.
     * 如果{@link #allMustSucceed}为真，则使用给定的Throwable失败这个future。
     * 另外，如果它是{@link Error}或者如果{@link #allMustSucceed}是{@code true}，则记录throwable，
     * throwable不会导致此future失败，这是我们第一次看到特别的throwable。
     */
    private void handleException(Throwable throwable) {
      checkNotNull(throwable);

      boolean completedWithFailure = false;
      boolean firstTimeSeeingThisException = true;
      if (allMustSucceed) {
        // As soon as the first one fails, throw the exception up.
        // The result of all other inputs is then ignored.
          // 一旦第一个失败，抛出异常。 然后忽略所有其他输入的结果。
        completedWithFailure = setException(throwable); // 这里失败的原因是，有其他future已经设置其失败了，
        // 所以false时要调用下面的addCausalChain看此失败原因是否第一次出现，然后记录一下。
        if (completedWithFailure) {
          releaseResourcesAfterFailure();
        } else {
          // Go up the causal chain to see if we've already seen this cause; if we have, even if
          // it's wrapped by a different exception, don't log it.
            // 走上因果链，看看我们是否已经看到了这个原因; 如果我们有，即使它被一个不同的异常包装，也不要记录它。
          firstTimeSeeingThisException = addCausalChain(getOrInitSeenExceptions(), throwable);
        }
      }

      // | and & used because it's faster than the branch required for || and &&
      if (throwable instanceof Error
          | (allMustSucceed & !completedWithFailure & firstTimeSeeingThisException)) {
        String message =
            (throwable instanceof Error)
                ? "Input Future failed with Error"
                : "Got more than one input Future failure. Logging failures after the first";
        logger.log(Level.SEVERE, message, throwable);
      }
    }

    @Override
    final void addInitialException(Set<Throwable> seen) {
      if (!isCancelled()) {
        // TODO(cpovirk): Think about whether we could/should use Verify to check this.
        boolean unused = addCausalChain(seen, trustedGetException());
      }
    }

    /** Handles the input at the given index completing. */
    private void handleOneInputDone(int index, Future<? extends InputT> future) {
      // The only cases in which this Future should already be done are (a) if it was cancelled or
      // (b) if an input failed and we propagated that immediately because of allMustSucceed.
        // 此Future应该已经完成的仅有的情况是（a）如果它被取消或（b）如果某个输入失败并且由于设置了allMustSucceed导致我们立即传播它。
        // 此future是指这个this这个AggregateFuture实例。

        // 下面这个checkState的三个判断条件没看明白 ?
        // 终于想明白了：将判断表达式allMustSucceed || !isDone() || isCancelled()记为A，
        // 如果isCancelled()为true，则isDone必定为true，则下面的判断必定为true，
        // 下面的判断只有一种情况下会使A为false、抛出异常：isCancelled()为false且isDone为true且allMustSucceed为false，
        // 表示的是：如果不要求所有的输入future都成功（allMustSucceed为false），此时的init里会走“listenable.addListener(this, directExecutor());”的分支，
        // 而走这个分支时，因为不会快速失败（allMustSucceed为false）而且又不是主动取消的(isCancelled()为false)，但是isDone却是完成的，
        // 这本身就是有问题的，所以要抛出异常。
        // 当isCancelled()为false且allMustSucceed为false时，只有isDone为false才是正常的情况，这种情况下调用handleOneInputDone的代码的流程：
        // “listenable.addListener(this, directExecutor());”的分支  -->   decrementCountAndMaybeComplete()  -->   processCompleted  -->  handleOneInputDone

        // 其实仔细想想，上面的英文注释说的跟我上面的解释是一样的，只是过作者的注释写的不够具体详细。
      checkState(
          allMustSucceed || !isDone() || isCancelled(),
          "Future was done before all dependencies completed");

      try {
        checkState(future.isDone(), "Tried to set value from future which is not done");
        if (allMustSucceed) {
          if (future.isCancelled()) {
            // clear running state prior to cancelling children, this sets our own state but lets
            // the input futures keep running as some of them may be used elsewhere.
              // 在取消孩子之前清除运行状态，这设置了我们自己的状态，但让输入的futures继续运行，因为其中一些可能在其他地方使用。
              // 这个children指this这个AggregateFuture。
            runningState = null;
            cancel(false);
          } else {
            // We always get the result so that we can have fail-fast, even if we don't collect
              // 我们总是得到结果，以便我们可以快速失败，即使我们不收集
            InputT result = getDone(future);
            if (collectsValues) {
              collectOneValue(allMustSucceed, index, result);
            }
          }
        } else if (collectsValues && !future.isCancelled()) {
          collectOneValue(allMustSucceed, index, getDone(future));
        }
      } catch (ExecutionException e) {
        handleException(e.getCause());
      } catch (Throwable t) {
        handleException(t);
      }
    }

    private void decrementCountAndMaybeComplete() {
      int newRemaining = decrementRemainingAndGet();
      checkState(newRemaining >= 0, "Less than 0 remaining futures");
      if (newRemaining == 0) {
        processCompleted();
      }
    }

    private void processCompleted() {
      // Collect the values if (a) our output requires collecting them and (b) we haven't been
      // collecting them as we go. (We've collected them as we go only if we needed to fail fast)
        // 在以下情况下，收集这些值：如果（a）我们的产出需要收集它们，（b）我们没有收集它们。 （我们只有在需要快速失败时才收集它们）
      if (collectsValues & !allMustSucceed) {
        int i = 0;
        for (ListenableFuture<? extends InputT> listenable : futures) {
          handleOneInputDone(i++, listenable);
        }
      }
      handleAllCompleted();
    }

    /**
     * Listeners implicitly keep a reference to {@link RunningState} as they're inner classes, so we
     * free resources here as well for the allMustSucceed=true case (i.e. when a future fails, we
     * immediately release resources we no longer need); additionally, the future will release its
     * reference to {@link RunningState}, which should free all associated memory when all the
     * futures complete and the listeners are released.
     * 监听器隐含地保留对{@link RunningState}的引用，因为它们是内部类，因此我们在此处释放资源以及allMustSucceed = true情况
     * （即，当future失败时，我们立即释放资源，因为我们不再需要它们了）; 此外，future将释放对{@link RunningState}的引用，
     * 当所有future完成并释放监听器时，它应释放所有相关内存。
     *
     * <p>TODO(user): Write tests for memory retention
     */
    @ForOverride
    @OverridingMethodsMustInvokeSuper
    void releaseResourcesAfterFailure() {
      this.futures = null;
    }

    /**
     * Called only if {@code collectsValues} is true.
     * 仅在{@code collectctsValues}为true时才调用。
     *
     * <p>If {@code allMustSucceed} is true, called as each future completes; otherwise, called for
     * each future when all futures complete.
     * <p>如果{@code allMustSucceed}为true，则在每个future完成时被调用; 否则，当所有future完成时，对每个future调用此方法。
     */
    abstract void collectOneValue(boolean allMustSucceed, int index, @Nullable InputT returnValue);

    abstract void handleAllCompleted();

    void interruptTask() {}
  }

  /** Adds the chain to the seen set, and returns whether all the chain was new to us. */
  private static boolean addCausalChain(Set<Throwable> seen, Throwable t) {
    for (; t != null; t = t.getCause()) {
      boolean firstTimeSeen = seen.add(t);
      if (!firstTimeSeen) {
        /*
         * We've seen this, so we've seen its causes, too. No need to re-add them. (There's one case
         * where this isn't true, but we ignore it: If we record an exception, then someone calls
         * initCause() on it, and then we examine it again, we'll conclude that we've seen the whole
         * chain before when it fact we haven't. But this should be rare.)
         * 我们已经看到了这一点，所以我们也看到了它的原因。 无需重新添加它们。
         * （有一种情况不是这样，但我们忽略它：如果我们记录一个异常，那么有人在其上调用initCause（），然后我们再次检查它，
         * 我们将得出结论，我们已经看到了整个链，但是事实上我们没有。但这应该是罕见的。）
         */
        return false;
      }
    }
    return true;
  }
}
