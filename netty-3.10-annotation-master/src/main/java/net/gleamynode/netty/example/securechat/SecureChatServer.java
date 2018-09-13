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

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import net.gleamynode.netty.bootstrap.ServerBootstrap;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 * 这个例子如果想运行成功：需要修改java.security的配置文件，
 * 在Mac上路径为：/Library/Java/JavaVirtualMachines/jdk1.8.0_121.jdk/Contents/Home/jre/lib/security/java.security。
 * 将jdk.certpath.disabledAlgorithms=MD2, RSA keySize < 1024这行注释掉，
 * 原因在于：在java1.6之后的这个配置文件中，认为MD2的加密方式安全性太低，因而不支持这种加密方式，同时也不支持RSA长度小于1024的密文，因此注释掉此行代码。
 *
 * 参考链接：http://blog.csdn.net/applehepeach/article/details/50509782
 */
public class SecureChatServer {

    public static void main(String[] args) throws Exception {
        // Start server.
        ChannelFactory factory =
            new NioServerSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        SecureChatServerHandler handler = new SecureChatServerHandler();

        bootstrap.setPipelineFactory(new SecureChatPipelineFactory(handler));
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);

        bootstrap.bind(new InetSocketAddress(8080));
    }
}
