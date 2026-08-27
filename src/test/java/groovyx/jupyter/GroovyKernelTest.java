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
    void languageInfoDescribesGroovy() {
        assertEquals("groovy", kernel.getLanguageInfo().getName());
        assertEquals(".groovy", kernel.getLanguageInfo().getFileExtension());
    }
}
