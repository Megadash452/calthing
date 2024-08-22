use jni::{JNIEnv, objects::{JObject, JObjectArray, JString}};
use jni_macros::call;

// pub static mut ENV: Option<*mut JNIEnv<'static>> = None;

#[macro_export]
macro_rules! println {
    ($env:expr, $($arg:tt)*) => {
        $crate::utils::__println($env, format!($($arg)*))
    };
    // ($($arg:tt)*) => {
    //     $crate::utils::__println_unsafe(format!($($arg)*))
    // };
}
#[macro_export]
macro_rules! eprintln {
    ($env:expr, $($arg:tt)*) => {
        $crate::utils::__eprintln($env, format!($($arg)*))
    };
}

// #[allow(dead_code)]
// #[doc(hidden)]
// pub fn __println_unsafe(s: String) {
//     if let Some(env) = unsafe { ENV.map(|e| &mut *e) } {
//         __println(env, s)
//     }
// }

/// Print a **message** to the Android logger.
/// 
/// Android ignores the `STDOUT` and `STDERR` files and uses some other Log system that is unknown to me atm,
/// so to get any message error or debug message at runtime the native code would need to call the Log methods.
#[allow(dead_code)]
#[doc(hidden)]
pub fn __println(env: &mut JNIEnv, s: String) {
    call!(static android.util.Log::i(
        java.lang.String(JObject::from(env.new_string("Rust").unwrap())),
        java.lang.String(JObject::from(env.new_string(s).unwrap()))
    ) -> int);
}
/// Print an **error** to the Android logger.
/// 
/// Android ignores the `STDOUT` and `STDERR` files and uses some other Log system that is unknown to me atm,
/// so to get any message error or debug message at runtime the native code would need to call the Log methods.
#[allow(dead_code)]
#[doc(hidden)]
pub fn __eprintln(env: &mut JNIEnv, s: String) {
    call!(static android.util.Log::e(
        java.lang.String(env.new_string("Rust").unwrap()),
        java.lang.String(env.new_string(s).unwrap()),
    ) -> int);
}

/// Get a [`String`] from a native function argument.
/// 
/// Only use this if the argument declared on the Java side has the form of `String`.
#[allow(dead_code)]
pub fn get_string(env: &JNIEnv, arg: JString) -> String {
    String::from(unsafe { env.get_string_unchecked(&arg).expect("String argument can't be NULL") })
}
/// Get a nullable [`String`] from a native function argument.
/// 
/// Only use this if the arugment declared on the Java side has the form of `String` or `String?`.
#[allow(dead_code)]
pub fn get_nullable_string(env: &JNIEnv, arg: JString) -> Option<String> {
    unsafe { env.get_string_unchecked(&arg) }
        .ok()
        .map(|s| String::from(s))
}

fn string_array<'local>(env: &mut JNIEnv<'local>, src: &[&str]) -> JObjectArray<'local> {
    let array = env.new_object_array(
        src.len().try_into().unwrap(),
        "java/lang/String",
        unsafe { JObject::from_raw(std::ptr::null_mut()) } 
    ).expect("Failed to create String array");

    for (i, &element) in src.iter().enumerate() {
        env.set_object_array_element(&array, i.try_into().unwrap(), env.new_string(element).unwrap()).unwrap();
    }

    array
}

// Wrapper class for `android.database.Cursor`
pub struct Cursor<'local>(JObject<'local>);
impl <'local> Cursor<'local> {
    pub fn new(cursor: JObject<'local>) -> Self { Self(cursor) }

    /// Query the Android Content Provider at some *URI*, and get a cursor as a result.
    pub fn query(env: &mut JNIEnv<'local>, context: &JObject, uri: &str, projection: &[&str], selection: &str, selection_args: &[&str], sorting: &str) -> Result<Self, String> {
        let content_resolver = call!(context.getContentResolver() -> android.content.ContentResolver);
        let uri = call!(static android.net.Uri::parse(java.lang.String(&JObject::from(env.new_string(uri).unwrap()))) -> android.net.Uri);
        let result = call!(content_resolver.query(
            android.net.Uri(uri),
            [java.lang.String](string_array(env, projection)),
            java.lang.String(env.new_string(selection).unwrap()),
            [java.lang.String](string_array(env, selection_args)),
            java.lang.String(env.new_string(sorting).unwrap()),
        ) -> Result<android.database.Cursor, String>)?;

        Ok(Self(result))
    }

    pub fn row_count(&self, env: &mut JNIEnv) -> usize { call!((self.0).getCount() -> int) as usize }

    pub fn next(&self, env: &mut JNIEnv) -> bool { call!((self.0).moveToNext() -> bool) }
    pub fn get_string(&self, env: &mut JNIEnv<'local>, index: u32) -> JString<'local> {
        call!((self.0).getString(int(index as i32)) -> java.lang.String).into()
    }
    pub fn get_int(&self, env: &mut JNIEnv, index: u32) -> i32 {
        call!((self.0).getInt(int(index as i32)) -> int)
    }

    pub fn close(self, env: &mut JNIEnv) { call!((self.0).close() -> void) }
}

pub struct DocUri<'local>(pub JObject<'local>);
impl <'local> DocUri<'local> {
    
}
