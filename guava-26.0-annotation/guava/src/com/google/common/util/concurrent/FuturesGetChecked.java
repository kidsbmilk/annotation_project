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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Thread.currentThread;
import static java.util.Arrays.asList;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.j2objc.annotations.J2ObjCIncompatible;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

/** Static methods used to implement {@link Futures#getChecked(Future, Class)}. */
@GwtIncompatible
final class FuturesGetChecked {
  @CanIgnoreReturnValue
  static <V, X extends Exception> V getChecked(Future<V> future, Class<X> exceptionClass) throws X {
    return getChecked(bestGetCheckedTypeValidator(), future, exceptionClass);
  }

  /** Implementation of {@link Futures#getChecked(Future, Class)}. */
  @CanIgnoreReturnValue
  @VisibleForTesting
  static <V, X extends Exception> V getChecked(
      GetCheckedTypeValidator validator, Future<V> future, Class<X> exceptionClass) throws X {
    validator.validateClass(exceptionClass);
    try {
      return future.get();
    } catch (InterruptedException e) {
      currentThread().interrupt(); // 重设中断标记
      throw newWithCause(exceptionClass, e);
    } catch (ExecutionException e) {
      wrapAndThrowExceptionOrError(e.getCause(), exceptionClass);
      throw new AssertionError(); // 这个应该是多余的，上面的wrapAndThrowExceptionOrError必定会抛出异常，不会执行这行代码的。
      // 但是，这行代码还不能删除，因为上面的wrapAndThrowExceptionOrError虽然内部抛出异常，但是其返回值为void，
      // 此catch里要有返回语句，所以插了一个AssertionError异常。可以注释掉AssertionError然后看看编译器提示什么。
    }
  }

  /** Implementation of {@link Futures#getChecked(Future, Class, long, TimeUnit)}. */
  @CanIgnoreReturnValue
  static <V, X extends Exception> V getChecked(
      Future<V> future, Class<X> exceptionClass, long timeout, TimeUnit unit) throws X {
    // TODO(cpovirk): benchmark a version of this method that accepts a GetCheckedTypeValidator
    bestGetCheckedTypeValidator().validateClass(exceptionClass);
    try {
      return future.get(timeout, unit);
    } catch (InterruptedException e) {
      currentThread().interrupt(); // 重设中断标记
      throw newWithCause(exceptionClass, e);
    } catch (TimeoutException e) {
      throw newWithCause(exceptionClass, e);
    } catch (ExecutionException e) {
      wrapAndThrowExceptionOrError(e.getCause(), exceptionClass);
      throw new AssertionError(); // 这个应该是多余的，上面的wrapAndThrowExceptionOrError必定会抛出异常，不会执行这行代码的。
      // 但是，这行代码还不能删除，因为上面的wrapAndThrowExceptionOrError虽然内部抛出异常，但是其返回值为void，
      // 此catch里要有返回语句，所以插了一个AssertionError异常。可以注释掉AssertionError然后看看编译器提示什么。
    }
  }

  @VisibleForTesting
  interface GetCheckedTypeValidator {
    void validateClass(Class<? extends Exception> exceptionClass);
  }

  private static GetCheckedTypeValidator bestGetCheckedTypeValidator() {
    return GetCheckedTypeValidatorHolder.BEST_VALIDATOR;
  }

  @VisibleForTesting
  static GetCheckedTypeValidator weakSetValidator() {
    // 这个方法好像已经不用了，用bestGetCheckedTypeValidator替代了，在bestGetCheckedTypeValidator里会先尝试获取，
    // GetCheckedTypeValidatorHolder.ClassValueValidator.INSTANCE，如果失败的话，就获取GetCheckedTypeValidatorHolder.WeakSetValidator.INSTANCE。
    return GetCheckedTypeValidatorHolder.WeakSetValidator.INSTANCE;
  }

  @J2ObjCIncompatible // ClassValue
  @VisibleForTesting
  static GetCheckedTypeValidator classValueValidator() {
    // 这个方法好像已经不用了，用bestGetCheckedTypeValidator替代了，在bestGetCheckedTypeValidator里会先尝试获取，
    // GetCheckedTypeValidatorHolder.ClassValueValidator.INSTANCE，如果失败的话，就获取GetCheckedTypeValidatorHolder.WeakSetValidator.INSTANCE。
    return GetCheckedTypeValidatorHolder.ClassValueValidator.INSTANCE;
  }

  /**
   * Provides a check of whether an exception type is valid for use with {@link
   * FuturesGetChecked#getChecked(Future, Class)}, possibly using caching.
   * 用于{@link FuturesGetChecked＃getChecked（Future，Class）}来检查异常类型是否有效，可能使用缓存。
   *
   * 这里的缓存，在ClassValueValidator是通过ClassValue来实现的;在WeakSetValidator是通过CopyOnWriteArraySet中添加已验证的异常来实现的。
   *
   * <p>Uses reflection to gracefully fall back to when certain implementations aren't available.
   * 当某些实现不可用时，使用反射优雅地回退。
   */
  @VisibleForTesting
  static class GetCheckedTypeValidatorHolder {
    static final String CLASS_VALUE_VALIDATOR_NAME =
        GetCheckedTypeValidatorHolder.class.getName() + "$ClassValueValidator"; // 这个是指GetCheckedTypeValidatorHolder.ClassValueValidator这个枚举类的完整类名。

    static final GetCheckedTypeValidator BEST_VALIDATOR = getBestValidator();

    @IgnoreJRERequirement // getChecked falls back to another implementation if necessary
    @J2ObjCIncompatible // ClassValue
    enum ClassValueValidator implements GetCheckedTypeValidator {
      INSTANCE;

      /*
       * Static final fields are presumed to be fastest, based on our experience with
       * UnsignedBytesBenchmark. TODO(cpovirk): benchmark this
       */
      private static final ClassValue<Boolean> isValidClass =
          new ClassValue<Boolean>() { // ClassValue in Java 7：https://stackoverflow.com/questions/7444420/classvalue-in-java-7，下面两个链接是对这个链接的翻译。
            // Java中的ClassValue 7：https://codeday.me/bug/20171023/88534.html
            // ClassValue Java 7中：https://stackoverrun.com/cn/q/1900196
            @Override
            protected Boolean computeValue(Class<?> type) {
              checkExceptionClassValidity(type.asSubclass(Exception.class));
              return true;
            }
          };

      @Override
      public void validateClass(Class<? extends Exception> exceptionClass) {
        isValidClass.get(exceptionClass); // throws if invalid; returns safely (and caches) if valid
      }
    }

    enum WeakSetValidator implements GetCheckedTypeValidator {
      INSTANCE;

      /*
       * Static final fields are presumed to be fastest, based on our experience with
       * UnsignedBytesBenchmark. TODO(cpovirk): benchmark this
       * 根据我们使用UnsignedBytesBenchmark的经验，静态常量字段被认为是最快的。 TODO（cpovirk）：基准测试
       */
      /*
       * A CopyOnWriteArraySet<WeakReference> is faster than a newSetFromMap of a MapMaker map with
       * weakKeys() and concurrencyLevel(1), even up to at least 12 cached exception types.
       * CopyOnWriteArraySet <WeakReference>比使用weakKeys（）和concurrencyLevel（1）的MapMaker映射的newSetFromMap更快，甚至至少12个缓存的异常类型。
       *
       * guava MapMaker使用示例：http://outofmemory.cn/code-snippet/13345/
       * CopyOnWriteArraySet是jdk里的，而MapMaker是guava提供的。
       */
      private static final Set<WeakReference<Class<? extends Exception>>> validClasses =
          new CopyOnWriteArraySet<>();

      @Override
      public void validateClass(Class<? extends Exception> exceptionClass) {
        for (WeakReference<Class<? extends Exception>> knownGood : validClasses) {
          if (exceptionClass.equals(knownGood.get())) {
            return;
          }
          // TODO(cpovirk): if reference has been cleared, remove it?
        }
        checkExceptionClassValidity(exceptionClass);

        /*
         * It's very unlikely that any loaded Futures class will see getChecked called with more
         * than a handful of exceptions. But it seems prudent to set a cap on how many we'll cache.
         * This avoids out-of-control memory consumption, and it keeps the cache from growing so
         * large that doing the lookup is noticeably slower than redoing the work would be.
         * 任何加载的Futures类都不太可能看到getChecked被调用超出少数常见的异常。 但是对于我们将缓存的数量设置上限似乎是明智的。
         * 这避免了失控的内存消耗，并且它使缓存不会变得如此之大以至于执行查找的速度明显慢于重做工作。
         *
         * Ideally we'd have a real eviction policy, but until we see a problem in practice, I hope
         * that this will suffice. I have not even benchmarked with different size limits.
         * 理想情况下，我们有一个真正的驱逐政策，但直到我们在实践中看到问题，我希望这就足够了。 我甚至没有基于不同大小限制的基准测试。
         */
        if (validClasses.size() > 1000) {
          validClasses.clear();
        }

        validClasses.add(new WeakReference<Class<? extends Exception>>(exceptionClass));
      }
    }

    /**
     * Returns the ClassValue-using validator, or falls back to the "weak Set" implementation if
     * unable to do so.
     * 返回使用ClassValue的验证程序，如果无法执行，则返回“弱集”实现。
     */
    static GetCheckedTypeValidator getBestValidator() {
      try {
        Class<?> theClass = Class.forName(CLASS_VALUE_VALIDATOR_NAME);
        return (GetCheckedTypeValidator) theClass.getEnumConstants()[0];
      } catch (Throwable t) { // ensure we really catch *everything*
        return weakSetValidator();
      }
    }
  }

  // TODO(cpovirk): change parameter order to match other helper methods (Class, Throwable)?
  // TODO（cpovirk）：更改参数顺序以匹配其他辅助方法（Class，Throwable）？
  private static <X extends Exception> void wrapAndThrowExceptionOrError(
      Throwable cause, Class<X> exceptionClass) throws X {
    if (cause instanceof Error) { // Error异常是非受检异常，不需要在方法上用throws来声明，对于Error异常，不应该在程序中捕获。
      throw new ExecutionError((Error) cause);
    }
    if (cause instanceof RuntimeException) { // 非受检异常，不需要在方法上用throws来声明，对于非受检异常可以捕获也可以不捕获。
      throw new UncheckedExecutionException(cause);
    }
    throw newWithCause(exceptionClass, cause);
  }

  /*
   * TODO(user): FutureChecker interface for these to be static methods on? If so, refer to it in
   * the (static-method) Futures.getChecked documentation
   * TODO（用户）：FutureChecker接口这些是静态方法吗？ 如果是这样，请在（静态方法）Futures.getChecked文档中引用它
   */

  private static boolean hasConstructorUsableByGetChecked(
      Class<? extends Exception> exceptionClass) {
    try {
      Exception unused = newWithCause(exceptionClass, new Exception());
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static <X extends Exception> X newWithCause(Class<X> exceptionClass, Throwable cause) {
    // getConstructors() guarantees this as long as we don't modify the array.
    // 只要我们不修改数组，getConstructors（）就会保证这一点。
    @SuppressWarnings({"unchecked", "rawtypes"})
    List<Constructor<X>> constructors = (List) Arrays.asList(exceptionClass.getConstructors());
    for (Constructor<X> constructor : preferringStrings(constructors)) {
      @Nullable X instance = newFromConstructor(constructor, cause);
      if (instance != null) {
        if (instance.getCause() == null) {
          instance.initCause(cause);
        }
        return instance;
      }
    }
    throw new IllegalArgumentException( // 非受检异常，不需要在方法上用throws来声明，对于非受检异常可以捕获也可以不捕获。
        "No appropriate constructor for exception of type "
            + exceptionClass
            + " in response to chained exception",
        cause);
  }

  private static <X extends Exception> List<Constructor<X>> preferringStrings(
      List<Constructor<X>> constructors) {
    return WITH_STRING_PARAM_FIRST.sortedCopy(constructors);
  }

  private static final Ordering<Constructor<?>> WITH_STRING_PARAM_FIRST =
      Ordering.natural()
          .onResultOf(
              new Function<Constructor<?>, Boolean>() {
                @Override
                public Boolean apply(Constructor<?> input) {
                  return asList(input.getParameterTypes()).contains(String.class);
                }
              })
          .reverse();

  private static <X> @Nullable X newFromConstructor(Constructor<X> constructor, Throwable cause) {
    Class<?>[] paramTypes = constructor.getParameterTypes();
    Object[] params = new Object[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      Class<?> paramType = paramTypes[i];
      if (paramType.equals(String.class)) {
        params[i] = cause.toString();
      } else if (paramType.equals(Throwable.class)) {
        params[i] = cause;
      } else {
        return null;
      }
    }
    try {
      return constructor.newInstance(params);
    } catch (IllegalArgumentException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException e) {
      return null;
    }
  }

  @VisibleForTesting
  static boolean isCheckedException(Class<? extends Exception> type) {
    return !RuntimeException.class.isAssignableFrom(type);
  }

  @VisibleForTesting
  static void checkExceptionClassValidity(Class<? extends Exception> exceptionClass) {
    checkArgument(
        isCheckedException(exceptionClass),
        "Futures.getChecked exception type (%s) must not be a RuntimeException",
        exceptionClass);
    checkArgument(
        hasConstructorUsableByGetChecked(exceptionClass),
        "Futures.getChecked exception type (%s) must be an accessible class with an accessible "
            + "constructor whose parameters (if any) must be of type String and/or Throwable",
        exceptionClass);
  }

  private FuturesGetChecked() {}
}
