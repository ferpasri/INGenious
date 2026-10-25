# Bundled evidence libraries

This Maven repository contains the two version 0.3.0 libraries used by the minimal evidence integration:

- [TESTAR Evidence Java](https://github.com/ferpasri/testar-evidence-java): `org.testar:testar-evidence:0.3.0`.
- [Playwright Java adapter](https://github.com/ferpasri/testar-evidence-playwright-java-adapter): `org.testar:testar-evidence-playwright-java-adapter:0.3.0`.

The Engine POM files resolve these artifacts from this directory. The adapter POM declares the evidence library as a dependency. Other dependencies are downloaded through Maven as usual.

Each artifact includes its original POM and SHA-1 checksums. Both JARs contain the BSD 3-Clause license under `META-INF/LICENSE`; a copy is provided in [LICENSE-BSD-3-Clause](LICENSE-BSD-3-Clause).
