/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;

import java.net.Socket;

/**
 * A duplex {@link Channel} that has two sides that can be shutdown independently.
 *
 * duplex：
 * adj.	有两部分的;
 * n.	连栋式的两栋住宅; 联式房屋; 占两层楼的公寓套房; 复式住宅;
 *
 * 这个类好像是封装Channel为双向可操作的，输入与输出两个通道，可独立关闭。
 *
 * TCP协议提供全双工服务，就是所说的TCP携带的数据可以在同一时间双向流动，当两个主机中的tcp握手成功之后，通过发送缓存和接收缓存等可以实现双向通信。
 */
public interface DuplexChannel extends Channel {
    /**
     * Returns {@code true} if and only if the remote peer shut down its output so that no more
     * data is received from this channel.  Note that the semantic of this method is different from
     * that of {@link Socket#shutdownInput()} and {@link Socket#isInputShutdown()}.
     */
    boolean isInputShutdown();

    /**
     * @see Socket#shutdownInput()
     */
    ChannelFuture shutdownInput();

    /**
     * Will shutdown the input and notify {@link ChannelPromise}.
     *
     * @see Socket#shutdownInput()
     */
    ChannelFuture shutdownInput(ChannelPromise promise);

    /**
     * @see Socket#isOutputShutdown()
     */
    boolean isOutputShutdown();

    /**
     * @see Socket#shutdownOutput()
     */
    ChannelFuture shutdownOutput();

    /**
     * Will shutdown the output and notify {@link ChannelPromise}.
     *
     * @see Socket#shutdownOutput()
     */
    ChannelFuture shutdownOutput(ChannelPromise promise);

    /**
     * Determine if both the input and output of this channel have been shutdown.
     */
    boolean isShutdown();

    /**
     * Will shutdown the input and output sides of this channel.
     * @return will be completed when both shutdown operations complete.
     */
    ChannelFuture shutdown();

    /**
     * Will shutdown the input and output sides of this channel.
     * @param promise will be completed when both shutdown operations complete.
     * @return will be completed when both shutdown operations complete.
     */
    ChannelFuture shutdown(ChannelPromise promise);
}
