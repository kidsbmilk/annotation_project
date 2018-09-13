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

import net.gleamynode.netty.logging.Logger;


/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 */

// 这个为什么没有是否可共享到多个pipeline中的标记ChannelPipelineCoverage ?zz?
public class SimpleChannelHandler implements ChannelUpstreamHandler {

    private static final Logger logger =
        Logger.getLogger(SimpleChannelHandler.class.getName());

    public void handleUpstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

        if (e instanceof MessageEvent) {
            messageReceived(ctx, (MessageEvent) e);
        } else if (e instanceof ChildChannelStateEvent) {
            ChildChannelStateEvent evt = (ChildChannelStateEvent) e;
            if (evt.getChildChannel().isOpen()) {
                childChannelOpen(ctx, evt);
            } else {
                childChannelClosed(ctx, evt);
            }
        } else if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
            case OPEN: // 经观察发现，不论是发起什么有关于连接的操作，都会先有一个OPEN事件，然后才是其他的BOUND、CONNECTED等等。
                if (Boolean.TRUE.equals(evt.getValue())) {
                    channelOpen(ctx, evt);
                } else {
                    channelClosed(ctx, evt);
                }
                break;
            case BOUND:
                if (evt.getValue() != null) {
                    channelBound(ctx, evt);
                } else {
                    channelUnbound(ctx, evt);
                }
                break;
            case CONNECTED:
                if (evt.getValue() != null) {
                    channelConnected(ctx, evt);
                } else {
                    channelDisconnected(ctx, evt);
                }
                break;
            case INTEREST_OPS:
                channelInterestChanged(ctx, evt);
                break;
            }
        } else if (e instanceof ExceptionEvent) {
            exceptionCaught(ctx, (ExceptionEvent) e);
        } else {
            ctx.sendUpstream(e);
        }
    }

    public void channelBound(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception { // ServerBootstrap#Binder里没有重写此方法。
        ctx.sendUpstream(e);
    }

    public void channelClosed(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void channelInterestChanged(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void channelDisconnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void channelOpen(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception { // ServerBootstrap#Binder里重写此方法了。
        ctx.sendUpstream(e);
    }

    public void channelUnbound(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (this == ctx.getPipeline().getLast()) {
            logger.warn(
                    "EXCEPTION, please implement " + getClass().getName() +
                    ".exceptionCaught() for proper handling.", e.getCause());
        }
        ctx.sendUpstream(e);
    }

    public void childChannelClosed(
            ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void childChannelOpen(
            ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }
}
