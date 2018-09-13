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
package net.gleamynode.netty.bootstrap;

import static net.gleamynode.netty.channel.Channels.*;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelException;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelHandler;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.channel.ChannelPipeline;
import net.gleamynode.netty.channel.ChannelPipelineCoverage;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ChildChannelStateEvent;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.SimpleChannelHandler;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class ServerBootstrap extends Bootstrap {

    private volatile ChannelHandler parentHandler; // 不太明白这个parentHandler有什么用处 ?zz?
    // 在bind里，用到这个parentHandler了，是不是方便对数据做一些预处理或者是附加处理 ?zz?

    public ServerBootstrap() {
        super();
    }

    public ServerBootstrap(ChannelFactory channelFactory) {
        super(channelFactory);
    }

    public ChannelHandler getParentHandler() {
        return parentHandler;
    }

    public void setParentHandler(ChannelHandler parentHandler) {
        this.parentHandler = parentHandler;
    }

    public Channel bind() {
        SocketAddress localAddress = (SocketAddress) getOption("localAddress");
        if (localAddress == null) {
            throw new IllegalStateException("localAddress option is not set.");
        }
        return bind(localAddress);
    }

    public Channel bind(final SocketAddress localAddress) {
        final BlockingQueue<ChannelFuture> futureQueue =
            new LinkedBlockingQueue<ChannelFuture>(); // 这是一个阻塞队列。

        ChannelPipeline bossPipeline = pipeline(); // Bootstrap有一个pipeline成员，这里是又创建了一个临时使用的。
        /**
         * Bootstrap里的pipeline成员是默认初始化的，只要创建一个Bootstrap就会有一个默认创建好的pipeline成员。
         */
        bossPipeline.addLast("binder", new Binder(localAddress, futureQueue));

        ChannelHandler parentHandler = getParentHandler();
        if (parentHandler != null) {
            bossPipeline.addLast("userHandler", parentHandler);
        }

        Channel channel = getFactory().newChannel(bossPipeline); // 这里传入的是临时的bossPipeline，并不是Bootstrap中的pipeline成员。
        /**
         * 对于EchoServer.java中，这个getFactory()得到的是NioServerSocketChannelFactory。
         */
        // 这个newChannel最终会导致阻塞队列运行，监听端口，等待客户端的连接到来。

        // 下面的流程与ClientBootstrap里的类似。

        // Wait until the future is available.
        /**
         * 上面的newChannel会引发一系列的fireChannelOpen操作，会调用Binder#channelOpen方法，在Binder#channelOpen中会引发bind操作，当然，这个bind操作
         * 是封装在channel里的，只会返回一个future，用于将来异步执行bind绑定完成后的操作。
         */
        ChannelFuture future = null;
        do {
            try {
                future = futureQueue.poll(Integer.MAX_VALUE, TimeUnit.SECONDS); // 在Binder#channelOpen执行后，这里就可以得到一个future了。
            } catch (InterruptedException e) {
                // Ignore
            }
        } while (future == null); // 注意：这里并不是阻塞等待客户端的连接到来，这里仅仅是获取一个用于将来取出连接完成信息的Future对象。

        // Wait for the future.
        future.awaitUninterruptibly();
        if (!future.isSuccess()) { // 这个future.isSuccess()判断的是bind事件是否操作成功，
            // 只要NioServerSocketPipelineSink#bind中的channel.socket.socket().bind(localAddress, channel.getConfig().getBacklog())执行不出错，
            // 这个future就已经成功了。
            future.getChannel().close().awaitUninterruptibly();
            throw new ChannelException("Failed to bind to: " + localAddress, future.getCause());
        }

        return channel;
    }

    @ChannelPipelineCoverage("one")
    private final class Binder extends SimpleChannelHandler {

        private final SocketAddress localAddress;
        private final BlockingQueue<ChannelFuture> futureQueue;
        private final Map<String, Object> childOptions =
            new HashMap<String, Object>();

        Binder(SocketAddress localAddress, BlockingQueue<ChannelFuture> futureQueue) {
            this.localAddress = localAddress;
            this.futureQueue = futureQueue;
        }

        @Override
        public void channelOpen(
                ChannelHandlerContext ctx,
                ChannelStateEvent evt) {
            evt.getChannel().getConfig().setPipelineFactory(getPipelineFactory());
            // Event与Channel相关联，Channel又与Config相关联，而Config又与PipelineFactory相关联，
            // 而PipelineFactory又与Pipeline相关联，Bootstrap与PipelineFactory有关联。所以，在ServerBootstrap中可以得到pipeline。
            // 所以，ServerBootstrap#Bindder#channelOpen中可以有以下代码：evt.getChannel().getConfig().setPipelineFactory(getPipelineFactory());
            // 见：NioServerSocketPipelineSink#Boss#run里的代码：ChannelPipeline pipeline = channel.getConfig().getPipelineFactory().getPipeline();

            // Split options into two categories: parent and child.
            Map<String, Object> allOptions = getOptions();
            Map<String, Object> parentOptions = new HashMap<String, Object>();
            for (Entry<String, Object> e: allOptions.entrySet()) {
                if (e.getKey().startsWith("child.")) {
                    childOptions.put(
                            e.getKey().substring(6),
                            e.getValue());
                } else if (!e.getKey().equals("pipelineFactory")) {
                    parentOptions.put(e.getKey(), e.getValue());
                }
            }

            // Apply parent options.
            evt.getChannel().getConfig().setOptions(parentOptions);

            futureQueue.offer(evt.getChannel().bind(localAddress)); // 实际完成绑定的代码。
            ctx.sendUpstream(evt);
        }

        @Override
        public void childChannelOpen(
                ChannelHandlerContext ctx,
                ChildChannelStateEvent e) throws Exception {
            // Apply child options.
            e.getChildChannel().getConfig().setOptions(childOptions);
            ctx.sendUpstream(e);
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            ctx.sendUpstream(e);
        }
    }
}
