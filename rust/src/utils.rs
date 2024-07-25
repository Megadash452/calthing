use std::panic::UnwindSafe;

use jni::{JNIEnv, objects::{JObject, JValue, JString}};

pub static mut ENV: Option<*mut JNIEnv<'static>> = None;

#[macro_export]
macro_rules! println {
    // ($env:expr, $($arg:tt)*) => {
    //     $crate::utils::_println($env, format!($($arg)*))
    // };
    ($($arg:tt)*) => {
        $crate::utils::_println_unsafe(format!($($arg)*))
    };
}
#[macro_export]
macro_rules! eprintln {
    ($env:expr, $($arg:tt)*) => {
        $crate::utils::_eprintln($env, format!($($arg)*))
    };
}

pub fn _println_unsafe(s: String) {
    if let Some(env) = unsafe { ENV.map(|e| &mut *e) } {
        _println(env, s)
    }
}

#[allow(dead_code)]
#[doc(hidden)]
/// Print a **message** to the Android logger.
/// 
/// Android ignores the `STDOUT` and `STDERR` files and uses some other Log system that is unknown to me atm,
/// so to get any message error or debug message at runtime the native code would need to call the Log methods.
pub fn _println(env: &mut JNIEnv, s: String) {
    let class = env.find_class("android/util/Log")
        .expect("Can't find class Log");
    env.call_static_method(class, "i", "(Ljava/lang/String;Ljava/lang/String;)I", &[
        JValue::Object(&JObject::from(env.new_string("Rust").unwrap())),
        JValue::Object(&JObject::from(env.new_string(s).unwrap()))
    ]).expect("Could not call static function Log.i()");
}
#[allow(dead_code)]
#[doc(hidden)]
/// Print an **error** to the Android logger.
/// 
/// Android ignores the `STDOUT` and `STDERR` files and uses some other Log system that is unknown to me atm,
/// so to get any message error or debug message at runtime the native code would need to call the Log methods.
pub fn _eprintln(env: &mut JNIEnv, s: String) {
    let class = env.find_class("android/util/Log")
        .expect("Can't find class Log");
    env.call_static_method(class, "i", "(Ljava/lang/String;Ljava/lang/String;)I", &[
        JValue::Object(&JObject::from(env.new_string("Rust").unwrap())),
        JValue::Object(&JObject::from(env.new_string(s).unwrap()))
    ]).expect("Could not call static function Log.i()");
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

/// Runs a Rust function, and returns its value if successful.
/// If the function panics, the panic is caught and a Java Exception will be thrown and [None] will be returned.
pub fn catch_throw<R>(env: &mut JNIEnv, f: impl FnOnce() -> R + UnwindSafe) -> Option<R> {
    match std::panic::catch_unwind(f) {
        Ok(r) => Some(r),
        Err(_) => {
            env.throw_new("me/marti/calprovexample/RustPanic", "Rust had a panic!").unwrap();
            None
        }
    }
}
