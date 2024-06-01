use std::{fs::File, io, ops::{Deref, DerefMut}, os::fd::FromRawFd as _, path::PathBuf};

// Gets the absolute path to a file of an open **file descriptor**.
pub fn fdpath(fd: i32) -> io::Result<PathBuf> {
    std::fs::read_link(format!("/proc/self/fd/{fd}"))
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
