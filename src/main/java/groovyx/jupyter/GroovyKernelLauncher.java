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

import org.dflib.jjava.jupyter.channels.JupyterConnection;
import org.dflib.jjava.jupyter.kernel.KernelConnectionProperties;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point invoked by Jupyter with the connection file as the only argument
 * (see the {@code argv} in kernel.json).
 */
public final class GroovyKernelLauncher {

    private GroovyKernelLauncher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: GroovyKernelLauncher <connection_file>");
            System.exit(2);
        }

        // default kernel-side logging (slf4j-simple) to warn — INFO chatter from
        // Grape's maven-resolver etc. otherwise floods notebook stderr; users can
        // override with -Dorg.slf4j.simpleLogger.defaultLogLevel=info in jvmArgs
        if (System.getProperty("org.slf4j.simpleLogger.defaultLogLevel") == null) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
        }

        String connectionFile = Files.readString(Path.of(args[0]));
        KernelConnectionProperties connProps = KernelConnectionProperties.parse(connectionFile);
        JupyterConnection connection = new JupyterConnection(connProps);

        GroovyKernel kernel = GroovyKernel.builder().build();
        kernel.onStartup();
        kernel.becomeHandlerForConnection(connection);

        connection.connect();
        connection.waitUntilClose();
    }
}
