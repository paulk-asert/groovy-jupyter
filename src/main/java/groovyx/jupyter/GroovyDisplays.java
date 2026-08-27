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

import org.codehaus.groovy.runtime.InvokerHelper;
import org.dflib.jjava.jupyter.kernel.display.RenderContext;
import org.dflib.jjava.jupyter.kernel.display.Renderer;
import org.dflib.jjava.jupyter.kernel.display.mime.MIMEType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in rich renderers for common Groovy data shapes, emitted as plain mime
 * bundles (no frontend-specific JavaScript, per the project's display policy):
 * <ul>
 *   <li>{@code List<Map>} rows (e.g. {@code CsvSlurper} output) → HTML table;</li>
 *   <li>{@code Map} → two-column HTML table;</li>
 *   <li>GINQ {@code Queryable} results (incl. {@code NamedRecord} rows) → HTML table.</li>
 * </ul>
 * Every bundle keeps the {@code text/plain} fallback the base renderer adds, so
 * console frontends still see useful output.
 */
public final class GroovyDisplays {

    static final int MAX_ROWS = 100;

    private static final String GINQ_QUERYABLE =
            "org.apache.groovy.ginq.provider.collection.runtime.Queryable";

    private GroovyDisplays() {
    }

    @SuppressWarnings("unchecked")
    public static void registerAll(Renderer renderer) {
        renderer.createRegistration(List.class)
                .preferring(MIMEType.TEXT_HTML)
                .register(GroovyDisplays::renderRows);
        renderer.createRegistration(Map.class)
                .preferring(MIMEType.TEXT_HTML)
                .register(GroovyDisplays::renderMap);
        try {
            Class<Object> queryable = (Class<Object>) Class.forName(GINQ_QUERYABLE);
            renderer.createRegistration(queryable)
                    .preferring(MIMEType.TEXT_HTML)
                    .register(GroovyDisplays::renderQueryable);
        } catch (ClassNotFoundException ignored) {
            // groovy-ginq not on the classpath; nothing to register
        }
    }

    private static void renderRows(List<?> rows, RenderContext context) {
        String html = tableFor(rows);
        if (html != null) {
            context.renderIfRequested(MIMEType.TEXT_HTML, () -> html);
        }
        // non-tabular lists fall through to the text/plain fallback
    }

    private static void renderMap(Map<?, ?> map, RenderContext context) {
        context.renderIfRequested(MIMEType.TEXT_HTML, () -> {
            StringBuilder sb = new StringBuilder("<table><thead><tr><th>key</th><th>value</th></tr></thead><tbody>");
            int shown = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (shown++ == MAX_ROWS) {
                    break;
                }
                sb.append("<tr><td>").append(escape(e.getKey()))
                        .append("</td><td>").append(escape(e.getValue())).append("</td></tr>");
            }
            sb.append("</tbody></table>");
            appendTruncationNote(sb, map.size());
            return sb.toString();
        });
    }

    private static void renderQueryable(Object queryable, RenderContext context) {
        List<?> rows = (List<?>) InvokerHelper.invokeMethod(queryable, "toList", null);
        String html = tableFor(rows);
        if (html == null) {
            // single-column result: one value per row
            StringBuilder sb = new StringBuilder("<table><tbody>");
            int shown = 0;
            for (Object row : rows) {
                if (shown++ == MAX_ROWS) {
                    break;
                }
                sb.append("<tr><td>").append(escape(row)).append("</td></tr>");
            }
            sb.append("</tbody></table>");
            appendTruncationNote(sb, rows.size());
            html = sb.toString();
        }
        String result = html;
        context.renderIfRequested(MIMEType.TEXT_HTML, () -> result);
    }

    /**
     * Renders a list of "row-shaped" objects (Maps, or GINQ NamedTuples) as an
     * HTML table; returns null if the list isn't tabular.
     */
    private static String tableFor(List<?> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        Set<String> columns = new LinkedHashSet<>();
        List<Object> sample = new ArrayList<>();
        int shown = 0;
        for (Object row : rows) {
            if (columnsOf(row) == null) {
                return null;
            }
            if (shown++ < MAX_ROWS) {
                sample.add(row);
            }
            columns.addAll(columnsOf(row));
        }
        StringBuilder sb = new StringBuilder("<table><thead><tr>");
        for (String column : columns) {
            sb.append("<th>").append(escape(column)).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        for (Object row : sample) {
            sb.append("<tr>");
            for (String column : columns) {
                sb.append("<td>").append(escape(cellOf(row, column))).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        appendTruncationNote(sb, rows.size());
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> columnsOf(Object row) {
        if (row instanceof Map) {
            List<String> names = new ArrayList<>();
            ((Map<?, ?>) row).keySet().forEach(k -> names.add(String.valueOf(k)));
            return names;
        }
        if (row != null && hasNameList(row.getClass())) {
            return (List<String>) InvokerHelper.invokeMethod(row, "getNameList", null);
        }
        return null;
    }

    private static Object cellOf(Object row, String column) {
        if (row instanceof Map) {
            return ((Map<?, ?>) row).get(column);
        }
        return InvokerHelper.invokeMethod(row, "get", column);
    }

    private static boolean hasNameList(Class<?> type) {
        try {
            type.getMethod("getNameList");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static void appendTruncationNote(StringBuilder sb, int totalRows) {
        if (totalRows > MAX_ROWS) {
            sb.append("<p><em>… showing first ").append(MAX_ROWS)
                    .append(" of ").append(totalRows).append(" rows</em></p>");
        }
    }

    private static String escape(Object value) {
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
