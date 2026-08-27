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

import groovy.lang.MissingPropertyException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void defDeclarationsAreCellLocalInPhase0() {
        // Phase 0 documents script semantics: def makes a local that evaporates.
        // Declaration lifting (a later phase) will change this.
        evaluator.eval("def y = 10");
        assertThrows(MissingPropertyException.class, () -> evaluator.eval("y"));
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
