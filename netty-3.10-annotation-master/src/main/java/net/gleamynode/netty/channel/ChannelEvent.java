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
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.composedOf net.gleamynode.netty.channel.ChannelFuture
 */

/**
 * ChannelEvent定义一个事件，在netty中，Channel用于关联一个连接的操作事件，而future用于异步得到事件的结果。
 */
public interface ChannelEvent {
    Channel getChannel(); // Event与Channel相关联，Channel又与Config相关联，而Config又与PipelineFactory相关联，
    // 而PipelineFactory又与Pipeline相关联，Bootstrap与PipelineFactory有关联。所以，在ServerBootstrap中可以得到pipeline。
    // 所以，ServerBootstrap#Bindder#channelOpen中可以有以下代码：evt.getChannel().getConfig().setPipelineFactory(getPipelineFactory());
    ChannelFuture getFuture();
}
