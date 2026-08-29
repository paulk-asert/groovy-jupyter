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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges a socket-naming mismatch for the {@code ipc} transport (Unix domain sockets, used e.g. by
 * hosted Google Colab). Jupyter names each channel socket {@code {ip}-{port}}, but jjava-jupyter up to
 * 1.0-a8 binds {@code {ip}:{port}} (<a href="https://github.com/dflib/jjava/issues/134">dflib/jjava#134</a>).
 * After the channels are bound, this creates a {@code {ip}-{port}} symlink to each {@code {ip}:{port}}
 * socket and removes the links on shutdown. It is a no-op for {@code tcp}, and for a jjava-jupyter that
 * already binds the Jupyter name (no {@code :} socket to link) — so it can stay until that fix is released.
 */
final class IpcSocketShim {

    private IpcSocketShim() {
    }

    /** Creates the links for an ipc connection and registers their removal at JVM exit. */
    static void install(KernelConnectionProperties props) {
        List<Path> links = link(props);
        if (!links.isEmpty()) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> links.forEach(IpcSocketShim::deleteQuietly)));
        }
    }

    /**
     * Creates {@code {ip}-{port}} → {@code {ip}:{port}} links for every channel whose {@code :} socket
     * exists and whose Jupyter-named path does not. Returns the links created (possibly none).
     */
    static List<Path> link(KernelConnectionProperties props) {
        List<Path> created = new ArrayList<>();
        if (!"ipc".equalsIgnoreCase(props.getTransport())) {
            return created;
        }
        int[] ports = {props.getShellPort(), props.getIopubPort(), props.getStdinPort(), props.getControlPort(), props.getHbPort()};
        for (int port : ports) {
            Path bound = Path.of(props.getIp() + ":" + port);
            Path expected = Path.of(props.getIp() + "-" + port);
            if (Files.exists(bound) && !Files.exists(expected, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createSymbolicLink(expected, bound.toAbsolutePath());
                    created.add(expected);
                } catch (IOException | UnsupportedOperationException e) {
                    System.err.println("groovy-jupyter: could not link ipc socket " + expected + " -> " + bound + ": " + e);
                }
            }
        }
        return created;
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort at shutdown
        }
    }
}
