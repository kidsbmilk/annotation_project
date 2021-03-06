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
package net.gleamynode.netty.handler.codec.replay;

import static net.gleamynode.netty.channel.Channels.*;

import java.net.SocketAddress;

import net.gleamynode.netty.buffer.ChannelBuffer;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.channel.ChannelPipelineCoverage;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.Channels;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.channel.SimpleChannelHandler;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
@ChannelPipelineCoverage("one")
public abstract class ReplayingDecoder<T extends Enum<T>> extends SimpleChannelHandler {

    private final ChannelBuffer cumulation = new UnsafeDynamicChannelBuffer(256);
    private final ReplayingDecoderBuffer replayable = new ReplayingDecoderBuffer(cumulation);
    private volatile T state;
    private volatile int checkpoint;

    protected ReplayingDecoder() {
        this(null);
    }

    protected ReplayingDecoder(T initialState) {
        this.state = initialState;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {

        Object m = e.getMessage();
        if (!(m instanceof ChannelBuffer)) {
            ctx.sendUpstream(e);
            return;
        }

        ChannelBuffer input = (ChannelBuffer) m;
        if (!input.readable()) {
            return;
        }

        cumulation.discardReadBytes();
        cumulation.writeBytes(input);
        callDecode(ctx, e.getChannel(), e.getRemoteAddress());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        cleanup(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        cleanup(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        ctx.sendUpstream(e);
    }

    private void callDecode(ChannelHandlerContext context, Channel channel, SocketAddress remoteAddress) throws Exception {
        while (cumulation.readable()) {
            int oldReaderIndex = checkpoint = cumulation.readerIndex();
            Object result = null;
            try {
                result = decode(context, channel, replayable, state);
                if (result == null) {
                    if (oldReaderIndex == cumulation.readerIndex()) {
                        throw new IllegalStateException(
                                "null cannot be returned if no data is consumed.");
                    } else {
                        // Previous data has been discarded.
                        // Probably it's reading on.
                        continue;
                    }
                }
            } catch (ReplayError replay) { // 注意这里：只有出现ReplyError时才会不断尝试。
                // Return to the checkpoint (or oldPosition) and retry.
                cumulation.readerIndex(checkpoint);
            }

            if (result == null) {
                // Seems like more data is required.
                // Let's wait for the next notification.
                break;
            }

            if (oldReaderIndex == cumulation.readerIndex()) {
                throw new IllegalStateException(
                        "decode() method must consume at least one byte "
                                + "if it returned a decoded message.");
            }

            // A successful decode
            Channels.fireMessageReceived(context, channel, result, remoteAddress);
        }
    }

    private void cleanup(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        try {
            if (cumulation.readable()) {
                // Make sure all data was read before notifying a closed channel.
                callDecode(ctx, e.getChannel(), null);
                if (cumulation.readable()) {
                    // and send the remainders too if necessary.
                    Object partiallyDecoded = decodeLast(ctx, e.getChannel(), cumulation, state);
                    if (partiallyDecoded != null) {
                        fireMessageReceived(ctx, e.getChannel(), partiallyDecoded, null);
                    }
                }
            }
        } catch (ReplayError replay) {
            // Ignore
        } finally {
            ctx.sendUpstream(e);
        }
    }

    protected void checkpoint() {
        checkpoint = cumulation.readerIndex();
    }

    protected void checkpoint(T state) {
        this.state = state;
        checkpoint = cumulation.readerIndex();
    }

    protected abstract Object decode(ChannelHandlerContext ctx,
            Channel channel, ChannelBuffer buffer, T state) throws Exception;

    protected Object decodeLast(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, T state) throws Exception {
        return decode(ctx, channel, buffer, state);
    }
}
