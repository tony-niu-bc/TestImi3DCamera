package com.hjimi.colordepth;

import com.hjimi.api.iminect.ImiDevice;
import com.hjimi.api.iminect.ImiFrameMode;
import com.hjimi.api.iminect.Utils;

import java.nio.ByteBuffer;

public class SimpleViewer extends Thread
{
    private boolean mShouldRun = false;

    private GLPanel     mGLPanel;
    private DecodePanel mDecodePanel;

    private ImiDevice.ImiStreamType mStreamType;
    private ImiDevice               mDevice;
    private ImiFrameMode            mCurrentMode;

    private void drawFrame(ImiDevice.ImiFrame nextFrame)
    {
        // draw color image.
        switch (mCurrentMode.getFormat())
        {
            case IMI_PIXEL_FORMAT_DEP_16BIT:
                if (null != mGLPanel)
                {
                    mGLPanel.paint(null,
                                   Utils.depth2RGB888(nextFrame, true, false),
                                   nextFrame.getWidth(),
                                   nextFrame.getHeight());
                }
                break;

            case IMI_PIXEL_FORMAT_IMAGE_RGB24:
                if (null != mGLPanel) {
                    mGLPanel.paint(null,
                                   nextFrame.getData(),
                                   nextFrame.getWidth(),
                                   nextFrame.getHeight());
                }
                break;

            case IMI_PIXEL_FORMAT_IMAGE_H264:
                if (null != mDecodePanel) {
                    mDecodePanel.paint(nextFrame.getData(),
                                       nextFrame.getTimeStamp());
                }
                break;

            case IMI_PIXEL_FORMAT_IMAGE_YUV420SP:
                ByteBuffer frameData = Utils.yuv420sp2RGB(nextFrame);

                if (null != mGLPanel) {
                    mGLPanel.paint(null,
                                   frameData,
                                   nextFrame.getWidth(),
                                   nextFrame.getHeight());
                }
                break;

            default:
                break;
        }
    }

    public SimpleViewer(ImiDevice               device,
                        ImiDevice.ImiStreamType streamType)
    {
        mDevice     = device;
        mStreamType = streamType;
    }

    public void setGLPanel(GLPanel GLPanel) {
        this.mGLPanel = GLPanel;
    }

    public void setDecodePanel(DecodePanel decodePanel) {
        this.mDecodePanel = decodePanel;
    }

    @Override
    public void run()
    {
        super.run();

        // get current framemode.
        mCurrentMode = mDevice.getCurrentFrameMode(mStreamType);

        // start read frame.
        while (mShouldRun)
        {
            ImiDevice.ImiFrame nextFrame = mDevice.readNextFrame(mStreamType,
                                                                 25); // 1秒30帧，每帧33毫秒

            //frame maybe null, if null, continue.
            if (null == nextFrame) {
                continue;
            }

            // draw frame.
            drawFrame(nextFrame);
        }
    }

    public void toPause()
    {
        if (null != mGLPanel) {
            mGLPanel.onPause();
        }
    }

    public void toResume()
    {
        if (null != mGLPanel) {
            mGLPanel.onResume();
        }
    }

    public void toStart()
    {
        if (!mShouldRun)
        {
            mShouldRun = true;

            // start read thread
            this.start();
        }
    }

    public void toDestroy()
    {
        mShouldRun = false;
    }
}
