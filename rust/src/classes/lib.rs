pub mod fs;

use ez_jni::call;
use jni::{JNIEnv, objects::JObject};

// Wrapper class for `android.database.Cursor`
pub struct Cursor<'local>(JObject<'local>);
impl<'local> Cursor<'local> {
    pub fn new(cursor: JObject<'local>) -> Self {
        Self(cursor)
    }

    /// Query the Android Content Provider at some *URI*, and get a cursor as a result.
    pub fn query_str(
        env: &mut JNIEnv<'local>,
        context: &JObject,
        uri: &str,
        projection: &[&str],
        selection: &str,
        selection_args: &[&str],
        sorting: &str,
    ) -> Result<Self, String> {
        let uri = call!(static android.net.Uri.parse(String(uri)) -> android.net.Uri);
        Self::query(env, context, &uri, projection, selection, selection_args, sorting)
    }

    /// Query the Android Content Provider at some *URI*, and get a cursor as a result.
    pub fn query(
        env: &mut JNIEnv<'local>,
        context: &JObject,
        uri: &JObject,
        projection: &[&str],
        selection: &str,
        selection_args: &[&str],
        sorting: &str,
    ) -> Result<Self, String> {
        let content_resolver =
            call!(context.getContentResolver() -> android.content.ContentResolver);
        let result = call!(content_resolver.query(
            android.net.Uri(uri),
            [String](projection),
            String(selection),
            [String](selection_args),
            String(sorting),
        ) -> Result<android.database.Cursor, String>)?;

        Ok(Self(result))
    }

    pub fn row_count(&self, env: &mut JNIEnv) -> usize {
        call!((self.0).getCount() -> int) as usize
    }

    pub fn next(&self, env: &mut JNIEnv) -> bool {
        call!((self.0).moveToNext() -> bool)
    }
    pub fn get_string(&self, env: &mut JNIEnv<'local>, index: u32) -> String {
        call!((self.0).getString(int(index as i32)) -> String)
    }
    pub fn get_int(&self, env: &mut JNIEnv, index: u32) -> i32 {
        call!((self.0).getInt(int(index as i32)) -> int)
    }

    pub fn close(self, env: &mut JNIEnv) {
        call!((self.0).close() -> void)
    }
}
impl<'local> AsRef<JObject<'local>> for Cursor<'local> {
    fn as_ref(&self) -> &JObject<'local> {
        &self.0
    }
}
