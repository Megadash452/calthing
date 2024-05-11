use std::{fs::File, ops::{Deref, DerefMut}, os::fd::FromRawFd as _};

use jni::{objects::JString, JNIEnv};

/// A file that is *not owned* by this object, meaning the file descriptor is *not consumed*.
/// 
/// Use this intead of [File] when getting a `ParcelFileDescriptor` from opening a file in Java.
pub struct FileRef(File);
impl FileRef {
    pub fn new(fd: i32) -> Self {
        Self(unsafe { File::from_raw_fd(fd) })
    }
}
impl Deref for FileRef {
    type Target = File;
    fn deref(&self) -> &Self::Target {
        &self.0
    }
}
impl DerefMut for FileRef {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}
impl Drop for FileRef {
    fn drop(&mut self) {
        std::mem::forget(std::mem::replace(&mut self.0, unsafe { File::from_raw_fd(0) }))
    }
}

#[allow(dead_code)]
/// Get a [`String`] from a native function argument.
/// 
/// Only use this if the argument declared on the Java side has the form of `String`.
pub fn get_string(env: &JNIEnv, arg: JString) -> String {
    String::from(unsafe { env.get_string_unchecked(&arg).expect("String argument can't be NULL") })
}
#[allow(dead_code)]
/// Get a nullable [`String`] from a native function argument.
/// 
/// Only use this if the arugment declared on the Java side has the form of `String` or `String?`.
pub fn get_nullable_string(env: &JNIEnv, arg: JString) -> Option<String> {
    unsafe { env.get_string_unchecked(&arg) }
        .ok()
        .map(|s| String::from(s))
}

