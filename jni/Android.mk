LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := imiPCDjni
LOCAL_SRC_FILES := imiPCDjni.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)