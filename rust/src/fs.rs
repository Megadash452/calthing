use std::{ffi::{CStr, CString}, fs::File, io, mem::ManuallyDrop, ops::{Deref, DerefMut}, os::{fd::FromRawFd as _, unix::ffi::OsStrExt}, path::{Path, PathBuf}};

fn stat(fd: i32) -> io::Result<libc::stat> {
    unsafe {
        let mut stat = std::mem::zeroed();
        if libc::fstat(fd, &mut stat) == -1 {
            return Err(io::Error::last_os_error())
        }
        Ok(stat)
    }
}
fn statat(fd: i32, path: &CStr) -> io::Result<libc::stat> {
    unsafe {
        let mut stat = std::mem::zeroed();
        if libc::fstatat(fd, path.as_ptr(), &mut stat, libc::AT_SYMLINK_NOFOLLOW) == -1 {
            return Err(io::Error::last_os_error())
        }
        Ok(stat)
    }
}

#[inline]
const fn is_dir(st_mode: libc::mode_t) -> bool {
    st_mode & libc::S_IFMT == libc::S_IFDIR
}

// Gets the absolute path to a file of an open **file descriptor**.
pub fn fdpath(fd: i32) -> io::Result<PathBuf> {
    std::fs::read_link(format!("/proc/self/fd/{fd}"))
}

/// Creates a new string that can be used for `libc`.
/// Returns `InvalidInput` error according to [`CString::new()`].
fn path_to_cstring(path: &Path) -> io::Result<CString> {
    CString::new(path.as_os_str().as_bytes())
        .map_err(|error| io::Error::new(
            io::ErrorKind::InvalidInput,
            format!("Error converting Path to CString: {error}")
        ))
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

/// An object that can use methods related to directories, such as create files, read entries...
/// 
/// A [`DirRef`] is **owned** if it was opened by this language/process.
/// For example, a DirRef opened using a file descriptor obtained from *Java* is **NOT owned**.
/// 
/// If using a *file descriptor* obtained from Android's [`Content Provider`](https://developer.android.com/reference/android/content/ContentProvider#openFile(android.net.Uri,%20java.lang.String,%20android.os.CancellationSignal)),
/// [`Self::new_unowned()`] must be used, or this will cause a *fatal error*.
pub struct DirRef { fd: i32, owned: bool }
impl DirRef {
    // note: files will not be created with all these permissions but will be selected using umask.
    /// Default mode to pass to `mkdir`
    const DEFAULT_DIR_MODE: libc::mode_t = libc::S_IRWXU | libc::S_IRWXG | libc::S_IRWXO;
    /// Defailt mode to pass to `creat`.
    const DEFAULT_FILE_MODE: libc::mode_t = libc::S_IRUSR | libc::S_IWUSR |  libc::S_IRGRP | libc::S_IWGRP |  libc::S_IROTH | libc::S_IWOTH;

    // /// Open a directory at a **path** *relative* to **fd**.
    // pub fn new_at(mut fd: i32, path: impl AsRef<Path>) -> io::Result<Self> {
    //     let path = path_to_cstring(path.as_ref())?;
    //     Self::open_check(fd)?;
    //     fd = unsafe { libc::openat(fd, path.as_ptr(), libc::O_DIRECTORY) };
    //     Ok(Self { fd, owned: true })
    // }
    /// Open a directory using a **file descriptor** that is already owned by something else (like `ParcelFileDescriptor` in Java).
    pub fn new_unowned(mut fd: i32) -> io::Result<Self> {
        // dup is necessary to open the directory stream
        fd = unsafe { libc::dup(fd) };
        Self::open_check(fd)?;
        Ok(Self { fd, owned: false })
    }

    /// Check whether opening the directory was successful. Useful to avoid repeating code.
    fn open_check(fd: i32) -> io::Result<()> {
        // Check if passed fd is for a directory
        // On some architectures, 'mode_t' has type u16. Casting is required. 
        if !is_dir(stat(fd)?.st_mode as libc::mode_t) {
            return Err(io::Error::new(io::ErrorKind::Other, "Expected fd to be for a directory"))
        }
        Ok(())
    }

    /// Create a directory which is a direct child of `self`.
    /// Similar to [`std::fs::create_dir()`].
    /// 
    /// If you want to create a directory that is a descendant of `self`,
    /// having one or more directories between it and `self`, see [`Self::create_dir_all()`].
    pub fn create_dir(&self, name: &str) -> io::Result<()> {
        if name.contains('/') {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "File/Dir name cannot contain '/'"))
        }
        self.create_dir_all(Path::new(name))
    }

    /// Creates a directory that is a descendant of `self`.
    /// Similar to [`std::fs::create_dir_all()`].
    pub fn create_dir_all(&self, path: &Path) -> io::Result<()> {
        // Create nul-terminated version of string
        let path = path_to_cstring(path)?;

        unsafe {
            if libc::mkdirat(self.fd, path.as_ptr(), Self::DEFAULT_DIR_MODE) == -1 {
                // The call to mkdir can fail if a file exists, even if it's a directory. 
                if *libc::__errno() == libc::EEXIST && is_dir(statat(self.fd, &path)?.st_mode as libc::mode_t) {
                    return Ok(());
                }
                Err(io::Error::last_os_error())
            } else {
                Ok(())
            }
        }
    }

    /// Helper for opening a file.
    /// **mode** is only used if the flag `O_CREAT` is passed. Otherwise it can be 0
    fn openat(&self, path: impl AsRef<Path>, flags: libc::c_int, mode: libc::c_uint) -> io::Result<std::fs::File> {
        let path = path_to_cstring(path.as_ref())?;

        unsafe {
            let file = libc::openat(self.fd, path.as_ptr(), flags, mode);
            if file == -1 {
                return Err(io::Error::last_os_error())
            }

            Ok(File::from_raw_fd(file))
        }
    }

    /// Create a new file in a path *relative* to this directory.
    /// 
    /// If the file exists, an **error** with code [`AlreadyExists`](io::ErrorKind::AlreadyExists) will be returned.
    pub fn create_new_file(&self, path: impl AsRef<Path>) -> io::Result<std::fs::File> {
        self.openat(
            path,
            libc::O_CREAT | libc::O_EXCL | libc::O_RDWR, // O_EXCL: fails if file exists
            // Allow all permissions except execute (note: file will not be created with all these permissions)
            Self::DEFAULT_FILE_MODE as libc::c_uint
        )
    }
    /// Open a file that exists
    pub fn open_file(&self, path: impl AsRef<Path>, write: bool) -> io::Result<std::fs::File> {
        self.openat(path, if write { libc::O_RDWR } else { libc::O_RDONLY }, 0)
    }
}
impl Drop for DirRef {
    fn drop(&mut self) {
        if self.owned {
            unsafe { libc::close(self.fd); }
        }
    }
}