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

# Releasing

A release is a pushed `vX.Y.Z` tag. The
[Release workflow](../.github/workflows/release.yml) builds with
`-PreleaseVersion=X.Y.Z`, runs the tests, and creates a **draft** GitHub
release with `groovy-jupyter-kernelspec-X.Y.Z.zip` and `SHA256SUMS` attached.
Nothing is public until the draft is published by hand.

## Checklist

1. **Notebooks.** With `./gradlew installKernelSpec` (plus the Spark
   `-PjvmArgs`), run at least `iris`, `whiskey` and `spark` top to bottom and
   save them, so their metadata shows the released Groovy version.
2. **Dry run.** Actions → Release → *Run workflow* on `main`. Download the
   `kernelspec` artifact and install it on a machine (or container) that
   has never had the kernel:

   ```
   unzip groovy-jupyter-kernelspec-*.zip
   jupyter kernelspec install groovy-jupyter-kernelspec-<version> --user --name groovy
   jupyter console --kernel groovy     # try: 6 * 7, then an @Grab
   ```

3. **Tag.**

   ```
   git tag -a vX.Y.Z -m "groovy-jupyter X.Y.Z"
   git push origin vX.Y.Z
   ```

4. **Publish.** Edit the draft's generated notes: headline changes, the
   Groovy and minimum-JDK versions, known limitations. Then publish it.
   Tick *pre-release* while the version is 0.x if you want it kept out of
   "latest".
5. **Afterwards.** Bump the default `version` in `build.gradle` to the next
   `-SNAPSHOT`.

## Licensing notes for the zip

Everything in `lib/` is Apache-2.0, MIT or BSD, except the JeroMQ classes
merged into the kernel jar (MPL-2.0, see [libs/README.md](../libs/README.md)).
The jar carries the MPL licence text and a pointer to the exact source commit
under `META-INF/jeromq/`, which covers MPL's binary-distribution terms. Once
JeroMQ 0.7.0 is on Maven Central, swap the `embedded` dependency for the
released artifact and delete `libs/`.
