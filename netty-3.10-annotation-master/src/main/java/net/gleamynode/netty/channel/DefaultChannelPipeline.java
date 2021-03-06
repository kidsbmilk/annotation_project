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

import static net.gleamynode.netty.channel.ChannelPipelineCoverage.*;

import java.lang.annotation.AnnotationFormatError;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import net.gleamynode.netty.logging.Logger;


public class DefaultChannelPipeline implements ChannelPipeline {

    static final Logger logger = Logger.getLogger(DefaultChannelPipeline.class);

    private final ChannelSink discardingSink = new ChannelSink() { // 这个处理池会丢弃任何事件。
        public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) {
            logger.warn("Not attached yet; discarding: " + e);
        }

        public void exceptionCaught(ChannelPipeline pipeline,
                ChannelEvent e, ChannelPipelineException cause) throws Exception {
            throw cause;
        }
    };

    private volatile Channel channel;
    private volatile ChannelSink sink;
    private volatile DefaultChannelHandlerContext head;
    private volatile DefaultChannelHandlerContext tail;
    private final Map<String, DefaultChannelHandlerContext> name2ctx =
        new HashMap<String, DefaultChannelHandlerContext>(4);

    public Channel getChannel() {
        return channel;
    }

    public ChannelSink getSink() {
        ChannelSink sink = this.sink;
        if (sink == null) { // 这个处理池会丢弃任何事件。
            return discardingSink;
        }
        return sink;
    }

    public void attach(Channel channel, ChannelSink sink) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (sink == null) {
            throw new NullPointerException("sink");
        }
        if (this.channel != null || this.sink != null) {
            throw new IllegalStateException("attached already");
        }
        this.channel = channel;
        this.sink = sink;
    }

    public synchronized void addFirst(String name, ChannelHandler handler) {
        if (name2ctx.isEmpty()) {
            init(name, handler); // 初始化
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext oldHead = head;
            DefaultChannelHandlerContext newHead = new DefaultChannelHandlerContext(null, oldHead, name, handler);
            oldHead.prev = newHead;
            head = newHead;
            name2ctx.put(name, newHead);
        }
    }

    public synchronized void addLast(String name, ChannelHandler handler) {
        if (name2ctx.isEmpty()) {
            init(name, handler); // 初始化
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext oldTail = tail;
            DefaultChannelHandlerContext newTail = new DefaultChannelHandlerContext(oldTail, null, name, handler);
            oldTail.next = newTail;
            tail = newTail;
            name2ctx.put(name, newTail);
        }
    }

    public synchronized void addBefore(String baseName, String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = getContextOrDie(baseName);
        if (ctx == head) {
            addFirst(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(ctx.prev, ctx, name, handler);
            ctx.prev.next = newCtx;
            ctx.prev = newCtx;
            name2ctx.put(name, newCtx);
        }
    }

    public synchronized void addAfter(String baseName, String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = getContextOrDie(baseName);
        if (ctx == tail) {
            addLast(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(ctx, ctx.next, name, handler);
            ctx.next.prev = newCtx;
            ctx.next = newCtx;
            name2ctx.put(name, newCtx);
        }
    }

    public synchronized void remove(ChannelHandler handler) {
        remove(getContextOrDie(handler));
    }

    public synchronized ChannelHandler remove(String name) {
        return remove(getContextOrDie(name)).getHandler();
    }

    @SuppressWarnings("unchecked")
    public synchronized <T extends ChannelHandler> T remove(Class<T> handlerType) {
        return (T) remove(getContextOrDie(handlerType)).getHandler();
    }

    private DefaultChannelHandlerContext remove(DefaultChannelHandlerContext ctx) {
        if (head == tail) {
            head = tail = null;
            name2ctx.clear();
        } else if (ctx == head) {
            removeFirst();
        } else if (ctx == tail) {
            removeLast();
        } else {
            DefaultChannelHandlerContext prev = ctx.prev;
            DefaultChannelHandlerContext next = ctx.next;
            prev.next = next;
            next.prev = prev;
            name2ctx.remove(ctx.getName());
        }
        return ctx;
    }

    public synchronized ChannelHandler removeFirst() {
        if (name2ctx.isEmpty()) {
            throw new NoSuchElementException();
        }

        DefaultChannelHandlerContext oldHead = head;
        oldHead.next.prev = null;
        head = oldHead.next;
        name2ctx.remove(oldHead.getName());
        return oldHead.getHandler();
    }

    public synchronized ChannelHandler removeLast() {
        if (name2ctx.isEmpty()) {
            throw new NoSuchElementException();
        }

        DefaultChannelHandlerContext oldTail = tail;
        oldTail.prev.next = null;
        tail = oldTail.prev;
        name2ctx.remove(oldTail.getName());
        return oldTail.getHandler();
    }

    public synchronized void replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(getContextOrDie(oldHandler), newName, newHandler);
    }

    public synchronized ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return replace(getContextOrDie(oldName), newName, newHandler);
    }

    @SuppressWarnings("unchecked")
    public synchronized <T extends ChannelHandler> T replace(
            Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return (T) replace(getContextOrDie(oldHandlerType), newName, newHandler);
    }

    private ChannelHandler replace(DefaultChannelHandlerContext ctx, String newName, ChannelHandler newHandler) {
        if (ctx == head) {
            removeFirst();
            addFirst(newName, newHandler);
        } else if (ctx == tail) {
            removeLast();
            addLast(newName, newHandler);
        } else {
            boolean sameName = ctx.getName().equals(newName);
            if (!sameName) {
                checkDuplicateName(newName);
            }
            DefaultChannelHandlerContext prev = ctx.prev;
            DefaultChannelHandlerContext next = ctx.next;
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(prev, next, newName, newHandler);
            prev.next = newCtx;
            next.prev = newCtx;
            if (!sameName) {
                name2ctx.remove(ctx.getName());
                name2ctx.put(newName, newCtx);
            } // 这里有小bug，如果新旧的名字相同，则还是需要将name2ctx里的替换为新的ctx。 ?zz?
        }
        return ctx.getHandler();
    }

    public synchronized ChannelHandler getFirst() {
        return head.getHandler();
    }

    public synchronized ChannelHandler getLast() {
        return tail.getHandler();
    }

    public synchronized ChannelHandler get(String name) {
        DefaultChannelHandlerContext ctx = name2ctx.get(name);
        if (ctx == null) {
            return null;
        } else {
            return ctx.getHandler();
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized <T extends ChannelHandler> T get(Class<T> handlerType) {
        ChannelHandlerContext ctx = getContext(handlerType);
        if (ctx == null) {
            return null;
        } else {
            return (T) ctx.getHandler();
        }
    }

    public synchronized ChannelHandlerContext getContext(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return name2ctx.get(name);
    }

    public synchronized ChannelHandlerContext getContext(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        if (name2ctx.isEmpty()) {
            return null;
        }
        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            if (ctx.getHandler() == handler) {
                return ctx;
            }

            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
        }
        return null;
    }

    public synchronized ChannelHandlerContext getContext(
            Class<? extends ChannelHandler> handlerType) {
        if (name2ctx.isEmpty()) {
            return null;
        }
        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            if (handlerType.isAssignableFrom(ctx.getHandler().getClass())) { // 判断当前的handlerType是否是ctx.getHandler().getClass()的父类或者相同类。
                return ctx;
            }

            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
        }
        return null;
    }

    public Map<String, ChannelHandler> toMap() {
        Map<String, ChannelHandler> map = new LinkedHashMap<String, ChannelHandler>();
        if (name2ctx.isEmpty()) {
            return map;
        }

        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            map.put(ctx.getName(), ctx.getHandler());
            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
        }
        return map;
    }

    /**
     * up是从head沿着next往后面执行。
     */
    public void sendUpstream(ChannelEvent e) {
        DefaultChannelHandlerContext head = getActualUpstreamContext(this.head);
        if (head == null) {
            logger.warn(
                    "The pipeline contains no upstream handlers; discarding: " + e);
            return;
        }

        sendUpstream(head, e);
    }

    void sendUpstream(DefaultChannelHandlerContext ctx, ChannelEvent e) {
        try {
            ((ChannelUpstreamHandler) ctx.getHandler()).handleUpstream(ctx, e);
        } catch (Throwable t) {
            notifyException(e, t);
        }
    }

    /**
     * down是从tail沿着prev往前面执行。
     */
    public void sendDownstream(ChannelEvent e) {
        DefaultChannelHandlerContext tail = getActualDownstreamContext(this.tail);
        if (tail == null) {
            try {
                getSink().eventSunk(this, e);  // 如果前面没有的话，则交给池Sink来处理事件，算是事件的最终处理者，比如bind，connect等等。
                return;
            } catch (Throwable t) {
                notifyException(e, t);
            }
        }

        sendDownstream(tail, e);
    }

    void sendDownstream(DefaultChannelHandlerContext ctx, ChannelEvent e) {
        try {
            ((ChannelDownstreamHandler) ctx.getHandler()).handleDownstream(ctx, e);
        } catch (Throwable t) {
            notifyException(e, t);
        }
    }

    DefaultChannelHandlerContext getActualUpstreamContext(DefaultChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }

        DefaultChannelHandlerContext realCtx = ctx;
        while (!realCtx.canHandleUpstream()) {
            realCtx = realCtx.next;
            if (realCtx == null) {
                return null;
            }
        }

        return realCtx;
    }

    DefaultChannelHandlerContext getActualDownstreamContext(DefaultChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }

        DefaultChannelHandlerContext realCtx = ctx;
        while (!realCtx.canHandleDownstream()) {
            realCtx = realCtx.prev;
            if (realCtx == null) {
                return null;
            }
        }

        return realCtx;
    }

    void notifyException(ChannelEvent e, Throwable t) {
        ChannelPipelineException pe;
        if (t instanceof ChannelPipelineException) {
            pe = (ChannelPipelineException) t;
        } else {
            pe = new ChannelPipelineException(t);
        }

        try {
            sink.exceptionCaught(this, e, pe);
        } catch (Exception e1) {
            logger.warn("An exception was thrown by an exception handler.", e1);
        }
    }

    private void init(String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = new DefaultChannelHandlerContext(null, null, name, handler);
        head = tail = ctx;
        name2ctx.clear();
        name2ctx.put(name, ctx);
    }

    private void checkDuplicateName(String name) {
        if (name2ctx.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate handler name.");
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(String name) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) getContext(name);
        if (ctx == null) {
            throw new NoSuchElementException(name);
        } else {
            return ctx;
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) getContext(handler);
        if (ctx == null) {
            throw new NoSuchElementException(handler.getClass().getName());
        } else {
            return ctx;
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(Class<? extends ChannelHandler> handlerType) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) getContext(handlerType);
        if (ctx == null) {
            throw new NoSuchElementException(handlerType.getName());
        } else {
            return ctx;
        }
    }

    private class DefaultChannelHandlerContext implements ChannelHandlerContext {
        volatile DefaultChannelHandlerContext next;
        volatile DefaultChannelHandlerContext prev;
        private final String name;
        private final ChannelHandler handler;
        private final boolean canHandleUpstream;
        private final boolean canHandleDownstream;

        DefaultChannelHandlerContext(
                DefaultChannelHandlerContext prev, DefaultChannelHandlerContext next,
                String name, ChannelHandler handler) {

            if (name == null) {
                throw new NullPointerException("name");
            }
            if (handler == null) {
                throw new NullPointerException("handler");
            }
            canHandleUpstream = handler instanceof ChannelUpstreamHandler;
            canHandleDownstream = handler instanceof ChannelDownstreamHandler;


            if (!canHandleUpstream && !canHandleDownstream) {
                throw new IllegalArgumentException(
                        "handler must be either " +
                        ChannelUpstreamHandler.class.getName() + " or " +
                        ChannelDownstreamHandler.class.getName() + '.');
            }


            ChannelPipelineCoverage coverage = handler.getClass().getAnnotation(ChannelPipelineCoverage.class); // 这个标记用来判断一个handler是否可以在多个pipeline中共享。
            if (coverage == null) {
                logger.warn(
                        "Handler '" + handler.getClass().getName() +
                        "' doesn't have a '" +
                        ChannelPipelineCoverage.class.getSimpleName() +
                        "' annotation with its class declaration. " +
                        "It is recommended to add the annotation to tell if " +
                        "one handler instance can handle more than one pipeline " +
                        "(\"" + ALL + "\") or not (\"" + ONE + "\")");
            } else {
                String coverageValue = coverage.value();
                if (coverageValue == null) {
                    throw new AnnotationFormatError(
                            ChannelPipelineCoverage.class.getSimpleName() +
                            " annotation value is undefined for type: " +
                            handler.getClass().getName());
                }


                if (!coverageValue.equals(ALL) && !coverageValue.equals(ONE)) {
                    throw new AnnotationFormatError(
                            ChannelPipelineCoverage.class.getSimpleName() +
                            " annotation value: " + coverageValue +
                            " (must be either \"" + ALL + "\" or \"" + ONE + ")");
                }
            }

            this.prev = prev;
            this.next = next;
            this.name = name;
            this.handler = handler;
        }

        public ChannelPipeline getPipeline() {
            return DefaultChannelPipeline.this; // 注意这个用法！！！因为DefaultChannelHandlerContext是DefaultChannelPipeline私有内部类，
            // 所以在DefaultChannelHandlerContext中为了得到当前的DefaultChannelPipeline对象，就得写成这个样子。
        }

        public boolean canHandleDownstream() {
            return canHandleDownstream;
        }

        public boolean canHandleUpstream() {
            return canHandleUpstream;
        }

        public ChannelHandler getHandler() {
            return handler;
        }

        public String getName() {
            return name;
        }

        public void sendDownstream(ChannelEvent e) { // Down对应的是前面的prev。
            DefaultChannelHandlerContext prev = getActualDownstreamContext(this.prev);
            if (prev == null) { // 如果前面没有的话，则交给池Sink来处理事件，算是事件的最终处理者，比如bind，connect等等。
                try {
                    getSink().eventSunk(DefaultChannelPipeline.this, e);
                } catch (Throwable t) {
                    notifyException(e, t);
                }
            } else { // 如果前面有的话，则交给前面的处理。
                DefaultChannelPipeline.this.sendDownstream(prev, e);
            }
        }

        public void sendUpstream(ChannelEvent e) { // Up对应的是后面的next。
            // 注意：这个是DefaultChannelHandlerContext里的。
            DefaultChannelHandlerContext next = getActualUpstreamContext(this.next);
            if (next != null) { // 如果后面没有了，则结束，如果有，则调用next来处理。
                DefaultChannelPipeline.this.sendUpstream(next, e);
            }
        }
    }
}