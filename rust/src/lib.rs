#![cfg(target_os="android")]
#![allow(non_snake_case)]

mod log;
mod types;
use log::*;
use types::*;

use std::{fs::File, io::ErrorKind, path::PathBuf};
use jni::{
    objects::{JClass, JString}, sys::jobject, JNIEnv
};

const NULL: jobject = std::ptr::null_mut();

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

#[repr(i8)]
pub enum ImportResult {
    Error, Success, FileExists
}
// Getting s a file descriptor seems to be the only way to open a file not owned by the app, even with permission.
#[no_mangle]
pub extern "system" fn Java_me_marti_calprovexample_DavSyncRs_import_1file<'local>(mut env: JNIEnv<'local>, _class: JClass<'local>, fd: i32, name: JString<'local>, appDir: JString<'local>) -> ImportResult {
    // The App's internal directory where the process has permissions to read/write files.
    let appDir = PathBuf::from(get_string(&env, appDir));
    let name = get_string(&env, name);
    // The file that the user picked ot import
    let mut file = FileRef::new(fd);

    // let cal = match davsync::parse_file(BufReader::new(&*file)) {
    //     Ok(cal) => cal,
    //     Err(error) => {
    //         env.throw(format!("Error parsing .ical file:\n{error}")).unwrap();
    //         return NULL
    //     }
    // };

    let mut internal_file = match File::create_new(appDir.join(name)) {
        Ok(file) => file,
        Err(error) =>
            return if error.kind() == ErrorKind::AlreadyExists {
                ImportResult::FileExists
            } else {
                env.throw(format!("Error opening file in App dir: {error}")).unwrap();
                ImportResult::Error
            }
    };
    if let Err(error) = std::io::copy(&mut *file, &mut internal_file) {
        env.throw(format!("Error copying file in App dir: {error}")).unwrap();
        return ImportResult::Error
    }

    ImportResult::Success
}
