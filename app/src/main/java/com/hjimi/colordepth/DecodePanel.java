package com.hjimi.colordepth;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DecodePanel
{
    private MediaCodec mCodec = null;

    public DecodePanel(){
    }

    public void initDecoder(Surface surface,
                            int     width,
                            int     height)
    {
        // init decoder
        try
        {
            /* "video/x-vnd.on2.vp8" - VP8 video (i.e. video in .webm)
             * "video/x-vnd.on2.vp9" - VP9 video (i.e. video in .webm)
             * "video/avc"           - H.264/AVC video
             * "video/hevc"          - H.265/HEVC video
             * "video/mp4v-es"       - MPEG4 video
             * "video/3gpp"          - H.263 video
             * "audio/3gpp"          - AMR narrowband audio
             * "audio/amr-wb"        - AMR wideband audio
             * "audio/mpeg"          - MPEG1/2 audio layer III
             * "audio/mp4a-latm"     - AAC audio (note, this is raw AAC packets, not packaged in LATM!)
             * "audio/vorbis"        - vorbis audio
             * "audio/g711-alaw"     - G.711 alaw audio
             * "audio/g711-mlaw"     - G.711 ulaw audio
             */
            MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc",
                                                                    width,
                                                                    height);

            mCodec = MediaCodec.createDecoderByType("video/avc");
            mCodec.configure(mediaFormat, surface, null, 0);
            mCodec.start();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopDecoder()
    {
        // stop decoder
        if (null != mCodec)
        {
            mCodec.stop();
            mCodec.release();
            mCodec = null;
        }
    }

    public void paint(ByteBuffer bufferImage,
                      long       timeStamp)
    {
        // queue inputbuffer
        if (null != bufferImage)
        {
            try
            {
                int inputBufferIndex = mCodec.dequeueInputBuffer(1000);

                if (0 <= inputBufferIndex)
                {
                    ByteBuffer inputBuffer = mCodec.getInputBuffer(inputBufferIndex);

                    if (null != inputBuffer) {
                        inputBuffer.put(bufferImage);

                        mCodec.queueInputBuffer(inputBufferIndex,
                                                0,
                                                bufferImage.capacity(),
                                                timeStamp,
                                                0);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        // release outputbuffer
        try
        {
            BufferInfo info = new BufferInfo();

            int outputBufferIndex = mCodec.dequeueOutputBuffer(info, 1000);

            if(0 <= outputBufferIndex){
                mCodec.releaseOutputBuffer(outputBufferIndex,
                                           0 != info.size);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
