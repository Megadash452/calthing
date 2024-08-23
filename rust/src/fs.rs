use std::{fs::File, io, mem::ManuallyDrop, ops::{Deref, DerefMut}, os::fd::FromRawFd as _, path::{Component, Path, PathBuf}};
use jni::{objects::{JObject, JString}, JNIEnv};
use jni_macros::call;
use crate::{get_string, Cursor, DocUri};

static DIR_MIME_TYPE: &str = "vnd.android.document/directory";

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

/// Can't use libc to handle files stored in Shared storage, so i'm using java methods instead.
#[derive(Debug)]
pub struct ExternalDir<'local> {
    context: JObject<'local>,
    doc_uri: JObject<'local>,
}
impl <'local> ExternalDir<'local> {
    /// Returns [`None`] if the file in Shared Storage doesn't exist or is not a directory.
    pub fn new(env: &mut JNIEnv<'local>, context: JObject<'local>, doc_uri: DocUri<'local>) -> Option<Self> {
        let doc_uri: JObject = doc_uri.into();
        
        // Check if doc_uri is a directory
        if !call!(static me.marti.calprovexample.jni.DavSyncRsHelpers::isDir(
            android.content.Context(&context),
            android.net.Uri(&doc_uri)
        ) -> bool) {
            return None
        }
        
        Some(Self { context, doc_uri })
    }
    
    fn file_exists(&self, env: &mut JNIEnv<'local>, file_name: &str) -> bool {
        self.entries(env).iter()
            .find(|entry| entry.file_name() == file_name)
            .is_some()
    }

    pub fn entries(&self, env: &mut JNIEnv<'local>) -> Box<[ExternalDirEntry<'local>]> {
        // // Get the treeUri so that entries can also have the tree id in their URI
        let tree_uri = call!(static me.marti.calprovexample.jni.DavSyncRsHelpers::getDocumentTreeUri(
            android.net.Uri(&self.doc_uri)
        ) -> android.net.Uri);
        // Call the query helper function to list all entries of the directory
        let cursor = Cursor::new(
            call!(static me.marti.calprovexample.jni.DavSyncRsHelpers::queryChildrenOfDocument(
                android.content.Context(&self.context),
                android.net.Uri(&self.doc_uri)
            ) -> Result<android.database.Cursor, String>)
            .inspect_err(|e | {
                let doc_uri_str = JString::from(call!((self.doc_uri).toString() -> java.lang.String));
                let doc_uri_str = get_string(env, doc_uri_str);
                panic!("Failed to query entries of {doc_uri_str:?}:\n{e}")
            }).unwrap()
        );

        let mut entries = Vec::with_capacity(cursor.row_count(env));
        // Iterate through the results of the query to create Dir Entries
        while cursor.next(env) {
            let doc_id = cursor.get_string(env, 0);
            let doc_uri = call!(static android.provider.DocumentsContract::buildDocumentUriUsingTree(
                android.net.Uri(&tree_uri),
                java.lang.String(&doc_id)
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
    pub fn open_file(&self, env: &mut JNIEnv<'local>, path: impl AsRef<Path>) -> io::Result<std::fs::File> {
        let path = path.as_ref();
        if path.is_absolute() {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "Path argument must be a relative path; provided absolute path"))
        }
        let content_resolver = call!((self.context).getContentResolver() -> android.content.ContentResolver);
        let mut fd = call!(content_resolver.openAssetFileDescriptor(
            android.net.Uri(DocUri::new_unchecked(env.new_local_ref(&self.doc_uri).unwrap()).join(env, path).into()),
            java.lang.String(env.new_string("rw").unwrap()),
        ) -> Result<Option<android.content.res.AssetFileDescriptor>, String>)
            .map_err(|err| io::Error::new(io::ErrorKind::NotFound, err))?
            .ok_or_else(|| io::Error::other("Failed to open file because ContentProvider crashed"))?;
        fd = call!(fd.getParcelFileDescriptor() -> android.os.ParcelFileDescriptor);
        
        // The file descriptor is open (called "openAssetFileDescriptor") and owned (called "detachFd").
        Ok(unsafe { std::fs::File::from_raw_fd(call!(fd.detachFd() -> int)) })
    }

    /// Create a **file** that is a descendant of this directory in the file tree,
    /// and also create all subsequent parent directories of the file if they don't exist.
    /// 
    /// Returns [io::ErrorKind::AlreadyExists] if a file with this name already exists at this path.
    ///
    /// The **path** must be a relative path; an absolute path will cause an error.
    pub fn create_file_at(&self, env: &mut JNIEnv<'local>, path: impl AsRef<Path>) -> io::Result<DocUri<'local>> {
        let path = path.as_ref();
        if path.is_absolute() {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "Path argument must be a relative path; provided absolute path"))
        }
        let file_name = path.file_name()
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "Path must have a file name"))?
            .to_str()
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "Path must be UTF-8"))?;
        
        match path.parent() {
            Some(parent_dir) => self.create_dir_at(env, parent_dir)?
                .create_file(env, file_name),
            None => self.create_file(env, file_name)
        }
    }
    
    /// Create a **file** in this directory.
    /// Returns [io::ErrorKind::AlreadyExists] if a file with this name already exists.
    pub fn create_file(&self, env: &mut JNIEnv<'local>, file_name: &str) -> io::Result<DocUri<'local>> {
        // Check if file already exists
        if self.file_exists(env, file_name) {
            return Err(io::Error::new(io::ErrorKind::AlreadyExists, format!("A file named {file_name:?} already exists")))
        }
        let file_name = Path::new(&file_name);
        let ext = file_name.extension()
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "File name has no extension"))?
            .to_str().unwrap(); // Guaranteed to be UTF-8
        let file_stem = file_name.file_stem()
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "Invalid filename"))?
            .to_str().unwrap(); // Guaranteed to be UTF-8
        // Create the file
        self.create_document(env, file_stem, &mime_guess::from_ext(ext).first_or_octet_stream().to_string())
    }

    /// Create a **directory** that is a descendant of this directory in the file tree,
    /// and also create all subsequent parent directories of the directory if they don't exist.
    /// 
    /// If the directory already exists at this path, it is opened without creating any directories.
    ///
    /// The **path** must be a relative path; an absolute path will cause an error.
    pub fn create_dir_at(&self, env: &mut JNIEnv<'local>, path: impl AsRef<Path>) -> io::Result<Self> {
        let path = path.as_ref();
        if path.is_absolute() {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "Path argument must be a relative path; provided absolute path"))
        }
        
        /// Open a directory (or create it if it doesn't exists) that is a direct child of **dir**.
        fn create_or_open_dir<'local>(env: &mut JNIEnv<'local>, dir: &ExternalDir<'local>, sub_dir: Component) -> io::Result<ExternalDir<'local>> {
            let sub_dir = match sub_dir {
                Component::Normal(component) => component.to_str()
                    .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "Path must be UTF-8"))?,
                _ => return Err(io::Error::new(io::ErrorKind::InvalidInput, "Path contains invalid components (e.g. \"..\"), must be a relative path"))
            };
            match dir.entries(env).into_vec().into_iter()
                .find(|entry| entry.file_name() == sub_dir)
            {
                Some(entry) => entry.into_dir(env.new_local_ref(&dir.context).unwrap())
                    .ok_or_else(|| io::Error::new(io::ErrorKind::AlreadyExists, "One of the descendant directories in the path already exists, but it is a file")),
                // Otherwise, create it
                None => dir.create_dir(env, sub_dir)
            }
        }
        
        let mut components = path.components();
        let mut current = match components.next() {
            Some(comp) => create_or_open_dir(env, self, comp)?,
            None => return Err(io::Error::new(io::ErrorKind::InvalidInput, "Path must not be empty"))
        };
        
        for component in components {
            current = create_or_open_dir(env, &current, component)?
        }
        
        Ok(current)
    }
    
    /// Create a **directory** that is a child if this directory.
    /// 
    /// If the directory already exists, it is opened without creating any directories.
    pub fn create_dir(&self, env: &mut JNIEnv<'local>, name: &str) -> io::Result<Self> {
        // Check if file already exists
        match self.entries(env).into_vec().into_iter()
            .find(|entry| entry.file_name() == name)
        {
            Some(dir) => {
                let err_msg = format!("A file named {:?} already exists, and is not a directory", dir.file_name());
                Ok(dir.into_dir(env.new_local_ref(&self.context).unwrap())
                    .ok_or_else(|| io::Error::new(io::ErrorKind::AlreadyExists, err_msg))?)
            },
            // FIXME: returns an URI with the wrong tree
            None => self.create_document(env, name, DIR_MIME_TYPE)
                .map(move |doc_uri| Self { context: env.new_local_ref(&self.context).unwrap(), doc_uri: doc_uri.into() })
        }
    }
    
    /// Helper function that calls the create document in java.
    /// **file_stem** is teh file name without extension.
    /// 
    /// Check if document exists before calling this
    fn create_document(&self, env: &mut JNIEnv<'local>, file_stem: &str, mime: &str) -> io::Result<DocUri<'local>> {
        let uri = call!(static android.provider.DocumentsContract::createDocument(
            android.content.ContentResolver(call!((self.context).getContentResolver() -> android.content.ContentResolver)),
            android.net.Uri(&self.doc_uri),
            java.lang.String(JObject::from(env.new_string(mime).unwrap())),
            java.lang.String(JObject::from(env.new_string(file_stem).unwrap()))
        ) -> Result<Option<android.net.Uri>, String>)
            .map_err(|msg| io::Error::new(io::ErrorKind::NotFound, msg))?
            .ok_or_else(|| io::Error::other("Failed to create file {file_name:?}, unknown reason"))?;
        
        DocUri::new(env, uri)
            .map_err(|err| io::Error::other(format!("DocumentsContract.createDocument() returned an invalid DocUri: {err}")))
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
    /// Get an [`ExternalDir`] from this entry if it is a directory.
    pub fn into_dir(self, context: JObject<'local>) -> Option<ExternalDir<'local>> {
        if self.is_dir() {
            Some(ExternalDir { context, doc_uri: self.doc_uri })
        } else {
            None
        }
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
