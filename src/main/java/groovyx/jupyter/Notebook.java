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

import org.dflib.jjava.jupyter.kernel.BaseKernel;
import org.dflib.jjava.jupyter.kernel.BaseNotebookStatics;
import org.dflib.jjava.jupyter.kernel.display.DisplayData;

import java.util.Base64;
import java.util.UUID;

/**
 * Cell-facing helpers, statically star-imported into every cell. Inherits the
 * base statics ({@code display(o)} returning a display id, {@code updateDisplay(id, o)},
 * {@code render(o)}, {@code eval(src)}, {@code printf}) and adds direct mime
 * emitters. Display ids allow in-place updates — the primitive behind streaming
 * output, progress reporting and live chart refresh:
 *
 * <pre>{@code
 * id = display('working...')
 * // ... later ...
 * updateDisplay(id, 'done')
 * }</pre>
 */
public class Notebook extends BaseNotebookStatics {

    /**
     * End a cell with {@code HIDDEN} to suppress its output — the
     * self-documenting equivalent of ending with {@code null} (a null result
     * publishes nothing), and the successor to BeakerX's {@code OutputCell.HIDDEN}.
     */
    public static final Object HIDDEN = null;

    protected Notebook() {
    }

    public static String displayHtml(String html) {
        return displayMime("text/html", html);
    }

    public static String displaySvg(String svg) {
        return displayMime("image/svg+xml", svg);
    }

    public static String displayMarkdown(String markdown) {
        return displayMime("text/markdown", markdown);
    }

    public static String displayPng(byte[] bytes) {
        return displayMime("image/png", Base64.getEncoder().encodeToString(bytes));
    }

    public static void updateHtml(String id, String html) {
        updateMime(id, "text/html", html);
    }

    public static void updateSvg(String id, String svg) {
        updateMime(id, "image/svg+xml", svg);
    }

    public static void updateMarkdown(String id, String markdown) {
        updateMime(id, "text/markdown", markdown);
    }

    private static String displayMime(String mime, Object content) {
        DisplayData data = bundle(mime, content);
        data.setDisplayId(UUID.randomUUID().toString());
        BaseKernel.notebookKernel().display(data);
        return data.getDisplayId();
    }

    private static void updateMime(String id, String mime, Object content) {
        BaseKernel.notebookKernel().getIO().display.updateDisplay(id, bundle(mime, content));
    }

    private static DisplayData bundle(String mime, Object content) {
        DisplayData data = new DisplayData();
        data.putData(mime, content);
        data.putText(String.valueOf(content));
        return data;
    }
}
