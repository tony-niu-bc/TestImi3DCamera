package com.hjimi.colordepth;

import android.content.res.Resources;
import android.opengl.GLES20;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;


public class ShaderUtil
{
    private static int
    loadShader(int    shaderType,
               String source)
    {
        int iShader = GLES20.glCreateShader(shaderType);

        if (0 != iShader)
        {
            GLES20.glShaderSource(iShader, source);
            GLES20.glCompileShader(iShader);

            int[] compiled = new int[1];
            GLES20.glGetShaderiv(iShader, GLES20.GL_COMPILE_STATUS, compiled, 0);

            if (0 == compiled[0])
            {
                Log.e("ES20_ERROR", "Could not compile shader " + shaderType + ":");
                Log.e("ES20_ERROR", GLES20.glGetShaderInfoLog(iShader));

                GLES20.glDeleteShader(iShader);

                iShader = 0;
            }
        }

        return iShader;
    }

    static void checkGlError(String op)
    {
        int error = GLES20.glGetError();
        if (GLES20.GL_NO_ERROR != error)
        {
            Log.e("ES20_ERROR", op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    static int createProgram(String vertexSource,
                             String fragmentSource)
    {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER,
                                      vertexSource);

        if (0 == vertexShader)
        {
            return 0;
        }

        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER,
                                     fragmentSource);
        if (0 == pixelShader)
        {
            return 0;
        }

        int program = GLES20.glCreateProgram();

        if (0 != program)
        {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");

            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");

            GLES20.glLinkProgram(program);

            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);

            if (GLES20.GL_TRUE != linkStatus[0])
            {
                Log.e("ES20_ERROR", "Could not link program: ");
                Log.e("ES20_ERROR", GLES20.glGetProgramInfoLog(program));

                GLES20.glDeleteProgram(program);

                program = 0;
            }
        }

        return program;
    }

    public static String
    loadFromAssetsFile(String    fname,
                       Resources res)
    {
        String result = null;

        try
        {
            InputStream fis = res.getAssets().open(fname);

            int byteValue = 0;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            while (-1 != (byteValue = fis.read()))
            {
                baos.write(byteValue);
            }

            byte[] buff = baos.toByteArray();

            baos.close();
            fis.close();

            result = new String(buff, "UTF-8");
            result = result.replaceAll("\\r\\n", "\n");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return result;
    }
}
