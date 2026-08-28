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
import org.dflib.jjava.jupyter.kernel.ReplacementOptions;
import org.dflib.jjava.jupyter.kernel.display.DisplayData;
import org.dflib.jjava.jupyter.kernel.display.mime.MIMEType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyKernelTest {

    private final GroovyKernel kernel = GroovyKernel.builder().build();

    @Test
    void evalPipelineRendersTextResult() {
        DisplayData out = kernel.evalBuilder("6 * 7").resolveMagics().renderResults().eval();
        assertEquals("42", out.getData(MIMEType.TEXT_PLAIN));
    }

    @Test
    void nullResultRendersNothing() {
        DisplayData out = kernel.evalBuilder("x = 1\nnull").resolveMagics().renderResults().eval();
        assertNull(out);
    }

    @Test
    void hiddenConstantSuppressesOutput() {
        DisplayData out = kernel.evalBuilder("hi = 'there'\nHIDDEN").resolveMagics().renderResults().eval();
        assertNull(out);
        assertEquals("there", kernel.getEvaluator().getBinding().getVariable("hi"));
    }

    @Test
    void gstringResultsRenderAsText() {
        kernel.evalBuilder("who = 'world'").resolveMagics().eval();
        DisplayData out = kernel.evalBuilder("\"hi ${who}\"").resolveMagics().renderResults().eval();
        assertEquals("hi world", out.getData(MIMEType.TEXT_PLAIN));
    }

    @Test
    void percentSignIsNotAMagic() {
        DisplayData out = kernel.evalBuilder("7 % 4").resolveMagics().renderResults().eval();
        assertEquals("3", out.getData(MIMEType.TEXT_PLAIN));
    }

    @Test
    void completionOffersBindingVariablesAndKeywords() {
        kernel.evalBuilder("myCounter = 1").resolveMagics().eval();
        ReplacementOptions options = kernel.complete("myCou", 5);
        assertTrue(options.getReplacements().contains("myCounter"));
        assertEquals(0, options.getSourceStart());
        assertEquals(5, options.getSourceEnd());

        ReplacementOptions keywordOptions = kernel.complete("whi", 3);
        assertTrue(keywordOptions.getReplacements().contains("while"));
    }

    @Test
    void memberCompletionUsesTheMetaClass() {
        kernel.evalBuilder("txt = 'hello'").resolveMagics().eval();
        ReplacementOptions jdkMethod = kernel.complete("txt.toUp", 8);
        assertTrue(jdkMethod.getReplacements().contains("toUpperCase"));
        assertEquals(4, jdkMethod.getSourceStart());

        ReplacementOptions dgmMethod = kernel.complete("txt.rev", 7);
        assertTrue(dgmMethod.getReplacements().contains("reverse"));
    }

    @Test
    void powerAssertsRenderInErrors() {
        Throwable caught = org.junit.jupiter.api.Assertions.assertThrows(Throwable.class,
                () -> kernel.evalBuilder("assert 6 * 7 == 43").resolveMagics().eval());
        String rendered = String.join("\n", kernel.formatError(caught));
        assertTrue(rendered.contains("assert 6 * 7 == 43"), rendered);
        assertTrue(rendered.contains("42"), rendered);
    }

    @Test
    void interruptionRendersAsOneFriendlyLine() {
        String rendered = String.join("\n", kernel.formatError(new InterruptedException()));
        assertTrue(rendered.contains("Execution interrupted"));
    }

    @Test
    void isCompleteAnswers() {
        assertEquals(BaseKernel.IS_COMPLETE_YES, kernel.isComplete("println 'hi'"));
        assertEquals("", kernel.isComplete("if (true) {"));
    }

    @Test
    void bannerNamesGroovyAndJvmVersions() {
        String banner = kernel.getBanner();
        assertTrue(banner.contains("Groovy " + kernel.getLanguageInfo().getVersion()), banner);
        assertTrue(banner.contains("JDK " + System.getProperty("java.version")), banner);
        assertTrue(banner.contains(GroovyKernel.KERNEL_NAME), banner);
    }

    @Test
    void listOfMapsRendersAsHtmlTable() {
        Object rows = kernel.evalBuilder("[[name: 'a', v: 1], [name: 'b', v: 2]]").resolveMagics().eval();
        DisplayData out = kernel.getRenderer().render(rows);
        String html = String.valueOf(out.getData(MIMEType.parse("text/html")));
        assertTrue(html.contains("<table"), html);
        assertTrue(html.contains("<th>name</th>"), html);
        assertTrue(html.contains("<td>2</td>"), html);
        assertTrue(out.hasDataForType(MIMEType.TEXT_PLAIN)); // console fallback kept
    }

    @Test
    void mapsRenderAsKeyValueTable() {
        Object counts = kernel.evalBuilder("[alpha: 1, beta: 2]").resolveMagics().eval();
        String html = String.valueOf(kernel.getRenderer().render(counts).getData(MIMEType.parse("text/html")));
        assertTrue(html.contains("<td>alpha</td>"), html);
        assertTrue(html.contains("<td>2</td>"), html);
    }

    @Test
    void tableCellsAreHtmlEscaped() {
        Object rows = kernel.evalBuilder("[[snippet: '<script>alert(1)</script>']]").resolveMagics().eval();
        String html = String.valueOf(kernel.getRenderer().render(rows).getData(MIMEType.parse("text/html")));
        assertTrue(html.contains("&lt;script&gt;"), html);
        assertTrue(!html.contains("<script>"), html);
    }

    @Test
    void plainListsStayTextOnly() {
        Object list = kernel.evalBuilder("[1, 2, 3]").resolveMagics().eval();
        DisplayData out = kernel.getRenderer().render(list);
        assertTrue(!out.hasDataForType(MIMEType.parse("text/html")));
        assertEquals("[1, 2, 3]", out.getData(MIMEType.TEXT_PLAIN));
    }

    @Test
    void ginqResultsRenderAsHtmlTable() {
        Object queryable = kernel.evalBuilder("GQ { from n in [2, 3] select n, n * n }")
                .resolveMagics().eval();
        String html = String.valueOf(kernel.getRenderer().render(queryable).getData(MIMEType.parse("text/html")));
        assertTrue(html.contains("<table"), html);
        assertTrue(html.contains("<td>9</td>"), html);
    }

    @Test
    void grabbedExtensionsAreInstalledWhenClasspathGrows(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        // compile an Extension implementation into a directory, services file included,
        // then simulate a grab by adding that directory to the session classloader
        org.codehaus.groovy.control.CompilerConfiguration config = new org.codehaus.groovy.control.CompilerConfiguration();
        config.setTargetDirectory(dir.toFile());
        try (groovy.lang.GroovyClassLoader compiler = new groovy.lang.GroovyClassLoader(getClass().getClassLoader(), config)) {
            compiler.parseClass(
                    "class TestExt implements org.dflib.jjava.jupyter.Extension {\n"
                            + "    void install(org.dflib.jjava.jupyter.kernel.BaseKernel k) {\n"
                            + "        System.setProperty('groovyx.jupyter.testExt', 'installed')\n"
                            + "    }\n"
                            + "}", "TestExt.groovy");
        }
        java.nio.file.Path services = dir.resolve("META-INF/services");
        java.nio.file.Files.createDirectories(services);
        java.nio.file.Files.writeString(services.resolve("org.dflib.jjava.jupyter.Extension"), "TestExt\n");
        try {
            kernel.getEvaluator().getClassLoader().addURL(dir.toUri().toURL());
            kernel.evalBuilder("1 + 1").resolveMagics().eval();
            assertEquals("installed", System.getProperty("groovyx.jupyter.testExt"));
        } finally {
            System.clearProperty("groovyx.jupyter.testExt");
        }
    }

    @Test
    void languageInfoDescribesGroovy() {
        assertEquals("groovy", kernel.getLanguageInfo().getName());
        assertEquals(".groovy", kernel.getLanguageInfo().getFileExtension());
    }
}
