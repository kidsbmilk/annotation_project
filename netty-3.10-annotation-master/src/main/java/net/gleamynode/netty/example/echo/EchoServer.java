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
package net.gleamynode.netty.example.echo;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import net.gleamynode.netty.bootstrap.ServerBootstrap;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class EchoServer {

    public static void main(String[] args) throws Exception {
        // Start server.
        ChannelFactory factory =
            new NioServerSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        EchoHandler handler = new EchoHandler();

        bootstrap.getPipeline().addLast("handler", handler);
        /**
         * 在这里，这个是有用处，只不过在建立连接的前半段没用，前半段用的是ServerBootstrap#bind里的bossPipeline。
         * NioServerSocketPipelineSink#Boss里有后半段操作，在后半段，handler开始起作用了。
         */

        /**
         * 这里使用的是和EchoClient一样的handler，首先是客户端连接过来，然后客户端发送数据，之后服务器再返回同样的数据，在两边都统计速率。
         * 有点像乒乓球一样，打过来，回过去。
         */
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);

        bootstrap.bind(new InetSocketAddress(8080)); // 打开一个阻塞队列线程，监听端口等待客户端的连接到来。

        // Start performance monitor.
        new ThroughputMonitor(handler).start(); // 打开一个统计速率的线程。
    }
}
