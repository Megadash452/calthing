use std::{mem::transmute, path::PathBuf};
use jni::objects::{JObject, JString};
use jni_macros::{jni_fn, package};
use crate::{file_stem, get_app_dir, get_string, DocUri, ExternalDir, ILLEGAL_FILE_CHARACTERS, SUFFIX_DIR};

package!("me.marti.calprovexample");

#[jni_fn("jni.DavSyncRs")]
pub fn create_calendar_files<'local>(
    context: JObject<'local>,
    file_name: JString<'local>,
    color: JObject<'local>,
    external_dir_uri: JObject<'local>,
) {
    let file_name = get_string(env, file_name);
    let external_dir_uri = if external_dir_uri.is_null() {
        None
    } else {
        Some(DocUri::from_tree_uri(env, external_dir_uri).unwrap())
    };
    let rgb_color = unsafe { (
        transmute(
            env.get_field(&color, "r", "B")
                .unwrap_or_else(|err| panic!("Error getting Color.r: {err}"))
                .b().unwrap_or_else(|err| panic!("Expected Color.r to be a byte: {err}"))
        ),
        transmute(
            env.get_field(&color, "g", "B")
                .unwrap_or_else(|err| panic!("Error getting Color.g: {err}"))
                .b().unwrap_or_else(|err| panic!("Expected Color.g to be a byte: {err}"))
        ),
        transmute(
            env.get_field(&color, "b", "B")
                .unwrap_or_else(|err| panic!("Error getting Color.b: {err}"))
                .b().unwrap_or_else(|err| panic!("Expected Color.b to be a byte: {err}"))
        ),
    ) };
    
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

    write_color_to_calendar_file(file_stem(&file_name), rgb_color);
}

pub fn write_file_data_to_calendar(name: &str, rgb_color: (u8, u8, u8)) {
    // TODO:
}

#[jni_fn("jni.DavSyncRs")]
pub fn write_calendar_data_to_file<'local>(name: JString) {
    // TODO:
}

#[jni_fn("jni.DavSyncRs")]
pub fn write_color_to_calendar_file<'local>(name: JString, rgb_color: JObject) {
    // TODO:
}
fn write_color_to_calendar_file(name: &str, rgb_color: (u8, u8, u8)) {
    // TODO:
}
