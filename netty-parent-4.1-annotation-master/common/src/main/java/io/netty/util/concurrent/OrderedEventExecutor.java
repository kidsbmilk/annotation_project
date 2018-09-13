/*
 * Copyright 2016 The Netty Project
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
package io.netty.util.concurrent;

/**
 * Marker interface for {@link EventExecutor}s that will process all submitted tasks in an ordered / serial fashion.
 */
/**
 * 这个接口只起到标记作用，说明实现此接口的类都将以一种有序或者序列化的方式来处理所有提交的任务。
 *
 * 虽然OrderedEventExecutor里没有额外的方法，但是EventExecutor里已经有一些方法了。
 */
public interface OrderedEventExecutor extends EventExecutor {
}
