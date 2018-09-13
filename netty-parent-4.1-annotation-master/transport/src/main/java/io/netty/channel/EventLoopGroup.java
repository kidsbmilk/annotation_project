/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.util.concurrent.EventExecutorGroup;

/**
 * Special {@link EventExecutorGroup} which allows registering {@link Channel}s that get
 * processed for later selection during the event loop.
 *
 */

/**
 * 注意：这些都是接口或者抽象类，一般只含有一些方法定义，并不包含具体的变量以及方法实现。
 *
 * 具体的实现可能见NioEventLoop或者是NioEventLoopGroup.
 *
 * （EventLoop与EventLoopGroup）的设计关系与（EventExecutor与EventExecutorGroup）设计关系非常近。
 * EventLoop继承自EventLoopGroup，EventLoop由EventLoopGroup管理；EventExecutor继承自EventExecutorGroup，EventExecutor由EventExecutorGroup管理。
 */
public interface EventLoopGroup extends EventExecutorGroup {
    /**
     * Return the next {@link EventLoop} to use
     */
    @Override
    EventLoop next();

    /**
     * Register a {@link Channel} with this {@link EventLoop}. The returned {@link ChannelFuture}
     * will get notified once the registration was complete.
     *
     * 注意这是异步的操作，直接返回ChannelFuture.
     */
    ChannelFuture register(Channel channel);

    /**
     * Register a {@link Channel} with this {@link EventLoop} using a {@link ChannelFuture}. The passed
     * {@link ChannelFuture} will get notified once the registration was complete and also will get returned.
     *
     * 注意这是异步的操作，直接返回ChannelFuture.
     */
    ChannelFuture register(ChannelPromise promise);

    /**
     * Register a {@link Channel} with this {@link EventLoop}. The passed {@link ChannelFuture}
     * will get notified once the registration was complete and also will get returned.
     *
     * @deprecated Use {@link #register(ChannelPromise)} instead.
     *
     * 注意这是异步的操作，直接返回ChannelFuture.
     */
    @Deprecated
    ChannelFuture register(Channel channel, ChannelPromise promise);
}
