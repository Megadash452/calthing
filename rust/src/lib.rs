#![cfg(target_os="android")]
#![allow(non_snake_case)]

use std::ffi::CStr;
use std::path::Path;
use std::ffi::OsStr;
use std::os::unix::ffi::OsStrExt;

use jni::{
    objects::{JClass, JObject, JString, JValue}, sys::jstring, JNIEnv
};

#[no_mangle]
pub extern "system" fn Java_me_marti_calprovexample_DavSyncRs_write_data_to_file<'local>(env: JNIEnv<'local>, _class: JClass<'local>, data: JObject<'local>, path: JString<'local>) -> jstring {
    let path: &Path = unsafe {
        OsStr::from_bytes(
            CStr::from_ptr(env.get_string_unchecked(&path).expect("String 'path` can't be NULL.").get_raw())
                .to_bytes()
        ).as_ref()
    };
    todo!()
}

// #[no_mangle]
// pub extern "system" fn Java_me_marti_calprovexample_DavSyncRs_add(mut env: JNIEnv, _class: JClass, x: i32, y: i32) -> i32 {
//     let l = AndroidLogger::new(&mut env).unwrap();
//     let r = davsync::add(x, y);

//     l.debug(&mut env, format!("Add function ran from Rust. Hi {r}"));

//     r
// }

// #[no_mangle]
// pub extern "system" fn Java_me_marti_calprovexample_DavSyncRs_readFile<'local>(env: JNIEnv<'local>, _class: JClass<'local>, path: JString<'local>) -> jstring {
//     let path: &Path = unsafe {
//         OsStr::from_bytes(
//             CStr::from_ptr(env.get_string_unchecked(&path).expect("String 'path` can't be NULL.").get_raw())
//                 .to_bytes()
//         ).as_ref()
//     };
//     let result = match davsync::read_file(path) {
//         Ok(result) => result,
//         Err(error) => error.to_string()
//     };

//     env.new_string(result).expect("Can't create Java String").into_raw()
// }


// /// A pair of streams which will be used to send logs to the android logging class.
// struct LoggingStreams {
//     read: RefCell<UnixStream>,
//     write: RefCell<UnixStream>
// }
// impl LoggingStreams {
//     pub fn new() -> io::Result<Self> {
//         let (read, write) = UnixStream::pair()?;
//         Ok(Self { read: RefCell::new(read), write: RefCell::new(write) })
//     }
//     fn read_to_string(&self) -> io::Result<String> {
//         let mut buf = String::new();
//         self.read.borrow_mut().read_to_string(&mut buf)?;
//         Ok(buf)
//     }
// }
// // impl Read for LoggingStreams {
// //     fn read(&self, buf: &mut [u8]) -> io::Result<usize> {
// //         self.read.borrow_mut().read(buf)
// //     }
// // }
// impl Logger for LoggingStreams {
//     fn log(&self, msg: &str) {
//         self.write.borrow_mut().write(msg.as_bytes()).unwrap();
//     }
// }

struct AndroidLogger<'a> {
    /// Reference to the android.util.Log class.
    log_class: JClass<'a>,
    /// Tag for log messages.
    tag: JString<'a>,
}

impl<'a> AndroidLogger<'a> {
    /// helper function
    pub fn new(env: &mut JNIEnv<'a>) -> Result<Self, jni::errors::Error> {
        Ok(Self {
            log_class: env.find_class("android/util/Log")?,
            tag: env.new_string("LoggerRs")?,
        })
    }

    pub fn debug(&self, env: &mut JNIEnv<'a>, msg: impl AsRef<str>) {
        env.call_static_method(
            &self.log_class,
            "d",
            "(Ljava/lang/String;Ljava/lang/String;)I",
            &[
                JValue::Object(&self.tag),
                JValue::Object(&JObject::from(env.new_string(msg.as_ref()).expect("Couldnt convert utf-8 to utf-16")))
            ]
        ).unwrap();
    }
}
