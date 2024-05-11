# Generic Rust bindings for Java

Bindings for the DavSync library to be used in Android

# Setup

Install Android **NDK**:
Open *Android Studio*, go to `Settings` > `Languages & Frameworks` > `Android SDK` > `SDK Tools`,
Check the box for **`NDK (Side by side)`** and click Apply to install.

Create a symlink to the installed version of the NDK:
`ln -s ~/Android/Sdk/ndk/<latest version number>/ ~/Android/Sdk/ndk/latest`

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
