#![cfg(target_os="android")]
#![allow(non_snake_case)]

mod utils;
mod fs;
use utils::*;
use fs::*;

use std::{collections::HashMap, fs::File, io::ErrorKind, path::PathBuf};
use jni::{
    objects::{JClass, JString}, sys::jobject, JNIEnv
};

const NULL: jobject = std::ptr::null_mut();
/// These are the names of the directories where synced data will be stored
const DIRECTORIES: [&str; 2] = ["calendars", "contacts"];

// #[no_mangle]
// pub extern "system" fn Java_me_marti_calprovexample_DavSyncRs_write_data_to_file<'local>(env: JNIEnv<'local>, _class: JClass<'local>, data: JObject<'local>, path: JString<'local>) -> jstring {
//     let path: &Path = unsafe {
//         OsStr::from_bytes(
//             CStr::from_ptr(env.get_string_unchecked(&path).expect("String 'path` can't be NULL.").get_raw())
//                 .to_bytes()
//         ).as_ref()
//     };
//     todo!()
// }

#[no_mangle]
pub extern "system" fn Java_me_marti_calprovexample_DavSyncRs_initialize_1sync_1dir<'local>(mut env: JNIEnv<'local>, _class: JClass<'local>, dir_fd: i32) {
    match initialize_sync_dir(dir_fd) {
        Ok(v) => v,
        Err(error) => env.throw(error).unwrap()
    }
}

fn initialize_sync_dir(dir_fd: i32) -> Result<(), String> {
    // The fd must be duped because it is owned by ParcelFileDescriptor in the Java side.
    let dir = DirRef::new_unowned(dir_fd)
        .map_err(|error| format!("Error opening directory: {error}"))?;

    // Tells which entries from DIRECTORIES already exist in this directory.
    let mut dirs_exist = DIRECTORIES
        .iter()
        .map(|&dir| (dir, false))
        .collect::<HashMap<&'static str, bool>>();

    for entry in dir.read_entries()
        .map_err(|error| format!("Error opening directory: {error}"))?
    {
        let entry = entry
            .map_err(|error| format!("Error reading entry: {error}"))?;

        let name = match entry.name() {
            Some(name) => name,
            // Ignore entry if name is not a valid str
            None => continue
        };

        // Mark that the entry exists so that we don't attempt to create directory..
        if let Some(exists) = dirs_exist.get_mut(name) {
            *exists = true
        }
    }

    // The directories that will be created
    let create_dirs = dirs_exist.into_iter()
        .filter_map(|(name, exists)| if exists { None } else { Some(name) });
    for name in create_dirs {
        dir.create_dir(name)
            .map_err(|error| format!("Error creating directory: {error}"))?;
    }

    Ok(())
}


#[repr(i8)]
pub enum ImportResult {
    Error, Success, FileExists
}
// Getting s a file descriptor seems to be the only way to open a file not owned by the app, even with permission.
#[no_mangle]
pub extern "system" fn Java_me_marti_calprovexample_DavSyncRs_import_1file<'local>(mut env: JNIEnv<'local>, _class: JClass<'local>, fd: i32, name: JString<'local>, appDir: JString<'local>) -> ImportResult {
    let name = get_string(&env, name);
    // The App's internal directory where the process has permissions to read/write files.
    let appDir = PathBuf::from(get_string(&env, appDir));

    match import_file(fd, name, appDir) {
        Ok(v) => if v {
            ImportResult::Success
        } else {
            ImportResult::FileExists
        },
        Err(error) => {
            env.throw(error).unwrap();
            ImportResult::Error
        }
    }
}

/// Copy an *`.ics`* file from an external location to the program's local directory.
/// 
/// Returns `false` if the file couln't be imported because a file with that anme already exists in the local directory.
fn import_file(fd: i32, name: String, appDir: PathBuf) -> Result<bool, String> {
    // The file that the user picked ot import
    let mut file = FileRef::new(fd);

    std::fs::create_dir_all(&appDir)
        .map_err(|error| format!("Error creating directories leading up to {appDir:?}: {error}"))?;

    let mut internal_file = match File::create_new(appDir.join(name)) {
        Ok(file) => file,
        Err(error) =>
            return if error.kind() == ErrorKind::AlreadyExists {
                Ok(false)
            } else {
                Err(format!("Error opening file in App dir: {error}"))
            }
    };

    std::io::copy(&mut *file, &mut internal_file)
        .map_err(|error| format!("Error copying file in App dir: {error}"))?;

    Ok(true)
}
