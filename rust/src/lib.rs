#![cfg(target_os="android")]
#![allow(non_snake_case)]

mod utils;
mod throw;
mod fs;
use utils::*;
use fs::*;

use std::{collections::HashMap, fs::File, io::ErrorKind, path::{Path, PathBuf}, ptr::NonNull};
use jni::{objects::{JObject, JString}, sys::{_jobject, jobject}, JNIEnv};
use throw::catch_throw;
use jni_macros::{call, jni_fn, package};

package!("me.marti.calprovexample");

const NULL: jobject = std::ptr::null_mut();
/// These are the names of the directories where synced data will be stored
const DIRECTORIES: [&str; 2] = ["calendars", "contacts"];

/// The name that is appended to the directories' path to get the destination directory. E.g.: `"<app_dir>/calendars"`.
// TODO: will change so that it is automatically detected whether to use "calendar" or "contacts" depending on the file type.
const SUFFIX_DIR: &str = "calendars";

#[jni_fn("jni.DavSyncRs")]
pub fn initialize_dirs<'local>(external_dir_fd: i32, app_dir: JString) {
    catch_throw!(&mut env, || {
        let app_dir = PathBuf::from(get_string(&env, app_dir));
        initialize_dirs(external_dir_fd, &app_dir)
            .inspect_err(|err| panic!("{err}"))
    });
}
/// Initialize the **internal** and **external** directories by creating all necessary sub-directories (e.g. calendars and contacts directories).
///
/// ### Parameters
/// - **external_dir_fd** is the *file descriptor* for the directory in shared storage the user picked to sync files.
/// - **app_dir** is the internal directory where all of this app's files are stored.
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

#[jni_fn("jni.DavSyncRs")]
pub fn merge_dirs<'local>(context: JObject<'local>, external_dir_uri: JObject<'local>) {
    catch_throw!(&mut env, || {
        let app_dir = get_app_dir(&mut env, &context);
        let external_dir_uri = DocUri::new(&mut env, external_dir_uri).unwrap();
        if let Err(err) = merge_dirs(&mut env, external_dir_uri, &app_dir, context) {
            env.throw(err).unwrap()
        }
    });
}
fn merge_dirs<'local>(env: &mut JNIEnv<'local>, external_dir_uri: DocUri<'local>, app_dir: &Path, context: JObject<'local>) -> Result<(), String> {
    let internal_files = app_dir.join("calendars").read_dir()
        .map_err(|err| format!("Failed reading directory: {err}"))?;
    let external_files = {
        let doc_uri = external_dir_uri.join(env, "calendars");
        ExternalDir::new(env, context, doc_uri)
            .ok_or_else(|| format!("Couldn't open directory"))?
    };
    external_files.open_file(env, "1000003539.ics").unwrap();
    
    Ok(())
}

#[repr(i8)]
pub enum ImportResult {
    Error, Success, FileExists
}
// Getting s a file descriptor seems to be the only way to open a file not owned by the app, even with permission.
#[jni_fn("jni.DavSyncRs")]
pub fn import_file_internal<'local>(file_fd: i32, file_name: JString, app_dir: JString) -> ImportResult {
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
/// A *successful* (`Ok(true)`) call to this function should be subsequently followed by a call to [`import_file_external()`]
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

#[jni_fn("jni.DavSyncRs")]
pub fn import_file_external<'local>(external_file_fd: i32, file_name: JString, app_dir: JString) {
    let (file_name,  app_dir) = match catch_throw!(&mut env, || (
        get_string(&env, file_name),
        PathBuf::from(get_string(&env, app_dir))
    )) {
        Some(v) => v,
        None => return
    };

    if let Some(Err(error)) = catch_throw(&mut env, || import_file_external(external_file_fd, &file_name, &app_dir)) {
        // Failed to complete import because couldn't copy to external file.
        // Delete the imported file in the internal directory.
        catch_throw!(&mut env, || {
            if let Err(error2) = std::fs::remove_file(app_dir.join(SUFFIX_DIR).join(file_name)) {
                env.throw(format!("Couldnt delete internal imported file: {error2}")).unwrap();
            };
            env.throw(error).unwrap();
        });
    }
}
/// Write the contents of the file already imported in the *internal directory* to the new file created in *sync directory* (external).
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

#[jni_fn("jni.DavSyncRs")]
pub fn new_calendar_from_file<'local>(context: JObject, name: JString, app_dir: JString) -> jobject {
    match catch_throw!(&mut env, || {
        let name = get_string(&env, name);
        let app_dir = PathBuf::from(get_string(&env, app_dir));
        new_calendar_from_file(name, app_dir, &mut env, context)
    }) {
        Some(Ok(obj)) => obj.as_ptr(),
        Some(Err(err)) => {
            env.throw(err).unwrap();
            NULL
        },
        None => 0 as jobject
    }
}
fn new_calendar_from_file(name: String, app_dir: PathBuf, env: &mut JNIEnv, context: JObject) -> Result<NonNull<_jobject>, String> {
    // Check that a calendar with this name doesn't already exist
    let exists = call!(static me.marti.calprovexample.jni.DavSyncRsHelpers::checkUniqueName(
        android.content.Context(context),
        java.lang.String(env.new_string(&name).unwrap())
    ) -> Result<bool, String>)?;

    Err("TODO: return InternalUserCalendar".to_string())
}
