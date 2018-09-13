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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Echoes back any received data from a client.
 */

/**
 * 和客户端的代码相比, 没有很大的差别, 基本上也是进行了如下几个部分的初始化:

 EventLoopGroup: 不论是服务器端还是客户端, 都必须指定 EventLoopGroup. 在这个例子中, 指定了 NioEventLoopGroup,
 表示一个 NIO 的EventLoopGroup, 不过服务器端需要指定两个 EventLoopGroup, 一个是 bossGroup, 用于处理客户端的连接请求;
 另一个是 workerGroup, 用于处理与各个客户端连接的 IO 操作.
 ChannelType: 指定 Channel 的类型. 因为是服务器端, 因此使用了 NioServerSocketChannel.
 Handler: 设置数据的处理器.
 */
public final class EchoServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // acceptor线程池中线程数为1，
        // 其实服务器端的ServerSocketChannel只绑定到了bossGroup中的一个线程，因此在调用Java NIO的Selector.select处理客户端的连接请求时，
        // 实际上是在一个线程中的，所以对只有一个服务的应用来说，bossGroup设置多个线程是没有什么作用的，反而还会造成资源浪费。
        // the creator of Netty syas multiple boss threads are useful if we share NioEventLoopGroup between diffent server bootstraps,
        // but I don't see the reason for it.

        // https://segmentfault.com/a/1190000007403873，这里分析的非常好！！！

        EventLoopGroup workerGroup = new NioEventLoopGroup(); // 默认线程数在MultithreadEventLoopGroup.java中设置，这个是NIO线程池
        // 因为使用NIO，所以指定NioEventLoopGroup来接受和 处理新连接，指定通道类型为NioServerSocketChannel
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
                    /**
                     * 这个例子中使用NIO，因为它是目前最常用的传输方式，你可能会使用NIO很长时间，但是你可以选择不同的传输实现。
                     * 例如，这个例子使用 OIO方式传输，你需要指定OioServerSocketChannel。Netty框架中实现了多重传输方式，将再后面讲述。
                     */
             .option(ChannelOption.SO_BACKLOG, 100)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() { // 这是创建了一个匿名类对象。在建立连接完成、注册完成子连接后（见ChannelInitializer里的注释），
                 // 会把EchoServerHandler加入到新连接的pipeline里。同时，会把Initilizer本身从pipelie里移除。
                 // 对比ServerBootstrap#init里的代码注释。
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.alloc()));
                     }
                     //p.addLast(new LoggingHandler(LogLevel.INFO));
                     p.addLast(new EchoServerHandler());
                 }
             });
            /**
             * 调用childHandler放来指定连接后调用的ChannelHandler，
             * 这个方法传ChannelInitializer类型的参数，ChannelInitializer是个抽象类，
             * 所以需要实现initChannel方法，这个方法就是用来设置ChannelHandler。
             */

            // Start the server.
            ChannelFuture f = b.bind(PORT).sync();
            /**
             * 最后绑定服务器等待直到绑定完成，调用sync()方法会阻塞直到服务器完成绑定，
             * 然后服务器等待通道关闭，因为使用sync()，所以关闭操作也 会被阻塞。
             * 现在你可以关闭EventLoopGroup和释放所有资源，包括创建的线程。
             */

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
/**
 * 1、创建ServerBootstrap实例来引导绑定和启动服务器 创建NioEventLoopGroup对象来处理事件，如接受新连接、接收数据、写数据等等
 * 2、设置childHandler执行所有的连接请求
 * 3、都设置完毕后，调用ServerBootstrap.bind()方法来绑定服务器。
 */