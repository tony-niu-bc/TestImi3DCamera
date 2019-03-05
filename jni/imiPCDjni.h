#ifndef _IMI_PCD_H
#define _IMI_PCD_H

#include <jni.h>
#include <stdio.h>
#include <android/log.h>

#ifndef NELEM
#define NELEM(x) ((int)(sizeof(x) / sizeof((x)[0])))
#endif

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_com_hjimi_pointcloud_SimpleViewer_disposePCD(JNIEnv * env, jclass obj, jobject srcpcd, int width, int height, jobject despcd);

#ifdef __cplusplus
}
#endif

#endif
