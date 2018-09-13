/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.handler.codec.serialization;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */

/**
 * 这个类不太明白为啥只需要重载这些方法，不太明白各个方法的含义 ?zz?
 *
 * compact:
 * vt.	压紧，（使）坚实; 把…弄紧密，把…弄结实; 使（文体）简洁，简化; 变紧密，变结实;
 * adj.	紧凑的; 简洁的，（文体等）紧凑的; 小巧易携带的; （物质） 致密的，（体格）结实的;
 * n.	协议; 条约; 带镜小粉盒; 小汽车;
 */
class CompactObjectOutputStream extends ObjectOutputStream {

    static final int TYPE_PRIMITIVE = 0;
    static final int TYPE_NON_PRIMITIVE = 1;

    CompactObjectOutputStream(OutputStream out) throws IOException {
        super(out);
    }

    @Override
    protected void writeStreamHeader() throws IOException {
        writeByte(STREAM_VERSION);
    }

    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
        if (desc.forClass().isPrimitive()) {
            write(TYPE_PRIMITIVE);
            super.writeClassDescriptor(desc);
        } else {
            write(TYPE_NON_PRIMITIVE);
            writeUTF(desc.getName());
        }
    }
}
