// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.common.io;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class IOUtilsTest {
    @Test
    public void testSkipFully() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
        IOUtils.skipFully(in, 2);
        Assertions.assertEquals(3, in.read());
        IOUtils.skipFully(in, 0);
        IOUtils.skipFully(in, -1);
    }

    @Test
    public void testSkipFullyWithZeroProgress() throws IOException {
        InputStream in = new ZeroProgressInputStream(new byte[] {1, 2, 3});
        IOUtils.skipFully(in, 2);
        Assertions.assertEquals(3, in.read());
    }

    @Test
    public void testSkipFullyAtEof() {
        IOException exception = Assertions.assertThrows(IOException.class,
                () -> IOUtils.skipFully(new ZeroProgressInputStream(new byte[0]), 1));
        Assertions.assertEquals("Premature EOF from inputStream", exception.getMessage());
    }

    @Test
    public void testSkipFullyPastEof() {
        Assertions.assertThrows(IOException.class,
                () -> IOUtils.skipFully(new ZeroProgressInputStream(new byte[] {1}), 2));
    }

    @Test
    public void testSkipFullyWithNonPositiveLength() throws IOException {
        InputStream in = new ZeroProgressInputStream(new byte[] {1});
        IOUtils.skipFully(in, 0);
        IOUtils.skipFully(in, -1);
        Assertions.assertEquals(1, in.read());
    }

    @Test
    public void testSkipExceptionPropagation() {
        IOException expected = new IOException("skip failure");
        InputStream in = new InputStream() {
            @Override
            public long skip(long n) throws IOException {
                throw expected;
            }

            @Override
            public int read() {
                Assertions.fail("read should not follow a failed skip");
                return -1;
            }
        };
        Assertions.assertSame(expected,
                Assertions.assertThrows(IOException.class, () -> IOUtils.skipFully(in, 1)));
    }

    @Test
    public void testReadExceptionPropagation() {
        IOException expected = new IOException("read failure");
        InputStream in = new InputStream() {
            private boolean skipped;

            @Override
            public long skip(long n) {
                Assertions.assertFalse(skipped, "skip repeated without trying read");
                skipped = true;
                return 0;
            }

            @Override
            public int read() throws IOException {
                throw expected;
            }
        };
        Assertions.assertSame(expected,
                Assertions.assertThrows(IOException.class, () -> IOUtils.skipFully(in, 1)));
    }

    private static class ZeroProgressInputStream extends ByteArrayInputStream {
        private boolean skipped;

        private ZeroProgressInputStream(byte[] data) {
            super(data);
        }

        @Override
        public long skip(long n) {
            // A stream may make no progress in skip() even when read() still has data.
            // Fail deterministically instead of allowing the old loop to spin forever.
            Assertions.assertFalse(skipped, "skip repeated without trying read");
            skipped = true;
            return 0;
        }

        @Override
        public int read() {
            skipped = false;
            return super.read();
        }
    }
}
