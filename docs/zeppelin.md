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

# Using the Groovy kernel from Apache Zeppelin

Apache Zeppelin ships a kernel-agnostic
[Jupyter bridge interpreter](https://zeppelin.apache.org/docs/latest/interpreter/jupyter.html)
that can run any installed Jupyter kernel — including this one, with **no
kernel-side changes**. Verified against Zeppelin 0.12.1.

## Prerequisites

1. The Groovy kernelspec installed for the user running Zeppelin
   (`./gradlew installKernelSpec ...` — see the [README](../README.md)).
2. A Python environment for the bridge's sidecar process, findable as
   `python` on the Zeppelin daemon's `PATH` (or configured via the
   `zeppelin.python` interpreter property):

   ```
   pip install jupyter-client grpcio 'protobuf==3.20.3'
   ```

   The `protobuf==3.20.3` pin matters: Zeppelin's bundled gRPC stubs were
   generated with an older protoc, and protobuf ≥ 4 refuses them — the symptom
   is paragraphs failing with *"Fail to launch Jupyter Kernel as the python
   process is failed"*.

## Usage

Prefix a paragraph with `%jupyter(kernel=groovy)`:

```
%jupyter(kernel=groovy)
rows = new groovy.csv.CsvSlurper().parse(new File('/path/to/whiskey.csv'))
rows.size()
```

One kernel session backs the whole note, so **state persists across
paragraphs** — variables, classes, methods, imports and `@Grab`bed
dependencies defined in one paragraph are available in the next, exactly as in
Jupyter cells. The first paragraph is slow (Zeppelin starts the bridge, its
Python sidecar, and the kernel JVM); later paragraphs are immediate.

## Display mapping

The bridge translates Jupyter mime bundles into Zeppelin's display system:

| Kernel output | Zeppelin result type | Rendered? |
|---|---|---|
| text results, `println` | TEXT | yes |
| HTML — auto-rendered tables (`List<Map>`, `Map`, GINQ), `displayHtml` | HTML | yes |
| `displayPng` | IMG | yes |
| `displaySvg` | TEXT (falls back to the plain-text form) | no — shows raw markup |
| `displayMarkdown` | TEXT | no — shows raw source |

Guidance for Zeppelin-facing notebooks: tables just work; for charts either
emit PNG, or wrap SVG markup in `displayHtml(svg)` — HTML-embedded SVG renders
fine:

```
%jupyter(kernel=groovy)
displayHtml("<svg xmlns='http://www.w3.org/2000/svg' width='60' height='60'><circle cx='30' cy='30' r='25' fill='green'/></svg>")
HIDDEN
```

## Notebook portability

Zeppelin's `zeppelin-jupyter` module converts between the two notebook formats
(`.ipynb` ↔ `.zpln`) in both directions, so notebooks written for this kernel
can move into Zeppelin notes and back.
