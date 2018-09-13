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

import io.netty.util.concurrent.OrderedEventExecutor;

/**
 * Will handle all the I/O operations for a {@link Channel} once registered.
 *
 * One {@link EventLoop} instance will usually handle more than one {@link Channel} but this may depend on
 * implementation details and internals.
 *
 */

/**
 * 在Netty中，一个EventLoop需要负责两个工作，第一个是作为IO线程，负责相应的IO操作；
 * 第二个是作为任务线程，执行taskQueue中的任务。
 */

/**
 * 注意：这些都是接口或者抽象类，一般只含有一些方法定义，并不包含具体的变量以及方法实现。
 *
 * 具体的实现可能见NioEventLoop或者是NioEventLoopGroup.
 */
public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {
    @Override
    EventLoopGroup parent();
}
