use std::{fs::File, io, mem::ManuallyDrop, ops::{Deref, DerefMut}, os::fd::FromRawFd as _, path::{Path, PathBuf}};
use jni::{objects::JObject, JNIEnv};
use jni_macros::call;

use crate::{get_string, Cursor};

// Gets the absolute path to a file of an open **file descriptor**.
pub fn fdpath(fd: i32) -> io::Result<PathBuf> {
    std::fs::read_link(format!("/proc/self/fd/{fd}"))
}

/// A file that is *not owned* by this object, meaning the file descriptor is *not consumed*.
///
/// Use this intead of [File] when getting a `ParcelFileDescriptor` from opening a file in Java.
pub struct FileRef(ManuallyDrop<File>);
impl FileRef {
    pub fn new(fd: i32) -> Self {
        Self(unsafe { ManuallyDrop::new(File::from_raw_fd(fd)) })
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

// pub struct DocUri<'local>(JObject<'local>);
// impl <'local> FromStr for DocUri<'local> {
//     type Err = ;

//     fn from_str(s: &str) -> Result<Self, Self::Err> {
//         todo!()
//     }
// }

/// Can't use libc to handle files stored in SHared storage, so i'm using java methods instead.
pub struct ExternalDir<'local, 'c> {
    context: &'c JObject<'local>,
    doc_uri: JObject<'local>,
}
impl <'local, 'c> ExternalDir<'local, 'c> {
    pub fn new(context: &'c JObject<'local>, uri: JObject<'local>) -> Self {
        // TODO: check if uri is for a directory
        Self { context, doc_uri: uri }
    }

    pub fn entries<'env>(&self, env: &mut JNIEnv<'env>) -> Box<[ExternalDirEntry<'env>]> {
        // Get the treeUri so that entries can also have the tree id in their URI
        let tree_uri = call!(static me.marti.calprovexample.jni.DavSyncRsHelpers::getDocumentTreeUri(
            android.net.Uri(self.doc_uri)
        ) -> android.net.Uri);
        // Call the query helper function to list all entries of the directory
        let cursor = Cursor::new(call!(static me.marti.calprovexample.jni.DavSyncRsHelpers::queryChildrenOfDocument(
            android.content.Context(self.context),
            android.net.Uri(self.doc_uri)
        ) -> android.database.Cursor));

        let mut entries = Vec::with_capacity(cursor.row_count(env));
        // Iterate through the results of the query to create Dir Entries
        while cursor.next(env) {
            let doc_id = cursor.get_string(env, 0);
            let doc_uri = call!(static android.provider.DocumentsContract::buildDocumentUriUsingTree(
                android.net.Uri(tree_uri), java.lang.String(doc_id)
            ) -> android.net.Uri);
            let mime_type = cursor.get_string(env, 1);
            let flags = cursor.get_int(env, 2);

            entries.push(ExternalDirEntry { doc_uri, doc_id: get_string(env, doc_id), mime_type: get_string(env, mime_type), flags });
        }
        cursor.close(env);

        entries.into()
    }

    /// Open a file that is a descendant of this directory in the file tree.
    ///
    /// The **path** must be a relative path; an absolute path will cause an error.
    pub fn open_file(&self, env: &mut JNIEnv, path: &Path) -> io::Result<std::fs::File> {
        !path.is_absolute();
        todo!()
    }

    /// Create a **file** that is a descendant of this directory in the file tree,
    /// and also create all subsequent parent directories of the file if they don't exist.
    ///
    /// The **path** must be a relative path; an absolute path will cause an error.
    pub fn create_file(&self, env: &mut JNIEnv, path: &Path) -> io::Result<std::fs::File> {
        !path.is_absolute();
        // TODO: check if file exists
        {
            let result = env.call_static_method("android/provider/DocumentsContract",
                "createDocument", "(Landroid/content/ContentResolver;Landroid/net/Uri;Ljava/lang/String;Ljava/lang/String;)Landroid/net/Uri;", &[
                    todo!()
                ]
            ).inspect_err(|err| panic!("Failed to call buildDocumentUriUsingTree(): {err}")).unwrap()
            .l().inspect_err(|err| panic!("Value returned by buildDocumentUriUsingTree() is not an Object: {err}")).unwrap();
            if result.is_null() {
                Some(result)
            } else {
                None
            }
        };
        todo!()
    }

    /// Create a **directory** that is a descendant of this directory in the file tree,
    /// and also create all subsequent parent directories of the directory if they don't exist.
    ///
    /// The **path** must be a relative path; an absolute path will cause an error.
    pub fn create_dir<'env>(&self, env: &mut JNIEnv<'env>, path: &Path) -> io::Result<ExternalDir<'env, 'c>> {
        !path.is_absolute();
        todo!()
    }
}

pub struct ExternalDirEntry<'local> {
    doc_uri: JObject<'local>,
    doc_id: String,
    mime_type: String,
    flags: i32
}
impl <'local> ExternalDirEntry<'local> {
    pub fn is_dir(&self) -> bool {
        self.mime_type == "vnd.android.document/directory"
    }
    /// Get the name of the file or directory of this entry.
    pub fn file_name(&self) -> &str {
        // Split the path at the last component (hence reverse split)
        self.doc_id.rsplit_once('/')
            // Strip the "primary:" part of the doc id if it's only one component
            .or_else(|| self.doc_id.split_once(':'))
            .map(|split| split.1)
            // Would be weird if the doc id didn't have that "primary:" part, but should still be good
            .unwrap_or(self.doc_id.as_str())
    }
}
impl <'local> std::fmt::Debug for ExternalDirEntry<'local> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ExternalDirEntry")
            .field("doc_id", &self.doc_id)
            .field("mime_type", if self.is_dir() {
                &"directory"
            } else {
                &self.mime_type
            })
            .finish()
    }
}
