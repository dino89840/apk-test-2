LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := cmflix_secrets
LOCAL_SRC_FILES := ../cpp/secrets.cpp
LOCAL_CFLAGS := -O2 -fvisibility=hidden
include $(BUILD_SHARED_LIBRARY)
