# Netty Project

Netty is an asynchronous event-driven network application framework for rapid development of maintainable high performance protocol servers & clients.

## Links

* [Web Site](http://netty.io/)
* [Downloads](http://netty.io/downloads.html)
* [Documentation](http://netty.io/wiki/)
* [@netty_project](https://twitter.com/netty_project)

## How to build

For the detailed information about building and developing Netty, please visit [the developer guide](http://netty.io/wiki/developer-guide.html).  This page only gives very basic information.

You require the following to build Netty:

* Latest stable [Oracle JDK 7](http://www.oracle.com/technetwork/java/)
* Latest stable [Apache Maven](http://maven.apache.org/)
* If you are on Linux, you need [additional development packages](http://netty.io/wiki/native-transports.html) installed on your system, because you'll build the native transport.

Note that this is build-time requirement.  JDK 5 (for 3.x) or 6 (for 4.0+) is enough to run your Netty-based application.

## Branches to look

Development of all versions takes place in each branch whose name is identical to `<majorVersion>.<minorVersion>`.  For example, the development of 3.9 and 4.0 resides in [the branch '3.9'](https://github.com/netty/netty/tree/3.9) and [the branch '4.0'](https://github.com/netty/netty/tree/4.0) respectively.


### *一些比较好的博客：

<br>[netty5学习笔记-内存池1-PoolChunk](http://blog.csdn.net/youaremoon/article/details/47910971)
<br>[netty5学习笔记-内存池2-PoolSubpage](http://blog.csdn.net/youaremoon/article/details/47984409)
<br>[netty5学习笔记-内存池3-PoolChunkList](http://blog.csdn.net/youaremoon/article/details/48085591)
<br>[netty5学习笔记-内存池4-PoolArena](http://blog.csdn.net/youaremoon/article/details/48184429)
<br>[netty5学习笔记-内存池5-PoolThreadCache](http://blog.csdn.net/youaremoon/article/details/50042373)
<br>[netty5笔记-总体流程分析1-ServerBootstrap启动](http://blog.csdn.net/youaremoon/article/details/50371221)
<br>[Netty轻量级对象池实现分析](https://www.cnblogs.com/aeexiaoqiang/p/6526021.html)
<br>[【转】Netty那点事（二）Netty中的buffer](https://www.cnblogs.com/chenmo-xpw/p/3938107.html)
<br>[Netty精粹之设计更快的ThreadLocal](https://my.oschina.net/andylucc/blog/614359)
<br>[源码之下无秘密 ── 做最好的 Netty 源码分析教程](https://segmentfault.com/a/1190000007282628)
<br>[netty5笔记-线程模型1-Promise](http://blog.csdn.net/youaremoon/article/details/50279965)
<br>[netty5笔记-总体流程分析1-ServerBootstrap启动](http://blog.csdn.net/youaremoon/article/details/50371221)
<br>[netty5笔记-总体流程分析2-ChannelPipeline](http://blog.csdn.net/youaremoon/article/details/50414114)
<br>[netty5笔记-总体流程分析3-ChannelHandlerContext](http://blog.csdn.net/youaremoon/article/details/50449867)
<br>[netty5笔记-总体流程分析3-NioServerSocketChannel](http://blog.csdn.net/youaremoon/article/details/50488092)
<br>[netty5笔记-总体流程分析4-NioSocketChannel之服务端视角](http://blog.csdn.net/youaremoon/article/details/50509563)
<br>[netty5笔记-总体流程分析5-客户端连接过程](http://blog.csdn.net/youaremoon/article/details/50538666)
<br>[Netty4 学习笔记之四: Netty HTTP服务的实现](http://blog.csdn.net/qazwsxpcm/article/details/78364023)