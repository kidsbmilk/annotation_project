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

package org.apache.flink.runtime.io.disk.iomanager;

import org.apache.flink.runtime.io.network.buffer.Buffer;

import java.io.IOException;

public interface BufferFileReader extends FileIOChannel {

	/**
	 * Read data into the buffer, assume each buffer has a 8-bytes header ahead of its data.
	 */
	void readInto(Buffer buffer) throws IOException;

	/**
	 * Read data into the buffer, assume without header.
	 */
	void readInto(Buffer buffer, long length) throws IOException;

	void seekToPosition(long position) throws IOException;

	boolean hasReachedEndOfFile();

}