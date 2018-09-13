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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelHandler;
import net.gleamynode.netty.channel.ChannelPipeline;
import net.gleamynode.netty.channel.ChannelPipelineFactory;
import net.gleamynode.netty.channel.SimpleChannelHandler;
import net.gleamynode.netty.logging.Logger;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.uses net.gleamynode.netty.channel.ChannelFactory
 */
public class Bootstrap {

    private static Logger logger = Logger.getLogger(Bootstrap.class);

    private volatile ChannelFactory factory;
    private volatile ChannelPipeline pipeline = pipeline();
    private volatile ChannelPipelineFactory pipelineFactory = pipelineFactory(pipeline); // Event与Channel相关联，Channel又与Config相关联，而Config又与PipelineFactory相关联，
    // 而PipelineFactory又与Pipeline相关联，Bootstrap与PipelineFactory有关联。所以，在ServerBootstrap中可以得到pipeline。
    // 所以，ServerBootstrap#Bindder#channelOpen中可以有以下代码：evt.getChannel().getConfig().setPipelineFactory(getPipelineFactory());
    private volatile Map<String, Object> options = new HashMap<String, Object>();

    public Bootstrap() {
        super();
    }

    public Bootstrap(ChannelFactory channelFactory) {
        setFactory(channelFactory);
    }

    public ChannelFactory getFactory() {
        ChannelFactory factory = this.factory;
        if (factory == null) {
            throw new IllegalStateException(
                    "factory is not set yet.");
        }
        return factory;
    }

    public void setFactory(ChannelFactory factory) {
        if (this.factory != null) { // 只能设置一次
            throw new IllegalStateException(
                    "factory can't change once set.");
        }
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        this.factory = factory;
    }

    public ChannelPipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(ChannelPipeline pipeline) {
        if (pipeline == null) {
            throw new NullPointerException("pipeline");
        }
        pipeline = this.pipeline;
        pipelineFactory = pipelineFactory(pipeline);
    }

    public Map<String, ChannelHandler> getPipelineAsMap() {
        ChannelPipeline pipeline = this.pipeline;
        if (pipeline == null) {
            throw new IllegalStateException("pipelineFactory in use");
        }
        return pipeline.toMap();
    }

    public void setPipelineAsMap(Map<String, ChannelHandler> pipelineMap) {
        if (pipelineMap == null) {
            throw new NullPointerException("pipelineMap");
        }

        if (!isOrderedMap(pipelineMap)) {
            throw new IllegalArgumentException(
                    "pipelineMap is not an ordered map. " +
                    "Please use " +
                    LinkedHashMap.class.getName() + ".");
        }

        ChannelPipeline pipeline = pipeline();
        for(Map.Entry<String, ChannelHandler> e: pipelineMap.entrySet()) {
            pipeline.addLast(e.getKey(), e.getValue());
        }

        setPipeline(pipeline);
    }

    public ChannelPipelineFactory getPipelineFactory() {
        return pipelineFactory;
    }

    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
        if (pipelineFactory == null) {
            throw new NullPointerException("pipelineFactory");
        }
        pipeline = null;
        this.pipelineFactory = pipelineFactory;
    }

    public Map<String, Object> getOptions() {
        return new TreeMap<String, Object>(options);
    }

    public void setOptions(Map<String, Object> options) {
        if (options == null) {
            throw new NullPointerException("options");
        }
        this.options = new HashMap<String, Object>(options);
    }

    public Object getOption(String key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        return options.get(key);
    }

    public void setOption(String key, Object value) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (value == null) {
            options.remove(key);
        } else {
            options.put(key, value);
        }
    }

    private static boolean isOrderedMap(Map<String, ChannelHandler> map) { // 在高版本Netty中没有看到这个方法了。
        Class<Map<String, ChannelHandler>> mapType = getMapClass(map);
        if (LinkedHashMap.class.isAssignableFrom(mapType)) {
            /**
             * class1.isAssignableFrom(class2) 判定此 Class 对象所表示的类或接口与指定的 Class 参数所表示的类或接口是否相同，或是否是其超类或超接口。
             * 如果是则返回 true；否则返回 false。如果该 Class 表示一个基本类型，且指定的 Class 参数正是该 Class 对象，则该方法返回 true；否则返回 false。
             *
             * LinkedHashMap类及其子类都是有序的。
             */
            if (logger.isDebugEnabled()) {
                logger.debug(mapType.getSimpleName() + " is an ordered map.");
            }
            return true;
        }

        if (logger.isDebugEnabled()) {
            logger.debug(
                    mapType.getName() + " is not a " +
                    LinkedHashMap.class.getSimpleName());
        }

        // Detect Apache Commons Collections OrderedMap implementations.
        /**
         * 上面的LinkedHashMap是Java内置的类；
         * 接下来，检测是否实现了通用有序接口。
         */
        Class<?> type = mapType;
        while (type != null) {
            for (Class<?> i: type.getInterfaces()) {
                if (i.getName().endsWith("OrderedMap")) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                mapType.getSimpleName() +
                                " is an ordered map (guessed from that it " +
                                " implements OrderedMap interface.)");
                    }
                    return true;
                }
            }
            type = type.getSuperclass(); // 一层一层地往父类找。
        }

        if (logger.isDebugEnabled()) {
            logger.debug(
                    mapType.getName() +
                    " doesn't implement OrderedMap interface.");
        }

        // Last resort: try to create a new instance and test if it maintains
        // the insertion order.
        // 最后，尝试创建一些实例，看看其是否能保持插入顺序。
        logger.debug(
                "Last resort; trying to create a new map instance with a " +
                "default constructor and test if insertion order is " +
                "maintained.");

        Map<String, ChannelHandler> newMap;
        try {
            newMap = mapType.newInstance();
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Failed to create a new map instance of '" +
                        mapType.getName() +"'.", e);
            }
            return false;
        }

        Random rand = new Random();
        List<String> expectedNames = new ArrayList<String>(); // 保存输入顺序
        ChannelHandler dummyHandler = new SimpleChannelHandler();
        for (int i = 0; i < 65536; i ++) {
            String filterName;
            do {
                filterName = String.valueOf(rand.nextInt());
            } while (newMap.containsKey(filterName)); // 直到找到一个不存在的。

            newMap.put(filterName, dummyHandler);
            expectedNames.add(filterName);

            Iterator<String> it = expectedNames.iterator();
            for (Object key: newMap.keySet()) {
                /**
                 * 我感觉这里的想法好：就是比较newMap是否能保持插入顺序，但是，这里的实现却是错的。
                 * newMap.keySet()这个的结果顺序除TreeMap外没有规定，这里虽然会出现"不能保持插入顺序"的错误提示，
                 * 但是，也并不能说明newMap就是有序的。
                 */
                if (!it.next().equals(key)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "The specified map didn't pass the insertion " +
                                "order test after " + (i + 1) + " tries.");
                    }
                    return false;
                }
            }
        }

        logger.debug("The specified map passed the insertion order test.");
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, ChannelHandler>> getMapClass(
            Map<String, ChannelHandler> map) {
        return (Class<Map<String, ChannelHandler>>) map.getClass();
    }
}
