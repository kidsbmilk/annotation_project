/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel.socket.nio;

import static net.gleamynode.netty.channel.Channels.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.gleamynode.netty.channel.AbstractChannelSink;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelException;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelFutureListener;
import net.gleamynode.netty.channel.ChannelPipeline;
import net.gleamynode.netty.channel.ChannelState;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.logging.Logger;
import net.gleamynode.netty.util.NamePreservingRunnable;

class NioClientSocketPipelineSink extends AbstractChannelSink {

    static final Logger logger =
        Logger.getLogger(NioClientSocketPipelineSink.class);
    private static final AtomicInteger nextId = new AtomicInteger();

    final int id = nextId.incrementAndGet();
    final Executor bossExecutor;
    private final Boss boss = new Boss(); // 这里是私有内部类。
    private final NioWorker[] workers;
    private final AtomicInteger workerIndex = new AtomicInteger();

    NioClientSocketPipelineSink(
            Executor bossExecutor, Executor workerExecutor, int workerCount) {
        this.bossExecutor = bossExecutor;
        workers = new NioWorker[workerCount];
        for (int i = 0; i < workers.length; i ++) {
            workers[i] = new NioWorker(id, i + 1, workerExecutor);
        }
    }

    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            NioClientSocketChannel channel =
                (NioClientSocketChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();

            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    NioWorker.close(channel, future);
                }
                break;
            case BOUND:
                if (value != null) {
                    bind(channel, future, (SocketAddress) value);
                } else {
                    NioWorker.close(channel, future);
                }
                break;
            case CONNECTED:
                if (value != null) {
                    connect(channel, future, (SocketAddress) value);
                } else {
                    NioWorker.close(channel, future);
                }
                break;
            case INTEREST_OPS:
                NioWorker.setInterestOps(channel, future, ((Integer) value).intValue());
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            NioSocketChannel channel = (NioSocketChannel) event.getChannel();
            channel.writeBuffer.offer(event);
            NioWorker.write(channel);
        }
    }

    private void bind(
            NioClientSocketChannel channel, ChannelFuture future,
            SocketAddress localAddress) {
        try {
            channel.socket.socket().bind(localAddress);
            channel.boundManually = true;
            future.setSuccess(); // 这个操作会导致通知监听此future的listener，执行一些异步操作。这里非常关键！！！
            fireChannelBound(channel, channel.getLocalAddress());
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void connect(
            final NioClientSocketChannel channel, ChannelFuture future,
            SocketAddress remoteAddress) {
        try {
            if (channel.socket.connect(remoteAddress)) { // 大致是：如果立即连接成功，则返回true，并在后续的监测连接的可读事件。
                NioWorker worker = nextWorker();
                channel.setWorker(worker);
                worker.register(channel, future);
            } else { // 大致是：如果立即返回但是没连接成功，则将这个连接事件注册到boss的事件循环里，并对future添加listener。
                // 这个future应该是connect的事件，然后添加一个listener，当future完成后，就用listener做一些后续的处理。
                future.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture future) {
                        if (future.isCancelled()) {
                            channel.close();
                        }
                    }
                });
                channel.connectFuture = future;
                boss.register(channel); // 监测连接的连接成功事件，见具体方法里的代码。
            }

        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    NioWorker nextWorker() {
        return workers[Math.abs(
                workerIndex.getAndIncrement() % workers.length)];
    }

    private class Boss implements Runnable { // 注意：这是内部私有类Boss，NioServerSocketPipelineSink里也有个内部私有类Boss。

        private final AtomicBoolean started = new AtomicBoolean();
        private volatile Selector selector;
        private final Object selectorGuard = new Object();

        Boss() {
            super();
        }

        void register(NioSocketChannel channel) {
            boolean firstChannel = started.compareAndSet(false, true);
            Selector selector;
            if (firstChannel) {
                try {
                    this.selector = selector = Selector.open();
                } catch (IOException e) {
                    throw new ChannelException(
                            "Failed to create a selector.", e);
                }
            } else {
                selector = this.selector;
                if (selector == null) {
                    do {
                        Thread.yield();
                        selector = this.selector;
                    } while (selector == null); // 这里是在什么情况下才会发生的呢 ?zz?
                }
            }

            if (firstChannel) {
                try {
                    channel.socket.register(selector, SelectionKey.OP_CONNECT, channel);
                } catch (ClosedChannelException e) {
                    throw new ChannelException(
                            "Failed to register a socket to the selector.", e);
                }
                bossExecutor.execute(new NamePreservingRunnable(
                        this,
                        "New I/O client boss #" + id)); // 注意这里的this，Boss对象是一个可运行的对象，新的线程从run开始执行。
            } else {
                synchronized (selectorGuard) {
                    selector.wakeup();
                    try {
                        channel.socket.register(selector, SelectionKey.OP_CONNECT, channel);
                    } catch (ClosedChannelException e) {
                        throw new ChannelException(
                                "Failed to register a socket to the selector.", e);
                    }
                }
            }
        }

        public void run() {
            boolean shutdown = false;
            Selector selector = this.selector;
            for (;;) {
                synchronized (selectorGuard) {
                    // This empty synchronization block prevents the selector
                    // from acquiring its lock.
                }
                try {
                    int selectedKeyCount = selector.select(500);
                    if (selectedKeyCount > 0) {
                        processSelectedKeys(selector.selectedKeys());
                    }

                    // Exit the loop when there's nothing to handle.
                    // The shutdown flag is used to delay the shutdown of this
                    // loop to avoid excessive Selector creation when
                    // connection attempts are made in a one-by-one manner
                    // instead of concurrent manner.
                    if (selector.keys().isEmpty()) {
                        if (shutdown) {
                            synchronized (selectorGuard) {
                                if (selector.keys().isEmpty()) {
                                    try {
                                        selector.close();
                                    } catch (IOException e) {
                                        logger.warn(
                                                "Failed to close a selector.",
                                                e);
                                    } finally {
                                        this.selector = null;
                                    }
                                    started.set(false);
                                    break;
                                } else {
                                    shutdown = false;
                                }
                            }
                        } else {
                            // Give one more second.
                            shutdown = true;
                        }
                    } else {
                        shutdown = false;
                    }
                } catch (Throwable t) {
                    logger.warn(
                            "Unexpected exception in the selector loop.", t);

                    // Prevent possible consecutive immediate failures.
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore.
                    }
                }
            }
        }

        private void processSelectedKeys(Set<SelectionKey> selectedKeys) {
            for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
                SelectionKey k = i.next();
                i.remove();

                if (!k.isValid()) {
                    close(k);
                    continue;
                }

                if (k.isConnectable()) {
                    connect(k);
                }
            }
        }

        private void connect(SelectionKey k) {
            NioClientSocketChannel ch = (NioClientSocketChannel) k.attachment();
            try {
                if (ch.socket.finishConnect()) {
                    k.cancel();
                    NioWorker worker = nextWorker();
                    ch.setWorker(worker);
                    worker.register(ch, ch.connectFuture);
                }
            } catch (Throwable t) {
                k.cancel();
                ch.connectFuture.setFailure(t);
                fireExceptionCaught(ch, t);
                close(k);
            }
        }

        private void close(SelectionKey k) {
            NioSocketChannel ch = (NioSocketChannel) k.attachment();
            NioWorker.close(ch, ch.getSucceededFuture());
        }
    }
}
