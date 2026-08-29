/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package groovyx.jupyter;

import org.dflib.jjava.jupyter.kernel.KernelConnectionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpcSocketShimTest {

    private static KernelConnectionProperties props(String transport, String ip) {
        // (ip, control, shell, stdin, hb, iopub, transport, signatureScheme, key)
        return new KernelConnectionProperties(ip, 4, 1, 3, 5, 2, transport, "hmac-sha256", "");
    }

    @Test
    void linksJupyterNamesToBoundSockets(@TempDir Path dir) throws IOException {
        String base = dir.resolve("kernel-abc").toString();
        for (int port = 1; port <= 5; port++) {
            Files.createFile(Path.of(base + ":" + port)); // stands in for the socket jjava-jupyter bound
        }

        List<Path> links = IpcSocketShim.link(props("ipc", base));

        assertEquals(5, links.size());
        for (int port = 1; port <= 5; port++) {
            Path link = Path.of(base + "-" + port);
            assertTrue(Files.isSymbolicLink(link), link + " should be a symlink");
            assertEquals(Path.of(base + ":" + port).toAbsolutePath(), Files.readSymbolicLink(link));
        }
    }

    @Test
    void noOpWhenKernelAlreadyBindsJupyterNames(@TempDir Path dir) throws IOException {
        String base = dir.resolve("kernel-abc").toString();
        for (int port = 1; port <= 5; port++) {
            Files.createFile(Path.of(base + "-" + port)); // jjava-jupyter with dflib/jjava#134 fixed
        }

        assertTrue(IpcSocketShim.link(props("ipc", base)).isEmpty());
        for (int port = 1; port <= 5; port++) {
            assertFalse(Files.isSymbolicLink(Path.of(base + "-" + port)));
        }
    }

    @Test
    void noOpForTcp(@TempDir Path dir) {
        assertTrue(IpcSocketShim.link(props("tcp", "127.0.0.1")).isEmpty());
        assertEquals(0, dir.toFile().list().length);
    }
}
