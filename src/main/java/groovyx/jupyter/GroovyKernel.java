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

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StackTraceUtils;
import org.dflib.jjava.jupyter.messages.Header;
import org.dflib.jjava.jupyter.kernel.BaseKernel;
import org.dflib.jjava.jupyter.kernel.BaseKernelBuilder;
import org.dflib.jjava.jupyter.kernel.HelpLink;
import org.dflib.jjava.jupyter.kernel.JupyterIO;
import org.dflib.jjava.jupyter.kernel.LanguageInfo;
import org.dflib.jjava.jupyter.kernel.ReplacementOptions;
import org.dflib.jjava.jupyter.kernel.comm.CommManager;
import org.dflib.jjava.jupyter.kernel.display.DisplayData;
import org.dflib.jjava.jupyter.kernel.display.Renderer;
import org.dflib.jjava.jupyter.kernel.history.HistoryManager;
import org.dflib.jjava.jupyter.kernel.magic.MagicTranspiler;
import org.dflib.jjava.jupyter.kernel.magic.MagicsRegistry;
import org.dflib.jjava.jupyter.kernel.magic.MagicsResolver;
import org.dflib.jjava.jupyter.kernel.util.StringStyler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Jupyter kernel for the Apache Groovy programming language.
 * <p>
 * Phase 0 scope: execute, text display via the renderer, rudimentary completion
 * (binding variables and keywords), syntax-based is_complete. Inspection,
 * interrupts, magics and declaration lifting are later phases.
 */
public class GroovyKernel extends BaseKernel {

    static final String KERNEL_NAME = "groovy-jupyter";
    static final String KERNEL_VERSION = "0.1.0-SNAPSHOT";

    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "def", "default", "do", "double", "else", "enum", "extends", "false", "final",
            "finally", "float", "for", "if", "implements", "import", "in", "instanceof", "int", "interface",
            "long", "native", "new", "null", "package", "private", "protected", "public", "record", "return",
            "short", "static", "super", "switch", "synchronized", "this", "throw", "throws", "trait",
            "transient", "true", "try", "var", "void", "volatile", "while");

    // "<binding variable>.<member prefix>" immediately before the cursor
    private static final Pattern MEMBER_ACCESS =
            Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\.([A-Za-z0-9_$]*)$");

    private static final int MAX_TRACE_LINES = 20;

    private final GroovyEvaluator evaluator;
    private int knownLoaderUrlCount = -1;

    protected GroovyKernel(
            String name,
            String version,
            LanguageInfo languageInfo,
            List<HelpLink> helpLinks,
            HistoryManager historyManager,
            JupyterIO io,
            CommManager commManager,
            Renderer renderer,
            MagicsResolver magicsResolver,
            MagicsRegistry magicsRegistry,
            boolean extensionsEnabled,
            StringStyler errorStyler,
            GroovyEvaluator evaluator) {
        super(name, version, languageInfo, helpLinks, historyManager, io, commManager, renderer,
                magicsResolver, magicsRegistry, extensionsEnabled, errorStyler);
        this.evaluator = evaluator;
        GroovyDisplays.registerAll(renderer);
    }

    public static Builder builder() {
        return new Builder();
    }

    public GroovyEvaluator getEvaluator() {
        return evaluator;
    }

    @Override
    public String getBanner() {
        return String.format("Groovy %s · JDK %s (%s) · %s %s · Jupyter protocol v%s",
                GroovySystem.getVersion(),
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                name, version,
                Header.PROTOCOL_VERISON);
    }

    @Override
    protected Object doEval(String source) {
        Object result;
        try {
            result = evaluator.eval(source);
        } catch (Error e) {
            // The base execute handler catches only Exception; an escaping Error
            // (AssertionError from a failed power assert, OutOfMemoryError, ...)
            // would kill the shell-channel loop and hang the kernel. Wrap it so the
            // normal error path applies; formatError unwraps for display.
            throw new CellError(e);
        }
        // A grab may have grown the session classpath: newly reachable Extension
        // services (renderer SPI jars) get installed, idempotently.
        rescanExtensionsIfClasspathGrew();
        // Normalize Groovy CharSequence flavors (GString et al.): the base Text renderer
        // registers for CharSequence and puts the raw object into the mime bundle, which
        // Gson would serialize structurally (GStringImpl internals) instead of as text.
        if (result instanceof CharSequence && !(result instanceof String)) {
            return result.toString();
        }
        return result;
    }

    private void rescanExtensionsIfClasspathGrew() {
        if (!extensionsEnabled) {
            return;
        }
        int urlCount = evaluator.getClassLoader().getURLs().length;
        if (urlCount != knownLoaderUrlCount) {
            knownLoaderUrlCount = urlCount;
            installExtensions(evaluator.getClassLoader());
        }
    }

    /** Carries a JVM Error out of cell execution as an Exception the base kernel can handle. */
    public static class CellError extends RuntimeException {
        public CellError(Error cause) {
            super(cause.toString(), cause);
        }
    }

    @Override
    public DisplayData inspect(String code, int at, boolean extraDetail) {
        return null; // later phase
    }

    @Override
    public void interrupt() {
        evaluator.interruptEval();
    }

    @Override
    protected List<String> formatError(Throwable e) {
        if (e instanceof InterruptedException) {
            return List.of(errorStyler.secondary("Execution interrupted."));
        }
        if (e instanceof CellError && e.getCause() != null) {
            e = e.getCause();
        }
        Throwable sanitized = StackTraceUtils.deepSanitize(e);
        List<String> lines = new ArrayList<>(
                // multi-line messages (power asserts!) render as the primary block
                errorStyler.primaryLines(sanitized.getClass().getSimpleName()
                        + (sanitized.getMessage() != null ? ": " + sanitized.getMessage() : "")));
        for (StackTraceElement frame : sanitized.getStackTrace()) {
            String cls = frame.getClassName();
            if (cls.startsWith("groovyx.jupyter.") || cls.startsWith("org.dflib.jjava.")) {
                break; // kernel plumbing is noise to a notebook user
            }
            lines.add(errorStyler.secondary("at " + frame));
            if (lines.size() >= MAX_TRACE_LINES) {
                lines.add(errorStyler.secondary("..."));
                break;
            }
        }
        Throwable cause = sanitized.getCause();
        if (cause != null && cause != sanitized) {
            lines.add(errorStyler.secondary("Caused by: " + cause));
        }
        return lines;
    }

    @Override
    public ReplacementOptions complete(String code, int at) {
        ReplacementOptions members = completeMember(code, at);
        if (members != null) {
            return members;
        }
        int start = at;
        while (start > 0 && Character.isJavaIdentifierPart(code.charAt(start - 1))) {
            start--;
        }
        if (start == at || (start > 0 && code.charAt(start - 1) == '.')) {
            // no prefix, or a dot-completion we couldn't resolve to a binding variable
            return null;
        }
        String prefix = code.substring(start, at);
        Set<String> matches = new TreeSet<>();
        for (Object variable : evaluator.getBinding().getVariables().keySet()) {
            String name = String.valueOf(variable);
            if (name.startsWith(prefix)) {
                matches.add(name);
            }
        }
        for (String keyword : KEYWORDS) {
            if (keyword.startsWith(prefix)) {
                matches.add(keyword);
            }
        }
        return matches.isEmpty() ? null : new ReplacementOptions(new ArrayList<>(matches), start, at);
    }

    /**
     * Member completion for {@code bindingVar.<prefix>}: offers the runtime
     * metaclass's methods and properties (including Groovy extension methods).
     */
    private ReplacementOptions completeMember(String code, int at) {
        Matcher m = MEMBER_ACCESS.matcher(code.substring(0, at));
        if (!m.find()) {
            return null;
        }
        String varName = m.group(1);
        String prefix = m.group(2);
        if (!evaluator.getBinding().hasVariable(varName)) {
            return null;
        }
        Object value = evaluator.getBinding().getVariable(varName);
        if (value == null) {
            return null;
        }
        MetaClass metaClass = InvokerHelper.getMetaClass(value);
        Set<String> matches = new TreeSet<>();
        for (MetaMethod method : metaClass.getMethods()) {
            addMatch(matches, method.getName(), prefix);
        }
        for (MetaMethod method : metaClass.getMetaMethods()) {
            addMatch(matches, method.getName(), prefix);
        }
        for (MetaProperty property : metaClass.getProperties()) {
            addMatch(matches, property.getName(), prefix);
        }
        return matches.isEmpty() ? null
                : new ReplacementOptions(new ArrayList<>(matches), at - prefix.length(), at);
    }

    private static void addMatch(Set<String> matches, String name, String prefix) {
        if (name.startsWith(prefix) && !name.contains("$")) {
            matches.add(name);
        }
    }

    @Override
    public String isComplete(String code) {
        switch (evaluator.checkSyntax(code)) {
            case COMPLETE:
                return IS_COMPLETE_YES;
            case INCOMPLETE:
                return ""; // incomplete, continue on next line with no indent
            case INVALID:
            default:
                return IS_COMPLETE_BAD;
        }
    }

    @Override
    protected ClassLoader getClassLoader() {
        // extensions (renderer SPI jars arriving via @Grab) load against the session classloader
        return evaluator.getClassLoader();
    }

    public static class Builder extends BaseKernelBuilder<Builder, GroovyKernel> {

        private Builder() {
        }

        @Override
        public GroovyKernel build() {
            LanguageInfo languageInfo = new LanguageInfo.Builder("groovy")
                    .version(GroovySystem.getVersion())
                    .mimetype("text/x-groovy")
                    .fileExtension(".groovy")
                    .pygments("groovy")
                    .codemirror("groovy")
                    .build();

            return new GroovyKernel(
                    name != null ? name : KERNEL_NAME,
                    version != null ? version : KERNEL_VERSION,
                    languageInfo,
                    buildHelpLinks(),
                    buildHistoryManager(),
                    buildJupyterIO(buildJupyterIOEncoding()),
                    buildCommManager(),
                    buildRenderer(),
                    buildNoOpMagicsResolver(),
                    buildMagicsRegistry(),
                    buildExtensionsEnabled(),
                    buildErrorStyler(),
                    new GroovyEvaluator());
        }

        protected List<HelpLink> buildHelpLinks() {
            return List.of(
                    new HelpLink("Apache Groovy documentation", "https://groovy-lang.org/documentation.html"),
                    new HelpLink("groovy-jupyter", "https://github.com/paulk-asert/groovy-jupyter"));
        }

        /**
         * Phase 0 has no magics ({@code @Grab} covers dependencies), so cell source
         * passes through untouched — a bare {@code %} in Groovy code must never be
         * misparsed as a magic.
         */
        protected MagicsResolver buildNoOpMagicsResolver() {
            return new MagicsResolver(" %", " %%", new MagicTranspiler()) {
                @Override
                public String resolve(String cellSource) {
                    return cellSource;
                }
            };
        }
    }
}
