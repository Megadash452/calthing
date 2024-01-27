LOCAL_PATH := ./src/
include $(CLEAR_VARS)
LOCAL_MODULE := davsync

BUILD_MODE := release
BUILD_DIR := target/
TARGETS := aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
ALL := $(patsubst %, $(BUILD_DIR)/%/$(BUILD_MODE)/lib$(LOCAL_MODULE).so, $(TARGETS))

all: $(ALL)

# Does for allow making multiline rules like this?
$(BUILD_DIR)/aarch64-linux-android/$(BUILD_MODE)/lib$(LOCAL_MODULE).so:
	cargo build --target aarch64-linux-android $(find release, $(BUILD_MODE))
$(BUILD_DIR)/armv7-linux-androideabi/$(BUILD_MODE)/lib$(LOCAL_MODULE).so:
	cargo build --target armv7-linux-androideabi $(find release, $(BUILD_MODE))$(BUILD_MODE)
$(BUILD_DIR)/i686-linux-android/$(BUILD_MODE)/lib$(LOCAL_MODULE).so:
	cargo build --target i686-linux-android $(find release, $(BUILD_MODE))
$(BUILD_DIR)/x86_64-linux-android/$(BUILD_MODE)/lib$(LOCAL_MODULE).so:
	cargo build --target x86_64-linux-android $(find release, $(BUILD_MODE))

clean:
	cargo clean

.SILENT:
.PHONY: clean
