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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyEvaluatorTest {

    private final GroovyEvaluator evaluator = new GroovyEvaluator();

    @Test
    void evaluatesExpression() {
        assertEquals(42, evaluator.eval("6 * 7"));
    }

    @Test
    void emptyResultIsNull() {
        assertNull(evaluator.eval("null"));
    }

    @Test
    void undeclaredVariablesPersistAcrossCells() {
        evaluator.eval("x = 5");
        assertEquals(6, evaluator.eval("x + 1"));
    }

    @Test
    void classesPersistAcrossCells() {
        evaluator.eval("class Point { int x; int y }");
        assertEquals(3, evaluator.eval("new Point(x: 1, y: 2).x + new Point(x: 2).x"));
    }

    @Test
    void importsPersistAcrossCells() {
        evaluator.eval("import java.time.LocalDate");
        assertEquals(2026, evaluator.eval("LocalDate.of(2026, 8, 27).year"));
    }

    @Test
    void staticImportsPersistAcrossCells() {
        evaluator.eval("import static java.lang.Math.max");
        assertEquals(4, evaluator.eval("max(3, 4)"));
    }

    @Test
    void aliasedImportsPersistAcrossCells() {
        evaluator.eval("import java.time.LocalDate as LD");
        assertEquals(8, evaluator.eval("LD.of(2026, 8, 27).monthValue"));
    }

    @Test
    void repeatingAnImportIsHarmless() {
        evaluator.eval("import java.time.LocalDate");
        evaluator.eval("import java.time.LocalDate");
        assertEquals(2026, evaluator.eval("LocalDate.of(2026, 1, 1).year"));
    }

    @Test
    void defDeclarationsAreLiftedToTheSession() {
        evaluator.eval("def y = 10");
        assertEquals(11, evaluator.eval("y + 1"));
    }

    @Test
    void typedDeclarationsAreLiftedWithCoercion() {
        evaluator.eval("long total = 5");
        assertEquals("Long", evaluator.eval("total.class.simpleName"));
        assertEquals(6L, evaluator.eval("total + 1"));
    }

    @Test
    void declarationsWithoutInitializerGetDefaults() {
        evaluator.eval("def z");
        assertEquals(true, evaluator.eval("z == null"));
        evaluator.eval("int k");
        assertEquals(0, evaluator.eval("k"));
    }

    @Test
    void nestedDeclarationsStayLocal() {
        evaluator.eval("if (true) { def local = 99 }");
        assertFalse(evaluator.getBinding().hasVariable("local"));
    }

    @Test
    void fieldAnnotatedDeclarationsAreNotLifted() {
        assertEquals(14, evaluator.eval("@groovy.transform.Field int fx = 7\nfx * 2"));
    }

    @Test
    void tupleDeclarationsRemainCellLocal() {
        assertEquals(3, evaluator.eval("def (a, b) = [1, 2]\na + b"));
        assertFalse(evaluator.getBinding().hasVariable("a"));
    }

    @Test
    void closuresCaptureLiftedVariables() {
        evaluator.eval("def base = 40");
        evaluator.eval("addTwo = { base + 2 }");
        assertEquals(42, evaluator.eval("addTwo()"));
    }

    @Test
    void recordsPersistAcrossCells() {
        evaluator.eval("record Pt(int x, int y) {}");
        assertEquals(5, evaluator.eval("new Pt(1, 2).x + new Pt(3, 4).y"));
    }

    @Test
    void interfacesAndCoercionsWorkAcrossCells() {
        evaluator.eval("interface Greeter { String greet(String n) }");
        assertEquals("hi groovy", evaluator.eval("g = { n -> 'hi ' + n } as Greeter\ng.greet('groovy')"));
    }

    @Test
    void annotatedMethodsPersistAcrossCells() {
        evaluator.eval("import java.lang.annotation.*\n"
                + "@Retention(RetentionPolicy.RUNTIME) @interface Doc { String value() }");
        evaluator.eval("@Doc('adds') int add2(int a, int b) { a + b }");
        assertEquals(42, evaluator.eval("add2(20, 22)"));
    }

    @Test
    @Timeout(30)
    void interruptStopsHotLoop() throws Exception {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                evaluator.eval("i = 0\nwhile (true) { i++ }");
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        runner.start();
        Thread.sleep(800);
        evaluator.interruptEval();
        runner.join(10_000);
        assertFalse(runner.isAlive());
        assertInstanceOf(InterruptedException.class, thrown.get());
    }

    @Test
    void kernelImplementationDependenciesAreHiddenFromCells() {
        Object visibility = evaluator.eval(
                "try { Class.forName('com.fasterxml.jackson.databind.ObjectMapper', false, this.class.classLoader); 'visible' }\n"
                        + "catch (ClassNotFoundException e) { 'hidden' }");
        assertEquals("hidden", visibility);
    }

    @Test
    void shippedGroovyModulesStillWorkOverHiddenDependencies() {
        // groovy-csv is cell-visible and internally links its (hidden) Jackson
        assertEquals(1, evaluator.eval(
                "new groovy.csv.CsvSlurper().parseText('a,b\\n1,2').size()"));
    }

    @Test
    void contextClassLoaderIsTheSessionLoaderDuringEval() {
        Object tccl = evaluator.eval("Thread.currentThread().contextClassLoader");
        assertEquals(evaluator.getClassLoader(), tccl);
        // and restored afterwards
        assertEquals(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
    }

    @Test
    void classOutputDirWritesCellClasses(@TempDir Path dir) {
        System.setProperty("groovy.jupyter.classOutputDir", dir.toString());
        try {
            GroovyEvaluator dumping = new GroovyEvaluator();
            dumping.eval("class Dumped { int v }");
            assertTrue(Files.exists(dir.resolve("Dumped.class")));
        } finally {
            System.clearProperty("groovy.jupyter.classOutputDir");
        }
    }

    @Test
    void bindingStyleMethodsSeeBindingVariables() {
        // the BeakerX-corpus style: undeclared vars + dynamic script methods
        evaluator.eval("factor = 2");
        evaluator.eval("int scale(int n) { n * factor }");
        assertEquals(10, evaluator.eval("scale(5)"));
    }

    @Test
    void syntaxCheckDistinguishesCompleteIncompleteInvalid() {
        assertEquals(GroovyEvaluator.Syntax.COMPLETE, evaluator.checkSyntax("6 * 7"));
        assertEquals(GroovyEvaluator.Syntax.INCOMPLETE, evaluator.checkSyntax("for (i in 1..3) {"));
    }

    @Test
    @Tag("network")
    void grabResolvesIntoSessionClassLoader() {
        Object result = evaluator.eval(
                "@Grab('org.apache.commons:commons-lang3:3.17.0')\n" +
                        "import org.apache.commons.lang3.StringUtils\n" +
                        "StringUtils.capitalize('groovy')");
        assertEquals("Groovy", result);
        // and the grabbed classes stay available in later cells
        assertEquals("GROOVY", evaluator.eval("StringUtils.upperCase('groovy')"));
    }
}
