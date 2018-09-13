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
package io.netty.example.echo;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Handler implementation for the echo server.
 */
@Sharable
/**
 * Sharable注解主要是用来标示一个ChannelHandler可以被安全地共享，即可以在多个Channel的ChannelPipeline中使用同一个ChannelHandler，
 * 而不必每一个ChannelPipeline都重新new一个新的ChannelHandler。也就是说您的ChannelHandler是线程安全的。
 * 这种情况比如会用在统计整体的吞吐量的时候用到。
 */
public class EchoServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ctx.write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }
    /**
     * 重写ChannelHandler的exceptionCaught方法可以捕获服务器的异常，比如客户端连接服务器后强制关闭，
     * 服务器会抛出"客户端主机强制关闭错 误"，通过重写exceptionCaught方法就可以处理异常，
     * 比如发生异常后关闭ChannelHandlerContext。
     */
}
/**
 * Netty使用futures和回调概念，它的设计允许你处理不同的事件类型.
 * 你的channelhandler必须继承ChannelInboundHandlerAdapter并且重写channelRead方法，
 * 这个方法在任何时候都会被调用来接收数据，在这个例子中接收的是字节。
 *
 * Netty使用多个Channel Handler来达到对事件处理的分离，因为可以很容的添加、更新、删除业务逻辑处理handler。
 * Handler很简单，它的每个 方法都可以被重写，它的所有的方法中只有channelRead方法是必须要重写的。
 */