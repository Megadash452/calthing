#![cfg(target_os="android")]
#![allow(non_snake_case)]

mod utils;
mod throw;
mod fs;
mod calendar;
use utils::*;
use fs::*;

use std::{fs::File, io::ErrorKind, path::{Path, PathBuf}, ptr::NonNull};
use jni::{objects::{JObject, JString}, sys::{_jobject, jobject}, JNIEnv};
use jni_macros::{call, jni_fn, package};

package!("me.marti.calprovexample");

/// These are the names of the directories where synced data will be stored
const DIRECTORIES: [&str; 2] = ["calendars", "contacts"];

/// The name that is appended to the directories' path to get the destination directory. E.g.: `"<app_dir>/calendars"`.
// TODO: will change so that it is automatically detected whether to use "calendar" or "contacts" depending on the file type.
const SUFFIX_DIR: &str = "calendars";

#[jni_fn("jni.DavSyncRs")]
pub fn initialize_dirs<'local>(context: JObject<'local>, external_dir_uri: JObject<'local>) {
    let app_dir = get_app_dir(env, &context);
    let external_dir_uri = DocUri::from_tree_uri(env, external_dir_uri).unwrap();
    initialize_dirs(env, external_dir_uri, &app_dir, context)
        .inspect_err(|err| panic!("{err}")).unwrap()
}
/// Initialize the **internal** and **external** directories by creating all necessary sub-directories (e.g. calendars and contacts directories).
///
/// ### Parameters
/// - **external_dir_fd** is the *file descriptor* for the directory in shared storage the user picked to sync files.
/// - **app_dir** is the internal directory where all of this app's files are stored.
fn initialize_dirs<'local>(env: &mut JNIEnv<'local>, external_dir_uri: DocUri<'local>, app_dir: &Path, context: JObject<'local>) -> Result<(), String> {
    let external_dir = ExternalDir::new(env, context, external_dir_uri)
        .ok_or_else(|| format!("Couldn't open external directory"))?;
    
    // -- Initialize internal directory (app_dir)
    let entries = std::fs::read_dir(app_dir)
        .map_err(|err| format!("Error reading internal directory: {err}"))?
        .filter_map(Result::ok)
        .filter_map(|entry| entry.file_name().to_str().map(str::to_string)) // Ignore entries that re not UTF-8
        .collect::<Box<[_]>>();
    // Find the DIRECTORIES that are missing from entries and create them
    for &dir in DIRECTORIES.iter()
        .filter(|&&dir| !entries.iter().any(|e| e == dir))
    {
        std::fs::create_dir(app_dir.join(dir))
            .map_err(|error| format!("Error creating directory: {error}"))?
    }
    
    // -- Initialize external directory (shared storage)
    // Don't have to find missing directories; will not return error if directories already exist.
    for &dir in &DIRECTORIES {
        external_dir.create_dir(env, dir)
            .map_err(|error| format!("Error creating directory: {error}"))?;
    }

    Ok(())
}

#[jni_fn("jni.DavSyncRs")]
pub fn merge_dirs<'local>(activity: JObject<'local>, external_dir_uri: JObject<'local>) {
    let context = call!(activity.getBaseContext() -> android.content.Context);
    let app_dir = get_app_dir(env, &context);
    let external_dir_uri = DocUri::from_tree_uri(env, external_dir_uri).unwrap();
    merge_dirs(env, external_dir_uri, &app_dir, activity, context)
        .inspect_err(|err| panic!("{err}")).unwrap()
}
fn merge_dirs<'local>(env: &mut JNIEnv<'local>, external_dir_uri: DocUri<'local>, app_dir: &Path, activity: JObject<'local>, context: JObject<'local>) -> Result<(), String> {
    let internal_dir = app_dir.join("calendars");
    let external_dir = {
        let doc_uri = external_dir_uri.join(env, "calendars");
        ExternalDir::new(env, env.new_local_ref(&context).unwrap(), doc_uri)
            .ok_or_else(|| format!("Couldn't open external directory"))?
    };

    let internal_files = internal_dir.read_dir()
        .map_err(|err| format!("Failed reading directory: {err}"))?
        .filter_map(Result::ok)
        .collect::<Box<[_]>>();
    let external_files = external_dir.entries(env);
    
    // Find the files that are in one directory but not in the other, and copy them to the other.
    let copy_to_internal = external_files.iter().filter(
        // Filter by files that are NOT in the internal directory
        |external_entry| internal_files.iter()
            .find(|internal_entry| external_entry.file_name() == internal_entry.file_name())
            .is_none()
    );
    let copy_to_external = internal_files.iter().filter(
        // Filter by files that are NOT in the external directory
        |internal_entry| external_files.iter()
            .find(|external_entry| internal_entry.file_name() == external_entry.file_name())
            .is_none()
    );
    let files_to_merge = internal_files.iter().filter(
        // Filter by files that IN BOTH directories
        |internal_entry| external_files.iter()
            .find(|external_entry| internal_entry.file_name() == external_entry.file_name())
            .is_some()
    );
    
    // -- Copy the files
    
    // Copy external files to internal directory
    for entry in copy_to_internal.clone() {
        let mut external_file = entry.open_file(env, &context, OpenOptions::ReadOnly)
            .map_err(|err| format!("Failed to open file in external directory: {err}"))?;
        
        // Open the file to copy to in the internal directory
        let mut internal_file = match File::create_new(internal_dir.join(entry.file_name())) {
            Ok(file) => file,
            Err(error) =>
                return if error.kind() == ErrorKind::AlreadyExists {
                    panic!("Unreachable: Already checked that file does not exist in internal directory")
                } else {
                    Err(format!("Error opening file in internal dir: {error}"))
                }
        };
    
        // Copy file's contents to the destination
        std::io::copy(&mut external_file, &mut internal_file)
            .map_err(|error| format!("Error copying to file in internal directory: {error}"))?;
    }
    
    // Copy internal files to external directory
    for entry in copy_to_external {
        let mut internal_file = std::fs::File::open(entry.path())
            .map_err(|err| format!("Failed to open file in internal directory: {err}"))?;
        
        // Open the file to copy to in the external directory
        let mut external_file = match external_dir.create_file(env, entry.file_name().to_str().expect("File name must be UTF-8")) {
            Ok(file) => file.open_file(env, &context, OpenOptions::write())
                .map_err(|err| format!("Failed to open newly created file in external directory: {err}"))?,
            Err(error) =>
                return if error.kind() == ErrorKind::AlreadyExists {
                    panic!("Unreachable: Already checked that file does not exist in external directory")
                } else {
                    Err(format!("Error opening file in external dir: {error}"))
                }
        };
        
        // Copy file's contents to the destination
        std::io::copy(&mut internal_file, &mut external_file)
            .map_err(|error| format!("Error copying to file in external directory: {error}"))?;
    }
    
    // TODO: merge files in files_to_merge
    /*kt
    // Check if common files are different, and ask user whether to accept incoming or keep internal
    for (fileName in filesToMerge) {
        val internalFile = Path("${this.filesDir.path}/calendars/$fileName").bufferedReader().use { it.readText() }
        val externalFile = this.contentResolver.openInputStream(syncDir.join("calendars/$fileName"))!!.use { it -> it.bufferedReader().use { it.readText() } }
        if (internalFile != externalFile)
            TODO("Show dialog asking user whether to keep current or accept incoming (launch in other thread)")
    }
    */
    
    // Add calendars from external directory to Content Provider
    // Calendars in internal dir are should already be in the Content Provider, so no need to do this for copyToExternal too.
    let perm_manager = env.get_field(&activity, "calendarPermission", "Lme/marti/calprovexample/ui/CalendarPermission;")
        .unwrap().l().unwrap();
    if let Some(perm) = call!(perm_manager.usePermission() -> Option<me.marti.calprovexample.ui.CalendarPermissionScope>) {
        for entry in copy_to_internal {
            let name = entry.file_stem();
            call!(perm.writeFileDataToCalendar(java.lang.String(env.new_string(name).unwrap())) -> void)
        }
        
        let user_calendars = call!(activity.getUserCalendars() -> Option<me.marti.calprovexample.ui.MutableCalendarsList>);
        if let Some(user_calendars) = user_calendars {
            call!(user_calendars.syncWithProvider() -> void)
        }
    };
    
    Ok(())
}

#[repr(i8)]
pub enum ImportResult {
    Error, Success, FileExists
}
/// Copy an *`.ics`* file's content into the internal *app's directory*.
///
/// A *successful* call to this function should be subsequently followed by a call to [`import_file_external()`]
///
/// ### Parameters
/// **file_uri** is the *Document Uri* of the file to be imported.
/// **context**: `android.content.Context`.
///
/// ### Return
/// Returns [`ImportResult::FileExists`] if the file couln't be imported because a file with that name already exists in the internal directory.
#[jni_fn("jni.DavSyncRs")]
pub fn import_file_internal<'local>(context: JObject<'local>, file_uri: JObject<'local>) -> ImportResult {
    let file_uri = DocUri::from_doc_uri(env, file_uri).unwrap();
    if import_file_internal(env, file_uri, context)
        .inspect_err(|err| panic!("{err}")).unwrap()
    {
        ImportResult::Success
    } else {
        ImportResult::FileExists
    }
}
fn import_file_internal<'local>(env: &mut JNIEnv<'local>, file_uri: DocUri<'local>, context: JObject<'local>) -> Result<bool, String> {
    let internal_dir = get_app_dir(env, &context).join(SUFFIX_DIR);

    // Ensure the destination directories are created (internal)
    std::fs::create_dir_all(&internal_dir)
        .map_err(|error| format!("Error creating directories leading up to {internal_dir:?}: {error}"))?;

    // The file that the user picked ot import
    let mut file = file_uri.open_file(env, &context, OpenOptions::ReadOnly)
        .map_err(|err| format!("Failed to open file to import: {err}"))?;

    // Open the file to copy to in the internal directory
    let mut internal_file = match File::create_new(internal_dir.join(file_uri.file_name(env))) {
        Ok(file) => file,
        Err(error) =>
            return if error.kind() == ErrorKind::AlreadyExists {
                Ok(false)
            } else {
                Err(format!("Error opening file in internal dir: {error}"))
            }
    };

    // Copy file's contents to the destination
    std::io::copy(&mut file, &mut internal_file)
        .map_err(|error| format!("Error copying to file in App dir: {error}"))?;

    Ok(true)
}

/// Copy a file named **file_name** from the *internal directory* to the **external directory** in Shared Storage.
#[jni_fn("jni.DavSyncRs")]
pub fn import_file_external<'local>(context: JObject<'local>, file_name: JString<'local>, external_dir_uri: JObject<'local>) {
    let external_dir_uri = DocUri::from_tree_uri(env, external_dir_uri).unwrap();
    let file_name = get_string(env, file_name);
    if let Err(err) = import_file_external(env, &file_name, external_dir_uri, &context) {
        // Failed to complete import because couldn't copy to external file.
        // Delete the imported file in the internal directory.
        let app_dir = get_app_dir(env, &context);
        if let Err(err) = std::fs::remove_file(app_dir.join(SUFFIX_DIR).join(file_name)) {
            panic!("Failed to delete internal imported file: {err}");
        };
        panic!("Failed to write to external file; deleted internal file.\nError: {err}");
    }
}
/// Write the contents of the file already imported in the *internal directory* to the new file created in *sync directory* (external).
fn import_file_external<'local>(env: &mut JNIEnv<'local>, file_name: &str, external_dir_uri: DocUri<'local>, context: &JObject<'local>) -> Result<(), String> {
    // Open the file to copy FROM in the internal directory
    let mut internal_file = std::fs::File::open(get_app_dir(env, context).join(SUFFIX_DIR).join(file_name))
        .map_err(|err| format!("Error opening file in internal dir: {err}"))?;
    // Open the file to copy TO in the sync directory (external)
    let mut external_file = ExternalDir::new(env, env.new_local_ref(context).unwrap(), external_dir_uri)
        .ok_or_else(|| "external_dir_uri does not point to a directory")?
        .create_file_at(env, PathBuf::from(SUFFIX_DIR).join(file_name))
        .map_err(|err| format!("Error creating external file: {err}"))?
        .open_file(env, context, OpenOptions::write())
        .map_err(|err| format!("Error opening newly created external file: {err}"))?;

    // Copy file's contents to the destination
    std::io::copy(&mut internal_file, &mut external_file)
        .map_err(|err| format!("Error copying to file in App dir: {err}"))?;

    Ok(())
}

#[jni_fn("jni.DavSyncRs")]
pub fn new_calendar_from_file<'local>(context: JObject, name: JString) -> jobject {
    let name = get_string(env, name);
    new_calendar_from_file(env, name, context)
        .inspect_err(|err| panic!("{err}")).unwrap()
        .as_ptr()
}
fn new_calendar_from_file(env: &mut JNIEnv, name: String, context: JObject) -> Result<NonNull<_jobject>, String> {
    // Check that a calendar with this name doesn't already exist
    let exists = call!(static me.marti.calprovexample.jni.DavSyncRsHelpers::checkUniqueName(
        android.content.Context(context),
        java.lang.String(env.new_string(&name).unwrap())
    ) -> Result<bool, String>)?;

    Err("TODO: return InternalUserCalendar".to_string())
}
