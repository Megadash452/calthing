package me.marti.calprovexample.jni;

import androidx.annotation.NonNull;

public class RustPanic extends RuntimeException {
    public RustPanic() {
        super("Rust had a panic! But panic data could not be obtained");
    }
    public RustPanic(@NonNull String msg) {
        super(msg);
    }
}
