use std::{
    fs,
    os::unix::fs::symlink,
    error::Error,
    path::PathBuf
};

fn main() -> Result<(), Box<dyn Error>>{
    let android_link_dir = PathBuf::from("../android/app/src/main/jniLibs");
    if android_link_dir.try_exists()? {
        return Ok(())
    }

    println!("cargo:rerun-if-changed={android_link_dir:?}");

    // Find the name of the dynamic library used by this crate.
    let cargo = fs::read_to_string("./Cargo.toml")?
        .parse::<toml::Table>()?;
    let lib_name: &str = cargo.get("lib")
        .ok_or("'lib' key not found in Cargo.toml")?
        .get("name")
        .ok_or("'name' key not found in 'lib'")?
        .as_str()
        .ok_or("value of 'name' is not string")?;

    let cwd = std::env::current_dir()?;

    // Ensure the directories where the following symlinks will be created in exist
    fs::create_dir_all(android_link_dir.join("arm64-v8a"))?;
    fs::create_dir_all(android_link_dir.join("armeabi-v7a"))?;
    fs::create_dir_all(android_link_dir.join("x86"))?;
    fs::create_dir_all(android_link_dir.join("x86_64"))?;
    
    // Setup symlinks to the cdylib for Android Studio
    // The .so files don;t exist yet, but links can be created to files that dont exist
    symlink(
        cwd.join(format!("target/aarch64-linux-android/release/lib{lib_name}.so")),
        android_link_dir.join(format!("arm64-v8a/lib{lib_name}.so"))
    )?;
    symlink(
        cwd.join(format!("target/armv7-linux-androideabi/release/lib{lib_name}.so")),
        android_link_dir.join(format!("armeabi-v7a/lib{lib_name}.so"))
    )?;
    symlink(
        cwd.join(format!("target/i686-linux-android/release/lib{lib_name}.so")),
        android_link_dir.join(format!("x86/lib{lib_name}.so"))
    )?;
    symlink(
        cwd.join(format!("target/x86_64-linux-android/release/lib{lib_name}.so")),
        android_link_dir.join(format!("x86_64/lib{lib_name}.so"))
    )?;

    Ok(())
}

/* Setup script: (not used)
NDK_HOME=$HOME/Android/Sdk/ndk-bundle

mkdir NDK
$NDK_HOME/build/tools/make_standalone_toolchain.py --api 26 --arch arm64 --install-dir NDK/arm64
$NDK_HOME/build/tools/make_standalone_toolchain.py --api 26 --arch arm --install-dir NDK/arm
$NDK_HOME/build/tools/make_standalone_toolchain.py --api 26 --arch x86 --install-dir NDK/x86
*/
