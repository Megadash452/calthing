use std::{
    collections::HashMap,
    env::{self, VarError},
    error::Error,
    ffi::{OsStr, OsString},
    fmt::Write,
    fs,
    io::Read,
    os::unix::fs::symlink,
    path::{Path, PathBuf},
};

fn main() -> Result<(), Box<dyn Error>> {
    let sdk_home = match env::var("ANDROID_SDK_HOME") {
        Ok(val) => PathBuf::from(val),
        // Derive android home directory if environment variable is not set
        Err(VarError::NotPresent) => PathBuf::from(env::var("HOME")?).join("Android/Sdk"),
        Err(error) => return Err(error.into()),
    };
    create_file_from_template(
        "./.cargo/config.toml.in",
        HashMap::from([("ANDROID_SDK_HOME", sdk_home.as_os_str())]),
    )?;

    let android_link_dir = PathBuf::from("../android/app/src/main/jniLibs");
    if android_link_dir.try_exists()? {
        return Ok(());
    }

    println!("cargo:rerun-if-changed={android_link_dir:?}");

    // Find the name of the dynamic library used by this crate.
    let cargo = fs::read_to_string("./Cargo.toml")?.parse::<toml::Table>()?;
    let lib_name: &str = cargo
        .get("lib")
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
        cwd.join(format!(
            "target/aarch64-linux-android/release/lib{lib_name}.so"
        )),
        android_link_dir.join(format!("arm64-v8a/lib{lib_name}.so")),
    )?;
    symlink(
        cwd.join(format!(
            "target/armv7-linux-androideabi/release/lib{lib_name}.so"
        )),
        android_link_dir.join(format!("armeabi-v7a/lib{lib_name}.so")),
    )?;
    symlink(
        cwd.join(format!(
            "target/i686-linux-android/release/lib{lib_name}.so"
        )),
        android_link_dir.join(format!("x86/lib{lib_name}.so")),
    )?;
    symlink(
        cwd.join(format!(
            "target/x86_64-linux-android/release/lib{lib_name}.so"
        )),
        android_link_dir.join(format!("x86_64/lib{lib_name}.so")),
    )?;

    Ok(())
}

/// Create a new file from  a *Template* (ending in `.in`),
/// replacing all *variables* in the *Template* with the corresponding *value* in **vars**.
///
/// Does nothing if file already exists.
///
/// **path** is the path (relative to cwd) to the template file, *includeing the `.in`*.
///
/// **vars** are *key-value pairs*, where the *key* is the **variable name**.
fn create_file_from_template(path: impl AsRef<Path>, vars: HashMap<&str, &OsStr>) -> Result<(), Box<dyn Error>> {
    const VAR_DELIM: &str = "@";
    fn var_name_check(name: &str) -> bool {
        !name.is_empty() && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
    }

    let path = path.as_ref();
    // The path of the resulting file created form this template (removing the '.in').
    let target_path = path.with_extension("");
    if target_path.exists() {
        return Ok(());
    }

    let mut file = fs::OpenOptions::new()
        .write(true)
        .read(true)
        .create(false)
        .open(path)
        .map_err(|err| format!("Error opening file {path:?}: {err}"))?;

    // Content of the Template file
    let content = {
        let mut buf = String::new();
        file.read_to_string(&mut buf)
            .map_err(|err| format!("Error reading file {path:?}: {err}"))?;
        buf
    };
    // Buffer of the resulting file's content after template is applied
    let mut buf = OsString::new();

    // Read each line to find the variable slots
    'lines: for line in content.lines() {
        // Index after where the end of the last variable was, making the following loop start looking for variables from there.
        let mut line_cursor = 0;
        while line_cursor <= line.len() {
            // Find the next VAR_DELIM, denoting the possible start of a variable slot.
            // Points to the LAST character of the instance of VAR_DELIM found in line.
            let var_start = match line[line_cursor..].find(VAR_DELIM) {
                // find returns the offset from where it started looking
                Some(offset) => line_cursor + offset + VAR_DELIM.len() - 1,
                // There are no variable slots in this line. Move to the next line.
                None => {
                    buf.write_str(&line[line_cursor..])?;
                    buf.write_char('\n')?;
                    continue 'lines;
                }
            };
            // Find the next VAR_DELIM character, denoting the end delimiter of the variable slot
            // Points to the FIRST character of the instance of VAR_DELIM found in line.
            let var_end = match &line[var_start + 1..].find(VAR_DELIM) {
                // find returns the offset from where it started looking
                Some(offset) => var_start + 1 + offset,
                // var_start was a standalong '@', not representing a variable slot. Move to the next line.
                None => {
                    buf.write_str(&line[line_cursor..])?;
                    buf.write_char('\n')?;
                    continue 'lines;
                }
            };

            // Append the content of the line up to where VAR_DELIM starts.
            // Will decide whether to leave variable or apply a value later.
            buf.write_str(&line[line_cursor..var_start + 1 - VAR_DELIM.len()])?;

            line_cursor = var_end + VAR_DELIM.len();
            let var_name = &line[var_start + 1..var_end];
            let value = *vars.get(var_name).ok_or(format!(
                "Found variable {var_name:?} that doesn't exist in {path:?}"
            ))?;

            if var_name_check(var_name) {
                buf.push(value);
            } else {
                // Is not a variable if does not pass the check. Leave the variable name.
                buf.write_str(&line[var_start - VAR_DELIM.len()..var_end + VAR_DELIM.len()])?;
            }
        }
    }

    // Write the result to the target file.
    fs::write(target_path, buf.as_os_str().as_encoded_bytes())?;

    Ok(())
}

/* old Setup script: (not used)
NDK_HOME=$HOME/Android/Sdk/ndk-bundle

mkdir NDK
$NDK_HOME/build/tools/make_standalone_toolchain.py --api 26 --arch arm64 --install-dir NDK/arm64
$NDK_HOME/build/tools/make_standalone_toolchain.py --api 26 --arch arm --install-dir NDK/arm
$NDK_HOME/build/tools/make_standalone_toolchain.py --api 26 --arch x86 --install-dir NDK/x86
*/
