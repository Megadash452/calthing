use jni::{JNIEnv, objects::{JObject, JValue}};

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