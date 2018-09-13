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
package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;

// 奇怪，这个类名中有"Wrapped"，怎么不与WrappedByteBuf类相似呢，不应该也有UnpooledUnsafeDirectByteBuf成员吗 ?zz?
final class WrappedUnpooledUnsafeDirectByteBuf extends UnpooledUnsafeDirectByteBuf {

    WrappedUnpooledUnsafeDirectByteBuf(ByteBufAllocator alloc, long memoryAddress, int size, boolean doFree) {
        super(alloc, PlatformDependent.directBuffer(memoryAddress, size), size, doFree);
    }

    @Override
    protected void freeDirect(ByteBuffer buffer) {
        PlatformDependent.freeMemory(memoryAddress);
    }
}
