# Vendored dependencies

## `jeromq-0.7.0-SNAPSHOT.jar`

[JeroMQ](https://github.com/zeromq/jeromq) built from `master` at commit
`f540268c81d787aee5f5ec9bc74a937a7f1ee8e8` (2 Mar 2025). Licence: MPL-2.0
(`jeromq-LICENSE.txt`).

Why a snapshot: the last JeroMQ release on Maven Central (0.6.0, Feb 2024)
emulates `ipc://` over loopback TCP and cannot talk to libzmq clients.
[zeromq/jeromq#998](https://github.com/zeromq/jeromq/pull/998) (merged Sep 2024,
unreleased) adds real Unix-domain-socket `ipc://` on JDK 16+ as a
multi-release jar. Jupyter hosts that launch kernels with `--transport=ipc`
(hosted Google Colab does) need it. Replace with the Maven Central artifact
once JeroMQ 0.7.0 ships.

Rebuild:

```
git clone https://github.com/zeromq/jeromq && cd jeromq && git checkout f540268
mvn -DskipTests -Dcheckstyle.skip=true -Dmaven.javadoc.skip=true package
cp jeromq/target/jeromq-0.7.0-SNAPSHOT.jar <this dir>/
```
