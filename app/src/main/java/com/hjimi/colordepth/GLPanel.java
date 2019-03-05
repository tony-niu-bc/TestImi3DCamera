package com.hjimi.colordepth;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

@SuppressLint("NewApi")
public class GLPanel extends GLSurfaceView
{
    private GLGraphics mGLGraphics;
    private ByteBuffer mBufferImage;

    private boolean bWork = true;

    private int mFrameWidth;
    private int mFrameHeight;

    @SuppressLint("NewApi")
    private class Scene implements Renderer
    {
        public void onDrawFrame(GL10 gl)
        {
            // 要清除 颜色缓冲 以及 深度缓冲
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

            if (null != mBufferImage) {
                mBufferImage.position(0);

                mGLGraphics.buildTextures(mBufferImage, mFrameWidth, mFrameHeight);
            }

            mGLGraphics.draw();
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        public void onSurfaceCreated(GL10 gl, EGLConfig config)
        {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

            mGLGraphics = new GLGraphics();

            if (!mGLGraphics.isProgramBuilt()) {
                mGLGraphics.buildProgram();
            }

            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
        }
    }

    @SuppressLint("NewApi")
    public GLPanel(Context context) {
        super(context);

        init(context);
    }

    public GLPanel(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context)
    {
        setEGLContextClientVersion(2);
        setRenderer(new Scene());
        // 图像数据改变后手动调用requestRender函数时渲染新图像
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void paint(float[]    vertices,
                      ByteBuffer buffer,
                      int        width,
                      int        height)
    {
		if (null == mGLGraphics) {
			return;
		}

        mBufferImage = buffer;

        mFrameWidth  = width;
        mFrameHeight = height;

        if (bWork) {
            requestRender();
        }
	}

    @Override
    public void onPause() {
        super.onPause();

        bWork = false;
    }

    @Override
    public void onResume() {
        super.onResume();

        bWork = true;
    }
}
