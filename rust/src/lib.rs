#![cfg(target_os="android")]
#![allow(non_snake_case)]

mod utils;
mod fs;
use jni_macros::{jni_fn, package};
use utils::*;
use fs::*;

use std::{collections::HashMap, fs::{DirEntry, File}, io::ErrorKind, path::{Path, PathBuf}};
use jni::objects::JString;

package!("me.marti.calprovexample");

// const NULL: jobject = std::ptr::null_mut();
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

/// The name that is appended to the directories' path to get the destination directory. E.g.: `"<app_dir>/calendars"`.
// TODO: will change so that it is automatically detected whether to use "calendar" or "contacts" depending on the file type.
const SUFFIX_DIR: &str = "calendars";

#[jni_fn]
pub fn initialize_dirs(external_dir_fd: i32, app_dir: JString) {
    let app_dir = PathBuf::from(get_string(&env, app_dir));
    // FIXME: for some reason, on my phone, libc::readdir() only returns directories (excluding . and ..), while on emulator it returns all files....
    // FIXME: Also, libc::seekdir does not work at all in the emulator. was using read
    if let Some(Err(err)) = catch_throw(&mut env, || initialize_dirs(external_dir_fd, &app_dir)) {
        env.throw(err).unwrap()
    }
}

/// Initialize the **internal** and **external** directories by creating all necessary directories (e.g. calendars and contacts directories).
/// 
/// ### Paramters
/// - **dir_fd** is the *file descriptor* for the directory in shared storage the user picked to sync files.
/// - **app_dir** is the app's internal direcotry where its files are stored.
fn initialize_dirs(external_dir_fd: i32, app_dir: &Path) -> Result<(), String> {
    let external_dir = fdpath(external_dir_fd)
        .map_err(|error| format!("Error getting path for fd: {error}"))?;

    // Initialize both external and internal (app_dir) directories
    for directory in [external_dir.as_path(), app_dir] {
        // Tells which entries from DIRECTORIES already exist in this directory.
        // Starts with all false.
        let mut dirs_exist = DIRECTORIES
            .iter()
            .map(|&dir| (dir, false))
            .collect::<HashMap<&'static str, bool>>();
    
        // Populate dirs_exist with true values for entries that exist in the directory.
        for entry in std::fs::read_dir(directory)
            .map_err(|error| format!("Error opening directory: {error}"))?
        {
            let entry = entry
                .map_err(|error| format!("Error reading entry: {error}"))?;
    
            let name = entry.file_name();
            let name = match name.to_str() {
                Some(name) => name,
                // Ignore entry if name is not a valid str
                None => continue
            };
    
            // Mark that the entry exists so that we don't attempt to create directory..
            if let Some(exists) = dirs_exist.get_mut(name) {
                *exists = true
            }
        }
    
        // Create the directories
        for name in dirs_exist.into_iter()
            .filter_map(|(name, exists)| if exists { None } else { Some(name) })
        {
            std::fs::create_dir(directory.join(name))
                .map_err(|error| format!("Error creating directory: {error}"))?
        }
    }

    Ok(())
}

#[jni_fn]
pub fn merge_dirs(external_dir_fd: i32, app_dir: JString) {
    let app_dir = PathBuf::from(get_string(&env, app_dir));
    if let Some(Err(err)) = catch_throw(&mut env, || merge_dirs(external_dir_fd, &app_dir)) {
        env.throw(err).unwrap()
    }
}
fn merge_dirs(external_dir_fd: i32, app_dir: &Path) -> Result<(), String> {
    let external_dir = fdpath(external_dir_fd)
        .map_err(|error| format!("Error getting path for fd: {error}"))?;
    fn ls(path: &Path) -> Result<Box<[DirEntry]>, String> {
        Result::from_iter(
            path.read_dir()
                .map_err(|error| format!("Error opening directory: {error}"))?
        ).map_err(|error| format!("Error reading directory entries: {error}"))
    }

    // TODO: external files always empty... (refer to comment of Java_me_marti_calprovexample_DavSyncRs_initialize_1dirs)
    let external_files = ls(&external_dir.join("calendars"))?;
    let internal_files = ls(&app_dir.join("calendars"))?;
    Ok(())
}

#[repr(i8)]
pub enum ImportResult {
    Error, Success, FileExists
}
// Getting s a file descriptor seems to be the only way to open a file not owned by the app, even with permission.
#[jni_fn]
pub fn import_file_internal(file_fd: i32, file_name: JString, app_dir: JString) -> ImportResult {
    let file_name = get_string(&env, file_name);
    // The App's internal directory where the process has permissions to read/write files.
    let app_dir = PathBuf::from(get_string(&env, app_dir));

    if let Some(r) = catch_throw(&mut env, || import_file_internal(file_fd, &file_name, &app_dir)) {
        match r {
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
    } else { ImportResult::Error }
}
/// Copy an *`.ics`* file's content into the internal *app's directory*.
/// 
/// A *successful* (`Ok(true)`) call to this function shold be subsequently followed by a call to [`import_file_external()`]
/// 
/// ### Parameters
/// **file_fd** is the *unowned* file descriptor of the file to be imported, and **file_name** is its name (including extension).
/// For **app_dir** and **sync_dir**, pass in the base directory regardless of whether it should go to *calendars* or *contacts*.
/// This function will append the correct path to those directories. 
/// 
/// ### Return
/// Returns `false` if the file couln't be imported because a file with that name already exists in the local directory.
fn import_file_internal(file_fd: i32, file_name: &str, app_dir: &Path) -> Result<bool, String> {
    let internal_dir = app_dir.join(SUFFIX_DIR);
    
    // Ensure the destination directories are created (internal)
    std::fs::create_dir_all(&internal_dir)
        .map_err(|error| format!("Error creating directories leading up to {internal_dir:?}: {error}"))?;

    // The file that the user picked ot import
    let mut file = FileRef::new(file_fd);

    // Open the file to copy to in the internal directory
    let mut internal_file = match File::create_new(internal_dir.join(file_name)) {
        Ok(file) => file,
        Err(error) =>
            return if error.kind() == ErrorKind::AlreadyExists {
                Ok(false)
            } else {
                Err(format!("Error opening file in internal dir: {error}"))
            }
    };

    // Copy file's contents to the destination
    std::io::copy(&mut *file, &mut internal_file)
        .map_err(|error| format!("Error copying to file in App dir: {error}"))?;

    Ok(true)
}

#[jni_fn]
pub fn import_file_external(external_file_fd: i32, file_name: JString, app_dir: JString) {
    let file_name = get_string(&env, file_name);
    // The App's internal directory where the process has permissions to read/write files.
    let app_dir = PathBuf::from(get_string(&env, app_dir));

    if let Some(Err(error)) = catch_throw(&mut env, || import_file_external(external_file_fd, &file_name, &app_dir)) {
        // Failed to complete import because couldn't copy to external file.
        // Delete the imported file in the internal directory.
        if let Err(error2) = std::fs::remove_file(app_dir.join(SUFFIX_DIR).join(file_name)) {
            env.throw(format!("Couldnt delete internal imported file: {error2}")).unwrap();
        };
        env.throw(error).unwrap();
    }
}
/// Write the contents of the file already imported in the *internal directory* to the file in *sync directory* (external).
fn import_file_external(external_file_fd: i32, file_name: &str, app_dir: &Path) -> Result<(), String> {
    // Open the file to copy FROM in the internal directory
    let mut internal_file = std::fs::File::open(app_dir.join(SUFFIX_DIR).join(file_name))
        .map_err(|error| format!("Error opening file in internal dir: {error}"))?;
    // Open the file to copy TO in the sync directory (external)
    let mut external_file = FileRef::new(external_file_fd);

    // Copy file's contents to the destination
    std::io::copy(&mut internal_file, &mut *external_file)
        .map_err(|error| format!("Error copying to file in App dir: {error}"))?;

    Ok(())
}
