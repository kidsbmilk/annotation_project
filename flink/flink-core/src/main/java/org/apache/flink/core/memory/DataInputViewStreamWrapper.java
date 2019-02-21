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

package org.apache.flink.core.memory;

import org.apache.flink.annotation.PublicEvolving;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class that turns an {@link InputStream} into a {@link DataInputView}.
 *
 * 这里的实现很巧妙，DataInputView里定义了一些抽象的方法，然后DataInputViewStreamWrapper直接继承DataInputStream，并以DataInputStream中的方法来实现DataInputView里的某些接口方法。
 * 问题是：为什么不直接继承DataInputStream并添加skipBytesToRead方法呢？
 * 因为：这里只是想在InputStream上增加一个视图，并不想使此视图成为InputStream，所以以这样的方式来实现了。
 *
 * 其实，这里的DataInputViewStreamWrapper的实现思路与DataInputStream的实现思路是一样的：
 * DataInputStream继承FilterInputStream（其实FilterInputStream只是把InputStream继承了一下，什么也没改，什么也没加），然后实现DataInput接口，将所有的InputStream转为DataInput。
 * 这里是继承DataInputStream，实现DataInputView接口，将InputStream转为DataInputView。
 *
 * 这里相当于用接口来实现多重继承。
 */
@PublicEvolving
public class DataInputViewStreamWrapper extends DataInputStream implements DataInputView {

	public DataInputViewStreamWrapper(InputStream in) {
		super(in);
	}

	@Override
	public void skipBytesToRead(int numBytes) throws IOException {
		if (skipBytes(numBytes) != numBytes){
			throw new EOFException("Could not skip " + numBytes + " bytes.");
		}
	}
}
