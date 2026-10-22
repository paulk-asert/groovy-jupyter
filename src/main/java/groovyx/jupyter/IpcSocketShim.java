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
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges a socket-naming mismatch for the {@code ipc} transport (Unix domain sockets, used e.g. by
 * hosted Google Colab). Jupyter names each channel socket {@code {ip}-{port}}, but jjava-jupyter up to
 * 1.0-a8 binds {@code {ip}:{port}} (<a href="https://github.com/dflib/jjava/issues/134">dflib/jjava#134</a>).
 * After the channels are bound, this creates a {@code {ip}-{port}} symlink to each {@code {ip}:{port}}
 * socket and removes the links on shutdown. It is a no-op for {@code tcp}, and for a jjava-jupyter that
 * already binds the Jupyter name (no {@code :} socket to link) — so it can stay until that fix is released.
 * <p>
 * A kernel that dies without running its shutdown hooks (SIGKILL, OOM) leaves its {@code {ip}:{port}}
 * sockets behind. Jupyter only checks {@code {ip}-{port}} when picking ports, so it can hand the same
 * ports to the next kernel, whose bind would then fail with "Address already in use";
 * {@link #clearStale} removes such leftovers before binding.
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
            if (Files.exists(bound) && !Files.exists(expected, LinkOption.NOFOLLOW_LINKS)) {
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

    /**
     * Deletes leftover {@code {ip}:{port}} sockets of dead kernels, before the channels bind. A socket
     * counts as stale when its Jupyter-named {@code {ip}-{port}} path is absent: Jupyter picked the port
     * because that path was free, and a live kernel of ours always has the link. Only sockets are removed,
     * never regular files. Returns the paths deleted (possibly none).
     */
    static List<Path> clearStale(KernelConnectionProperties props) {
        List<Path> removed = new ArrayList<>();
        if (!"ipc".equalsIgnoreCase(props.getTransport())) {
            return removed;
        }
        int[] ports = {props.getShellPort(), props.getIopubPort(), props.getStdinPort(), props.getControlPort(), props.getHbPort()};
        for (int port : ports) {
            Path bound = Path.of(props.getIp() + ":" + port);
            Path expected = Path.of(props.getIp() + "-" + port);
            try {
                if (Files.exists(expected, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                BasicFileAttributes attrs = Files.readAttributes(bound, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isOther()) { // a socket (not a file, directory or link)
                    Files.delete(bound);
                    removed.add(bound);
                    System.err.println("groovy-jupyter: removed stale ipc socket " + bound);
                }
            } catch (NoSuchFileException ignored) {
                // nothing left behind for this channel
            } catch (IOException e) {
                System.err.println("groovy-jupyter: could not remove stale ipc socket " + bound + ": " + e);
            }
        }
        return removed;
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort at shutdown
        }
    }
}
