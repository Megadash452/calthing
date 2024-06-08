#![cfg(target_os="android")]
#![allow(non_snake_case)]

mod utils;
mod fs;
use utils::*;
use fs::*;

use std::{collections::HashMap, fs::File, io::ErrorKind, path::PathBuf};
use jni::{
    objects::{JClass, JString}, JNIEnv
};

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

#[no_mangle]
pub extern "system" fn Java_me_marti_calprovexample_DavSyncRs_initialize_1sync_1dir<'local>(mut env: JNIEnv<'local>, _class: JClass<'local>, dir_fd: i32) {
    // FIXME: for some reason, on my phone, libc::readdir() only returns directories (excluding . and ..), while on emulator it returns all files....
    // FIXME: Also, libc::seekdir does not work at all in the emulator. was using read
    match initialize_sync_dir(dir_fd) {
        Ok(v) => v,
        Err(error) => env.throw(error).unwrap()
    }
}

fn initialize_sync_dir(dir_fd: i32) -> Result<(), String> {
    let path = fdpath(dir_fd)
        .map_err(|error| format!("Error getting path for fd: {error}"))?;

    // Tells which entries from DIRECTORIES already exist in this directory.
    let mut dirs_exist = DIRECTORIES
        .iter()
        .map(|&dir| (dir, false))
        .collect::<HashMap<&'static str, bool>>();

    for entry in std::fs::read_dir(&path)
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

    // The directories that will be created
    let create_dirs = dirs_exist.into_iter()
        .filter_map(|(name, exists)| if exists { None } else { Some(name) });
    for name in create_dirs {
        std::fs::create_dir(path.join(name))
            .map_err(|error| format!("Error creating directory: {error}"))?
    }

    Ok(())
}


#[repr(i8)]
pub enum ImportResult {
    Error, Success, FileExists
}
// Getting s a file descriptor seems to be the only way to open a file not owned by the app, even with permission.
#[no_mangle]
pub extern "system" fn Java_me_marti_calprovexample_DavSyncRs_import_1file<'local>(mut env: JNIEnv<'static>, _class: JClass<'local>, file_fd: i32, file_name: JString<'local>, app_dir: JString<'local>, sync_dir_fd: i32) -> ImportResult {
    let file_name = get_string(&env, file_name);
    // The App's internal directory where the process has permissions to read/write files.
    let app_dir = PathBuf::from(get_string(&env, app_dir));

    unsafe { ENV = Some(&mut env as *mut _) };

    let r = match import_file(file_fd, file_name, app_dir, sync_dir_fd) {
        Ok(v) => if v {
            ImportResult::Success
        } else {
            ImportResult::FileExists
        },
        Err(error) => {
            env.throw(error).unwrap();
            ImportResult::Error
        }
    };

    unsafe { ENV = None };
    return r
}

/// Copy an *`.ics`* file's content into the internal *app's directory*
/// and also to the external directory used by the *sync* service.
/// 
/// For **app_dir** and **sync_dir**, pass in the base directory regardless of whether it should go to *calendars* or *contacts*.
/// This function will append the correct path to those directories. 
/// 
/// Returns `false` if the file couln't be imported because a file with that name already exists in the local directory.
fn import_file(file_fd: i32, file_name: String, app_dir: PathBuf, external_file_fd: i32) -> Result<bool, String> {
    /// The name that is appended to the directories' path to get the destination directory. E.g.: `"<app_dir>/calendars"`.
    // TODO: will change so that it is automatically detected whether to use "calendar" or "contacts" depending on the file type.
    const SUFFIX_DIR: &str = "calendars";
    let internal_dir = app_dir.join(SUFFIX_DIR);
    // // The base exernal dir where files are stored. This is NOT where the file will be imported. The suffix still needs to be appended.
    // let external_base_dir = DirRef::new_unowned(sync_dir_fd)
    //     .map_err(|error| format!("'sync_dir_fd' is not a file descriptor for a directory: {error}"))?;

    // Ensure the destination directories are created (internal)
    std::fs::create_dir_all(&internal_dir)
        .map_err(|error| format!("Error creating directories leading up to {internal_dir:?}: {error}"))?;

    // // Ensure the destination directories are created (external)
    // external_base_dir.create_dir(SUFFIX_DIR)
    //     .map_err(|error| format!("Error creating directory: {error}"))?;

    // The file that the user picked ot import
    let mut file = FileRef::new(unsafe { libc::dup(file_fd) });

    println!("Create file to {:?}", internal_dir.join(&file_name));

    // Open the file to copy to in the internal directory
    let mut internal_file = match File::create_new(internal_dir.join(&file_name)) {
        Ok(file) => file,
        Err(error) =>
            return if error.kind() == ErrorKind::AlreadyExists {
                println!("file exists");
                Ok(false)
            } else {
                Err(format!("Error opening file in internal dir: {error}"))
            }
    };

    // Copy file's contents to the internal directory
    std::io::copy(&mut *file, &mut internal_file)
        .map_err(|error| format!("Error copying to file in App dir: {error}"))?;

    let mut external_file = FileRef::new(external_file_fd);
    std::io::copy(&mut *file, &mut *external_file)
        .map_err(|error| format!("Error copying to file in External dir: {error}"))?;

    // FIXME: cant create external file

    // // Open the file to copy to in the sync directory (external)
    // let mut external_file = match external_base_dir.create_new_file(PathBuf::from(SUFFIX_DIR).join(&file_name)) {
    //     Ok(file) => file,
    //     Err(error) =>
    //         return if error.kind() == ErrorKind::AlreadyExists {
    //             // FIXME: if internal_file didn't exist, then it won't exist here either. So this error might be redundant...
    //             Ok(false)
    //         } else {
    //             // TODO: delete internal_file
    //             Err(format!("Error opening file in external dir: {error}"))
    //         }
    // };

    // // Copy file's contents to each respective file
    // for copy_to in [&mut internal_file, &mut external_file] {
    //     std::io::copy(&mut *file, copy_to)
    //         .map_err(|error| format!("Error copying file in App dir: {error}"))?;
    // }

    Ok(true)
}
