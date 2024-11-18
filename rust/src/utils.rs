use jni::{JNIEnv, objects::JObject};
use ez_jni::call;
use std::path::PathBuf;

/// Returns the directory owned by this App (where it's files are stored) in the Android System.
pub fn get_app_dir(env: &mut JNIEnv, context: &JObject) -> PathBuf {
    let app_dir = call!(context.getFilesDir() -> java.io.File);
    PathBuf::from(call!(app_dir.getPath() -> String))
}
