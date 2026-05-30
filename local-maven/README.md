# Bundled offline Maven repository

A small Maven repository checked into the project so the build does not rely on
JitPack building a specific pinned commit (JitPack no longer serves an artifact
for that dav4jvm commit, which broke the build).

It is registered in `app/build.gradle`:

```groovy
repositories {
    maven { url uri("$rootDir/local-maven") }
    // ...
}
```

## Contents

- `com.github.bitfireAT:dav4jvm:02fe1a95e6`
  Built from https://github.com/bitfireAT/dav4jvm at commit
  `02fe1a95e6b86e323bec3784d7d2fe2d4081dde6` with:

  ```
  GIT_COMMIT=02fe1a95e6 ./gradlew publishToMavenLocal
  ```

  (run from a clone whose directory is named `dav4jvm` so the artifact id is
  `dav4jvm`), then copying the published `.jar`, `.pom` and `.module` here.

Transitive dependencies (okhttp, kotlin-stdlib, …) are still resolved from Maven
Central as usual.
