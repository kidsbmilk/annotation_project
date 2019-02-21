/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.util;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSerializationUtil;
import org.apache.flink.api.common.typeutils.base.MapSerializer;
import org.apache.flink.api.java.typeutils.runtime.KryoRegistrationSerializerConfigSnapshot;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.io.IOReadableWritable;
import org.apache.flink.core.memory.DataInputViewStreamWrapper;
import org.apache.flink.core.memory.DataOutputViewStreamWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility class to create instances from class objects and checking failure reasons.
 */
@Internal
public final class InstantiationUtil {

	private static final Logger LOG = LoggerFactory.getLogger(InstantiationUtil.class);

	/**
	 * A custom ObjectInputStream that can load classes using a specific ClassLoader.
	 */
	public static class ClassLoaderObjectInputStream extends ObjectInputStream {

		protected final ClassLoader classLoader;

		public ClassLoaderObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
			super(in);
			this.classLoader = classLoader;
		}

		@Override
		protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
			if (classLoader != null) {
				String name = desc.getName();
				try {
					return Class.forName(name, false, classLoader);
				} catch (ClassNotFoundException ex) {
					// check if class is a primitive class
					Class<?> cl = primitiveClasses.get(name);
					if (cl != null) {
						// return primitive class
						return cl;
					} else {
						// throw ClassNotFoundException
						throw ex;
					}
				}
			}

			return super.resolveClass(desc);
		}

		// ------------------------------------------------

		private static final HashMap<String, Class<?>> primitiveClasses = new HashMap<>(9);

		static {
			primitiveClasses.put("boolean", boolean.class);
			primitiveClasses.put("byte", byte.class);
			primitiveClasses.put("char", char.class);
			primitiveClasses.put("short", short.class);
			primitiveClasses.put("int", int.class);
			primitiveClasses.put("long", long.class);
			primitiveClasses.put("float", float.class);
			primitiveClasses.put("double", double.class);
			primitiveClasses.put("void", void.class);
		}
	}

	/**
	 * This is maintained as a temporary workaround for FLINK-6869.
	 * 这是FLINK-6869的临时解决方法。
	 *
	 * <p>Before 1.3, the Scala serializers did not specify the serialVersionUID.
	 * Although since 1.3 they are properly specified, we still have to ignore them for now
	 * as their previous serialVersionUIDs will vary depending on the Scala version.
	 * 在1.3之前，Scala序列化程序未指定serialVersionUID。虽然从1.3开始就已正确指定，但我们现在仍然必须忽略它们，因为他们以前的serialVersionUID会因Scala版本而异。
	 *
	 * <p>This can be removed once 1.2 is no longer supported.
	 * 一旦不再支持1.2，就可以删除它。
	 */
	private static final Set<String> scalaSerializerClassnames = new HashSet<>();
	static {
		scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.TraversableSerializer");
		scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.CaseClassSerializer");
		scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.EitherSerializer");
		scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.EnumValueSerializer");
		scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.OptionSerializer");
		scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.TrySerializer");
		scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.UnitSerializer");
	}

	/**
	 * The serialVersionUID might change between Scala versions and since those classes are
	 * part of the tuple serializer config snapshots we need to ignore them.
	 * serialVersionUID可能会在Scala版本之间发生变化，因为这些类是元组序列化器配置快照的一部分，我们需要忽略它们。
	 *
	 * @see <a href="https://issues.apache.org/jira/browse/FLINK-8451">FLINK-8451</a>
	 */
	private static final Set<String> scalaTypes = new HashSet<>();
	static {
		scalaTypes.add("scala.Tuple1");
		scalaTypes.add("scala.Tuple2");
		scalaTypes.add("scala.Tuple3");
		scalaTypes.add("scala.Tuple4");
		scalaTypes.add("scala.Tuple5");
		scalaTypes.add("scala.Tuple6");
		scalaTypes.add("scala.Tuple7");
		scalaTypes.add("scala.Tuple8");
		scalaTypes.add("scala.Tuple9");
		scalaTypes.add("scala.Tuple10");
		scalaTypes.add("scala.Tuple11");
		scalaTypes.add("scala.Tuple12");
		scalaTypes.add("scala.Tuple13");
		scalaTypes.add("scala.Tuple14");
		scalaTypes.add("scala.Tuple15");
		scalaTypes.add("scala.Tuple16");
		scalaTypes.add("scala.Tuple17");
		scalaTypes.add("scala.Tuple18");
		scalaTypes.add("scala.Tuple19");
		scalaTypes.add("scala.Tuple20");
		scalaTypes.add("scala.Tuple21");
		scalaTypes.add("scala.Tuple22");
		scalaTypes.add("scala.Tuple1$mcJ$sp");
		scalaTypes.add("scala.Tuple1$mcI$sp");
		scalaTypes.add("scala.Tuple1$mcD$sp");
		scalaTypes.add("scala.Tuple2$mcJJ$sp");
		scalaTypes.add("scala.Tuple2$mcJI$sp");
		scalaTypes.add("scala.Tuple2$mcJD$sp");
		scalaTypes.add("scala.Tuple2$mcIJ$sp");
		scalaTypes.add("scala.Tuple2$mcII$sp");
		scalaTypes.add("scala.Tuple2$mcID$sp");
		scalaTypes.add("scala.Tuple2$mcDJ$sp");
		scalaTypes.add("scala.Tuple2$mcDI$sp");
		scalaTypes.add("scala.Tuple2$mcDD$sp");
	}

	/**
	 * An {@link ObjectInputStream} that ignores serialVersionUID mismatches when deserializing objects of
	 * anonymous classes or our Scala serializer classes and also replaces occurences of GenericData.Array
	 * (from Avro) by a dummy class so that the KryoSerializer can still be deserialized without
	 * Avro being on the classpath.
	 * 一个{@link ObjectInputStream}在反序列化匿名类或Scala序列化程序类的对象时忽略serialVersionUID不匹配，
	 * 并且还用虚拟类替换GenericData.Array（来自Avro）的出现，这样KryoSerializer仍然可以在不使用Avro的情况下被反序列化。
	 *
	 * <p>The {@link TypeSerializerSerializationUtil.TypeSerializerSerializationProxy} uses this specific object input stream to read serializers,
	 * so that mismatching serialVersionUIDs of anonymous classes / Scala serializers are ignored.
	 * This is a required workaround to maintain backwards compatibility for our pre-1.3 Scala serializers.
	 * See FLINK-6869 for details.
	 * {@link TypeSerializerSerializationUtil.TypeSerializerSerializationProxy}使用此特定对象输入流来读取序列化程序，
	 * 以便忽略匿名类/ Scala序列化程序的不匹配serialVersionUID。 这是维护1.3之前的Scala序列化程序的向后兼容性所必需的解决方法。
	 * 有关详细信息，请参阅FLINK-6869。
	 *
	 * 这个类不应该起名为FailureTolerant，而应该是SerialVersionTolerant。
	 *
	 * @see <a href="https://issues.apache.org/jira/browse/FLINK-6869">FLINK-6869</a>
	 */
	public static class FailureTolerantObjectInputStream extends InstantiationUtil.ClassLoaderObjectInputStream {

		public FailureTolerantObjectInputStream(InputStream in, ClassLoader cl) throws IOException {
			super(in, cl);
		}

		@Override
		protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
			ObjectStreamClass streamClassDescriptor = super.readClassDescriptor();

			try {
				Class.forName(streamClassDescriptor.getName(), false, classLoader);
			} catch (ClassNotFoundException e) {

				// 用虚拟类替换GenericData.Array（来自Avro）的出现，这样KryoSerializer仍然可以在不使用Avro的情况下被反序列化。
				final ObjectStreamClass equivalentSerializer =
						MigrationUtil.getEquivalentSerializer(streamClassDescriptor.getName());

				if (equivalentSerializer != null) {
					return equivalentSerializer;
				}
			}

			final Class localClass = resolveClass(streamClassDescriptor);
			final String name = localClass.getName();
			// 忽略匿名类/ Scala序列化程序的不匹配serialVersionUID
			if (scalaSerializerClassnames.contains(name) || scalaTypes.contains(name) || isAnonymousClass(localClass)) {
				final ObjectStreamClass localClassDescriptor = ObjectStreamClass.lookup(localClass);
				if (localClassDescriptor != null
					&& localClassDescriptor.getSerialVersionUID() != streamClassDescriptor.getSerialVersionUID()) {
					LOG.warn("Ignoring serialVersionUID mismatch for anonymous class {}; was {}, now {}.",
						streamClassDescriptor.getName(), streamClassDescriptor.getSerialVersionUID(), localClassDescriptor.getSerialVersionUID());

					streamClassDescriptor = localClassDescriptor;
				}
			}

			return streamClassDescriptor;
		}
	}

	private static boolean isAnonymousClass(Class clazz) {
		final String name = clazz.getName();

		// isAnonymousClass does not work for anonymous Scala classes; additionally check by class name
		if (name.contains("$anon$") || name.contains("$anonfun") || name.contains("$macro$")) {
			return true;
		}

		// calling isAnonymousClass or getSimpleName can throw InternalError for certain Scala types, see https://issues.scala-lang.org/browse/SI-2034
		// until we move to JDK 9, this try-catch is necessary
		try {
			return clazz.isAnonymousClass();
		} catch (InternalError e) {
			return false;
		}
	}

	/**
	 * A mapping between the full path of a deprecated serializer and its equivalent.
	 * These mappings are hardcoded and fixed.
	 * 已弃用的序列化程序的完整路径与其等效项之间的映射。 这些映射是硬编码和固定的。
	 *
	 * <p>IMPORTANT: mappings can be removed after 1 release as there will be a "migration path".
	 * As an example, a serializer is removed in 1.5-SNAPSHOT, then the mapping should be added for 1.5,
	 * and it can be removed in 1.6, as the path would be Flink-{< 1.5} -> Flink-1.5 -> Flink-{>= 1.6}.
	 * 我并不是太明白上面这段英文，“migration path”与mappings的增减有必然关系吗？
	 * 我的理解是：随着“migration path”的变化，也就是版本升级，这里的mappings可能会产生变化，
	 * 但是并不是每次“migration path”的变化必然会导致mappings的变化（上面的英文好像是这个意思）。
	 */
	private enum MigrationUtil {

		// To add a new mapping just pick a name and add an entry as the following:

		GENERIC_DATA_ARRAY_SERIALIZER(
				"org.apache.avro.generic.GenericData$Array",
				ObjectStreamClass.lookup(KryoRegistrationSerializerConfigSnapshot.DummyRegisteredClass.class)), // TODO-read：这里引出了一个大的模块，之前从未接触过的。
		HASH_MAP_SERIALIZER(
				"org.apache.flink.runtime.state.HashMapSerializer",
				ObjectStreamClass.lookup(MapSerializer.class)); // added in 1.5

		/** An internal unmodifiable map containing the mappings between deprecated and new serializers. */
		private static final Map<String, ObjectStreamClass> EQUIVALENCE_MAP = Collections.unmodifiableMap(initMap());

		/** The full name of the class of the old serializer. */
		private final String oldSerializerName;

		/** The serialization descriptor of the class of the new serializer. */
		private final ObjectStreamClass newSerializerStreamClass;

		MigrationUtil(String oldSerializerName, ObjectStreamClass newSerializerStreamClass) {
			this.oldSerializerName = oldSerializerName;
			this.newSerializerStreamClass = newSerializerStreamClass;
		}

		private static Map<String, ObjectStreamClass> initMap() {
			final Map<String, ObjectStreamClass> init = new HashMap<>(4);
			for (MigrationUtil m: MigrationUtil.values()) {
				init.put(m.oldSerializerName, m.newSerializerStreamClass);
			}
			return init;
		}

		private static ObjectStreamClass getEquivalentSerializer(String classDescriptorName) {
			return EQUIVALENCE_MAP.get(classDescriptorName);
		}
	}

	/**
	 * Creates a new instance of the given class.
	 *
	 * @param <T> The generic type of the class.
	 * @param clazz The class to instantiate.
	 * @param castTo Optional parameter, specifying the class that the given class must be a subclass off. This
	 *               argument is added to prevent class cast exceptions occurring later.
	 * @return An instance of the given class.
	 *
	 * @throws RuntimeException Thrown, if the class could not be instantiated. The exception contains a detailed
	 *                          message about the reason why the instantiation failed.
	 */
	public static <T> T instantiate(Class<T> clazz, Class<? super T> castTo) {
		if (clazz == null) {
			throw new NullPointerException();
		}

		// check if the class is a subclass, if the check is required
		// isAssignableFrom这个方法名起的不好，A isAssignableFrom B，字面意是指：A是否可以由B来赋值，也就是B能否赋值给A，
		// 可是，A和B在jvm中是类，而类是不能这样赋值的，只有类的对象能这样赋值，即：B类的对象实例能赋值给A类的对象实例。
		// 所以，这里字面上是判断对象是否可赋值，但是实际上目的是判断A是否是B的父类，方法名改为：isParentClassTo比较好。
		if (castTo != null && !castTo.isAssignableFrom(clazz)) {
			throw new RuntimeException("The class '" + clazz.getName() + "' is not a subclass of '" +
				castTo.getName() + "' as is required.");
		}

		return instantiate(clazz);
	}

	/**
	 * Creates a new instance of the given class.
	 *
	 * @param <T> The generic type of the class.
	 * @param clazz The class to instantiate.

	 * @return An instance of the given class.
	 *
	 * @throws RuntimeException Thrown, if the class could not be instantiated. The exception contains a detailed
	 *                          message about the reason why the instantiation failed.
	 */
	public static <T> T instantiate(Class<T> clazz) {
		if (clazz == null) {
			throw new NullPointerException();
		}

		// try to instantiate the class
		try {
			return clazz.newInstance();
		}
		catch (InstantiationException | IllegalAccessException iex) {
			// check for the common problem causes
			checkForInstantiation(clazz);

			// here we are, if non of the common causes was the problem. then the error was
			// most likely an exception in the constructor or field initialization
			throw new RuntimeException("Could not instantiate type '" + clazz.getName() +
					"' due to an unspecified exception: " + iex.getMessage(), iex);
		}
		catch (Throwable t) {
			String message = t.getMessage();
			throw new RuntimeException("Could not instantiate type '" + clazz.getName() +
				"' Most likely the constructor (or a member variable initialization) threw an exception" +
				(message == null ? "." : ": " + message), t);
		}
	}

	/**
	 * Checks, whether the given class has a public nullary constructor.
	 *
	 * @param clazz The class to check.
	 * @return True, if the class has a public nullary constructor, false if not.
	 */
	public static boolean hasPublicNullaryConstructor(Class<?> clazz) {
		Constructor<?>[] constructors = clazz.getConstructors();
		for (Constructor<?> constructor : constructors) {
			if (constructor.getParameterTypes().length == 0 &&
					Modifier.isPublic(constructor.getModifiers())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks, whether the given class is public.
	 *
	 * @param clazz The class to check.
	 * @return True, if the class is public, false if not.
	 */
	public static boolean isPublic(Class<?> clazz) {
		return Modifier.isPublic(clazz.getModifiers());
	}

	/**
	 * Checks, whether the class is a proper class, i.e. not abstract or an interface, and not a primitive type.
	 *
	 * @param clazz The class to check.
	 * @return True, if the class is a proper class, false otherwise.
	 */
	public static boolean isProperClass(Class<?> clazz) {
		int mods = clazz.getModifiers();
		return !(Modifier.isAbstract(mods) || Modifier.isInterface(mods) || Modifier.isNative(mods));
	}

	/**
	 * Checks, whether the class is an inner class that is not statically accessible. That is especially true for
	 * anonymous inner classes.
	 *
	 * @param clazz The class to check.
	 * @return True, if the class is a non-statically accessible inner class.
	 */
	public static boolean isNonStaticInnerClass(Class<?> clazz) {
		return clazz.getEnclosingClass() != null &&
			(clazz.getDeclaringClass() == null || !Modifier.isStatic(clazz.getModifiers()));
	}

	/**
	 * Performs a standard check whether the class can be instantiated by {@code Class#newInstance()}.
	 *
	 * @param clazz The class to check.
	 * @throws RuntimeException Thrown, if the class cannot be instantiated by {@code Class#newInstance()}.
	 */
	public static void checkForInstantiation(Class<?> clazz) {
		final String errorMessage = checkForInstantiationError(clazz);

		if (errorMessage != null) {
			throw new RuntimeException("The class '" + clazz.getName() + "' is not instantiable: " + errorMessage);
		}
	}

	public static String checkForInstantiationError(Class<?> clazz) {
		if (!isPublic(clazz)) {
			return "The class is not public.";
		} else if (clazz.isArray()) {
			return "The class is an array. An array cannot be simply instantiated, as with a parameterless constructor.";
		} else if (!isProperClass(clazz)) {
			return "The class is not a proper class. It is either abstract, an interface, or a primitive type.";
		} else if (isNonStaticInnerClass(clazz)) {
			return "The class is an inner class, but not statically accessible.";
		} else if (!hasPublicNullaryConstructor(clazz)) {
			return "The class has no (implicit) public nullary constructor, i.e. a constructor without arguments.";
		} else {
			return null;
		}
	}

	public static <T> T readObjectFromConfig(Configuration config, String key, ClassLoader cl) throws IOException, ClassNotFoundException {
		byte[] bytes = config.getBytes(key, null);
		if (bytes == null) {
			return null;
		}

		return deserializeObject(bytes, cl);
	}

	public static void writeObjectToConfig(Object o, Configuration config, String key) throws IOException {
		byte[] bytes = serializeObject(o);
		config.setBytes(key, bytes);
	}

	public static <T> byte[] serializeToByteArray(TypeSerializer<T> serializer, T record) throws IOException { // TODO-read: 这个TypeSerializer又引出了一大块东西。
		if (record == null) {
			throw new NullPointerException("Record to serialize to byte array must not be null.");
		}

		ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
		DataOutputViewStreamWrapper outputViewWrapper = new DataOutputViewStreamWrapper(bos);
		serializer.serialize(record, outputViewWrapper);
		return bos.toByteArray();
	}

	public static <T> T deserializeFromByteArray(TypeSerializer<T> serializer, byte[] buf) throws IOException {
		if (buf == null) {
			throw new NullPointerException("Byte array to deserialize from must not be null.");
		}

		DataInputViewStreamWrapper inputViewWrapper = new DataInputViewStreamWrapper(new ByteArrayInputStream(buf));
		return serializer.deserialize(inputViewWrapper);
	}

	public static <T> T deserializeFromByteArray(TypeSerializer<T> serializer, T reuse, byte[] buf) throws IOException {
		if (buf == null) {
			throw new NullPointerException("Byte array to deserialize from must not be null.");
		}

		DataInputViewStreamWrapper inputViewWrapper = new DataInputViewStreamWrapper(new ByteArrayInputStream(buf));
		return serializer.deserialize(reuse, inputViewWrapper);
	}

	@SuppressWarnings("unchecked")
	public static <T> T deserializeObject(byte[] bytes, ClassLoader cl) throws IOException, ClassNotFoundException {
		return deserializeObject(bytes, cl, false);
	}

	@SuppressWarnings("unchecked")
	public static <T> T deserializeObject(InputStream in, ClassLoader cl) throws IOException, ClassNotFoundException {
		return deserializeObject(in, cl, false);
	}

	@SuppressWarnings("unchecked")
	public static <T> T deserializeObject(byte[] bytes, ClassLoader cl, boolean isFailureTolerant)
			throws IOException, ClassNotFoundException {

		return deserializeObject(new ByteArrayInputStream(bytes), cl, isFailureTolerant);
	}

	@SuppressWarnings("unchecked")
	public static <T> T deserializeObject(InputStream in, ClassLoader cl, boolean isFailureTolerant)
			throws IOException, ClassNotFoundException {

		final ClassLoader old = Thread.currentThread().getContextClassLoader();
		// not using resource try to avoid AutoClosable's close() on the given stream
		try {
			ObjectInputStream oois = isFailureTolerant
				// 见这个类的注释，此类主要是为了解决序列化版本不一致的问题，并不是说对所有错误都有保护。
				? new InstantiationUtil.FailureTolerantObjectInputStream(in, cl)
				: new InstantiationUtil.ClassLoaderObjectInputStream(in, cl);
			Thread.currentThread().setContextClassLoader(cl);
			return (T) oois.readObject();
		}
		finally {
			Thread.currentThread().setContextClassLoader(old);
		}
	}

	public static byte[] serializeObject(Object o) throws IOException {
		// try块退出时，会自动调用res.close()方法，关闭资源。
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(o);
			oos.flush();
			return baos.toByteArray();
		}
	}

	public static void serializeObject(OutputStream out, Object o) throws IOException {
		ObjectOutputStream oos = new ObjectOutputStream(out);
		oos.writeObject(o);
	}

	public static boolean isSerializable(Object o) {
		try {
			serializeObject(o);
		} catch (IOException e) {
			return false;
		}

		return true;
	}

	/**
	 * Clones the given serializable object using Java serialization.
	 *
	 * @param obj Object to clone
	 * @param <T> Type of the object to clone
	 * @return The cloned object
	 *
	 * @throws IOException Thrown if the serialization or deserialization process fails.
	 * @throws ClassNotFoundException Thrown if any of the classes referenced by the object
	 *                                cannot be resolved during deserialization.
	 */
	public static <T extends Serializable> T clone(T obj) throws IOException, ClassNotFoundException {
		if (obj == null) {
			return null;
		} else {
			return clone(obj, obj.getClass().getClassLoader());
		}
	}

	/**
	 * Clones the given serializable object using Java serialization, using the given classloader to
	 * resolve the cloned classes.
	 *
	 * @param obj Object to clone
	 * @param classLoader The classloader to resolve the classes during deserialization.
	 * @param <T> Type of the object to clone
	 *
	 * @return Cloned object
	 *
	 * @throws IOException Thrown if the serialization or deserialization process fails.
	 * @throws ClassNotFoundException Thrown if any of the classes referenced by the object
	 *                                cannot be resolved during deserialization.
	 */
	public static <T extends Serializable> T clone(T obj, ClassLoader classLoader) throws IOException, ClassNotFoundException {
		if (obj == null) {
			return null;
		} else {
			// 为什么要这样实现呢？
			// 我想是因为这样的：并不确定T是否实现了clone方法，所以，先序列化出来，然后再反序列化，可以实现所有对象的clone功能。
			final byte[] serializedObject = serializeObject(obj);
			return deserializeObject(serializedObject, classLoader);
		}
	}

	/**
	 * Clones the given writable using the {@link IOReadableWritable serialization}.
	 *
	 * @param original Object to clone
	 * @param <T> Type of the object to clone
	 * @return Cloned object
	 * @throws IOException Thrown is the serialization fails.
	 */
	public static <T extends IOReadableWritable> T createCopyWritable(T original) throws IOException {
		if (original == null) {
			return null;
		}

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (DataOutputViewStreamWrapper out = new DataOutputViewStreamWrapper(baos)) {
			original.write(out);
		}

		final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		try (DataInputViewStreamWrapper in = new DataInputViewStreamWrapper(bais)) {

			@SuppressWarnings("unchecked")
			T copy = (T) instantiate(original.getClass());
			copy.read(in);
			return copy;
		}
	}


	// --------------------------------------------------------------------------------------------

	/**
	 * Private constructor to prevent instantiation.
	 */
	private InstantiationUtil() {
		throw new RuntimeException();
	}
}
