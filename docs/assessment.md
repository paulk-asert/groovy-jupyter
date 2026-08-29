<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Groovy Notebook Assessment — a Minimal Jupyter Kernel for Groovy 6 on JDK 25

*Plan of attack for a first-party Groovy notebook offering · Assessment dates: 26–27 August 2026 · Status: **proposed pending team review***

## Verdict

**None of the obvious candidate bases (IJava, Apache Toree, BeakerX, Google Colab) is viable to build *on*. Build a purpose-built Groovy kernel speaking the Jupyter wire protocol, with a deliberately minimal permanently-owned core and integration points instead of bundled features.** \[PURSUE\]

1. **Foundation as a dependency, not a fork:** `org.dflib.jjava:jjava-jupyter` (Apache-2.0, actively maintained, the renamed continuation of SpencerPark's language-agnostic base kernel) supplies the Jupyter protocol layer; [kotlin-jupyter](https://github.com/Kotlin/kotlin-jupyter) is the architecture role model; BeakerX is an idea quarry, not a foundation. Targeting the Jupyter protocol buys JupyterLab, Notebook 7, VS Code, Binder, Codespaces — and Zeppelin, via Zeppelin's own kernel-agnostic bridge (§8).
2. **Dependencies via `@Grab` on the new `groovy-grape-maven` engine** (Groovy 6, maven-resolver-based, no Ivy) — the notebook doubles as dogfooding for the new engine. No libraries are bundled: Smile turns out to be ASF Category X since 2019 (§5), and the `@Grab` model dissolves rather than solves that problem.
3. **Display is pass-through mime bundles only** (HTML, SVG, PNG, Vega-Lite JSON — types frontends render natively). No custom JS, no widgets, no plotting API of our own, ever. Libraries self-register renderers through the base module's `Extension` ServiceLoader SPI; Alipsa's **Matrix** (MIT, SVG-native charts, self-identified "no Jupyter integration" gap) is the natural launch partner (§6).
4. **A real differentiator on modern JDKs:** `Thread.stop()` throws unconditionally since JDK 20 and is removed in 26, so no JVM kernel can hard-kill a runaway cell — but Groovy can compile every cell with the `@ThreadInterrupt` customizer, making interrupt actually work. kotlin-jupyter's interrupt verifiably falls back to `Thread.stop()` and is broken for CPU-bound cells on JDK 20+ (§3.3).
5. **Timing:** Toree's retirement was proposed on the Incubator list during this assessment (25–26 Aug 2026); the only maintained JVM-language Spark kernel left is Scala-only almond. A Groovy kernel with the two documented Spark routes (§7) fills a genuine ASF-shaped gap: post-Toree, Zeppelin is the remaining ASF notebook *UI* and this kernel would be the only ASF notebook *kernel* — complementary, not competing.

Guiding principle, set during review discussion: **the graveyard (BeakerX, IJava, Toree, and JetBrains' recent quiet step back from Kotlin Notebook in IDEA) is filled by accretions, not by the protocol core** — the permanently-owned surface must stay small enough for one or two people to carry indefinitely. Working name: `groovy-jupyter` (§9).

## 1. Goal and method

Goal: a notebook-style offering for Groovy — recent JVM (JDK 25 LTS), Groovy 6, and a data-science story (originally imagined as "a library like Smile built in"; amended by the licensing findings in §5). Candidate bases named up front: SpencerPark/IJava, Apache Toree, BeakerX, Google Colab, or something else.

Method: the JVM kernel ecosystem, library licensing, the Toree/Spark situation, and the Groovy-native dataframe libraries (Underdog, Matrix — previously reviewed in the [whisky-revisited](https://groovy.apache.org/blog/whisky-revisited) blog post) were researched and verified 26–27 Aug 2026 against GitHub repos, Maven Central POMs/metadata, project docs, and Incubator mailing-list archives; kernel-source claims (interrupt paths, magics coupling, Zeppelin bridge mechanics) were checked against the actual source files. Groovy-side claims were verified against the local master tree. Key facts carry dates below; anything inferred rather than verified is flagged.

## 2. Candidate bases evaluated

| Candidate | Status (verified Aug 2026) | License | Verdict |
|---|---|---|---|
| [SpencerPark/IJava](https://github.com/SpencerPark/IJava) + jupyter-jvm-basekernel | Effectively unmaintained — IJava last release v1.3.0 (May 2019); base library frozen Feb 2024. JShell-centric. Lineage continues in dflib/jjava. | MIT | **Use successor** |
| [dflib/jjava](https://github.com/dflib/jjava) | Very active — 1.0-a8 (19 Aug 2026); deliberately relicensed MIT→Apache-2.0 (1.0-a6) for reuse; Maven-module split with a reusable base (`jjava-jupyter`); Java 11+; JeroMQ 0.6 + Gson; pip/Homebrew/jbang distribution. Maintained by the DFLib org (Andrus Adamchik). | Apache-2.0 | **FOUNDATION** |
| [rapaio-jupyter-kernel](https://github.com/padreati/rapaio-jupyter-kernel) | Active — 3.0.4 (Jun 2026); JDK 17+ (advertises up to 26 incl. preview); own from-scratch protocol 5.4 implementation, shaded single jar. | MIT | Reference |
| [Ganymede](https://github.com/allen-ball/ganymede) | Semi-dormant — last release Sep 2023; sparse commits since. The only kernel running Groovy today (JSR-223 inside a JShell kernel); README warns only pre-7 Notebook fully supported. | Apache-2.0 | Demo only |
| [Apache Toree](https://incubator.apache.org/projects/toree.html) | Incubating since Dec 2015, never graduated; 0.6.0 IPMC vote (Mar 2026) never completed; retirement proposed on general@incubator 25–26 Aug 2026 (no formal vote yet). Deeply Spark-coupled Scala codebase. | Apache-2.0 | **No** |
| [BeakerX](https://github.com/twosigma/beakerx) | Abandoned — v2 Groovy kernel stuck at 2.0.0 (Aug 2020); v1 repo last pushed Dec 2023; pre-JupyterLab-4; heavy custom JS widget stack, 5 languages, autotranslation, Spark UI widget. | Apache-2.0 | Idea quarry + corpus (§6.4) |
| [kotlin-jupyter](https://github.com/Kotlin/kotlin-jupyter) | Actively maintained (PyPI 0.19.0.944, May 2026); in-process REPL, versioned JSON library descriptors behind `%use`, renderers API, pip/conda packaging. Same engine as IntelliJ's Kotlin Notebook — which JetBrains recently open-sourced without official support, a caution sign for fat offerings. | Apache-2.0 | **ARCHITECTURE MODEL** |
| Google Colab | Hosted runtimes support exactly Python, R, Julia; custom kernels not selectable, no ETA (FAQ). Arbitrary kernels only via "connect to local runtime". | proprietary | UX bar only |
| [Apache Zeppelin](https://github.com/apache/zeppelin) | Active (0.12.1, Jun 2026) but its interpreter architecture (Thrift RPC, Angular webapp) is its own island; still ships an old GroovyShell-based interpreter. Integration runs the other way — via its Jupyter bridge (§8). | Apache-2.0 | Bridge target |
| New 2025–26 entrants | Oracle javavscode "Interactive Java Notebooks" (VS-Code-hosted, JShell, `.ijnb`); JTaccuino (JavaFX, own format); jupyter-java org (curation/jbang installers, not a kernel); Polyglot Notebooks being deprecated (never JVM). | various | Confirm the gap |

Cross-cutting observation: **every viable Java kernel today is JShell-based, and none serves Groovy** (Ganymede's JSR-223 mode is the closest, and it is stale). A Groovy kernel is a real gap, not a me-too.

## 3. Recommended architecture

### 3.1 Foundation: `jjava-jupyter` as a Maven dependency

`org.dflib.jjava:jjava-jupyter:1.0-a8` is published on Central as its own artifact (18 Aug 2026), separate from `jjava-kernel` (their JShell kernel), `jjava-launcher`, and `jjava-maven`. It is the direct continuation of SpencerPark's language-agnostic `jupyter-jvm-basekernel` — dflib republished it under that name through 1.0-M3, then renamed it — so language-agnosticism is its design intent, and their own Java kernel consumes it exactly as ours would. Protocol fixes, messaging-version updates, JeroMQ/security bumps, and the magics/extension framework flow to us with a version bump. Dependency graph: `groovy-jupyter-kernel` → `jjava-jupyter` (Apache-2.0) → JeroMQ (MPL-2.0, ASF Category B, binary-only) + Gson — clean for an ASF-adjacent or eventually donated project.

| jjava module | What it is (source-verified) | Disposition |
|---|---|---|
| `jjava-jupyter` | Protocol layer, channels, message types, magics-as-interfaces, kernel builder, `Extension` ServiceLoader SPI | **Depend** |
| `jjava-kernel` | Their JShell Java kernel | Not used (unless a dual Java+Groovy install story is wanted later) |
| `jjava-launcher` | One class: `ProcessBuilder` wrapper the kernelspec points at; injects `JJAVA_JVM_OPTS`, inherits IO, shutdown hook | Copy pattern (~100 lines, own env var) — too small and JJava-branded to be a version-coupled dep |
| `jjava-maven` | `%maven`/`%loadFromPOM` on Maveniverse MIMA; resolver core imports only `jjava-jupyter`, but the magics import `org.dflib.jjava.kernel.JavaKernel` and the module compile-depends on `jjava-kernel` | **Skip** — superseded by `groovy-grape-maven` (§3.4) |
| `jjava-distro` | Assembly module: shading, kernelspec bundling, testcontainers integration tests (real Jupyter in Docker driving the kernel); repo-root `pip/` + `pyproject.toml` layout | Copy patterns — esp. the testcontainers IT harness |

> **Caveats on the dependency.** (a) The `1.0-a*` line is alpha — API churn is permitted and packages were reshaped within the last year; pin and upgrade deliberately. (b) The base was refactored with one consumer (JShell); JShell-isms may leak in completion/error-formatting hooks — the plan is genericization patches upstream, not a fork (they relicensed to Apache-2.0 precisely to enable reuse, and a second-language consumer validates the module). (c) Fork remains the exit, never the plan: Apache-2.0 means the module (small: protocol layer + channels + messages) can be absorbed if dflib ever goes the way of IJava.

### 3.2 Eval engine: persistent `GroovyClassLoader` + `Binding`, not JShell

- Each cell compiles as a script against a session-long `GroovyClassLoader` (classes defined in one cell stay visible in later cells) with a shared `Binding`.
- **The one semantic trap to solve early:** `def x = 1` in a script is a local that evaporates when the cell ends; undeclared assignments go to the binding. The kernel must lift `def`/typed declarations into the binding (AST transform), or scripts behave bafflingly across cells. BeakerX's Groovy kernel solved this — the one place its code is worth reading closely.
- Completion: wire groovysh's existing completer machinery to `complete_request`; power asserts make error display genuinely better than any Java kernel's for teaching audiences.

### 3.3 Interrupts: `@ThreadInterrupt` — a Groovy-only differentiator

`Thread.stop()` throws `UnsupportedOperationException` unconditionally since JDK 20 (JDK-8293852; verified against JDK 25 javadoc) and its removal lands in JDK 26 (JDK-8368237/8368370). Consequence: *no* JVM kernel can hard-kill a runaway CPU-bound cell on a modern JDK. kotlin-jupyter's executor (verified in `JupyterExecutorImpl.kt`) tries `Thread.interrupt()` then falls back to `Thread.stop()` — i.e. broken on JDK 20+. Groovy alone can compile every cell with the `ThreadInterrupt` compiler customizer (optionally `TimedInterrupt`), making cooperative interruption actually fire inside loops with zero user effort. This should be on by default and featured in the docs.

### 3.4 Dependencies: `@Grab` on `groovy-grape-maven`

Groovy 6's `Grape` resolves its engine via a `ServiceLoader` SPI (`groovy.grape.GrapeEngine`, verified in `src/main/java/groovy/grape/Grape.java`); the new `groovy-grape-maven` subproject registers `GrapeMaven` built on maven-resolver (`maven-resolver-provider` + `maven-resolver-supplier-mvn4`), alongside legacy `groovy-grape-ivy`. The kernel ships `groovy-grape-maven` on its classpath and `@Grab` runs on maven-resolver with no Ivy and no third-party resolver dependency. Grape grabs into a specified `GroovyClassLoader` — the kernel's persistent session classloader is exactly the right target (same as groovysh's `:grab`), so cell-level `@Grab` needs no kernel special-casing.

- Magics are thin sugar at most: a `%maven g:a:v` line magic for users arriving from other kernels is a three-line wrapper over `Grape.grab(...)`; `@Grab` is the idiomatic path and works day one. Beyond that, magics ≈ a `%classpath` report.
- Dogfooding feedback loop: the kernel will want to enumerate what a grab actually resolved (for the `%classpath` report); if `GrapeEngine`'s `resolve`/`listDependencies` surface doesn't cover it cleanly, that is a small legitimate API addition to make while Groovy 6 is still in beta.
- Real-world Grape test corpus: the `groovy_jcuda_colab.ipynb` spike (Aug 2026) exercised classifier grabs (`jcuda-natives:…:linux-x86_64`), the `transitive=false` shorthand, and found `@GrabExclude` silently cancelling an explicit co-grab (surfacing much later as a native-load failure) — all against JCuda's awkward real-world POM (a classifier-less `jcuda-natives` transitive that doesn't exist). All three behaviors should be tested for parity or deliberate fix on `GrapeMaven` while 6 is in beta; the `@GrabExclude` overshoot is a JIRA candidate in its own right.
- **The one accepted v1 scope addition:** a flag making the session `GroovyClassLoader` write compiled cell classes to a directory. Independently useful (debugging, bytecode inspection) and the enabler for classic-mode Spark (§7.2).

### 3.5 Display: pass-through mime bundles + the `Extension` SPI

- The kernel emits Jupyter `display_data` mime bundles and renders result values through a small renderer registry. Frontends already render `text/html`, `image/svg+xml`, `image/png`, and `application/vnd.vegalite.v*+json` natively — so the chart story is "your library produces SVG/HTML/Vega-Lite; the kernel passes it through". **We ship zero JavaScript and never build a plotting API.** (BeakerX's welded-to-frontend widget stack is the canonical counterexample.)
- Instead of kotlin-jupyter's centrally maintained library-descriptor repo (hundreds of JSON files — a maintenance treadmill), libraries self-integrate via `jjava-jupyter`'s `Extension` ServiceLoader SPI: a `@Grab`-ed jar carrying a `META-INF/services` extension is activated and registers its own renderers/default imports. Burden decentralized to library authors, exactly like Groovy extension modules.
- GINQ results → table rendering is a natural built-in showcase (one renderer, no dependencies).
- Streaming and in-place-updatable output are already in the base module (verified: `PublishStream`, `PublishUpdateDisplayData`, `PublishClearOutput` in `jjava-jupyter`'s message classes); the kernel exposes a tiny cell-facing `display()` handle with `update()` — a few lines of core that serve LLM token streaming (§6.3), progress bars, and Spark job progress alike.

## 4. The minimalism principle (design constraint, set during review)

Evidence base: BeakerX died of its accretions (frontend-pinned JS widgets, five languages, autotranslation, Spark UI), not of the protocol; IJava died of single-maintainer drift; Toree of Spark-coupling + community attrition; JetBrains — a well-resourced team — recently open-sourced Kotlin Notebook in IDEA without official support. The quirky Python/JVM crossover magnifies maintenance weight. Therefore the permanently-owned core is capped at roughly: one protocol dependency, one Grape engine shipped anyway, ~2–3k lines of kernel, and a pass-through display mechanism — a surface one or two people can carry indefinitely.

**In core (owned forever):** execute / complete / interrupt (`@ThreadInterrupt`) / stdin / error display; declaration lifting; mime renderer registry; `%classpath`; class-output-dir flag.

**Integration point (someone else's weight):** libraries via `@Grab` (incl. Smile, GPL notwithstanding — user-initiated fetch, never distributed by us); renderers via `Extension` SPI jars maintained with the libraries; charts via native-mime pass-through; Spark via Connect/`outputDir` recipes; Zeppelin via *their* bridge; install via the jupyter-java jbang catalog.

**Never:** bundled libraries; central descriptor repo; custom JS/widgets; plotting API; polyglot/autotranslation; Toree-style SparkContext lifecycle management.

Packaging follows the same discipline: **one blessed install path first** — self-contained jar + the existing [jupyter-java](https://github.com/jupyter-java) jbang catalog (maintained by others) — pip later, jjava-style (Python as dumb file-copier, no logic on the Python side of the crossover), then conda-forge/Docker/Binder as downstream conveniences, not versioned products. Colab-grade zero-install is reached via Binder/Codespaces on the Docker image. Colab itself splits: via "connect to local runtime" the kernel works fully (Colab is just a frontend to a Jupyter server the user runs — including a rented GPU VM); on Colab's free *hosted* runtimes the kernel cannot run at all — but those are the ecosystem's only free GPUs (Binder and free Codespaces have none), so the proven fallback matters: Groovy-as-subprocess inside the Python runtime (apt JDK + Groovy zip + `%%writefile` + `!groovy`), demonstrated end-to-end including cuBLAS on a free T4 by the `groovy_jcuda_colab.ipynb` spike (Aug 2026, MatrixGroovy). Its setup preamble is the standing "Groovy on free Colab GPUs" template and should be linked from kernel docs — the two lanes complement, never compete. Related boundary: benchmark notebooks keep the forked-subprocess pattern even under the kernel — per-measurement JVM forking (JMH discipline, one JVM per size to avoid JIT-profile pollution) is structurally incompatible with a long-lived kernel JVM; the kernel serves stateful exploration, forked JVMs serve measurement.

## 5. Licensing findings

### 5.1 Smile cannot be bundled — and hasn't been bundleable since 2019

Verified against shipped Maven Central POMs and the GitHub LICENSE history (this corrects the common belief that 2.6.0 was the last Apache-licensed release):

| Smile versions | License (POM-verified) | ASF category |
|---|---|---|
| ≤ 1.5.3 (Jun 2019) | Apache-2.0 — the *last* Apache-licensed release | **A** |
| 2.0.0 – 2.6.0 (Nov 2019 – Dec 2020) | LGPL-3.0 (incl. 2.6.0 — POM: "GNU Lesser General Public License, Version 3") | **X** |
| ≥ 3.0.0 (Dec 2022) | GPL-3.0 + commercial dual licensing | **X** |
| Current: 6.3.0 (18 Aug 2026) | GPL-3.0 + commercial; v5+ requires Java 25 (v4.x Java 21) — technically impressive (GPU DL, pure-Java LLM inference) and JDK-25-aligned | **X** |

> **Resolution.** The `@Grab` integration model dissolves the problem: users grab Smile themselves (user-initiated fetch — fine); we never distribute it, in any jar, Docker image, or pre-seeded cache published by the ASF. Example notebooks may show `@Grab('com.github.haifengl:smile-core:…')` with a one-line license note. A community (non-ASF) convenience image could include it; an ASF-published one must not.

### 5.2 Apache-compatible data-science stack (for examples and docs, still not bundled)

| Library | License | Status (verified) | Scope |
|---|---|---|---|
| [DFLib](https://github.com/dflib/dflib) | Apache-2.0 | Very active — 2.0.0-M7 (23 Aug 2026) | Dataframes; first-class Jupyter + ECharts-as-HTML integration; same org as jjava |
| [Tribuo](https://github.com/oracle/tribuo) | Apache-2.0 | 4.3.2 (Apr 2025); slow cadence, Oracle-backed | Classical ML, ONNX/TF interop |
| [DJL](https://github.com/deepjavalibrary/djl) | Apache-2.0 | 0.36.0 (Dec 2025); active, AWS-backed | Deep learning, engine-agnostic |
| [Tablesaw](https://github.com/jtablesaw/tablesaw) | Apache-2.0 | 0.44.x (Jan 2026) — first releases since 2021; bursty | Dataframes |
| Commons Statistics | Apache-2.0 | 1.3 (Apr 2026) | Distributions, inference (Commons Math 4 itself is stalled at 4.0-beta1) |
| [ojAlgo](https://github.com/optimatika/ojAlgo) | MIT | 57.1.1 (Aug 2026); very active | Linear algebra, optimization |

Plus the Groovy-native libraries in §6. JeroMQ (the ZeroMQ binding underneath every JVM kernel) is MPL-2.0 — Category B, fine as a binary dependency.

## 6. Library ecosystem: Matrix, Underdog, and AI/LLM workloads

### 6.1 Matrix — launch partner for the integration model \[PURSUE\]

- MIT; Per Nyfelt (Alipsa); very active — 20 releases Jun–Jul 2026; all 22 artifacts on Central under `se.alipsa.matrix` with a BOM (`matrix-bom:2.5.1`); targets Groovy 5 / JDK 21.
- **SVG-native charts:** the 2026-rewritten `matrix-charts` (Charm grammar-of-graphics engine, plus `matrix-ggplot`/`matrix-pict` facades) produces SVG with PNG/JPG/PDF export — drops straight into an `image/svg+xml` mime bundle with zero display glue. `matrix-xchart` also exports PNG/SVG without showing a window (headless-ness inferred-likely, undocumented).
- **GPL exposure precisely bounded:** only the opt-in leaf `matrix-smile` (compile-scope smile-core 4.4.2, GPL) touches Smile; core/stats/charts are clean (EJML is Apache-2.0).
- **The fit is mutual:** Matrix's own `docs/python-comparison.md` lists "No Jupyter integration" as a known limitation — this kernel is exactly the missing piece; Per already announces releases on users@groovy. Adjacent Alipsa ecosystem: gmd (Groovy Markdown literate docs), Gade (JavaFX analytics IDE), and brand-new `jmlx` (MIT, MLX/Panama ML for Java 25+, Aug 2026).
- **Action:** propose a small `matrix-jupyter` extension jar (renderer SPI: Matrix → HTML table, chart → SVG) contributed to and maintained in the Alipsa org — the first worked example of the Extension mechanism. Not bundled; if the library ever goes dormant (single-maintainer), the kernel loses nothing.

### 6.2 Underdog — conceptually fits, not integrable yet \[REVISIT WHEN PUBLISHED\]

- Apache-2.0; Mario García (grooviter); commits through Jul 2026 — but **zero public releases anywhere**: no tags, nothing on Central (the `com.github.grooviter` group holds only its Tablesaw fork, which *is* on Central incl. a legacy `tablesaw-beakerx` artifact), `0.1.0-SNAPSHOT` published to a private Gitea registry (unreachable when probed); the whisky-underdog companion repo needed `mavenLocal()`. `@Grab` of Underdog is currently impossible.
- `underdog-ml` has an *api-scope* dependency on GPL smile-core 3.1.1; `underdog-plots`' `Render.show()` already returns the chart HTML as a String (browser popup is a side effect, skipped under `CI=true`) — nearly free `text/html` path — but the generated page loads ECharts 5.5.1-rc.1 from a CDN (breaks offline notebooks).
- **Action:** encourage publishing to Central (and vendoring/inlining ECharts); when that happens the same Extension SPI absorbs it with no kernel change. Until then it cannot be part of the story regardless of preference.

### 6.3 AI/LLM workloads — both modes fit the plan unchanged \[SHOWCASE\]

Prompted by Smile's LLM stack ("LLM inference natively on the JVM — no Python bridge"): the Llama/GQA/RoPE/KV-cache code lives in `smile-deep`, which *is* on Maven Central and therefore `@Grab`-able today under exactly the posture §5.1 sets for the rest of Smile — user-initiated fetch, never distributed by us, one-line GPL note in the example; plus a documented caveat for its heavyweight LibTorch natives and `--enable-native-access` (the launcher JVM-opts env var, §3.1). Two distinct usage modes, neither of which changes the core:

- **LLMs *inside* cells is a pure `@Grab` workload.** Category-A-clean options for docs and examples (licenses/activity verified 27 Aug 2026): **LangChain4j** (Apache-2.0, very active — unified client incl. Ollama/local models, streaming, tools, structured output), **Ollama4j** (MIT, active), **DJL** (Apache-2.0 — LLM inference via PyTorch/ONNX engines), **Jlama** (Apache-2.0 — pure-JVM inference on the Vector API/Panama, thematically perfect beside our JDK 25 story, though quiet since Oct 2025), `llama3.java` as the single-file teaching demo. Kernel-side needs are all already in the plan: streaming/updatable output (§3.5 — verified present in the base module), JVM flags via the launcher env var (`--enable-native-access`, `--add-modules jdk.incubator.vector`), and the §11 interrupt caveat applies (generation loops inside grabbed jars are not `@ThreadInterrupt`-instrumented — rely on the library's cancellation or kernel restart).
- **LLMs *generating* notebooks/cells lives frontend-side and arrives free from the protocol choice.** Jupyter AI's chat sidebar in JupyterLab, VS Code Copilot/agent notebooks, and coding agents editing `.ipynb` JSON are all kernel-agnostic. The kernel's contribution to generation quality is already core scope: solid `complete_request`/`is_complete` and rich error output — power asserts give an agent iterating on a failing cell a far better repair signal than a bare stack trace (worth a line in the announcement). Known gap: Jupyter AI's `%%ai` cell magics are IPython-only *(per its docs; not source-verified)* — a Groovy `%ai` would be a small optional Extension-SPI jar wrapping LangChain4j, off-core like everything else.
- **Ready-made example corpus (AI):** the [Exploring AI with Groovy](https://groovy.apache.org/blog/groovy-ai) blog post (Oct 2025, updated Nov 2025) and its [companion repo](https://github.com/paulk-asert/groovy-ai). The Ollama4j and LangChain4j sections (chat, conversation memory, structured output into records, `@Tool` methods) port to cells nearly verbatim and are *local-model, keyless* — the most reproducible kind of AI demo — with a Docker/GitHub-Actions Ollama setup that maps directly onto a devcontainer/Codespaces config. The notebook form adds the demo a blog can't show: LangChain4j streaming into an updatable display, token by token. Boundary (minimalism applied to examples): the Spring AI/Micronaut/Quarkus sections stay blog-only — app-framework lifecycles and compile-time DI don't fit cells (Embabel: test first). The corpus does double duty as the **Phase-1 acceptance suite**: its record/interface/annotated-method definitions across cells exercise exactly the declaration-lifting and class-persistence semantics flagged in §11.

### 6.4 The BeakerX-vintage corpus and migration guide \[PURSUE\]

Eight BeakerX-era Groovy notebooks live in [groovy-data-science](https://github.com/paulk-asert/groovy-data-science) (Whiskey, WhiskeyWayang, WhiskeyIgnite + DrunkenSailor, HousePrices, Iris, LanguageProcessing, Candles — inventoried programmatically, 27 Aug 2026). They are remarkably uniform: across all eight, exactly three BeakerX-isms appear, each with a mechanical mapping — which makes the corpus simultaneously a Phase-4 porting work-list and the definition of a one-page **BeakerX migration guide**. Every legacy Groovy-notebook user in existence is an orphaned BeakerX user (frozen since 2020, §2); the guide plus these eight ported notebooks is the adoption path for that population.

| BeakerX construct | Under this kernel |
|---|---|
| `%%classpath add mvn` + `group artifact version` lines | `@Grab('group:artifact:version')`, line for line. Scoping note: in BeakerX, `%%classpath` was session-wide while `@Grab` (also supported there) was scoped to its cell *(recalled behavior — not re-verified against BeakerX source)*; here `@Grab` resolves into the persistent session classloader, i.e. session-wide — one mechanism subsumes both, and the guide should state the broadened `@Grab` scope explicitly. |
| `%import` / `%import static` | Plain `import` statements in a cell (import persistence — part of the §11 declaration-semantics design item) |
| `TablesawDisplayer.register()` (per-library display registration) | Exactly what the Extension SPI automates — the grabbed jar self-registers its renderer; the line disappears |
| `OutputCell.HIDDEN` | Drop it, or end the cell with `null` (no `execute_result`) — a documented convention |
| `new Plot(…) << new Points(…)` (BeakerX JS widgets) | The only non-mechanical step: swap to SVG/HTML output. The notebooks already carry XChart (headless PNG/SVG export) among their own dependencies; Matrix charts or ECharts-as-HTML are the other §6 routes. |

Corpus observations: (a) **every session variable is undeclared** (`pca = …`, `records = …` — no `def` anywhere): BeakerX trained its users into binding style, so undeclared-into-binding must be flawless — that is how the entire existing corpus is written — and `def`-lifting is the *improvement* over BeakerX, not the compatibility requirement. The corpus joins groovy-ai (§6.3) as Phase-1 acceptance evidence. (b) The notebooks' kernelspec name is already `groovy` — file metadata ports untouched. (c) Whiskey.ipynb pins `smile-core 1.5.3` — the last Apache-2.0 Smile (§5.1) — so ports may keep it as-is, modernize via `@Grab` with the GPL note, or swap to Tribuo. (d) WhiskeyWayang and WhiskeyIgnite add Apache Wayang and Ignite engines beside Spark (§7) — strengthening the post-Toree "keep it in the ASF family" story.

Decision (minimalism applied): **no `%%classpath`/`%import` compatibility magics in core** \[DECLINE SHIM\] — the mappings are trivial enough that a guide beats a shim owned forever; at most a community compat extension jar later, via the same SPI as everything else.

## 7. Apache Spark strategy

**Context (verified in the Incubator archives):** Justin Mclean's Toree status thread (14 Aug 2026) went unanswered by the podling; PJ Fanning: "I firmly believe that the podling should be retired" (25 Aug); J-B Onofré concurred pending PPMC response (26 Aug). No formal vote yet, but the direction is clear, and the displaced users have nowhere JVM-shaped to go: almond (0.14.5, Jul 2026, BSD-3) is Scala-only; Livy is near-dormant; the JShell kernels have no cluster-mode class-shipping story; PySpark is the default exodus path. A Groovy kernel that demos well on Spark fills a genuine hole — with the "keep your Spark notebooks in the ASF family" line attached.

### 7.1 Route A — Spark Connect (strategic, zero kernel work) \[DOCUMENT + PROTOTYPE\]

- User `@Grab`s `org.apache.spark:spark-connect-client-jvm_2.13` and connects to a cluster. DataFrame/Dataset/SQL/most Structured Streaming work with **nothing user-defined ever leaving the client** — the REPL class-shipping problem does not arise. No RDD API/SparkContext over Connect.
- UDFs over Connect upload user classes via `ClassFinder`/`REPLClassDirMonitor` + `addArtifact` — pointable at our class-output directory (§3.4). **Groovy-bytecode UDFs over Connect are unproven — a prototype notebook, flagged as such.**

### 7.2 Route B — classic mode via `spark.repl.class.outputDir` \[RECIPE + ONE KERNEL FLAG\]

- Since Spark 2.0 (SPARK-11563), if `spark.repl.class.outputDir` is set, `SparkContext` registers that directory with the driver's RPC file server and executors' REPL classloader fetches classes from it. This is literally Zeppelin's Spark interpreter pattern (one `conf.set` line, verified in their `SparkInterpreter.java`); almond/ammonite-spark achieve the same with their own HTTP server + `spark.repl.class.uri`. Both configs are internal/undocumented but stable across Spark 2.0→4.x in practice.
- Kernel side: only the class-output-dir flag (§3.4). Everything else is a documented recipe; if demand shows, an optional `groovy-jupyter-spark` Extension jar (session-builder sugar, progress display — the almond model) lives off-core.
- Recipe guidance: closure owner-capture will hit the classic `NotSerializableException`s — steer UDF examples toward `@CompileStatic` cells, native lambdas, and static methods.

> **Version constraints for the docs:** Spark 4.x is Scala 2.13-only; an in-process (classic driver) session on JDK 25 requires **Spark ≥ 4.2.0** (Java 25 support landed there, Jul 2026, SPARK-51167 — with a JDK < 25.0.3 deprecation note). 4.0/4.1 are Java 17/21 only. Connect-client JDK constraints are looser but unverified on 25 specifically.

## 8. Zeppelin tie-ins

Zeppelin's legacy interpreter architecture (Thrift RPC, per-interpreter JVMs, Angular webapp) is too far from this path to build against — and doesn't need to be, because **the integration runs the other way** (all verified on apache/zeppelin master):

- **`zeppelin-jupyter-interpreter` is kernel-agnostic:** docs state "You can use any jupyter kernel as long as you installed the necessary dependencies" — `%jupyter(kernel=groovy)` runs our kernel inside Zeppelin with zero work on our side. Mechanics: Zeppelin → gRPC → a Python sidecar (`jupyter-client`, `grpcio`, `protobuf`) → ZeroMQ → kernel; the Python/JVM crossover again, but on *their* maintenance surface. Cost to us: one integration test + a docs PR adding a Groovy section to their `jupyter.md`. **Test half done (verified 28 Aug 2026, Zeppelin 0.12.1): works — incl. session state across paragraphs and `@Grab`; one setup gotcha: the sidecar needs `protobuf==3.20.3` (Zeppelin's bundled gRPC stubs predate protobuf 4).**
- **Notebook portability both ways:** the `zeppelin-jupyter` module converts `.ipynb` ↔ `.zpln` (nbformat/zformat), so existing Zeppelin Groovy notes carry over and our example notebooks work there.
- **Nothing to lift from their charting:** interpreters emit typed text (`%table` TSV, `%html`, `%img`, Angular directives); all rendering lives in the Angular webapp; Helium is effectively dead. Same lesson as BeakerX — rendering welded to a frontend doesn't outlive it. **Mime mapping verified 28 Aug 2026:** `text/html` → HTML (rendered — the kernel's auto-tables work natively), `image/png` → IMG (rendered), while `image/svg+xml` *and* `text/markdown` fall back to the text/plain form (raw source shown) — slightly stricter than the original inference, which assumed SVG was safe. Zeppelin-facing examples should emit HTML (or wrap SVG in `displayHtml`, which renders) or PNG.
- Their old GroovyShell-based `groovy/` interpreter still exists; once this kernel is real, their community has the option of pointing users at the bridge — their call. Their `SparkInterpreter` remains the reference implementation for §7.2.

Post-Toree framing for ASF folks: Zeppelin = the ASF notebook *UI*, this kernel = the ASF notebook *kernel*; "use JupyterLab, VS Code, or Zeppelin — same Groovy kernel".

## 9. Naming, repo, and governance

- **Name: `groovy-jupyter`** (repo/artifacts), pip `groovy-jupyter-kernel`, kernelspec id `groovy`, display name e.g. "Groovy 6 (JDK 25)". Rationale: the `I*` convention is the IPython-era lineage whose JVM exemplar (IJava) is the dead generation; the maintained modern precedent is `kotlin-jupyter`, also our architecture model; "groovy jupyter" is the literal search query; descriptive naming makes ASF branding review trivial. Name-collision check (27 Aug 2026): PyPI `igroovy`/`groovy-jupyter`/`groovy-jupyter-kernel`/`groovy-kernel` all free; only two zero-star 2019 `igroovy` GitHub relics. `IGroovy` stays available as an informal nickname; optionally register the pip name defensively.
- **Home: own repo** (groovy-community incubation; clean path to ASF donation once stable) — *not* a module inside dflib/jjava: that project is Java-branded, the Groovy kernel will accrete language-specific mass (Grape, `@ThreadInterrupt`, declaration lifting, GINQ renderers), and language kernels live with their language communities (kotlin-jupyter/JetBrains, IJulia/Julia, IRkernel/R). But raise it with Andrus Adamchik directly — riding their distro/pip/CI/release train with Groovy-team commit rights is a viable model B if he genuinely wants a multi-language kernel family; the governance question should be answered before code moves anywhere. Either way the collaboration is real: upstream genericization patches, listing in their docs and the jupyter-java jbang catalog, possibly a shared IT harness.

## 10. Phasing

| Phase | Content | Exit criterion |
|---|---|---|
| 0 — Spike (1–2 wks) | `jjava-jupyter` + GroovyShell engine; execute/display/complete in JupyterLab | Whisky notebook runs end-to-end with `@Grab` |
| 1 — Semantics | Declaration lifting; `@ThreadInterrupt` interrupts; groovysh completion; stdin; error rendering (power asserts); class-output-dir flag | Interrupt kills a hot loop; classes/vars survive across cells; the groovy-ai examples (records, interfaces, `@Tool` methods — §6.3) run cell-by-cell unmodified; the BeakerX corpus's binding-style variables and imports behave identically post-mapping (§6.4); VS Code + Lab + Notebook 7 pass the testcontainers ITs |
| 2 — Display + integration | Renderer registry + mime pass-through; GINQ table renderer; Extension SPI activation on grab; `matrix-jupyter` exemplar with Alipsa | Matrix chart renders as SVG with zero user glue |
| 3 — Packaging | Self-contained jar + jbang catalog entry (PR to jupyter-java); testcontainers IT harness; then pip (dumb file-copier), Binder image | `jbang install-kernel@…` works on a clean machine |
| 4 — Showcase | Notebook bank (groovy-data-science ports incl. the eight BeakerX-vintage `.ipynb` via the §6.4 mappings, published alongside the BeakerX migration guide; bundled-Smile examples → Tribuo/DFLib/Matrix, Smile kept as `@Grab` examples); AI notebooks from groovy-ai (§6.3: Ollama4j + LangChain4j incl. a token-streaming demo; Ollama devcontainer; optionally `smile-deep` as the maximalist `@Grab` demo); GPU lane from the jCuda/cuBLAS matrix spike (kernel form for GPU-equipped hosts; Colab-subprocess form kept as-is for free T4s, §4); Spark Connect + classic prototypes (§7); Zeppelin bridge test + docs PR; announce | Binder badge in README launches a working gallery |

## 11. Risks and open questions for team review

- **`jjava-jupyter` alpha churn / JShell leakage** — pin versions; budget for adaptation per bump until 1.0 GA; upstream patches early to establish the relationship (§3.1). Bus-factor exit: absorb the module (Apache-2.0).
- **Declaration-lifting semantics** — the design decision with the most user-visible consequences (what exactly does `def` mean across cells? typed declarations? classes? `import` persistence?). Needs a short design note + tests before Phase 1 hardens; BeakerX and kotlin-jupyter behaviors should be tabulated first.
- **Groovy UDFs over Spark Connect unproven** (§7.1) — prototype before claiming; classic-mode closure serialization needs honest docs. The Spark story should launch as "documented + demonstrated", not "supported", until both notebooks exist.
- **Single-maintainer partners** — Matrix (Per), Underdog (Mario), jjava (Andrus-led org): the integration-point architecture deliberately keeps each of them non-load-bearing for the kernel core; keep it that way.
- **Groovy 6 beta dependency** — the kernel targets 6.0.0-beta-2+ for `groovy-grape-maven`; a Groovy-5 fallback (Ivy Grape) is possible but adds a matrix row — proposal: don't; the kernel is a Groovy 6 showcase.
- **Interrupt guarantees are cooperative** — `@ThreadInterrupt` covers loops/method entries in *cell-compiled* code, not hot loops inside grabbed third-party jars; docs must state the restart-kernel fallback plainly rather than overclaiming.
- **Vega-Lite through the Zeppelin bridge** (§8) — resolved 28 Aug 2026: non-HTML/PNG mimes (SVG, markdown, by extension Vega-Lite) fall back to raw text; guidance in §8. **matrix-xchart true headlessness** (§6.1) — still inferred-likely, one test.
- **ZeroMQ transport layer — watch item (29 Aug 2026)** — the kernel currently vendors a JeroMQ built from master (`libs/`, zeromq/jeromq#998: real Unix-socket `ipc://` on JDK 16+, merged Sep 2024 but never released; last Central release 0.6.0, Feb 2024, repo quiet since Mar 2025) plus a launcher shim for jjava-jupyter's `{ip}:{port}` socket naming (dflib/jjava#134, PR #135). Both are needed for hosts that launch kernels with `--transport=ipc` (hosted Google Colab). Two things to watch: (a) a JeroMQ 0.7.0 release and a jjava-jupyter with #135, at which point the vendored jar, the `jjava-jupyter` exclude and `IpcSocketShim` are deleted in one commit; (b) [OMQ.java](https://github.com/paddor/omq.rs/tree/main/bindings/java) — a Rust-backed ZeroMQ-compatible binding (ISC, started May 2026) with native `ipc://` and markedly better throughput than JeroMQ. It is *not* a drop-in: jjava-jupyter's channels subclass `org.zeromq.ZMQ.Socket` directly (six files, ~1,100 lines), so adopting it means a transport-abstraction PR upstream, not a fix; it also requires JDK 25 + FFM (`--enable-native-access`), ships per-platform natives with no `linux-aarch64`, and Colab's image has JDK 21. Revisit only if a jjava transport SPI materialises or iopub throughput becomes a real complaint; JeroMQ's pure-Java, 17+, no-flags profile is the right default for a kernel.
- **ASF donation timing** — incubating outside the ASF release train first is proposed for iteration speed (alpha deps, fast releases); the team may prefer apache/groovy-* from day one — trade-off to discuss on dev@.
- **Sequencing** — proposed order: (1) sound out Andrus (base-module reuse, docs listing, model-B question), (2) Phase-0 spike, (3) dev-list \[DISCUSS\] with this assessment + working demo, (4) contact Per (matrix-jupyter) and Mario (publishing), (5) jbang-catalog PR, (6) Zeppelin docs PR once the bridge test passes.

## Sources

Kernel ecosystem verified 26 Aug 2026: [dflib/jjava](https://github.com/dflib/jjava) (1.0-a8; Apache-2.0 relicense in 1.0-a6, issue #77; docs at dflib.org/jjava), Maven Central metadata for `org.dflib.jjava:*` (jjava-jupyter 1.0-a8, 18 Aug 2026; basekernel lineage through 1.0-M3, May 2025), jjava source (launcher `KernelLauncher.java`; `jjava-maven` magics importing `org.dflib.jjava.kernel.JavaKernel`); [IJava](https://github.com/SpencerPark/IJava)/[basekernel](https://github.com/SpencerPark/jupyter-jvm-basekernel) (MIT, frozen); [rapaio](https://github.com/padreati/rapaio-jupyter-kernel) 3.0.4; [Ganymede](https://github.com/allen-ball/ganymede) 2.1.2 (JSR-223 Groovy); [BeakerX](https://github.com/twosigma/beakerx) v1/v2 repos + PyPI (`beakerx_kernel_groovy` 2.0.0, Aug 2020); [kotlin-jupyter](https://github.com/Kotlin/kotlin-jupyter) (PyPI 0.19.0.944; interrupt path in `JupyterExecutorImpl.kt`: interrupt → `Thread.stop()` fallback); [JeroMQ](https://github.com/zeromq/jeromq) MPL-2.0; Colab FAQ (Python/R/Julia only); Oracle [javavscode](https://github.com/oracle/javavscode) notebooks, [JTaccuino](https://github.com/jtaccuino/jtaccuino), [jupyter-java org](https://github.com/jupyter-java).

Thread.stop: JDK 25 javadoc (throws unconditionally since JDK 20, JDK-8293852); removal JDK-8368237/8368370 (JDK 26, partially verified).

Licensing verified 26–27 Aug 2026 against Maven Central POMs: smile-core 1.5.3 (Apache-2.0) / 2.6.0 (LGPL-3) / 3.0.0+ (GPL-3) / 6.3.0 current; smile version-JDK matrix per README; ASF policy per [legal/resolved](https://www.apache.org/legal/resolved.html); alternatives: [Tribuo](https://github.com/oracle/tribuo) 4.3.2, [Tablesaw](https://github.com/jtablesaw/tablesaw) 0.44.x, [DJL](https://github.com/deepjavalibrary/djl) 0.36.0, [DFLib](https://github.com/dflib/dflib) 2.0.0-M7, Commons Statistics 1.3, [ojAlgo](https://github.com/optimatika/ojAlgo) 57.1.1.

Groovy-native libraries verified 27 Aug 2026: [Matrix](https://github.com/Alipsa/matrix) (MIT; `se.alipsa.matrix` BOM 2.5.1; matrix-charts 0.5.0 SVG/Charm; matrix-smile 0.2.0 → smile-core 4.4.2 GPL; `docs/python-comparison.md` "No Jupyter integration"; users@ announcements Apr 2025), [Underdog](https://github.com/grooviter/underdog) (Apache-2.0; no public releases — no tags, zero Central artifacts, 0.1.0-SNAPSHOT → private Gitea; `underdog-ml` api-scope smile-core 3.1.1; `Render.groovy` returns HTML, CDN ECharts 5.5.1-rc.1; grooviter Tablesaw fork on Central incl. tablesaw-beakerx), Alipsa `jmlx` (Aug 2026).

Spark/Toree verified 27 Aug 2026: Incubator threads ([Mclean 14 Aug](http://www.mail-archive.com/general@incubator.apache.org/msg86934.html), [Fanning 25 Aug](http://www.mail-archive.com/general@incubator.apache.org/msg87022.html), [Onofré 26 Aug](http://www.mail-archive.com/general@incubator.apache.org/msg87027.html)); [almond](https://github.com/almond-sh/almond) 0.14.5 (BSD-3) + [ammonite-spark INTERNALS](https://github.com/alexarchambault/ammonite-spark/blob/main/INTERNALS.md); `spark.repl.class.outputDir` in [SparkContext.scala](https://github.com/apache/spark/blob/master/core/src/main/scala/org/apache/spark/SparkContext.scala) (SPARK-11563) and Zeppelin's `SparkInterpreter.java`; [Spark Connect overview](https://spark.apache.org/docs/latest/spark-connect-overview.html) (`spark-connect-client-jvm_2.13`, ClassFinder/`REPLClassDirMonitor`/`addArtifact`); Spark 4.0.0/4.1.0/4.2.0 release notes + [SPARK-51167](https://issues.apache.org/jira/browse/SPARK-51167) (Java 25 in 4.2.0); [Livy status](https://incubator.apache.org/projects/livy.html).

Zeppelin verified 27 Aug 2026 on apache/zeppelin master: `zeppelin-jupyter-interpreter` (`JupyterKernelInterpreter.java`: python sidecar, pip jupyter-client/grpcio checks, gRPC channel), `docs/interpreter/jupyter.md` ("any jupyter kernel"), `zeppelin-jupyter` converter (nbformat/zformat), `groovy/` interpreter module.

Groovy-side facts from the local master tree: `subprojects/groovy-grape-maven` (`GrapeMaven implements GrapeEngine`; maven-resolver-provider + supplier-mvn4 + javax.inject; new-in-6, no binary-compat baseline), `src/main/java/groovy/grape/Grape.java` (ServiceLoader engine discovery), `subprojects/groovy-grape-ivy`; Groovy 6.0.0-beta-2 on Central (12 Aug 2026), min JDK 17 per release notes.

AI/LLM verified 27 Aug 2026: `smile-deep` artifacts on Central (LibTorch-backed LLM stack per [smile homepage](https://haifengl.github.io/)); `jjava-jupyter` publish message classes (`PublishStream`/`PublishUpdateDisplayData`/`PublishClearOutput`) listed from the repo; GitHub API license/activity for [Jlama](https://github.com/tjake/Jlama) (Apache-2.0, last push Oct 2025), [ollama4j](https://github.com/ollama4j/ollama4j) (MIT), [langchain4j](https://github.com/langchain4j/langchain4j) (Apache-2.0), [embabel](https://github.com/embabel/embabel-agent) (Apache-2.0); Jupyter AI `%%ai` magics IPython-only per its docs (not source-verified); [Exploring AI with Groovy](https://groovy.apache.org/blog/groovy-ai) blog (groovy-website `blog/groovy-ai.adoc`) + [groovy-ai repo](https://github.com/paulk-asert/groovy-ai).

BeakerX corpus inventoried 27 Aug 2026 from the local paulk-asert/groovy-data-science checkout (eight `.ipynb`; magics and display constructs extracted programmatically from cell sources; Whiskey.ipynb `%%classpath` cell pins smile-core 1.5.3); BeakerX's `%%classpath`-session-wide vs `@Grab`-cell-scoped behavior per P. King recollection, not re-verified against BeakerX source.

Name checks 27 Aug 2026: PyPI 404s for igroovy/groovy-jupyter/groovy-jupyter-kernel/groovy-kernel; GitHub igroovy search.

---

*All recommendations herein are **proposed pending team review**. The minimal-core/integration-points principle and the specific scope calls (no bundling, no widgets, one install path first, own-repo governance) were shaped in review discussion but remain unratified by the wider team; the partner items additionally depend on dflib (Andrus Adamchik), Alipsa (Per Nyfelt), and grooviter (Mario García) interest and should be socialized before any commitment.*
