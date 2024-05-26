use jni::{JNIEnv, objects::{JObject, JValue, JString}};

#[allow(dead_code)]
/// Print a **message** to the Android logger.
/// 
/// Android ignores the `STDOUT` and `STDERR` files and uses some other Log system that is unknown to me atm,
/// so to get any message error or debug message at runtime the native code would need to call the Log methods.
pub fn println(env: &mut JNIEnv, s: &str) {
    let class = env.find_class("android/util/Log")
        .expect("Can't find class Log");
    env.call_static_method(class, "i", "(Ljava/lang/String;Ljava/lang/String;)I", &[
        JValue::Object(&JObject::from(env.new_string("Rust").unwrap())),
        JValue::Object(&JObject::from(env.new_string(s).unwrap()))
    ]).expect("Could not call static function Log.i()");
}
#[allow(dead_code)]
/// Print an **error** to the Android logger.
/// 
/// Android ignores the `STDOUT` and `STDERR` files and uses some other Log system that is unknown to me atm,
/// so to get any message error or debug message at runtime the native code would need to call the Log methods.
pub fn eprintln(env: &mut JNIEnv, s: &str) {
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
