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
package net.gleamynode.netty.example.securechat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import net.gleamynode.netty.bootstrap.ClientBootstrap;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.socket.nio.NioClientSocketChannelFactory;

/**
 * 这个例子如果想运行成功：需要修改java.security的配置文件，
 * 在Mac上路径为：/Library/Java/JavaVirtualMachines/jdk1.8.0_121.jdk/Contents/Home/jre/lib/security/java.security。
 * 将jdk.certpath.disabledAlgorithms=MD2, RSA keySize < 1024这行注释掉，
 * 原因在于：在java1.6之后的这个配置文件中，认为MD2的加密方式安全性太低，因而不支持这种加密方式，同时也不支持RSA长度小于1024的密文，因此注释掉此行代码。
 *
 * 参考链接：http://blog.csdn.net/applehepeach/article/details/50509782
 */
public class SecureChatClient {

    public static void main(String[] args) throws Exception {
        // Print usage if no argument is specified.
        if (args.length != 2) {
            System.err.println(
                    "Usage: " + SecureChatClient.class.getSimpleName() +
                    " <host> <port>");
            return;
        }

        // Parse options.
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        // Start client.
        ChannelFactory factory =
            new NioClientSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

        ClientBootstrap bootstrap = new ClientBootstrap(factory);
        SecureChatClientHandler handler = new SecureChatClientHandler();

        bootstrap.setPipelineFactory(new SecureChatPipelineFactory(handler));
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("keepAlive", true);

        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));

        // Wait until the connection attempt succeeds or fails.
        Channel channel = future.awaitUninterruptibly().getChannel();
        if (!future.isSuccess()) {
            future.getCause().printStackTrace();
            System.exit(0);
        }

        // Read commands from the stdin.
        ChannelFuture lastWriteFuture = null;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        for (;;) {
            String line = in.readLine();
            if (line == null) {
                break;
            }
            lastWriteFuture = channel.write(line + '\n');
        }

        // Wait until all messages are flushed before closing the channel.
        if (lastWriteFuture != null) {
            lastWriteFuture.awaitUninterruptibly();
        }

        channel.close().awaitUninterruptibly();

        // We should shut down all thread pools here to exit normally.
        // However, it's just fine to call System.exit(0) because we are
        // finished with the business.
        System.exit(0);
    }
}
