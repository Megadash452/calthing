use std::{io, fmt::Display, path::{Component, Path}};
use ez_jni::{call, utils::get_string};
use jni::{JNIEnv, objects::{JObject, JString}};
use super::Cursor;

static DIR_MIME_TYPE: &str = "vnd.android.document/directory";

/// Get the file name (*without extension*) from a file name.
pub fn file_stem(file_name: &str) -> &str {
    // Ignore the starting '.'
    file_name
        .strip_prefix('.')
        .unwrap_or(file_name)
        // Return the part BEFORE the last '.'
        .rsplit_once('.')
        .map(|(stem, _)| stem)
        // Return entire file name if there are no '.'
        .unwrap_or(file_name)
}

/// Represents a path of a Document in Shared Storage, which could be accessed through a *Document Tree*.
///
/// This is analogous to [`Path`] in a normal system.
///
/// Having a valid Uri does not mean that the file exists.
pub struct DocUri<'local>(JObject<'local>);
impl<'local> DocUri<'local> {
    /// Get a [DocUri] from an Uri that has access to a **Document Tree** and a **Document Path** for identifying a document within that tree.
    pub fn from_tree_uri(env: &mut JNIEnv<'local>, uri: JObject<'local>) -> Result<Self, String> {
        use jni::objects::JListIter;

        let segments = call!(uri.getPathSegments() -> java.util.List);
        let segments = env.get_list(&segments).unwrap();
        let mut segments = segments.iter(env).unwrap();
        fn next_segment(env: &mut JNIEnv, segments: &mut JListIter) -> String {
            segments
                .next(env)
                .unwrap()
                .map(|seg| get_string(JString::from(seg), env))
                .expect("Unexpected end of Uri Segments")
        }

        // The uri's path should have this format: "/tree/{document_tree}/document/{document_path}"
        if next_segment(env, &mut segments) != "tree" {
            return Err("DocUri must have Document Tree".to_string());
        }
        if segments.next(env).unwrap().is_none() {
            return Err("DocUri has Document Tree, but does not have a value for it".to_string());
        }
        if next_segment(env, &mut segments) != "document" {
            return Err("DocUri must have Document Path".to_string());
        }
        if segments.next(env).unwrap().is_none() {
            return Err("DocUri has Document Path, but does not have a value for it".to_string());
        }

        Ok(Self(uri))
    }

    /// Get a [DocUri] from an Uri that only has a **Document Path**.
    pub fn from_doc_uri(env: &mut JNIEnv<'local>, uri: JObject<'local>) -> Result<Self, String> {
        use jni::objects::JListIter;

        let segments = call!(uri.getPathSegments() -> java.util.List);
        let segments = env.get_list(&segments).unwrap();
        let mut segments = segments.iter(env).unwrap();
        fn next_segment(env: &mut JNIEnv, segments: &mut JListIter) -> String {
            segments
                .next(env)
                .unwrap()
                .map(|seg| get_string(JString::from(seg), env))
                .expect("Unexpected end of Uri Segments")
        }

        // The uri's path should have this format: "/document/{document_path}"
        if next_segment(env, &mut segments) != "document" {
            return Err("DocUri must have Document Path".to_string());
        }
        if segments.next(env).unwrap().is_none() {
            return Err("DocUri has Document Path, but does not have a value for it".to_string());
        }

        Ok(Self(uri))
    }

    /// Attempts to open a file in Shared Storage identified by the [`DocUri`] with the given mode (**options**).
    pub fn open_file(
        &self,
        env: &mut JNIEnv<'local>,
        context: &JObject,
        options: OpenOptions,
    ) -> io::Result<std::fs::File> {
        use std::os::fd::FromRawFd as _;

        let content_resolver = call!(context.getContentResolver() -> android.content.ContentResolver);
        let mut fd = call!(content_resolver.openAssetFileDescriptor(
            android.net.Uri(self.0),
            String(options.to_string()),
        ) -> Result<Option<android.content.res.AssetFileDescriptor>, String>)
            .map_err(|err| io::Error::new(io::ErrorKind::NotFound, err))?
            .ok_or_else(|| io::Error::other("Failed to open file because ContentProvider crashed"))?;
        fd = call!(fd.getParcelFileDescriptor() -> android.os.ParcelFileDescriptor);

        // The file descriptor is open (called "openAssetFileDescriptor") and owned (called "detachFd").
        Ok(unsafe { std::fs::File::from_raw_fd(call!(fd.detachFd() -> int)) })
    }

    /// Get the name of the file or directory for this Uri.
    /// Similar to [Path::file_name()].
    pub fn file_name(&self, env: &mut JNIEnv) -> String {
        let doc_path = call!((self.0).getLastPathSegment() -> Option<String>)
            .unwrap();
        Self::doc_path_file_name(&doc_path).to_string()
    }
    /// Get the [name](Self::file_name()) (*without extension*) of the file or directory for this Uri.
    /// Similar to [Path::file_stem()].
    pub fn file_stem(&self, env: &mut JNIEnv) -> String {
        let file_name = self.file_name(env);
        file_stem(&file_name).to_string()
    }

    /// Get the file name out of the {doc_path} part of an Document Uri
    fn doc_path_file_name(doc_path: &str) -> &str {
        // Split the path at the last component (hence reverse split)
        doc_path
            .rsplit_once('/')
            // Strip the "primary:" part of the doc id if it's only one component
            .or_else(|| doc_path.split_once(':'))
            .map(|split| split.1)
            // Would be weird if the doc id didn't have that "primary:" part, but should still be good
            .unwrap_or(doc_path)
    }

    /// Appends **path** to the end of the Document Path.
    pub fn join(&self, env: &mut JNIEnv<'local>, path: impl AsRef<Path>) -> Self {
        let path = path
            .as_ref()
            .to_str()
            .unwrap_or_else(|| panic!("Path must be valid UTF-8: {}", path.as_ref().display()));
        Self(
            call!(static me.marti.calprovexample.jni.DavSyncRsHelpers.joinDocUri(
                android.net.Uri(self.0),
                String(path)
            ) -> Option<android.net.Uri>)
                .unwrap_or_else(|| panic!("Passed in an invalid Uri to joinDocUri: {}", call!((self.0).toString() -> String))),
        )
    }
}
impl<'local> AsRef<JObject<'local>> for DocUri<'local> {
    fn as_ref(&self) -> &JObject<'local> {
        &self.0
    }
}

/// Can't use libc to handle files stored in Shared storage, so i'm using java methods instead.
pub struct ExternalDir<'local> {
    context: JObject<'local>,
    doc_uri: DocUri<'local>,
}
impl<'local> ExternalDir<'local> {
    /// Returns [`None`] if the file in Shared Storage doesn't exist or is not a directory.
    pub fn new(
        context: JObject<'local>,
        doc_uri: DocUri<'local>,
        env: &mut JNIEnv<'local>,
    ) -> Option<Self> {
        // Check if doc_uri is a directory
        if !call!(static me.marti.calprovexample.jni.DavSyncRsHelpers.isDir(
            android.content.Context(context),
            android.net.Uri(doc_uri.as_ref())
        ) -> Result<bool, String>)
        .ok()?
        {
            return None;
        }

        Some(Self { context, doc_uri })
    }

    fn file_exists(&self, env: &mut JNIEnv<'local>, file_name: &str) -> bool {
        self.entries(env)
            .iter()
            .any(|entry| entry.file_name() == file_name)
    }

    pub fn entries(&self, env: &mut JNIEnv<'local>) -> Box<[ExternalDirEntry<'local>]> {
        // // Get the treeUri so that entries can also have the tree id in their URI
        let tree_uri = call!(static me.marti.calprovexample.jni.DavSyncRsHelpers.getDocumentTreeUri(
            android.net.Uri(self.doc_uri.as_ref())
        ) -> android.net.Uri);
        // Call the query helper function to list all entries of the directory
        let cursor = Cursor::new(
            call!(static me.marti.calprovexample.jni.DavSyncRsHelpers.queryChildrenOfDocument(
                android.content.Context(self.context),
                android.net.Uri(self.doc_uri.as_ref())
            ) -> Result<android.database.Cursor, String>)
            .unwrap_or_else(|e| {
                let doc_uri_str = call!((self.doc_uri.as_ref()).toString() -> String);
                panic!("Failed to query entries of {doc_uri_str:?}:\n{e}")
            }),
        );

        let mut entries = Vec::with_capacity(cursor.row_count(env));
        // Iterate through the results of the query to create Dir Entries
        while cursor.next(env) {
            let doc_id = cursor.get_string(env, 0);
            let doc_uri = call!(static android.provider.DocumentsContract.buildDocumentUriUsingTree(
                android.net.Uri(tree_uri),
                java.lang.String(doc_id)
            ) -> android.net.Uri);
            let mime_type = cursor.get_string(env, 1);
            let flags = cursor.get_int(env, 2);

            entries.push(ExternalDirEntry {
                doc_uri: DocUri(doc_uri),
                doc_id,
                mime_type,
                flags,
            });
        }
        cursor.close(env);

        entries.into()
    }

    /// Open a file that is a descendant of this directory in the file tree.
    /// See [DocUri::open_file()].
    ///
    /// The **path** must be a relative path; an absolute path will cause an error.
    pub fn open_file(
        &self,
        env: &mut JNIEnv<'local>,
        path: impl AsRef<Path>,
        options: OpenOptions,
    ) -> io::Result<std::fs::File> {
        let path = path.as_ref();
        if path.is_absolute() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "Path argument must be a relative path; provided absolute path",
            ));
        }

        self.doc_uri
            .join(env, path)
            .open_file(env, &self.context, options)
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
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "Path argument must be a relative path; provided absolute path",
            ));
        }
        let file_name = path
            .file_name()
            .ok_or_else(|| {
                io::Error::new(io::ErrorKind::InvalidInput, "Path must have a file name")
            })?
            .to_str()
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "Path must be UTF-8"))?;

        match path.parent() {
            Some(parent_dir) => self
                .create_dir_at(env, parent_dir)?
                .create_file(env, file_name),
            None => self.create_file(env, file_name),
        }
    }

    /// Create a **file** in this directory.
    /// Returns [io::ErrorKind::AlreadyExists] if a file with this name already exists.
    pub fn create_file(&self, env: &mut JNIEnv<'local>, file_name: &str) -> io::Result<DocUri<'local>> {
        // Check if file already exists
        if self.file_exists(env, file_name) {
            return Err(io::Error::new(
                io::ErrorKind::AlreadyExists,
                format!("A file named {file_name:?} already exists"),
            ));
        }
        let file_name = Path::new(&file_name);
        let ext = file_name
            .extension()
            .ok_or_else(|| { io::Error::new(io::ErrorKind::InvalidInput, "File name has no extension")})?
            .to_str()
            .unwrap(); // Guaranteed to be UTF-8
        let file_stem = file_name
            .file_stem()
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "Invalid filename"))?
            .to_str()
            .unwrap(); // Guaranteed to be UTF-8
        // Create the file
        self.create_document(
            env,
            file_stem,
            mime_guess::from_ext(ext).first_or_octet_stream().as_ref(),
        )
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
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "Path argument must be a relative path; provided absolute path",
            ));
        }

        /// Open a directory (or create it if it doesn't exists) that is a direct child of **dir**.
        fn create_or_open_dir<'local>(
            env: &mut JNIEnv<'local>,
            dir: &ExternalDir<'local>,
            sub_dir: Component,
        ) -> io::Result<ExternalDir<'local>> {
            let sub_dir =
                match sub_dir {
                    Component::Normal(component) => component.to_str().ok_or_else(|| {
                        io::Error::new(io::ErrorKind::InvalidInput, "Path must be UTF-8")
                    })?,
                    _ => return Err(io::Error::new(
                        io::ErrorKind::InvalidInput,
                        "Path contains invalid components (e.g. \"..\"), must be a relative path",
                    )),
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
            None => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "Path must not be empty",
                ))
            }
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
        match self
            .entries(env)
            .into_vec()
            .into_iter()
            .find(|entry| entry.file_name() == name)
        {
            Some(dir) => {
                let err_msg = format!(
                    "A file named {:?} already exists, and is not a directory",
                    dir.file_name()
                );
                Ok(dir
                    .into_dir(env.new_local_ref(&self.context).unwrap())
                    .ok_or_else(|| io::Error::new(io::ErrorKind::AlreadyExists, err_msg))?)
            }
            // FIXME: returns an URI with the wrong tree
            None => self
                .create_document(env, name, DIR_MIME_TYPE)
                .map(move |doc_uri| Self {
                    doc_uri,
                    context: env.new_local_ref(&self.context).unwrap(),
                }),
        }
    }

    /// Helper function that calls the create document in java.
    /// **file_stem** is teh file name without extension.
    ///
    /// Check if document exists before calling this
    fn create_document(&self, env: &mut JNIEnv<'local>, file_stem: &str, mime: &str) -> io::Result<DocUri<'local>> {
        let uri = call!(static android.provider.DocumentsContract.createDocument(
            android.content.ContentResolver(call!((self.context).getContentResolver() -> android.content.ContentResolver)),
            android.net.Uri(self.doc_uri.as_ref()),
            String(mime),
            String(file_stem)
        ) -> Result<Option<android.net.Uri>, io::Error>)?
            .ok_or_else(|| io::Error::other("Failed to create file {file_name:?}, unknown reason"))?;

        DocUri::from_tree_uri(env, uri).map_err(|err| {
            io::Error::other(format!(
                "DocumentsContract.createDocument() returned an invalid DocUri: {err}"
            ))
        })
    }
}

pub struct ExternalDirEntry<'local> {
    doc_uri: DocUri<'local>,
    doc_id: String,
    mime_type: String,
    #[allow(dead_code)] flags: i32,
}
impl<'local> ExternalDirEntry<'local> {
    pub fn is_dir(&self) -> bool {
        self.mime_type == "vnd.android.document/directory"
    }
    /// Get an [`ExternalDir`] from this entry if it is a directory.
    pub fn into_dir(self, context: JObject<'local>) -> Option<ExternalDir<'local>> {
        if self.is_dir() {
            Some(ExternalDir {
                context,
                doc_uri: self.doc_uri,
            })
        } else {
            None
        }
    }

    /// See [DocUri::open_file()].
    pub fn open_file(
        &self,
        env: &mut JNIEnv<'local>,
        context: &JObject,
        options: OpenOptions,
    ) -> io::Result<std::fs::File> {
        self.doc_uri.open_file(env, context, options)
    }

    /// Get the name of the file or directory of this entry.
    pub fn file_name(&self) -> &str {
        DocUri::doc_path_file_name(&self.doc_id)
    }
    /// Get the name (*without extension*) of the file or directory of this entry.
    pub fn file_stem(&self) -> &str {
        file_stem(self.file_name())
    }

    /// Get the *path-like* object for this entry.
    pub fn uri(&self) -> &DocUri {
        &self.doc_uri
    }
}
impl<'local> std::fmt::Debug for ExternalDirEntry<'local> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ExternalDirEntry")
            .field("doc_id", &self.doc_id)
            .field(
                "mime_type",
                if self.is_dir() {
                    &"directory"
                } else {
                    &self.mime_type
                },
            )
            .finish()
    }
}

/// Options for opening files according to [ParceFileDescriptor::parseMode](https://developer.android.com/reference/android/os/ParcelFileDescriptor#parseMode(java.lang.String)).
pub enum OpenOptions {
    ReadOnly,
    WriteOnly {
        extra: Option<ExtraMode>,
    },
    ReadWrite {
        // Seems that can't have "rwa"
        truncate: bool,
    },
}
impl OpenOptions {
    /// Shortcut to get [`Self::WriteOnly`] without extra options.
    pub fn write() -> Self {
        Self::WriteOnly { extra: None }
    }
    /// Shortcut to get [`Self::ReadWrite`] without extra options.
    pub fn read_write() -> Self {
        Self::ReadWrite { truncate: false }
    }
}
impl Default for OpenOptions {
    /// Default mode is [`Self::ReadOnly`].
    fn default() -> Self {
        Self::ReadOnly
    }
}
impl Display for OpenOptions {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", match self {
            Self::ReadOnly => "r",
            Self::WriteOnly { extra: None } => "w",
            Self::WriteOnly {
                extra: Some(ExtraMode::Truncate),
            } => "wt",
            Self::WriteOnly {
                extra: Some(ExtraMode::Append),
            } => "wa",
            Self::ReadWrite { truncate: true } => "rwt",
            Self::ReadWrite { truncate: false } => "rw",
        })
    }
}

pub enum ExtraMode {
    Truncate,
    Append,
}
