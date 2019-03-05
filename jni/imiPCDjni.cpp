#include "imiPCDjni.h"
#include <assert.h>

#define  LOG_TAG    "ImiPCD"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


#pragma pack (push, 1)

typedef struct tagPoint3D {
    float x;
    float y;
    float z;
}ImiPoint3D;

#pragma pack(pop)

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_com_hjimi_pointcloud_SimpleViewer_disposePCD(JNIEnv * env, jclass obj, jobject srcpcd, int width, int height, jobject despcd)
{
	jbyte* src = (jbyte*)(env->GetDirectBufferAddress(srcpcd));
	if(NULL == src) {
		return -1;
	}
	jbyte* des = (jbyte*)(env->GetDirectBufferAddress(despcd));
	if(NULL == des) {
		return -2;
	}

	ImiPoint3D* srcPoint3D = (ImiPoint3D*)src;
	ImiPoint3D* desPoint3D = (ImiPoint3D*)des;
	int index = 0;
	int pcdindex = 0;
	float translationStep = 1.9f;
	int step = 1;
	if(width == 640) {
		step = 2;
	}
	for(int j = 0; j < height; j += step) {
		for(int i = 0; i < width; i += step) {
			index = j * width + i;
			if(srcPoint3D[index].x != 0 || srcPoint3D[index].y != 0 || srcPoint3D[index].z != 0) {
				desPoint3D[pcdindex].x = srcPoint3D[index].x;
				desPoint3D[pcdindex].y = -srcPoint3D[index].y;
				desPoint3D[pcdindex].z = -srcPoint3D[index].z + translationStep;
				pcdindex++;
			}
		}
	}
	
	return pcdindex;
}
#ifdef __cplusplus
}
#endif

