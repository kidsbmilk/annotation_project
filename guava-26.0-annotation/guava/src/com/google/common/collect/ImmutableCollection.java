/*
 * Copyright (C) 2008 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.collect;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtCompatible;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A {@link Collection} whose contents will never change, and which offers a few additional
 * guarantees detailed below.
 * 一个{@link Collection}，其内容永远不会改变，并提供下面详述的一些额外保证。
 *
 * <p><b>Warning:</b> avoid <i>direct</i> usage of {@link ImmutableCollection} as a type (just as
 * with {@link Collection} itself). Prefer subtypes such as {@link ImmutableSet} or {@link
 * ImmutableList}, which have well-defined {@link #equals} semantics, thus avoiding a common source
 * of bugs and confusion.
 * <p> <b>警告：</ b>避免<i>直接</ i>使用{@link ImmutableCollection}作为类型（就像{@link Collection}本身一样）。
 * 优先选择{@link ImmutableSet}或{@link ImmutableList}等子类型，它们具有明确定义的{@link #equals}语义，从而避免了常见的错误和混淆源。
 *
 * <h3>About <i>all</i> {@code Immutable-} collections</h3>
 * <h3>关于<i>所有</ i> {@code Immutable-}集合</ h3>
 *
 * <p>The remainder of this documentation applies to every public {@code Immutable-} type in this
 * package, whether it is a subtype of {@code ImmutableCollection} or not.
 * <p>本文档的其余部分适用于此程序包中的每个公共{@code Immutable-}类型，无论它是否是{@code ImmutableCollection}的子类型。
 *
 * <h4>Guarantees</h4>
 * <H4>保证</ H4>
 *
 * <p>Each makes the following guarantees:
 * <p>每一个都做出以下保证：
 *
 * <ul>
 *   <li><b>Shallow immutability.</b> Elements can never be added, removed or replaced in this
 *       collection. This is a stronger guarantee than that of {@link
 *       Collections#unmodifiableCollection}, whose contents change whenever the wrapped collection
 *       is modified.
 *       <b>浅不变性。</ b>此集合中永远不能添加，删除或替换元素。 这样的保证比{@link Collections#unmodifiableCollection}的更加强，
 *       因为对于后者来说，如果内部包装的集合改变了，其内容依然会改变。
 *   <li><b>Null-hostility.</b> This collection will never contain a null element.
 *       <b> Null-hostility。</ b>此集合永远不会包含null元素。
 *   <li><b>Deterministic iteration.</b> The iteration order is always well-defined, depending on
 *       how the collection was created. Typically this is insertion order unless an explicit
 *       ordering is otherwise specified (e.g. {@link ImmutableSortedSet#naturalOrder}). See the
 *       appropriate factory method for details. View collections such as {@link
 *       ImmutableMultiset#elementSet} iterate in the same order as the parent, except as noted.
 *       <b>确定性迭代。</ b>迭代顺序始终是明确定义的，具体取决于集合的创建方式。 通常这是插入顺序，
 *       除非另外指定了显式排序（例如{@link ImmutableSortedSet #naturalOrder}）。
 *       有关详细信息，请参阅相应的工厂方法，除非另有说明，否则查看{@link ImmutableMultiset＃elementSet}等集合的迭代次序与父级相同。
 *   <li><b>Thread safety.</b> It is safe to access this collection concurrently from multiple
 *       threads.
 *       <b>线程安全。</ b>从多个线程同时访问此集合是安全的。
 *   <li><b>Integrity.</b> This type cannot be subclassed outside this package (which would allow
 *       these guarantees to be violated).
 *       <b>完整性。</ b>此类型不能在此包之外进行子类化（这将允许违反这些保证）。
 * </ul>
 *
 * <h4>"Interfaces", not implementations</h4>
 * <h4>“接口”，而非实现</ h4>
 *
 * <p>These are classes instead of interfaces to prevent external subtyping, but should be thought
 * of as interfaces in every important sense. Each public class such as {@link ImmutableSet} is a
 * <i>type</i> offering meaningful behavioral guarantees. This is substantially different from the
 * case of (say) {@link HashSet}, which is an <i>implementation</i>, with semantics that were
 * largely defined by its supertype.
 * <p>这些是类而不是接口，用于防止外部子类型，但应该被视为每个重要意义上的接口。 每个公共类，如{@link ImmutableSet}都是<i>类型</ i>，提供有意义的行为保证。
 * 这与（例如）{@link HashSet}的情况大不相同，后者是<i>实现</ i>，其语义主要由其超类型定义。
 *
 * <p>For field types and method return types, you should generally use the immutable type (such as
 * {@link ImmutableList}) instead of the general collection interface type (such as {@link List}).
 * This communicates to your callers all of the semantic guarantees listed above, which is almost
 * always very useful information.
 * <p>对于字段类型和方法返回类型，通常应使用不可变类型（例如{@link ImmutableList}）而不是通用集合接口类型（例如{@link List}）。
 * 这会向您的调用者传达上面列出的所有语义保证，这几乎总是非常有用的信息。
 *
 * <p>On the other hand, a <i>parameter</i> type of {@link ImmutableList} is generally a nuisance to
 * callers. Instead, accept {@link Iterable} and have your method or constructor body pass it to the
 * appropriate {@code copyOf} method itself.
 * <p>另一方面，{@link ImmutableList}类型的<i>参数</ i>通常是对调用者来说比较麻烦。
 * 可替代的方案是，接受{@link Iterable}并让您的方法或构造函数体将其传递给相应的{@code copyOf}方法。
 *
 * <p>Expressing the immutability guarantee directly in the type that user code references is a
 * powerful advantage. Although Java 9 offers certain immutable collection factory methods, like <a
 * href="https://docs.oracle.com/javase/9/docs/api/java/util/Set.html#immutable">{@code Set.of}</a>,
 * we recommend continuing to use these immutable collection classes for this reason.
 * <p>直接在用户代码引用的类型中表达不变性保证是一个强大的优势。 虽然Java 9提供了某些不可变的集合工厂方法，
 * 例如<a href="https://docs.oracle.com/javase/9/docs/api/java/util/Set.html#immutable"> {@code Set}</a>，
 * 但是基于上述的原因，我们建议继续您使用这些不可变的集合类。
 *
 * <h4>Creation</h4>
 * <H4>创建</ H4>
 *
 * <p>Except for logically "abstract" types like {@code ImmutableCollection} itself, each {@code
 * Immutable} type provides the static operations you need to obtain instances of that type. These
 * usually include:
 * <p>除了{@code ImmutableCollection}本身等逻辑“抽象”类型之外，每个{@code Immutable}类型都提供了获取该类型实例所需的静态操作。 这些通常包括：
 *
 * <ul>
 *   <li>Static methods named {@code of}, accepting an explicit list of elements or entries.
 *       名为{@code of}的静态方法，接受明确的元素或条目列表。
 *   <li>Static methods named {@code copyOf} (or {@code copyOfSorted}), accepting an existing
 *       collection whose contents should be copied.
 *       名为{@code copyOf}（或{@code copyOfSorted}）的静态方法，接受应复制其内容的现有集合。
 *   <li>A static nested {@code Builder} class which can be used to populate a new immutable
 *       instance.
 *       一个静态嵌套的{@code Builder}类，可用于填充新的不可变实例。
 * </ul>
 *
 * <h4>Warnings</h4>
 * <H4>警告</ H4>
 *
 * <ul>
 *   <li><b>Warning:</b> as with any collection, it is almost always a bad idea to modify an element
 *       (in a way that affects its {@link Object#equals} behavior) while it is contained in a
 *       collection. Undefined behavior and bugs will result. It's generally best to avoid using
 *       mutable objects as elements at all, as many users may expect your "immutable" object to be
 *       <i>deeply</i> immutable.
 *       <b>警告：</ b>与任何集合一样，在集合中包含元素时，修改元素（以影响其{@link Object＃equals}行为的方式）几乎总是一个坏主意。
 *       将导致未定义的行为和错误。 通常最好避免使用可变对象作为元素，因为许多用户可能希望您的“不可变”对象<i>深度</ i>不可变。
 * </ul>
 *
 * <h4>Performance notes</h4>
 * <h4>性能说明</ h4>
 *
 * <ul>
 *   <li>Implementations can be generally assumed to prioritize memory efficiency, then speed of
 *       access, and lastly speed of creation.
 *       通常可以假设实现优先考虑内存效率，然后是访问速度，最后是创建速度。
 *   <li>The {@code copyOf} methods will sometimes recognize that the actual copy operation is
 *       unnecessary; for example, {@code copyOf(copyOf(anArrayList))} should copy the data only
 *       once. This reduces the expense of habitually making defensive copies at API boundaries.
 *       However, the precise conditions for skipping the copy operation are undefined.
 *       {@code copyOf}方法有时会认识到实际的复制操作是不必要的; 例如，{@code copyOf(copyOf(anArrayList)) }应该只复制一次数据。
 *       这减少了习惯性地在API边界制作防御性副本的费用。但是，跳过复制操作的确切条件是不确定的。
 *   <li><b>Warning:</b> a view collection such as {@link ImmutableMap#keySet} or {@link
 *       ImmutableList#subList} may retain a reference to the entire data set, preventing it from
 *       being garbage collected. If some of the data is no longer reachable through other means,
 *       this constitutes a memory leak. Pass the view collection to the appropriate {@code copyOf}
 *       method to obtain a correctly-sized copy.
 *       <b>警告：</ b>视图集合（例如{@link ImmutableMap＃keySet}或{@link ImmutableList #subList}）可能会保留对整个数据集的引用，
 *       从而防止对其进行垃圾回收。 如果某些数据无法通过其他方式访问，则会构成内存泄漏。
 *       将视图集合传递给相应的{@code copyOf}方法以获取正确大小的副本。
 *   <li>The performance of using the associated {@code Builder} class can be assumed to be no
 *       worse, and possibly better, than creating a mutable collection and copying it.
 *       使用相关的{@code Builder}类的性能可以假设不比创建可变集合并复制它更糟糕，也可能更好。
 *   <li>Implementations generally do not cache hash codes. If your element or key type has a slow
 *       {@code hashCode} implementation, it should cache it itself.
 *       实现通常不缓存哈希码。 如果您的元素或键类型具有慢{@code hashCode}实现，则应自行缓存它。
 * </ul>
 *
 * <h4>Example usage</h4>
 *
 * <pre>{@code
 * class Foo {
 *   private static final ImmutableSet<String> RESERVED_CODES =
 *       ImmutableSet.of("AZ", "CQ", "ZX");
 *
 *   private final ImmutableSet<String> codes;
 *
 *   public Foo(Iterable<String> codes) {
 *     this.codes = ImmutableSet.copyOf(codes);
 *     checkArgument(Collections.disjoint(this.codes, RESERVED_CODES));
 *   }
 * }
 * }</pre>
 *
 * <h3>See also</h3>
 *
 * <p>See the Guava User Guide article on <a href=
 * "https://github.com/google/guava/wiki/ImmutableCollectionsExplained"> immutable collections</a>.
 *
 * @since 2.0
 */
@GwtCompatible(emulated = true)
@SuppressWarnings("serial") // we're overriding default serialization
// TODO(kevinb): I think we should push everything down to "BaseImmutableCollection" or something,
// just to do everything we can to emphasize the "practically an interface" nature of this class.
// 我认为我们应该将所有内容都推到“BaseImmutableCollection”或其他东西，只是为了尽我们所能来强调这个类的“实际上是一个接口”的本质。
public abstract class ImmutableCollection<E> extends AbstractCollection<E> implements Serializable { // 见AbstractCollection类的注释说明，
  // 想实现一个继承自AbstractCollection集合类是非常容易的。
  /*
   * We expect SIZED (and SUBSIZED, if applicable) to be added by the spliterator factory methods.
   * These are properties of the collection as a whole; SIZED and SUBSIZED are more properties of
   * the spliterator implementation.
   * 我们期望通过spliterator工厂方法添加SIZED（和SUBSIZED，如果适用）。
   * 这些是整个集合的属性; SIZED和SUBSIZED是spliterator实现所需额外的属性。
   */
  static final int SPLITERATOR_CHARACTERISTICS =
      Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.ORDERED;

  ImmutableCollection() {}

  /** Returns an unmodifiable iterator across the elements in this collection. */
  @Override
  public abstract UnmodifiableIterator<E> iterator();

  // 这个Spliterator的说明，见我的skill_arena/spliterator_ex中的例子说明。
  @Override
  public Spliterator<E> spliterator() {
    return Spliterators.spliterator(this, SPLITERATOR_CHARACTERISTICS);
  }

  private static final Object[] EMPTY_ARRAY = {};

  @Override
  public final Object[] toArray() {
    int size = size();
    if (size == 0) {
      return EMPTY_ARRAY;
    }
    Object[] result = new Object[size];
    copyIntoArray(result, 0);
    return result;
  }

  @CanIgnoreReturnValue
  @Override
  public final <T> T[] toArray(T[] other) {
    checkNotNull(other);
    int size = size();
    if (other.length < size) {
      other = ObjectArrays.newArray(other, size);
    } else if (other.length > size) {
      other[size] = null;
    }
    copyIntoArray(other, 0);
    return other;
  }

  @Override
  public abstract boolean contains(@Nullable Object object);

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  public final boolean add(E e) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  public final boolean remove(Object object) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  public final boolean addAll(Collection<? extends E> newElements) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  public final boolean removeAll(Collection<?> oldElements) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  public final boolean removeIf(Predicate<? super E> filter) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  public final boolean retainAll(Collection<?> elementsToKeep) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  public final void clear() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns an {@code ImmutableList} containing the same elements, in the same order, as this
   * collection.
   * 以与此集合相同的顺序返回包含相同元素的{@code ImmutableList}。
   *
   * <p><b>Performance note:</b> in most cases this method can return quickly without actually
   * copying anything. The exact circumstances under which the copy is performed are undefined and
   * subject to change.
   * <p> <b>性能说明：</ b>在大多数情况下，此方法可以快速返回，而无需实际复制任何内容。
   * 执行副本的确切情况未定义且可能会发生变化。
   *
   * @since 2.0
   */
  public ImmutableList<E> asList() {
    switch (size()) {
      case 0:
        return ImmutableList.of();
      case 1:
        return ImmutableList.of(iterator().next());
      default:
        return new RegularImmutableAsList<E>(this, toArray());
    }
  }

  /**
   * Returns {@code true} if this immutable collection's implementation contains references to
   * user-created objects that aren't accessible via this collection's methods. This is generally
   * used to determine whether {@code copyOf} implementations should make an explicit copy to avoid
   * memory leaks.
   * 如果此不可变集合的实现包含对无法通过此集合的方法访问的用户创建对象的引用，则返回{@code true}。
   * 这通常用于确定{@code copyOf}实现是否应该进行显式复制以避免内存泄漏。
   */
  abstract boolean isPartialView();

  /**
   * Copies the contents of this immutable collection into the specified array at the specified
   * offset. Returns {@code offset + size()}.
   */
  @CanIgnoreReturnValue
  int copyIntoArray(Object[] dst, int offset) {
    for (E e : this) {
      dst[offset++] = e;
    }
    return offset;
  }

  Object writeReplace() {
    // We serialize by default to ImmutableList, the simplest thing that works.
      // 我们默认序列化为ImmutableList，这是最简单的方法。
    return new ImmutableList.SerializedForm(toArray());
  }

  /**
   * Abstract base class for builders of {@link ImmutableCollection} types.
   *
   * @since 10.0
   */
  public abstract static class Builder<E> {
    static final int DEFAULT_INITIAL_CAPACITY = 4;

    static int expandedCapacity(int oldCapacity, int minCapacity) {
      if (minCapacity < 0) {
        throw new AssertionError("cannot store more than MAX_VALUE elements");
      }
      // careful of overflow!
      int newCapacity = oldCapacity + (oldCapacity >> 1) + 1;
      if (newCapacity < minCapacity) {
        newCapacity = Integer.highestOneBit(minCapacity - 1) << 1;
      }
      if (newCapacity < 0) {
        newCapacity = Integer.MAX_VALUE;
        // guaranteed to be >= newCapacity
      }
      return newCapacity;
    }

    Builder() {}

    /**
     * Adds {@code element} to the {@code ImmutableCollection} being built.
     *
     * <p>Note that each builder class covariantly returns its own type from this method.
     *
     * @param element the element to add
     * @return this {@code Builder} instance
     * @throws NullPointerException if {@code element} is null
     */
    @CanIgnoreReturnValue
    public abstract Builder<E> add(E element);

    /**
     * Adds each element of {@code elements} to the {@code ImmutableCollection} being built.
     *
     * <p>Note that each builder class overrides this method in order to covariantly return its own
     * type.
     *
     * @param elements the elements to add
     * @return this {@code Builder} instance
     * @throws NullPointerException if {@code elements} is null or contains a null element
     */
    @CanIgnoreReturnValue
    public Builder<E> add(E... elements) {
      for (E element : elements) {
        add(element);
      }
      return this;
    }

    /**
     * Adds each element of {@code elements} to the {@code ImmutableCollection} being built.
     *
     * <p>Note that each builder class overrides this method in order to covariantly return its own
     * type.
     *
     * @param elements the elements to add
     * @return this {@code Builder} instance
     * @throws NullPointerException if {@code elements} is null or contains a null element
     */
    @CanIgnoreReturnValue
    public Builder<E> addAll(Iterable<? extends E> elements) {
      for (E element : elements) {
        add(element);
      }
      return this;
    }

    /**
     * Adds each element of {@code elements} to the {@code ImmutableCollection} being built.
     *
     * <p>Note that each builder class overrides this method in order to covariantly return its own
     * type.
     *
     * @param elements the elements to add
     * @return this {@code Builder} instance
     * @throws NullPointerException if {@code elements} is null or contains a null element
     */
    @CanIgnoreReturnValue
    public Builder<E> addAll(Iterator<? extends E> elements) {
      while (elements.hasNext()) {
        add(elements.next());
      }
      return this;
    }

    /**
     * Returns a newly-created {@code ImmutableCollection} of the appropriate type, containing the
     * elements provided to this builder.
     *
     * <p>Note that each builder class covariantly returns the appropriate type of {@code
     * ImmutableCollection} from this method.
     */
    public abstract ImmutableCollection<E> build();
  }
}
