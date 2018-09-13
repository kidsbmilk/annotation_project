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
package io.netty.util;

/**
 * Holds {@link Attribute}s which can be accessed via {@link AttributeKey}.
 *
 * Implementations must be Thread-safe.
 */

/**
 * 仔细看这里的英文注释。
 */
public interface AttributeMap { // 因为它的两个方法的返回类型都是由方法的传入类型决定的，所以，这里不需要提前声明泛型类型
    /**
     * Get the {@link Attribute} for the given {@link AttributeKey}. This method will never return null, but may return
     * an {@link Attribute} which does not have a value set yet.
     */
    <T> Attribute<T> attr(AttributeKey<T> key); // 这个方法的返回值的类型可以在调用方法时根据传入的对象类型进行设定

    /**
     * Returns {@code} true if and only if the given {@link Attribute} exists in this {@link AttributeMap}.
     */
    <T> boolean hasAttr(AttributeKey<T> key); // 这个方法的返回值的类型可以在调用方法时根据传入的对象类型进行设定
}
