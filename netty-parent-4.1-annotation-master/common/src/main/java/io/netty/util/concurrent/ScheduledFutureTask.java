/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util.concurrent;

import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.PriorityQueueNode;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("ComparableImplementedButEqualsNotOverridden")
final class ScheduledFutureTask<V> extends PromiseTask<V> implements ScheduledFuture<V>, PriorityQueueNode {
    private static final AtomicLong nextTaskId = new AtomicLong();
    private static final long START_TIME = System.nanoTime();

    static long nanoTime() { // 这里关于定时的时间计算的都是相对时间，相对于类开始的时间。
        return System.nanoTime() - START_TIME;
    }

    static long deadlineNanos(long delay) {
        return nanoTime() + delay;
    }

    private final long id = nextTaskId.getAndIncrement();
    private long deadlineNanos;
    /* 0 - no repeat, >0 - repeat at fixed rate, <0 - repeat with fixed delay */
    // 注意上面的英文说明，非常关键。https://www.jianshu.com/p/58fad8e42379，见ScheduledFutureTask#run()里的分析。
    private final long periodNanos;

    private int queueIndex = INDEX_NOT_IN_QUEUE;

    ScheduledFutureTask(
            AbstractScheduledEventExecutor executor,
            Runnable runnable, V result, long nanoTime) {

        this(executor, toCallable(runnable, result), nanoTime);
    }

    ScheduledFutureTask(
            AbstractScheduledEventExecutor executor,
            Callable<V> callable, long nanoTime, long period) {

        super(executor, callable);
        if (period == 0) {
            throw new IllegalArgumentException("period: 0 (expected: != 0)");
        }
        deadlineNanos = nanoTime;
        periodNanos = period;
    }

    ScheduledFutureTask(
            AbstractScheduledEventExecutor executor,
            Callable<V> callable, long nanoTime) {

        super(executor, callable);
        deadlineNanos = nanoTime;
        periodNanos = 0;
    }

    @Override
    protected EventExecutor executor() {
        return super.executor();
    }

    public long deadlineNanos() {
        return deadlineNanos;
    }

    public long delayNanos() {
        return Math.max(0, deadlineNanos() - nanoTime()); // 这个方法是判断任务是否到期。
    }

    public long delayNanos(long currentTimeNanos) {
        return Math.max(0, deadlineNanos() - (currentTimeNanos - START_TIME)); // 这个也是相对时间，减去类开始的时间
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(delayNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (this == o) {
            return 0;
        }

        ScheduledFutureTask<?> that = (ScheduledFutureTask<?>) o;
        long d = deadlineNanos() - that.deadlineNanos();
        if (d < 0) {
            return -1;
        } else if (d > 0) {
            return 1;
        } else if (id < that.id) {
            return -1;
        } else if (id == that.id) {
            throw new Error();
        } else {
            return 1;
        }
    }

    /**
     * if (periodNanos == 0) 对应 若干时间后执行一次 的定时任务类型，执行完了该任务就结束了。

     否则，进入到else代码块，先执行任务，然后再区分是哪种类型的任务，periodNanos大于0，表示是以固定频率执行某个任务，和任务的持续时间无关，
     然后，设置该任务的下一次截止时间为本次的截止时间加上间隔时间periodNanos，否则，就是每次任务执行完毕之后，间隔多长时间之后再次执行，
     截止时间为当前时间加上间隔时间，-p就表示加上一个正的间隔时间，最后，将当前任务对象再次加入到队列，实现任务的定时执行

     作者：简书闪电侠
     链接：https://www.jianshu.com/p/58fad8e42379
     來源：简书
     著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
     */
    @Override
    public void run() {
        assert executor().inEventLoop();
        try {
            if (periodNanos == 0) { // 只执行一次
                if (setUncancellableInternal()) {
                    V result = task.call();
                    setSuccessInternal(result);
                }
            } else {
                // check if is done as it may was cancelled
                if (!isCancelled()) {
                    task.call();
                    if (!executor().isShutdown()) {
                        long p = periodNanos;
                        if (p > 0) {
                            deadlineNanos += p;
                        } else { // 注意：这里时p为负值，-p表示加上一个正的时间间隔
                            deadlineNanos = nanoTime() - p;
                        }
                        if (!isCancelled()) {
                            // scheduledTaskQueue can never be null as we lazy init it before submit the task!
                            Queue<ScheduledFutureTask<?>> scheduledTaskQueue =
                                    ((AbstractScheduledEventExecutor) executor()).scheduledTaskQueue;
                            assert scheduledTaskQueue != null;
                            scheduledTaskQueue.add(this);
                        }
                    }
                }
            }
        } catch (Throwable cause) {
            setFailureInternal(cause);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean canceled = super.cancel(mayInterruptIfRunning);
        if (canceled) {
            ((AbstractScheduledEventExecutor) executor()).removeScheduled(this);
        }
        return canceled;
    }

    boolean cancelWithoutRemove(boolean mayInterruptIfRunning) {
        return super.cancel(mayInterruptIfRunning);
    }

    @Override
    protected StringBuilder toStringBuilder() {
        StringBuilder buf = super.toStringBuilder();
        buf.setCharAt(buf.length() - 1, ',');

        return buf.append(" id: ")
                  .append(id)
                  .append(", deadline: ")
                  .append(deadlineNanos)
                  .append(", period: ")
                  .append(periodNanos)
                  .append(')');
    }

    @Override
    public int priorityQueueIndex(DefaultPriorityQueue<?> queue) { // 获取内部状态
        return queueIndex;
    }

    @Override
    public void priorityQueueIndex(DefaultPriorityQueue<?> queue, int i) { // 设置内部状态
        queueIndex = i;
    }
}
