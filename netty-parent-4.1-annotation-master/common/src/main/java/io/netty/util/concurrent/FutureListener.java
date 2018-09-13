/*
 * Copyright 2013 The Netty Project
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
 * A subtype of {@link GenericFutureListener} that hides type parameter for convenience.
 * <pre>
 * Future f = new DefaultPromise(..);
 * f.addListener(new FutureListener() {
 *     public void operationComplete(Future f) { .. }
 * });
 * </pre>
 *
 * 上面注释说隐藏了类型信息、更加方便，体现在哪里呢，有点不太明白 ?zz?
 */

/**
 * GenericFutureListener接口中声明了operationComplete方法。
 */
public interface FutureListener<V> extends GenericFutureListener<Future<V>> { }
