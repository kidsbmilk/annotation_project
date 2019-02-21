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

package org.apache.flink.api.common.accumulators;

import org.apache.flink.annotation.Internal;
import org.apache.flink.util.FlinkException;
import org.apache.flink.util.OptionalFailure;
import org.apache.flink.util.SerializedValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Helper functions for the interaction with {@link Accumulator}.
 */
@Internal
public class AccumulatorHelper {
	private static final Logger LOG = LoggerFactory.getLogger(AccumulatorHelper.class);

	/**
	 * Merge two collections of accumulators. The second will be merged into the
	 * first.
	 *
	 * @param target
	 *            The collection of accumulators that will be updated
	 * @param toMerge
	 *            The collection of accumulators that will be merged into the
	 *            other
	 */
	public static void mergeInto(Map<String, OptionalFailure<Accumulator<?, ?>>> target, Map<String, Accumulator<?, ?>> toMerge) {
		for (Map.Entry<String, Accumulator<?, ?>> otherEntry : toMerge.entrySet()) {
			OptionalFailure<Accumulator<?, ?>> ownAccumulator = target.get(otherEntry.getKey());
			if (ownAccumulator == null) {
				// Create initial counter (copy!)
				target.put(
					otherEntry.getKey(),
					// 注意这里：使用的是unchecked的wrapUnchecked方法，在参数中，getValue()方法可能会抛出IllegalStateException异常，IllegalStateException是unchecked异常，
					// 后面的clone()方法是Accumulator.clone()方法，见其注释，此Accumulator.clone()方法是不允许抛出异常的，所以，这里调用的是unchecked方法。
					wrapUnchecked(otherEntry.getKey(), () -> otherEntry.getValue().clone()));
			}
			else if (ownAccumulator.isFailure()) {
				continue;
			}
			else {
				// 走到这里时，说明target里已经存在且不是失败的情况了，所以下面可以放心地调用getUnchecked方法，如果有值，就取值；
				// 如果没值，抛出unchecked方法，说明程序逻辑有问题，抛出异常，方便排查问题，这里也体现了就让它崩溃的编程思想。
				Accumulator<?, ?> accumulator = ownAccumulator.getUnchecked();
				// Both should have the same type
				compareAccumulatorTypes(otherEntry.getKey(),
					accumulator.getClass(), otherEntry.getValue().getClass());
				// Merge target counter with other counter

				target.put(
					otherEntry.getKey(),
					wrapUnchecked(otherEntry.getKey(), () -> mergeSingle(accumulator, otherEntry.getValue().clone())));
			}
		}
	}

	/**
	 * Workaround method for type safety.
	 */
	private static <V, R extends Serializable> Accumulator<V, R> mergeSingle(Accumulator<?, ?> target,
																			 Accumulator<?, ?> toMerge) {
		@SuppressWarnings("unchecked")
		Accumulator<V, R> typedTarget = (Accumulator<V, R>) target;

		@SuppressWarnings("unchecked")
		Accumulator<V, R> typedToMerge = (Accumulator<V, R>) toMerge;

		typedTarget.merge(typedToMerge);

		return typedTarget;
	}

	/**
	 * Compare both classes and throw {@link UnsupportedOperationException} if
	 * they differ.
	 */
	@SuppressWarnings("rawtypes")
	public static void compareAccumulatorTypes(
			Object name,
			Class<? extends Accumulator> first,
			Class<? extends Accumulator> second) throws UnsupportedOperationException {
		if (first == null || second == null) {
			throw new NullPointerException();
		}

		if (first != second) {
			if (!first.getName().equals(second.getName())) {
				throw new UnsupportedOperationException("The accumulator object '" + name
					+ "' was created with two different types: " + first.getName() + " and " + second.getName());
			} else {
				// damn, name is the same, but different classloaders
				throw new UnsupportedOperationException("The accumulator object '" + name
						+ "' was created with two different classes: " + first + " and " + second
						+ " Both have the same type (" + first.getName() + ") but different classloaders: "
						+ first.getClassLoader() + " and " + second.getClassLoader());
			}
		}
	}

	/**
	 * Transform the Map with accumulators into a Map containing only the
	 * results.
	 */
	public static Map<String, OptionalFailure<Object>> toResultMap(Map<String, Accumulator<?, ?>> accumulators) {
		Map<String, OptionalFailure<Object>> resultMap = new HashMap<>();
		for (Map.Entry<String, Accumulator<?, ?>> entry : accumulators.entrySet()) {
			resultMap.put(entry.getKey(), wrapUnchecked(entry.getKey(), () -> entry.getValue().getLocalValue()));
		}
		return resultMap;
	}

	private static <R> OptionalFailure<R> wrapUnchecked(String name, Supplier<R> supplier) { // 此方法中，使用了CheckedSupplier版本的createFrom，所以，要捕获可能的unchecked异常，然后转为checked异常。见createFrom里的说明。
		return OptionalFailure.createFrom(() -> {
			try {
				return supplier.get();
			} catch (RuntimeException ex) {
				LOG.error("Unexpected error while handling accumulator [" + name + "]", ex);
				throw new FlinkException(ex);
			}
		});
	}

	public static String getResultsFormatted(Map<String, Object> map) {
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			builder
				.append("- ")
				.append(entry.getKey())
				.append(" (")
				.append(entry.getValue().getClass().getName())
				.append(")");
			if (entry.getValue() instanceof Collection) {
				builder.append(" [").append(((Collection) entry.getValue()).size()).append(" elements]");
			} else {
				builder.append(": ").append(entry.getValue().toString());
			}
			builder.append(System.lineSeparator());
		}
		return builder.toString();
	}

	public static Map<String, Accumulator<?, ?>> copy(Map<String, Accumulator<?, ?>> accumulators) {
		Map<String, Accumulator<?, ?>> result = new HashMap<String, Accumulator<?, ?>>();

		for (Map.Entry<String, Accumulator<?, ?>> entry: accumulators.entrySet()){
			result.put(entry.getKey(), entry.getValue().clone());
		}

		return result;
	}

	/**
	 * Takes the serialized accumulator results and tries to deserialize them using the provided
	 * class loader.
	 * @param serializedAccumulators The serialized accumulator results.
	 * @param loader The class loader to use.
	 * @return The deserialized accumulator results.
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Map<String, OptionalFailure<Object>> deserializeAccumulators(
			Map<String, SerializedValue<OptionalFailure<Object>>> serializedAccumulators,
			ClassLoader loader) throws IOException, ClassNotFoundException {

		if (serializedAccumulators == null || serializedAccumulators.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<String, OptionalFailure<Object>> accumulators = new HashMap<>(serializedAccumulators.size());

		for (Map.Entry<String, SerializedValue<OptionalFailure<Object>>> entry : serializedAccumulators.entrySet()) {

			OptionalFailure<Object> value = null;
			if (entry.getValue() != null) {
				value = entry.getValue().deserializeValue(loader);
			}

			accumulators.put(entry.getKey(), value);
		}

		return accumulators;
	}
}
