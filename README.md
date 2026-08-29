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

[![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/paulk-asert/groovy-jupyter/main?labpath=notebooks%2Fwhiskey.ipynb)

A [Jupyter](https://jupyter.org/) kernel for [Apache Groovy](https://groovy.apache.org/)&trade;.
**Try it without installing anything** — the Binder badge above launches
JupyterLab in your browser with the kernel (Groovy 6 on JDK 25) and the
example notebooks ready to run.

Status: **phases 0–4 (initial cut) — a working kernel, packaged and
showcased**. The kernel: execute with session semantics (`def` lifting,
classes/methods/imports persisting across cells), `@Grab` on Groovy 6's
maven-resolver Grape engine, **working interrupts** (cells compile with
`ThreadInterrupt`), power-assert error rendering, member completion, stdin,
and rich display — `List<Map>` rows, `Map`s and GINQ results render as HTML
tables automatically; `display`/`displaySvg`/`displayHtml`/... helpers with
in-place updates; renderer extensions self-register from `@Grab`-ed jars via
a ServiceLoader SPI; `groovy-csv` and `groovy-ginq` ship with the kernel.
Runs on **JDK 25**. Packaging: a portable kernelspec zip
(`./gradlew kernelSpecZip`) and a working
[Binder deployment](https://mybinder.org/v2/gh/paulk-asert/groovy-jupyter/main?labpath=notebooks%2Fwhiskey.ipynb);
five example notebooks (clustering, classification, regression, NLP,
time-series) ported from the BeakerX era, with a
[migration guide](docs/beakerx-migration.md). The wider plan is still
being socialized with the Groovy community; design decisions below are
proposals, not commitments. The full plan-of-attack assessment is recorded in
[docs/assessment.md](docs/assessment.md).

Getting started
---------------

Requires JDK 17+ (kernelspec records the installing JVM) and any Jupyter
frontend (JupyterLab, Jupyter Notebook, VS Code, `jupyter console`) — or
Apache Zeppelin via its built-in Jupyter bridge
(see [docs/zeppelin.md](docs/zeppelin.md)).

```
./gradlew installKernelSpec
```

installs the kernel jars and `kernel.json` into the per-user Jupyter kernels
directory. Then pick "Groovy" as the kernel in your frontend, or run
`jupyter console --kernel groovy`. To pin a specific JVM (e.g. JDK 25) into
the kernelspec, pass `-PjavaHome=/path/to/jdk`; extra JVM flags go in
`-PjvmArgs='--add-opens=... -Dfoo=bar'` (needed e.g. for Spark Connect/Arrow
or the Vector API — the Spark notebooks document the exact flags).

For a Gradle-free install on another machine: `./gradlew kernelSpecZip`
produces `build/distributions/groovy-jupyter-kernelspec-<version>.zip`; unzip
it and run `jupyter kernelspec install <unzipped-dir> --user --name groovy`.
The portable spec launches `java` from the PATH (17+ required).

Migrating notebooks from BeakerX? See the
[BeakerX migration guide](docs/beakerx-migration.md).

Hosts that launch kernels with `--transport=ipc` (Unix domain sockets — hosted
Google Colab does) are supported: the kernelspec ships a JeroMQ built from
master with real Unix-socket `ipc://` (see [libs/README.md](libs/README.md)),
and the launcher bridges the socket-naming gap in jjava-jupyter ≤ 1.0-a8
([dflib/jjava#134](https://github.com/dflib/jjava/issues/134)). Colab's hosted
runtimes only *offer* Python, R and Julia, but the kernel can be installed into
a hosted session (free T4 included): open
[notebooks/colab-kernel-bootstrap.ipynb](notebooks/colab-kernel-bootstrap.ipynb)
in Colab, run its install cell, reload the tab.

Current limitations: interrupts are cooperative — loops inside third-party
jars can't be stopped (restart the kernel); tuple declarations
(`def (a, b) = ...`) stay cell-local; completion doesn't yet chain through
expressions; no `%magics` beyond the design intent that `@Grab` covers
dependency needs.

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
