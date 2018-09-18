/*
 * Copyright (C) 2006 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.util.concurrent;

import com.google.common.annotations.GwtCompatible;

/**
 * Hidden superclass of {@link FluentFuture} that provides us a place to declare special GWT
 * versions of the {@link FluentFuture#catching(Class, com.google.common.base.Function)
 * FluentFuture.catching} family of methods. Those versions have slightly different signatures.
 *
 * 隐藏{@link FluentFuture}的父类，
 * 这个父类为我们提供了一个声明{@link FluentFuture #tracking（Class，com.google.common.base.Function）FluentFuture.catching}方法系列的特殊GWT版本的地方。
 * 这些版本的签名略有不同。
 *
 * 见下面的注释。
 */
@GwtCompatible(emulated = true)
abstract class GwtFluentFutureCatchingSpecialization<V> implements ListenableFuture<V> {
  /*
   * This server copy of the class is empty. The corresponding GWT copy contains alternative
   * versions of catching() and catchingAsync() with slightly different signatures from the ones
   * found in FluentFuture.java.
   *
   * 该类的服务器副本为空。 相应的GWT副本包含catch（）和catchAsync（）的替代版本，其签名与FluentFuture.java中的签名略有不同。
   *
   * 这种实现第一次见，真想看看是如何完成替换的。 TODO.
   */
}
