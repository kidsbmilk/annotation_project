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
package net.gleamynode.netty.channel;

/**
 * ChannelEvent定义一个事件，在netty中，Channel用于关联一个连接的操作事件，而future用于异步得到事件的结果。
 */
public class DefaultChannelEvent implements ChannelEvent {

    private final Channel channel;
    private final ChannelFuture future;

    public DefaultChannelEvent(Channel channel, ChannelFuture future) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (future == null) {
            throw new NullPointerException("future");
        }
        this.channel = channel;
        this.future = future;
    }

    public final Channel getChannel() {
        return channel;
    }

    public final ChannelFuture getFuture() {
        return future;
    }

    @Override
    public String toString() {
        return channel.toString();
    }
}
