<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

groovy-jupyter
==============

A [Jupyter](https://jupyter.org/) kernel for [Apache Groovy](https://groovy.apache.org/)&trade;.

Status: **phase 1 — session semantics and interrupts**: everything from the
phase-0 spike (execute, streamed output, text results, `@Grab` on Groovy 6's
maven-resolver Grape engine) plus: `def`/typed declarations now persist across
cells (declaration lifting), **interrupt actually stops runaway cell loops**
(cells compile with `ThreadInterrupt`), readable errors with power-assert
rendering and sanitized stack traces, member completion on binding variables
via the metaclass, stdin support, and an optional class-output directory
(`GROOVY_JUPYTER_CLASS_DIR`) for Spark class-shipping and bytecode inspection —
all verified end-to-end over the Jupyter wire protocol. The wider plan is still
being socialized with the Groovy community; design decisions below are
proposals, not commitments. The full plan-of-attack assessment is recorded in
[docs/assessment.html](docs/assessment.html).

Getting started
---------------

Requires JDK 17+ (kernelspec records the installing JVM) and any Jupyter
frontend (JupyterLab, Jupyter Notebook, VS Code, `jupyter console`).

```
./gradlew installKernelSpec
```

installs the kernel jars and `kernel.json` into the per-user Jupyter kernels
directory. Then pick "Groovy" as the kernel in your frontend, or run
`jupyter console --kernel groovy`.

Current limitations: results render as text only (rich mime display is the
next phase); interrupts are cooperative — loops inside third-party jars can't
be stopped (restart the kernel); tuple declarations (`def (a, b) = ...`) stay
cell-local; completion doesn't yet chain through expressions.

Design sketch
-------------

* A deliberately minimal kernel core speaking the Jupyter wire protocol,
  built on the [jjava-jupyter](https://github.com/dflib/jjava) base module (Apache-2.0)
* Targets Groovy 6 on a recent JDK (25 LTS)
* Dependencies arrive via `@Grab`, backed by Groovy 6's maven-resolver-based
  `groovy-grape-maven` engine — no bundled libraries
* Cells compiled with the `ThreadInterrupt` customizer, so interrupting a
  runaway cell actually works on modern JDKs
* Display via pass-through Jupyter mime bundles (HTML, SVG, PNG, Vega-Lite JSON)
  with a ServiceLoader extension SPI so libraries self-register their own
  renderers — no custom JavaScript, no widgets

License
-------

This project is licensed under the [Apache License, Version 2.0](LICENSE).
