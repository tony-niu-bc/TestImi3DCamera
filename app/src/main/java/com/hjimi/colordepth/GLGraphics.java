package com.hjimi.colordepth;

import android.annotation.SuppressLint;
import android.opengl.GLES20;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

class GLGraphics
{
    private int ytid = -1;
    private int mGraphWidth = -1;
    private int mGraphHeight = -1;

    private int mTexture = 0;
    private ByteBuffer mCoordByteBuffer;
    private ByteBuffer mVerticeByteBuffer;

    private static float[] squareVertices = { -1.0f, -1.0f,
                                               1.0f, -1.0f,
                                              -1.0f,  1.0f,
                                               1.0f,  1.0f };

    private static float[] coordVertices = { 0.0f, 1.0f,
                                             1.0f, 1.0f,
                                             0.0f, 0.0f,
                                             1.0f, 0.0f };

    private static final String VERTEX_SHADER = "attribute vec4 vPosition;\n"  +
                                                "attribute vec2 a_texCoord;\n" +
                                                "varying vec2 tc;\n"           +
                                                "void main() {\n"              +
                                                "gl_Position = vPosition;\n"   +
                                                "tc = a_texCoord;\n"           +
                                                "}\n";

    private static final String FRAGMENT_SHADER = "precision mediump float;\n"            +
                                                  "uniform sampler2D tex_y;\n"            +
                                                  "varying vec2 tc;\n"                    +
                                                  "void main() {\n"                       +
                                                  "gl_FragColor = texture2D(tex_y,tc);\n" +
                                                  "}\n";

    private boolean mIsProgBuilt = false;

    private int mProgram        = 0;
    private int mPositionHandle = -1;
    private int mCoordHandle    = -1;
    private int mYhandle        = -1;

    private int mVertexCount = -1;

    private void createBuffers() {
        mVerticeByteBuffer = ByteBuffer.allocateDirect(squareVertices.length * 4);
        mVerticeByteBuffer.order(ByteOrder.nativeOrder());
        mVerticeByteBuffer.asFloatBuffer().put(squareVertices);
        mVerticeByteBuffer.position(0);

        mCoordByteBuffer = ByteBuffer.allocateDirect(coordVertices.length * 4);
        mCoordByteBuffer.order(ByteOrder.nativeOrder());
        mCoordByteBuffer.asFloatBuffer().put(coordVertices);
        mCoordByteBuffer.position(0);
    }

    GLGraphics() {
        mTexture = GLES20.GL_TEXTURE0;

        createBuffers();
    }

    boolean isProgramBuilt() {
        return mIsProgBuilt;
    }

    @SuppressLint("NewApi")
    void buildProgram()
    {
        if (mProgram <= 0) {
            mProgram = ShaderUtil.createProgram(VERTEX_SHADER,
                                                FRAGMENT_SHADER);
        }

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        ShaderUtil.checkGlError("glGetAttribLocation vPosition");

        if (-1 == mPositionHandle) {
            throw new RuntimeException("Could not get attribute location for vPosition");
        }

        mCoordHandle = GLES20.glGetAttribLocation(mProgram, "a_texCoord");
        ShaderUtil.checkGlError("glGetAttribLocation a_texCoord");

        if (-1 == mCoordHandle) {
            throw new RuntimeException("Could not get attribute location for a_texCoord");
        }

        mYhandle = GLES20.glGetUniformLocation(mProgram, "tex_y");
        ShaderUtil.checkGlError("glGetUniformLocation tex_y");

        if (-1 == mYhandle) {
            throw new RuntimeException("Could not get uniform location for tex_y");
        }

        mIsProgBuilt = true;
    }

    @SuppressLint("NewApi")
    void draw()
    {
        GLES20.glUseProgram(mProgram);
        ShaderUtil.checkGlError("glUseProgram");

        GLES20.glVertexAttribPointer(mPositionHandle,
                                     2,
                                     GLES20.GL_FLOAT,
                                     false,
                                     8,
                                     mVerticeByteBuffer);
        ShaderUtil.checkGlError("glVertexAttribPointer mPositionHandle");

        GLES20.glEnableVertexAttribArray(mPositionHandle);

        GLES20.glVertexAttribPointer(mCoordHandle,
                                     2,
                                     GLES20.GL_FLOAT,
                                     false,
                                     8,
                                     mCoordByteBuffer);
        ShaderUtil.checkGlError("glVertexAttribPointer maTextureHandle");

        GLES20.glEnableVertexAttribArray(mCoordHandle);

        // bind textures
        GLES20.glActiveTexture(mTexture);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ytid);
        GLES20.glUniform1i(mYhandle, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mCoordHandle);
    }

    @SuppressLint("NewApi")
    void buildTextures(Buffer rgbBuffer, int width, int height)
    {
        boolean videoSizeChanged = ((width != mGraphWidth) ||
                                    (height != mGraphHeight));
        if (videoSizeChanged)
        {
            mGraphWidth = width;
            mGraphHeight = height;
        }

        if ((ytid < 0) ||
            videoSizeChanged)
        {
            if (ytid >= 0)
            {
                GLES20.glDeleteTextures(1, new int[] { ytid }, 0);
                ShaderUtil.checkGlError("glDeleteTextures");
            }

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            ShaderUtil.checkGlError("glGenTextures");
            ytid = textures[0];
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ytid);
        ShaderUtil.checkGlError("glBindTexture");

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,
                            0,
                            GLES20.GL_RGB, mGraphWidth,
                            mGraphHeight,
                            0,
                            GLES20.GL_RGB,
                            GLES20.GL_UNSIGNED_BYTE,
                            rgbBuffer);
        ShaderUtil.checkGlError("glTexImage2D");

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                               GLES20.GL_TEXTURE_MIN_FILTER,
                               GLES20.GL_NEAREST);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                               GLES20.GL_TEXTURE_MAG_FILTER,
                               GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                               GLES20.GL_TEXTURE_WRAP_S,
                               GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                               GLES20.GL_TEXTURE_WRAP_T,
                               GLES20.GL_CLAMP_TO_EDGE);
    }
}
