use std::{ffi::CStr, fs::File, io, ops::{Deref, DerefMut}, os::fd::FromRawFd as _};

use libc::c_char;

fn stat(fd: i32) -> io::Result<libc::stat> {
    unsafe {
        let mut stat = std::mem::zeroed();
        if libc::fstat(fd, &mut stat) == -1 {
            return Err(io::Error::last_os_error())
        }
        Ok(stat)
    }
}

/// A file that is *not owned* by this object, meaning the file descriptor is *not consumed*.
/// 
/// Use this intead of [File] when getting a `ParcelFileDescriptor` from opening a file in Java.
pub struct FileRef(File);
impl FileRef {
    pub fn new(fd: i32) -> Self {
        Self(unsafe { File::from_raw_fd(fd) })
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
impl Drop for FileRef {
    fn drop(&mut self) {
        std::mem::forget(std::mem::replace(&mut self.0, unsafe { File::from_raw_fd(0) }))
    }
}

/// An object that can use methods related to directories, such as create files, read entries...
/// 
/// If using a *file descriptor* obtained from Android's [`Content Provider`](https://developer.android.com/reference/android/content/ContentProvider#openFile(android.net.Uri,%20java.lang.String,%20android.os.CancellationSignal)),
/// [`Self::new_unowned()`] must be used, or this will cause a *fatal error*.
pub struct DirRef { fd: i32 }
impl DirRef {
    /// Takes the **file descriptor** for a directory and checks whether it indeed is a directory.
    /// 
    /// See [`Self::new_unowned()`] for **file descriptors** obtained from Android.
    pub fn new(fd: i32) -> io::Result<Self> {
        // Check if passed fd is for a directory
        // On some architectures, 'mode_t' has type u16. Casting is required. 
        if (stat(fd)?.st_mode as libc::mode_t & libc::S_IFMT) != libc::S_IFDIR {
            return Err(io::Error::new(io::ErrorKind::Other, "Expected fd to be for a directory"))
        }
        Ok(Self { fd })
    }
    /// Open a directory using a **file descriptor** that is already owned by something else (like `ParcelFileDescriptor` in Java).
    pub fn new_unowned(fd: i32) -> io::Result<Self> {
        Self::new(unsafe { libc::dup(fd) })
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
        unsafe {
            // Create nul-terminated version of string
            let name = {
                // Allocate buffer with extra space for nul-terminator
                let mut s = String::with_capacity(name.len() + 1);
                // Copy string to buffer
                s.push_str(name);
                // Add nul-terminator
                s.push('\0');
                s
            };
            // Get the rwx permission bits 
            let mode = stat(self.fd)?.st_mode & 0777;
            
            if libc::mkdirat(self.fd, name.as_ptr() as *const c_char, mode as libc::mode_t) == -1 {
                return Err(io::Error::last_os_error())
            }
        }
        Ok(())
    }

    /// Similar to [`std::fs::read_dir()`] but uses the *file descriptor* instead of a path.
    pub fn read_entries(&self) -> io::Result<ReadDir> {
        ReadDir::new(self.fd)
    }
}

/// Very similar to [`std::fs::ReadDir`].
pub struct ReadDir { stream: *mut libc::DIR, ended: bool }
impl ReadDir {
    fn new(fd: i32) -> io::Result<Self> {
        unsafe {
            // Open (execute) the directory
            let dir = libc::fdopendir(fd);
            if dir == std::ptr::null_mut() {
                return Err(io::Error::last_os_error())
            }
            // Skip the '.' and '..' entries
            libc::seekdir(dir, 2);

            Ok(Self { stream: dir, ended: false })
        }
    }
}
impl Iterator for ReadDir {
    type Item = io::Result<DirEntry>;

    fn next(&mut self) -> Option<Self::Item> {
        unsafe {
            if self.ended {
                return None
            }
            // To distinguish between end of entries and error, reset *errno* and then check if it has been set.
            *libc::__errno() = 0;
            let entry = libc::readdir(self.stream);

            if entry != std::ptr::null_mut() {
                // This code does not mutate the data in 'entry'
                Some(Ok(DirEntry { entry: entry as *const _ }))
            } else if *libc::__errno() != 0 {
                self.ended = true;
                Some(Err(io::Error::last_os_error()))
            } else {
                self.ended = true;
                None
            }
        }
    }
}

pub struct DirEntry { entry: *const libc::dirent }
impl DirEntry {
    /// The file name of the entry.
    /// 
    /// Returns `None` if the name contains invalid characters.
    pub fn name(&self) -> Option<&str> {
        // On some architectures, 'd_name' has type [i8; 256]. Casting to [u8; _] should be safe i think
        CStr::from_bytes_until_nul(unsafe { &*(&(&*self.entry).d_name as *const _ as *const [u8; 256]) })
            .ok()
            .and_then(|s| s.to_str().ok())
    }
}
