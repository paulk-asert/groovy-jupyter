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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodClosure;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The evaluation engine for the Groovy kernel: a session-long {@link GroovyClassLoader}
 * plus a shared {@link Binding}, with each cell compiled as its own script.
 * <p>
 * Session semantics (phase 0):
 * <ul>
 *   <li>undeclared assignments ({@code x = 1}) AND top-level declarations
 *       ({@code def x = 1}, {@code int x = 1} — lifted by {@link DeclarationLifting})
 *       go to the shared binding and persist across cells;</li>
 *   <li>classes defined in a cell persist (they live in the session classloader);</li>
 *   <li>methods defined in a cell persist: they are registered in the binding as
 *       {@link MethodClosure}s, and Groovy's script method-missing fallback dispatches
 *       later calls to them;</li>
 *   <li>top-level imports are accumulated and re-applied to subsequent cells;</li>
 *   <li>{@code @Grab} resolves into the session classloader, so grabbed dependencies
 *       are session-wide;</li>
 *   <li>every cell is compiled with {@link ThreadInterrupt}, so a kernel interrupt
 *       actually stops runaway loops in cell code (loops inside third-party jars
 *       remain uninterruptible — restart the kernel for those);</li>
 *   <li>if the {@code groovy.jupyter.classOutputDir} system property or
 *       {@code GROOVY_JUPYTER_CLASS_DIR} environment variable is set, compiled cell
 *       classes are also written there (e.g. for Spark's
 *       {@code spark.repl.class.outputDir} class-shipping, or bytecode inspection).</li>
 * </ul>
 */
public class GroovyEvaluator {

    // Recognizes a top-level import line: import [static] fq.Name[.*] [as Alias]
    private static final Pattern IMPORT_LINE = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?([\\w$]+(?:\\.[\\w$]+)*(?:\\.\\*)?)(?:\\s+as\\s+([\\w$]+))?\\s*;?\\s*$",
            Pattern.MULTILINE);

    public enum Syntax {COMPLETE, INCOMPLETE, INVALID}

    private final Binding binding = new Binding();
    private final GroovyClassLoader loader;
    private final ImportCustomizer imports = new ImportCustomizer();
    private final Set<String> knownImports = new LinkedHashSet<>();
    private final AtomicInteger cellCounter = new AtomicInteger();
    private final Object evalLock = new Object();
    private Thread evalThread;

    public GroovyEvaluator() {
        this(GroovyEvaluator.class.getClassLoader());
    }

    public GroovyEvaluator(ClassLoader parent) {
        CompilerConfiguration config = new CompilerConfiguration();
        // cell-facing display helpers available without imports
        imports.addStaticStars("groovyx.jupyter.Notebook");
        config.addCompilationCustomizers(
                imports,
                new DeclarationLifting(),
                new ASTTransformationCustomizer(ThreadInterrupt.class));
        String classDir = System.getProperty("groovy.jupyter.classOutputDir",
                System.getenv("GROOVY_JUPYTER_CLASS_DIR"));
        if (classDir != null && !classDir.isBlank()) {
            File dir = new File(classDir);
            dir.mkdirs();
            config.setTargetDirectory(dir);
        }
        this.loader = new GroovyClassLoader(parent, config);
    }

    public Binding getBinding() {
        return binding;
    }

    public GroovyClassLoader getClassLoader() {
        return loader;
    }

    /**
     * Compiles and runs one cell. The returned value is the value of the last
     * expression in the cell (Groovy script semantics), or null. A cell containing
     * only declarations (classes, interfaces, etc.) is compiled into the session
     * classloader without running anything.
     */
    public Object eval(String source) {
        synchronized (evalLock) {
            evalThread = Thread.currentThread();
        }
        try {
            Class<?> parsed = loader.parseClass(source, "Cell" + cellCounter.incrementAndGet() + ".groovy");
            Object result = null;
            if (Script.class.isAssignableFrom(parsed)) {
                Script script = (Script) InvokerHelper.createScript(parsed, binding);
                result = script.run();
                registerCellMethods(script, parsed);
            }
            // only remember imports from cells that compiled (and, for scripts, ran)
            rememberImports(source);
            return result;
        } finally {
            synchronized (evalLock) {
                evalThread = null;
            }
            // don't leak a pending interrupt into the channel loop / next cell
            Thread.interrupted();
        }
    }

    /**
     * Interrupts the currently executing cell, if any. Cell code is compiled with
     * {@link ThreadInterrupt}, so loops and method entries in cell-defined code
     * observe the interrupt and abort with an InterruptedException.
     */
    public void interruptEval() {
        synchronized (evalLock) {
            if (evalThread != null) {
                evalThread.interrupt();
            }
        }
    }

    /**
     * Makes methods declared in a cell callable from later cells: each is bound
     * as a MethodClosure, which Groovy's script method-missing fallback invokes.
     */
    private void registerCellMethods(Script script, Class<?> scriptClass) {
        for (Method method : scriptClass.getDeclaredMethods()) {
            String name = method.getName();
            if (method.isSynthetic()
                    || !Modifier.isPublic(method.getModifiers())
                    || name.contains("$")
                    || name.equals("run")
                    || name.equals("main")) {
                continue;
            }
            binding.setVariable(name, new MethodClosure(script, name));
        }
    }

    /**
     * Syntax-only probe used to answer is_complete_request. Compiles to the
     * CONVERSION phase with the Grab transformation disabled so probing never
     * triggers dependency resolution.
     */
    public Syntax checkSyntax(String code) {
        CompilerConfiguration probeConfig = new CompilerConfiguration();
        probeConfig.setDisabledGlobalASTTransformations(Set.of("groovy.grape.GrabAnnotationTransformation"));
        try (GroovyClassLoader probeLoader = new GroovyClassLoader(loader, probeConfig)) {
            CompilationUnit unit = new CompilationUnit(probeConfig, null, probeLoader);
            unit.addSource("IsCompleteProbe.groovy", code);
            unit.compile(Phases.CONVERSION);
            return Syntax.COMPLETE;
        } catch (CompilationFailedException e) {
            // The Groovy parser reports code that stops mid-construct as "Missing '<token>'"
            // (or an explicit <EOF> mention); genuine errors report "Unexpected input/character".
            String message = String.valueOf(e.getMessage());
            return message.contains("Missing '") || message.contains("<EOF>")
                    ? Syntax.INCOMPLETE
                    : Syntax.INVALID;
        } catch (Exception e) {
            return Syntax.COMPLETE; // abstain on unexpected probe failures
        }
    }

    private void rememberImports(String source) {
        Matcher m = IMPORT_LINE.matcher(source);
        while (m.find()) {
            String key = (m.group(1) != null ? "static " : "") + m.group(2)
                    + (m.group(3) != null ? " as " + m.group(3) : "");
            if (!knownImports.add(key)) {
                continue;
            }
            boolean isStatic = m.group(1) != null;
            String target = m.group(2);
            String alias = m.group(3);
            if (isStatic) {
                if (target.endsWith(".*")) {
                    imports.addStaticStars(target.substring(0, target.length() - 2));
                } else {
                    int lastDot = target.lastIndexOf('.');
                    String className = target.substring(0, lastDot);
                    String member = target.substring(lastDot + 1);
                    if (alias != null) {
                        imports.addStaticImport(alias, className, member);
                    } else {
                        imports.addStaticImport(className, member);
                    }
                }
            } else {
                if (target.endsWith(".*")) {
                    imports.addStarImports(target.substring(0, target.length() - 2));
                } else if (alias != null) {
                    imports.addImport(alias, target);
                } else {
                    imports.addImports(target);
                }
            }
        }
    }
}
