use std::{mem::transmute, path::PathBuf};
use ez_jni::{call, jni_fn, package, utils::get_string, FromException};
use jni::{objects::{JObject, JString}, JNIEnv};
use crate::{file_stem, get_app_dir, DocUri, ExternalDir, ILLEGAL_FILE_CHARACTERS, SUFFIX_DIR};

package!("me.marti.calprovexample");

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
struct Color {
    pub r: u8,
    pub g: u8,
    pub b: u8
}
impl Color {
    pub fn from_object(env: &mut JNIEnv, color: JObject) -> Self {
        unsafe { Self {
            r: transmute(
                env.get_field(&color, "r", "B")
                    .unwrap_or_else(|err| panic!("Error getting Color.r: {err}"))
                    .b().unwrap_or_else(|err| panic!("Expected Color.r to be a byte: {err}"))
            ),
            g: transmute(
                env.get_field(&color, "g", "B")
                    .unwrap_or_else(|err| panic!("Error getting Color.g: {err}"))
                    .b().unwrap_or_else(|err| panic!("Expected Color.g to be a byte: {err}"))
            ),
            b: transmute(
                env.get_field(&color, "b", "B")
                    .unwrap_or_else(|err| panic!("Error getting Color.b: {err}"))
                    .b().unwrap_or_else(|err| panic!("Expected Color.b to be a byte: {err}"))
            ),
        } }
    }
}

/// Create the files in internal and external storage for a new Calendar the user created.
/// 
/// If **external_dir_uri** is **`NULL`**, only the file in app storage will be created.
/// **file_name** is the name of the file that will be created in each directory (e.g. `"name.ics"`). */
#[jni_fn("jni.DavSyncRs")]
pub fn create_calendar_files<'local>(
    context: JObject<'local>,
    file_name: JString<'local>,
    color: JObject<'local>,
    external_dir_uri: JObject<'local>,
) {
    let file_name = get_string(file_name, env);
    let external_dir_uri = if external_dir_uri.is_null() {
        None
    } else {
        Some(DocUri::from_tree_uri(env, external_dir_uri).unwrap())
    };
    
    // Check for illegal characters
    if file_name.contains(ILLEGAL_FILE_CHARACTERS) {
        panic!("File name can't contain the following characters: {ILLEGAL_FILE_CHARACTERS:?}")
    }

    // Create file in App's internal storage
    std::fs::File::create_new(get_app_dir(env, &context).join(SUFFIX_DIR).join(&file_name))
        .unwrap_or_else(|err| panic!("Error creating file in internal directory: {err}"));
    // Create file in external directory in shared storage
    if let Some(external_dir_uri) = external_dir_uri {
        ExternalDir::new(env, context, external_dir_uri)
            .unwrap_or_else(|| panic!("external_dir_uri does not point to a directory"))
            .create_file_at(env, PathBuf::from(SUFFIX_DIR).join(&file_name))
            .unwrap_or_else(|err| panic!("Error creating external file: {err}"));
    }

    write_color_to_calendar_file(file_stem(&file_name), Color::from_object(env, color));
}

/// Read a *Calendar file* and write the data to the Calendar *Content Provider*.
/// 
/// Creates a new Calendar in the Content Provider if one with **name** does not exist.
#[jni_fn("jni.DavSyncRs")]
pub fn write_file_data_to_calendar<'local>(perm: JObject, name: JString, color: JObject) {
    #[derive(FromException)]
    #[class(me.marti.calprovexample.ElementExistsException)]
    struct ElementExists;
    
    // let context = call!(perm.getContext() -> android.content.Context);

    // Create the calendar if it does not exist. Ignore the result
    let _ = call!(static me.marti.calprovexample.calendar.ActionsKt::newCalendar(
        me.marti.calprovexample.ui.CalendarPermissionScope(perm),
        java.lang.String(name),
        me.marti.calprovexample.Color(color),
        kotlin.coroutines.Continuation(JObject::null())
    ) -> Result<Option<java.lang.Object>, ElementExists>)
        .inspect(|r| if r.is_none() { panic!("Failed creating calendar") });
    
    // TODO: parse file contents and add them to the Content Provider
    // TODO: add to list without adding to provider
}

#[jni_fn("jni.DavSyncRs")]
pub fn write_calendar_data_to_file<'local>(name: JString) {
    // TODO:
}

#[jni_fn("jni.DavSyncRs")]
pub fn write_color_to_calendar_file<'local>(name: JString, color: JObject) {
    // TODO:
}
fn write_color_to_calendar_file(name: &str, color: Color) {
    // TODO:
}
