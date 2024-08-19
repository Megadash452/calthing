use std::{any::Any, panic::UnwindSafe};

use jni::{objects::{JObject, JObjectArray, JString, JThrowable, JValue}, JNIEnv};
use jni_macros::call;

pub static mut ENV: Option<*mut JNIEnv<'static>> = None;

#[macro_export]
macro_rules! println {
    ($env:expr, $($arg:tt)*) => {
        $crate::utils::_println($env, format!($($arg)*))
    };
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
    call!(static android.util.Log::i(
        java.lang.String(JObject::from(env.new_string("Rust").unwrap())),
        java.lang.String(JObject::from(env.new_string(s).unwrap()))
    ) -> int);
}
#[allow(dead_code)]
#[doc(hidden)]
/// Print an **error** to the Android logger.
/// 
/// Android ignores the `STDOUT` and `STDERR` files and uses some other Log system that is unknown to me atm,
/// so to get any message error or debug message at runtime the native code would need to call the Log methods.
pub fn _eprintln(env: &mut JNIEnv, s: String) {
    call!(static android.util.Log::e(
        java.lang.String(JObject::from(env.new_string("Rust").unwrap())),
        java.lang.String(JObject::from(env.new_string(s).unwrap()))
    ) -> int);
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
        Err(payload) => {
            __throw_panic(env, payload);
            None
        }
    }
}
// Macro is necessary to prevent mutable borrow of env (in throw) while also borriwing it in the closure.
/// Runs a Rust function, and returns its value if successful.
/// If the function panics, the panic is caught and a Java Exception will be thrown and [None] will be returned.
#[macro_export]
macro_rules! catch_throw {
    ($env:expr, $f:expr) => {
        match std::panic::catch_unwind(::std::panic::AssertUnwindSafe($f)) {
            Ok(r) => Some(r),
            Err(payload) => {
                $crate::utils::__throw_panic($env, payload);
                None
            }
        }
    };
}
pub fn __throw_panic(env: &mut JNIEnv, payload: Box<dyn Any + Send>) {
    let panic_msg = payload.downcast::<&'static str>()
        .map(|msg| msg.to_string())
        .or_else(|payload| payload.downcast::<String>().map(|msg| *msg))
        .ok();
    let exception_obj = env.new_object("me/marti/calprovexample/jni/RustPanic", format!("(Ljava/lang/String;)V"), &[
        JValue::Object(&match panic_msg {
            Some(msg) => JObject::from(env.new_string(&msg).unwrap()),
            None => JObject::null()
        })
    ]);
    match exception_obj {
        Ok(obj) => env.throw(JThrowable::from(obj)).unwrap(),
        Err(err) => env.throw(format!("Failed to construct RustPanic object: {err}")).unwrap()
    }
}

/// Chekcs if an exception has been thrown from a previous JNI function call,
/// and converts that exception to a `Err(String)` so that it can be returned using `?`.
/// 
/// ***Panics*** any JNI calls fails.
/// 
/// NOTICE: Can't call any function (including print) between the time when the exception is thrown and when `JNIEnv::exception_clear()` is called.
/// This means that if a method call could throw, the checks (call, type, and null) should be done AFTER the exception is caught.
// TODO: let the erorr be any type that implements `FromJThrowable`
pub fn catch_exception(env: &mut JNIEnv) -> Result<(), String> {
    match env.exception_occurred() {
        Ok(ex) => if !ex.is_null() {
            env.exception_clear().unwrap();
            let msg = env.call_method(ex, "getMessage", "()Ljava/lang/String;", &[])
                .inspect_err(|err| panic!("Failed to call getMessage() on Throwable: {err}")).unwrap()
                .l().inspect_err(|err| panic!("Value returned by getMessage() is not a String: {err}")).unwrap();
            if msg.is_null() {
                panic!("Throwable message is NULL");
            }
            return Err(get_string(env, JString::from(msg)))
        } else {
            Ok(())
        },
        Err(err) => panic!("Failed to check if exception was thrown: {err}")
    }
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
    pub fn query(env: &mut JNIEnv<'local>, context: &JObject, uri: &str, projection: &[&str], selection: &str, selection_args: &[&str], sorting: &str) -> Self {
        let content_resolver = call!(context.getContentResolver() -> android.content.ContentResolver);
        let uri = call!(static android.net.Uri::parse(java.lang.String(&JObject::from(env.new_string(uri).unwrap()))) -> android.net.Uri);
        let projection = JObject::from(string_array(env, projection));
        let selection = JObject::from(env.new_string(selection).unwrap());
        let selection_args = JObject::from(string_array(env, selection_args));
        let sorting = JObject::from(env.new_string(sorting).unwrap());
        let result = call!(content_resolver.query(
            android.net.Uri(uri),
            [java.lang.String](projection),
            java.lang.String(selection),
            [java.lang.String](selection_args),
            java.lang.String(sorting),
        ) -> android.database.Cursor);

        Self(result)
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
