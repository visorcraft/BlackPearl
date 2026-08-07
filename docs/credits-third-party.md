# Third-Party Licenses

This document lists the third-party libraries bundled in release builds
of BlackPearl, grouped by license text. It mirrors the in-app
**Settings → About → Licenses → Third-party** view
(`app/src/main/res/raw/licenses_third_party.txt`) - update both together
after any dependency change. Resolved versions come from
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.

BlackPearl is distributed under GPL-3.0-only; the libraries listed here
are included under their stated GPL-compatible licenses and we
acknowledge their authors and copyright holders accordingly.

## Licenses in use

- **Apache License 2.0** (all bundled libraries)

## Libraries

Direct dependencies:

| Library | Version | License | Project |
| ------- | ------- | ------- | ------- |
| `org.jetbrains.kotlin:kotlin-stdlib` | 1.9.24 | Apache-2.0 | https://github.com/JetBrains/kotlin |
| `androidx.appcompat:appcompat` | 1.7.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| `androidx.recyclerview:recyclerview` | 1.3.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| `androidx.documentfile:documentfile` | 1.0.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx |

Transitive AndroidX libraries (all Apache-2.0):

- `androidx.activity:activity` 1.7.0
- `androidx.annotation:annotation` 1.6.0
- `androidx.annotation:annotation-experimental` 1.4.0
- `androidx.appcompat:appcompat-resources` 1.7.0
- `androidx.arch.core:core-common` 2.2.0
- `androidx.arch.core:core-runtime` 2.2.0
- `androidx.collection:collection` 1.1.0
- `androidx.concurrent:concurrent-futures` 1.1.0
- `androidx.core:core` 1.13.0
- `androidx.core:core-ktx` 1.13.0
- `androidx.cursoradapter:cursoradapter` 1.0.0
- `androidx.customview:customview` 1.0.0
- `androidx.customview:customview-poolingcontainer` 1.0.0
- `androidx.drawerlayout:drawerlayout` 1.0.0
- `androidx.emoji2:emoji2` 1.3.0
- `androidx.emoji2:emoji2-views-helper` 1.3.0
- `androidx.fragment:fragment` 1.5.4
- `androidx.interpolator:interpolator` 1.0.0
- `androidx.lifecycle:lifecycle-common` 2.6.2
- `androidx.lifecycle:lifecycle-livedata-core` 2.6.2
- `androidx.lifecycle:lifecycle-process` 2.6.2
- `androidx.lifecycle:lifecycle-runtime` 2.6.2
- `androidx.lifecycle:lifecycle-viewmodel` 2.6.2
- `androidx.lifecycle:lifecycle-viewmodel-savedstate` 2.6.2
- `androidx.loader:loader` 1.0.0
- `androidx.profileinstaller:profileinstaller` 1.3.1
- `androidx.resourceinspection:resourceinspection-annotation` 1.0.1
- `androidx.savedstate:savedstate` 1.2.1
- `androidx.startup:startup-runtime` 1.1.1
- `androidx.tracing:tracing` 1.0.0
- `androidx.vectordrawable:vectordrawable` 1.1.0
- `androidx.vectordrawable:vectordrawable-animated` 1.1.0
- `androidx.versionedparcelable:versionedparcelable` 1.1.1
- `androidx.viewpager:viewpager` 1.0.0

Other transitive libraries:

| Library | Version | License | Project |
| ------- | ------- | ------- | ------- |
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` | 1.6.4 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| `org.jetbrains:annotations` | 13.0 | Apache-2.0 | https://github.com/JetBrains/java-annotations |
| `com.google.guava:listenablefuture` | 1.0 | Apache-2.0 | https://github.com/google/guava |

## License Texts

The full Apache License 2.0 text is reproduced in the in-app
Third-party view (`app/src/main/res/raw/licenses_third_party.txt`) and
at <https://www.apache.org/licenses/LICENSE-2.0>.
