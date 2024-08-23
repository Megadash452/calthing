package me.marti.calprovexample.jni;

import androidx.annotation.NonNull;

public class RustPanic extends RuntimeException {
    public RustPanic(@NonNull String msg) {
        super(msg);
    }
}
