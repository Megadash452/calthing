mod calendar;
mod utils;

use jni::{JNIEnv, objects::JObject};
use ez_jni::{call, jni_fn, new, println, FromObject};
use std::{io::{self, Read as _, Write as _}, path::PathBuf};
use utils::get_app_dir;
use classes::fs::{file_stem, DocUri, ExternalDir, OpenOptions};

/// These are the names of the directories where synced data will be stored
const DIRECTORIES: [&str; 2] = ["calendars", "contacts"];

/// The name that is appended to the directories' path to get the destination directory. E.g.: `"<app_dir>/calendars"`.
// TODO: will change so that it is automatically detected whether to use "calendar" or "contacts" depending on the file type.
const SUFFIX_DIR: &str = "calendars";
const ILLEGAL_FILE_CHARACTERS: [char; 3] = ['/', '*', ':'];

jni_fn! { me.marti.calprovexample.jni.DavSyncRs =>
    /// Initialize the **internal** and **external** directories by creating all necessary sub-directories (e.g. calendars and contacts directories).
    ///
    /// ### Parameters
    /// - **external_dir_fd** is the *file descriptor* for the directory in shared storage the user picked to sync files.
    /// - **app_dir** is the internal directory where all of this app's files are stored.
    pub fn initialize_dirs<'local>(context: android.content.Context, external_dir_uri: Option<android.net.Uri>) {
        // -- Initialize internal directory (app_dir)
        let app_dir = get_app_dir(env, &context);

        let entries = std::fs::read_dir(&app_dir)
            .unwrap_or_else(|err| panic!("Error reading internal directory: {err}"))
            .filter_map(Result::ok)
            .filter_map(|entry| entry.file_name().to_str().map(str::to_string)) // Ignore entries that re not UTF-8
            .collect::<Box<[_]>>();
        // Find the DIRECTORIES that are missing from entries and create them
        for &dir in DIRECTORIES
            .iter()
            .filter(|&&dir| !entries.iter().any(|e| e == dir))
        {
            std::fs::create_dir(app_dir.join(dir))
                .unwrap_or_else(|error| panic!("Error creating directory: {error}"))
        }

        // -- Initialize external directory (shared storage)
        if let Some(external_dir_uri) = external_dir_uri {
            let external_dir_uri = DocUri::from_tree_uri(env, external_dir_uri).unwrap();
            let external_dir = ExternalDir::new(context, external_dir_uri, env)
                .unwrap_or_else(|| panic!("Couldn't open external directory"));
    
            // Don't have to find missing directories; will not return error if directories already exist.
            for &dir in &DIRECTORIES {
                external_dir
                    .create_dir(env, dir)
                    .unwrap_or_else(|error| panic!("Error creating directory: {error}"));
            }
        }
    }

    /// Copy files between the **internal** and **external** directories and resolve conflicts for files that exist in both.
    /// 
    /// This is used when the user selects the **external** directory;
    /// The contents of both directories have to be merged.
    /// 
    /// After they are merged, the changes are written to the *Calendar Content Provider*.
    pub fn merge_dirs<'local>(activity: me.marti.calprovexample.MainActivity, external_dir_uri: android.net.Uri) {
        let context = call!(activity.getBaseContext() -> android.content.Context);
        let app_dir = get_app_dir(env, &context);
        let external_dir_uri = DocUri::from_tree_uri(env, external_dir_uri).unwrap();
        println!("ExternalDir Uri: \"{}\"", external_dir_uri.to_string(env));
        let internal_dir = app_dir.join("calendars");
        let external_dir = {
            ExternalDir::new(env.new_local_ref(&context).unwrap(), external_dir_uri.join(env, "calendars"), env)
                .unwrap_or_else(|| panic!("Couldn't open external directory"))
        };
        let calendars_list = call!(activity.getUserCalendars() -> Option<me.marti.calprovexample.ui.MutableCalendarsList>);
    
        let internal_files = Result::<Vec<_>, _>::from_iter(
            internal_dir
                .read_dir()
                .unwrap_or_else(|err| panic!("Failed reading directory: {err}"))
        )
            .unwrap_or_else(|err| panic!("Failed getting directory entry: {err}"))
            .into_iter()
            .filter(|entry| entry.metadata().ok().is_some_and(|meta| meta.is_file()))
            .collect::<Box<[_]>>();
        let external_files = IntoIterator::into_iter(external_dir.entries(env))
            .filter(|entry| !entry.is_dir())
            .collect::<Box<[_]>>();
    
        // Find the files that are in one directory but not in the other, and copy them to the other.
        // Filter by files that are NOT in the internal directory
        let copy_to_internal = external_files.iter().filter(|external_entry| {
            !internal_files
                .iter()
                .any(|internal_entry| internal_entry.file_name() == external_entry.file_name())
        });
        // Filter by files that are NOT in the external directory
        let copy_to_external = internal_files.iter().filter(|internal_entry| {
            !external_files
                .iter()
                .any(|external_entry| internal_entry.file_name() == external_entry.file_name())
        });
        // Filter by files that IN BOTH directories
        let files_to_merge = external_files.iter().filter(|external_entry| {
            internal_files
                .iter()
                .any(|internal_entry| internal_entry.file_name() == external_entry.file_name())
        });
    
        // -- Copy the files
    
        // Copy external files to internal directory
        for entry in copy_to_internal.clone() {
            let mut external_file = entry
                .open_file(env, &context, OpenOptions::ReadOnly)
                .unwrap_or_else(|err| panic!("Failed to open file in external directory: {err}"));
    
            // Open the file to copy to in the internal directory
            let mut internal_file = match std::fs::File::create_new(internal_dir.join(entry.file_name())) {
                Ok(file) => file,
                Err(error) =>
                    if error.kind() == io::ErrorKind::AlreadyExists {
                        panic!("Unreachable: Already checked that file does not exist in internal directory")
                    } else {
                        panic!("Error opening file in internal dir: {error}")
                    },
            };
    
            // Copy file's contents to the destination
            std::io::copy(&mut external_file, &mut internal_file)
                .unwrap_or_else(|error| panic!("Error copying to file in internal directory: {error}"));
        }
    
        // Copy internal files to external directory
        for entry in copy_to_external {
            let mut internal_file = std::fs::File::open(entry.path())
                .unwrap_or_else(|err| panic!("Failed to open file in internal directory: {err}"));
    
            // Open the file to copy to in the external directory
            let mut external_file = match external_dir.create_file(
                env,
                entry.file_name().to_str().expect("File name must be UTF-8"),
            ) {
                Ok(file) => file
                    .open_file(env, &context, OpenOptions::write())
                    .unwrap_or_else(|err| panic!("Failed to open newly created file in external directory: {err}")),
                Err(error) =>
                    if error.kind() == io::ErrorKind::AlreadyExists {
                        panic!("Unreachable: Already checked that file does not exist in external directory")
                    } else {
                        panic!("Error opening file in external dir: {error}")
                    },
            };
    
            // Copy file's contents to the destination
            std::io::copy(&mut internal_file, &mut external_file)
                .unwrap_or_else(|error| panic!("Error copying to file in external directory: {error}"));
        }
    
        
        // Check if common files (files in both directories) are different, and ask user whether to accept incoming or keep internal
        for external_file in files_to_merge {
            let file_name = external_file.file_name();
            println!("Merging file \"{}\".", file_name);
            let internal_file = internal_dir.join(&file_name);

            // Read file contents
            let mut internal_file_content = Vec::new();
            let mut external_file_content = Vec::new();
            std::fs::File::open(&internal_file)
                .unwrap_or_else(|err| panic!("Failed to open file in internal directory: {err}"))
                .read_to_end(&mut internal_file_content)
                .unwrap_or_else(|err| panic!("Failed read contents of file in internal directory: {err}"));
            external_file.open_file(env, &context, OpenOptions::ReadOnly)
                .unwrap_or_else(|err| panic!("Failed to open file in external directory: {err}"))
                .read_to_end(&mut external_file_content)
                .unwrap_or_else(|err| panic!("Failed read contents of file in external directory: {err}"));

            if internal_file_content != external_file_content {
                use FileConflictResponse::*;
                #[derive(Debug, FromObject)]
                enum FileConflictResponse {
                    #[class(me.marti.calprovexample.jni.FileConflictResponse$Canceled)]
                    Canceled,
                    #[class(me.marti.calprovexample.jni.FileConflictResponse$OverWrite)]
                    OverWrite,
                    #[class(me.marti.calprovexample.jni.FileConflictResponse$Rename)]
                    Rename(#[field(name = newName)] String)
                }
                let user_choice = FileConflictResponse::from_object(
                    &call!(static me.marti.calprovexample.jni.DavSyncRsKt.showFileConflictDialog(String(external_file.file_stem())) -> me.marti.calprovexample.jni.FileConflictResponse),
                env)
                    .unwrap_or_else(|err| panic!("{err}"));
                println!("User selected {user_choice:?}");

                // Show dialog to user and wait for a response
                match user_choice {
                    // User chose to keep internal file; Rename external file and create new calendar with it
                    Rename(new_name) => {
                        todo!();
                        external_file.uri(); //.rename(&context, &new_name, env);
                            // .unwrap_or_else(|err| panic!("Failed to rename External file \"{}\": {err}", external_file.file_name()));
                    },
                    // User chose to overwrite internal file with external file.
                    OverWrite => {
                        if let Some(calendars_list) = &calendars_list {
                            call!(calendars_list.remove(String(external_file.file_stem())) -> me.marti.calprovexample.calendar.InternalUserCalendar);
                            call!(calendars_list.addFile(android.net.Uri(external_dir_uri.as_ref())) -> void);
                        } else {
                            println!("Overwrite Internal file (no userCalendars)");
                            // The path that the Internal file will go to when it is deleted
                            let deleted_path = app_dir.join("deleted").join(SUFFIX_DIR).join(&file_name);
                            // Move Internal file to "Recycle Bin"
                            std::fs::copy(&internal_file, &deleted_path)
                                .unwrap_or_else(|err| panic!("Failed to copy Internal file to deleted directory: {err}"));
                            // Write External file to Internal file
                            std::fs::OpenOptions::new()
                                .write(true)
                                .truncate(true)
                                .open(&internal_file)
                                .unwrap_or_else(|err| panic!("Failed to open Internal file \"{}\" with write+trunc: {err}", internal_file.display()))
                                .write_all(&external_file_content)
                                .unwrap_or_else(|err| panic!("Failed to write contents of External file to Internal file \"{}\": {err}", internal_file.display()));
                            // Create a SnackBar to undo this action
                            call!(activity.showOverwriteSnackBar(String(deleted_path.to_str().unwrap()), String(internal_file.to_str().unwrap())) -> void);
                        }
                    },
                    Canceled => { },
                }
            }
        }
    
        // Add calendars from external directory to Content Provider
        // Calendars in internal dir are should already be in the Content Provider, so no need to do this for copyToExternal too.
        let perm_manager = env.get_field(&activity,
                "calendarPermission", "Lme/marti/calprovexample/ui/CalendarPermission;",
            ).unwrap().l().unwrap();
        if let Some(perm) = call!(perm_manager.usePermission() -> Option<me.marti.calprovexample.ui.CalendarPermissionScope>) {
            for entry in copy_to_internal {
                let name = entry.file_stem();
                let davsyncrs = env.get_static_field("me/marti/calprovexample/jni/DavSyncRs", "INSTANCE", "Lme/marti/calprovexample/jni/DavSyncRs;")
                    .unwrap().l().unwrap();
                call!(davsyncrs.write_file_data_to_calendar(
                    me.marti.calprovexample.ui.CalendarPermission(perm),
                    String(name),
                    me.marti.calprovexample.Color(null)
                ) -> void);
            }
    
            if let Some(calendars_list) = calendars_list {
                call!(calendars_list.syncWithProvider() -> void)
            }
        };
    }

    /// Copy an *`.ics`* file's content into the internal *app's directory*.
    ///
    /// A *successful* call to this function should be subsequently followed by a call to [`import_file_external()`]
    ///
    /// ### Parameters
    /// **file_uri** is the *Document Uri* of the file to be imported.
    /// **file_name**: If not `NULL`, the file will be imported with this name instead of the *fileName* of **fileUri**.
    /// **context**: `android.content.Context`.
    ///
    /// ### Return
    /// Returns [`ImportResult::FileExists`] if the file couln't be imported because a file with that name already exists in the internal directory.
    pub fn import_file_internal<'local>(context: android.content.Context, file_uri: android.net.Uri, file_name: Option<String>) -> me.marti.calprovexample.jni.ImportFileResult {
        let file_uri = DocUri::from_doc_uri(env, file_uri).unwrap();
        let file_name = file_name.unwrap_or_else(|| file_uri.file_name(env));
        let cal_name = file_stem(&file_name);

        if import_file_internal(env, file_uri, &file_name, context)
            .unwrap_or_else(|err| panic!("{err}"))
        {
            println!("file '{file_name}' imported successfully");
            new!(me.marti.calprovexample.jni.ImportFileResult$Success(String(cal_name)))
        } else {
            println!("'{file_name}' is already imported. Overwrite?");
            new!(me.marti.calprovexample.jni.ImportFileResult$FileExists(String(cal_name)))
        }
    }

    /// Copy a file named **file_name** from the *internal directory* to the **external directory** in Shared Storage.
    pub fn import_file_external<'local>(
        context: android.content.Context,
        file_name: String,
        external_dir_uri: android.net.Uri,
    ) {
        let external_dir_uri = DocUri::from_tree_uri(env, external_dir_uri).unwrap();
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

    // pub fn new_calendar_from_file<'local>(context: JObject, name: JString) -> jobject {
    //     let name = get_string(env, name);
    //     new_calendar_from_file(env, name, context)
    //         .unwrap_or_else(|err| panic!("{err}"))
    //         .as_ptr()
    // }
    // fn new_calendar_from_file(
    //     env: &mut JNIEnv,
    //     name: String,
    //     context: JObject,
    // ) -> Result<NonNull<_jobject>, String> {
    //     // Check that a calendar with this name doesn't already exist
    //     let exists = call!(static me.marti.calprovexample.jni.DavSyncRsKt.checkUniqueName(
    //         android.content.Context(context),
    //         java.lang.String(env.new_string(&name).unwrap())
    //     ) -> Result<bool, String>)?;

    //     Err("TODO: return InternalUserCalendar".to_string())
    // }
}

fn import_file_internal<'local>(
    env: &mut JNIEnv<'local>,
    file_uri: DocUri<'local>,
    file_name: &str,
    context: JObject<'local>,
) -> Result<bool, String> {
    let internal_dir = get_app_dir(env, &context).join(SUFFIX_DIR);
    let file_path = internal_dir.join(file_name);

    // Ensure the destination directory is created (internal)
    std::fs::create_dir_all(&internal_dir).map_err(|error| {
        format!("Error creating directories leading up to {internal_dir:?}: {error}")
    })?;

    // The file that the user picked ot import
    let mut file = file_uri
        .open_file(env, &context, OpenOptions::ReadOnly)
        .map_err(|err| format!("Failed to open file to import: {err}"))?;

    // Open the file to copy to in the internal directory
    let mut internal_file = match std::fs::File::create_new(file_path) {
        Ok(file) => file,
        Err(error) => {
            return if error.kind() == io::ErrorKind::AlreadyExists {
                Ok(false)
            } else {
                Err(format!("Error opening file in internal dir: {error}"))
            }
        }
    };

    // Copy file's contents to the destination
    std::io::copy(&mut file, &mut internal_file)
        .map_err(|error| format!("Error copying to file in App dir: {error}"))?;

    Ok(true)
}

/// Write the contents of the file already imported in the *internal directory* to the new file created in *sync directory* (external).
fn import_file_external<'local>(
    env: &mut JNIEnv<'local>,
    file_name: &str,
    external_dir_uri: DocUri<'local>,
    context: &JObject<'local>,
) -> Result<(), String> {
    // Open the file to copy FROM in the internal directory
    let mut internal_file =
        std::fs::File::open(get_app_dir(env, context).join(SUFFIX_DIR).join(file_name))
            .map_err(|err| format!("Error opening file in internal dir: {err}"))?;
    // Open the file to copy TO in the sync directory (external)
    let mut external_file =
        ExternalDir::new(env.new_local_ref(context).unwrap(), external_dir_uri, env)
            .ok_or("external_dir_uri does not point to a directory")?
            .create_file_at(env, PathBuf::from(SUFFIX_DIR).join(file_name))
            .map_err(|err| format!("Error creating external file: {err}"))?
            .open_file(env, context, OpenOptions::write())
            .map_err(|err| format!("Error opening newly created external file: {err}"))?;

    // Copy file's contents to the destination
    std::io::copy(&mut internal_file, &mut external_file)
        .map_err(|err| format!("Error copying to file in App dir: {err}"))?;

    Ok(())
}