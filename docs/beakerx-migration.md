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

# Migrating BeakerX Groovy notebooks

[BeakerX](https://github.com/twosigma/beakerx)'s Groovy kernel has been frozen
since 2020 and predates JupyterLab 4 / Notebook 7. Its notebooks port to
`groovy-jupyter` with a handful of mechanical substitutions — the kernelspec
name is already `groovy`, so the notebook files themselves need no metadata
changes, and BeakerX's binding-style session variables (`records = ...`)
behave identically here.

## The five substitutions

| BeakerX construct | Under groovy-jupyter |
|---|---|
| `%%classpath add mvn`<br>`group artifact version` (one per line) | `@Grab('group:artifact:version')` (one per line), attached to an `import` — see below. **Scope note:** BeakerX's `%%classpath` was session-wide while its `@Grab` was scoped to the declaring cell; here `@Grab` resolves into the persistent session classloader, so it is session-wide — one mechanism covers both. |
| `%import a.b.C`<br>`%import static a.b.C.*` | Plain `import` / `import static` statements in any cell — imports persist to later cells. |
| `SomethingDisplayer.register()` (e.g. `tech.tablesaw.beakerx.TablesawDisplayer`) | Delete the line. Libraries self-register renderers via the kernel's ServiceLoader `Extension` mechanism when their jar is grabbed; `List<Map>` rows, `Map`s and GINQ results already render as HTML tables out of the box. |
| `OutputCell.HIDDEN` | End the cell with `HIDDEN` (auto-imported — often just delete the `OutputCell.` prefix), or delete the line entirely. Ending with `null` also works: a null result produces no output. |
| `new Plot(…) << new Points(…)`, `TimePlot`, `Histogram`, `SimpleTimePlot`, … (BeakerX JS widgets) | The one non-mechanical step: produce SVG or HTML instead and pass it through — `displaySvg(svg)` / `displayHtml(html)` — or use a charting library that exports SVG/PNG (XChart, matrix-charts, ECharts-as-HTML). The [ported notebooks](../notebooks/) include hand-rolled SVG scatter plots, histograms, time series and bar charts to copy from. |

## Gotchas worth knowing

- **Anchor `@Grab` on an import or declaration.** A `@Grab` annotation attached
  to a bare assignment statement (`@Grab(...)` directly followed by `x = 1`)
  currently causes Groovy to silently drop that statement. Grabs followed by an
  `import` — the usual style — are fine.
- **GINQ grouping over CSV-style data: prefer `groupby ... into`.** Groovy 6's
  `groupby k into g` binds the group as a first-class object with explicit
  aggregate lambdas:

  ```groovy
  GQ {
      from r in rows
      groupby r.Class into g
      select g.key as species, g.min(r -> r.'Petal length' as double) as min,
             (g.avg(r -> r.'Petal length' as double) as double).round(2) as mean
  }
  ```

  Besides reading well, this form sidesteps issues in the classic implicit
  aggregates with capitalized column names (the norm in CSV headers) present
  in current Groovy 6 betas.
- **Session semantics are a superset of BeakerX's.** Undeclared variables
  persist (as before), and `def`/typed declarations at the top level of a cell
  now persist too, as do methods and classes defined in cells. Tuple
  declarations (`def (a, b) = ...`) remain cell-local.
- **Interrupts work.** Cells compile with Groovy's `ThreadInterrupt`, so
  *Kernel → Interrupt* actually stops a runaway loop in cell code (loops inside
  third-party jars still require a kernel restart).

## Worked examples

Each classic notebook from
[groovy-data-science](https://github.com/paulk-asert/groovy-data-science) and
its port in this repository:

| BeakerX original | Port | Notes |
|---|---|---|
| `Whiskey.ipynb` | [`whiskey.ipynb`](../notebooks/whiskey.ipynb) | Tablesaw → `groovy-csv` `CsvSlurper` (ships with the kernel); k-means via Smile 1.5.3 `@Grab`; GINQ counts; PCA + SVG scatter |
| `Iris.ipynb` | [`iris.ipynb`](../notebooks/iris.ipynb) | GINQ `groupby ... into` stats; hand-rolled LOOCV; confusion matrix as auto-table; SVG scatter with misclassification rings |
| `HousePrices.ipynb` | [`houseprices.ipynb`](../notebooks/houseprices.ipynb) | `CsvSlurper` over a `GZIPInputStream`; OLS R² comparison table; SVG histogram and fit-line scatter |
| `LanguageProcessing.ipynb` | [`languageprocessing.ipynb`](../notebooks/languageprocessing.ipynb) | OpenNLP models via Maven Central `@Grab`s where available; cached classic models; results as tables |
| `Candles.ipynb` | [`candles.ipynb`](../notebooks/candles.ipynb) | tablesaw-excel → Apache POI `@Grab`; SVG time series with reference line; error-whisker bars |

Not ported: `WhiskeyWayang` (pending an Apache Wayang version refresh),
`WhiskeyIgnite` (Ignite 2.x needs `--add-opens` JVM flags on modern JDKs),
`DrunkenSailor` (superseded by the SVG examples above).
