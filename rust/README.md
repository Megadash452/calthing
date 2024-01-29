# Generic Rust bindings for Java

Bindings for the DavSync library to be used in Android

# Setup

Install targets for android:
`rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android`

Crate the android project in `<project-dir>/android` and download the rust crate to `<project-dir>/rust`.
so the file structure should look like this:
```
MyProject
├── android
│   ├── build.gradle.kts
│   └── ...
├── rust
│   ├── Cargo.toml
│   └── ...
```

Run [`./build.sh`](./build.sh) to build for all architectures.
A directory (`android/app/src/main/jniLibs`) will be created containing links to the compiled library.
