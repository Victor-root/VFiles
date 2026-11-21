# Baseline profile

The baseline profile tells Android which code of the app to compile ahead of time when it's
installed, which makes starting the app and scrolling its files smoother. It doesn't depend on
Google Play: the app installs it itself with ProfileInstaller.

To generate it again after large changes, connect a device running Android 13 or later (or a rooted
one), and run:

```
./gradlew :app:generateBaselineProfile
```

The profile is written to `app/src/main/generated/baselineProfiles/`, to be committed.
