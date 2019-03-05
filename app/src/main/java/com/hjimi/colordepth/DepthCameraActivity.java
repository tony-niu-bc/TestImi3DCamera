package com.hjimi.colordepth;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.icu.text.SimpleDateFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.hjimi.api.iminect.ImiDevice;
import com.hjimi.api.iminect.ImiDeviceAttribute;
import com.hjimi.api.iminect.ImiDeviceState;
import com.hjimi.api.iminect.ImiFrameMode;
import com.hjimi.api.iminect.ImiNect;
import com.hjimi.api.iminect.ImiPixelFormat;
import com.hjimi.api.iminect.Utils;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Date;

public class DepthCameraActivity extends AppCompatActivity
{
    private SimpleViewer mDepthViewer = null;

    private GLPanel     mGLDepthPanel     = null;
    private TextView    mTVAttrs          = null;
    private Button      mbtn_take_a_photo = null;

    private ImiDevice          mDevice          = null;
    private ImiDeviceAttribute mDeviceAttribute = null;

    private static final int MSG_DEVICE_OPEN_SUCCESS = 0;
    private static final int MSG_DEVICE_OPEN_FALIED  = 1;
    private static final int MSG_DEVICE_DISCONNECT   = 2;
    private static final int MSG_ENABLE_TAKE_A_PHOTO = 3;
    private static final int MSG_EXIT_APP            = 4;

    static class MyHandler extends Handler
    {
        WeakReference<DepthCameraActivity> mActivity;

        MyHandler(DepthCameraActivity activity) {
            mActivity = new WeakReference<DepthCameraActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg)
        {
            DepthCameraActivity depthCameraActivity = mActivity.get();

            switch (msg.what)
            {
                case MSG_DEVICE_OPEN_SUCCESS:
                    depthCameraActivity.runViewer();
                    break;

                case MSG_DEVICE_OPEN_FALIED:
                case MSG_DEVICE_DISCONNECT:
                    depthCameraActivity.showMessageDialog((String)msg.obj);
                    break;

                case MSG_ENABLE_TAKE_A_PHOTO:
                    depthCameraActivity.enableTakeAPhoto();
                    break;

                case MSG_EXIT_APP:
                    depthCameraActivity.Exit();
                    break;
            }
        }
    }

    private void enableTakeAPhoto()
    {
        mbtn_take_a_photo.setEnabled(true);
    }

    private void runViewer()
    {
        mTVAttrs.setText("Device SerialNumber : " + mDeviceAttribute.getSerialNumber());
        mbtn_take_a_photo.setEnabled(true);
    }

    private void Exit()
    {
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private MyHandler mMsgHandler = new MyHandler(this);

    private class ExitAppRunnable implements Runnable
    {
        @Override
        public void run()
        {
            if (null != mDepthViewer) {
                mDepthViewer.toPause();
                mDepthViewer.toDestroy();
            }

            if(null != mDevice)
            {
                mDevice.close();

                mDevice = null;

                ImiDevice.destroy();
            }

            ImiNect.destroy();

            mMsgHandler.sendEmptyMessage(MSG_EXIT_APP);
        }
    }

    private void showMessageDialog(String errMsg)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(errMsg);
        builder.setPositiveButton("quit",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();

                        new Thread(new ExitAppRunnable()).start();
                    }
                });
        builder.show();
    }

    private class MainListener implements ImiDevice.OpenDeviceListener,
                                          ImiDevice.DeviceStateListener
    {
        @Override
        public void onOpenDeviceSuccess()
        {
            // open device success.
            mDeviceAttribute = mDevice.getAttribute();

            mMsgHandler.sendEmptyMessage(MSG_DEVICE_OPEN_SUCCESS);
        }

        @Override
        public void onOpenDeviceFailed(String errorMsg)
        {
            // open device falied.
            mMsgHandler.sendMessage(mMsgHandler.obtainMessage(MSG_DEVICE_OPEN_FALIED,
                                                              errorMsg));
        }

        @Override
        public void onDeviceStateChanged(String         deviceInfo,
                                         ImiDeviceState state)
        {
            if (ImiDeviceState.IMI_DEVICE_STATE_CONNECT == state) {
                Toast.makeText(DepthCameraActivity.this,
                               deviceInfo + " CONNECT",
                               Toast.LENGTH_LONG)
                     .show();
            }
            else {
                Toast.makeText(DepthCameraActivity.this,
                               deviceInfo + " DISCONNECT",
                               Toast.LENGTH_LONG)
                     .show();
            }
        }
    }

    private class OpenDeviceRunnable implements Runnable
    {
        @Override
        public void run()
        {
            MainListener mainlistener = new MainListener();

            // get iminect instance.
            ImiNect.initialize();

            mDevice = ImiDevice.getInstance();
            mDevice.addDeviceStateListener(mainlistener);

            ImiFrameMode depthMode = new ImiFrameMode(ImiPixelFormat.IMI_PIXEL_FORMAT_DEP_16BIT,
                                                      640,
                                                      480,
                                                      30);
            mDevice.setFrameMode(ImiDevice.ImiStreamType.DEPTH,
                                 depthMode);

            // 设置数据流启用同步(仅对A200系列设备有效)
            mDevice.setFramesSync(true);
            // 开启深度图像配准 - 以生成配准深度图像，
            // 让深度图和彩色图重合在一起，即将深度图像的图像坐标系转换到彩色图像的图像坐标系下
            mDevice.setImageRegistration(true);

            mDevice.open(DepthCameraActivity.this,
                         0, // 只打开设备，不打开数据流
                         mainlistener);
        }
    }

    private class ReadDepthFrameRunnable implements Runnable
    {
        @Override
        public void run()
        {
            ImiDevice.ImiFrame nextFrame = null;
            while (null == nextFrame)
            {
                nextFrame = mDevice.readNextFrame(ImiDevice.ImiStreamType.DEPTH,
                                                  25); // 1秒30帧，每帧33毫秒

                if (null == nextFrame)
                {
                    try {
                        Thread.sleep(1);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            ByteBuffer bbRGB88 = Utils.depth2RGB888(nextFrame, true, false);

            mGLDepthPanel.paint(null,
                                bbRGB88,
                                nextFrame.getWidth(),
                                nextFrame.getHeight());

            int width  = nextFrame.getWidth();
            int height = nextFrame.getHeight();

            ByteBuffer bfPointCloud = ByteBuffer.allocateDirect(width * height * 3 * 4);
            bfPointCloud.order(ByteOrder.nativeOrder());
            FloatBuffer fbPointCloud = bfPointCloud.asFloatBuffer();
            fbPointCloud.position(0);

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_");
            String strDateTime = formatter.format(new Date(System.currentTimeMillis()));
            String strFileName = "/sdcard/Download/imidepth/" +
                                 strDateTime +
                                 Long.toString(System.currentTimeMillis());

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(strFileName + ".png");
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            if (null != fos)
            {
                // ARGB 字节缓冲区
                int[] iaARGB = new int[width * height];

                for (int idx = 0;
                     idx < iaARGB.length;
                     idx++)
                {
                    byte colorA = 0;
                    byte colorR = bbRGB88.get(3 * idx);
                    byte colorG = bbRGB88.get(3 * idx + 1);
                    byte colorB = bbRGB88.get(3 * idx + 2);
                    iaARGB[idx] = (colorA << 24) |
                                  (colorR << 16) |
                                  (colorG <<  8) |
                                  colorB;
                }

                Bitmap bmp = Bitmap.createBitmap(iaARGB,
                                                 width,
                                                 height,
                                                 Bitmap.Config.ARGB_8888);
                if (null != bmp)
                {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);

                    try {
                        fos.flush();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        fos.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    fos = null;

                    if (!bmp.isRecycled()) {
                        // 回收图片所占的内存
                        bmp.recycle();
                        bmp = null;

                        //提醒系统及时回收
                        System.gc();
                    }

                    Log.i("DepthCameraActivity",
                          strFileName + ".png was generated!");
                }
            }

            int iResult = mDevice.imiConvertDepthToPointCloud(nextFrame,
                                                              0,
                                                              0,
                                                              0,
                                                              0,
                                                              0,
                                                              fbPointCloud);
            Log.i("DepthCameraActivity",
                  "imiConvertDepthToPointCloud = " + Integer.toString(iResult));

            if (0 == iResult)
            {
                fbPointCloud.position(0);

                try {
                    fos = new FileOutputStream(strFileName + ".txt");
                }
                catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                if (null != fos)
                {
                    StringBuilder sbFloatInfo = new StringBuilder(Float.toString(fbPointCloud.get(0)));
                    sbFloatInfo.append("\n");

                    Log.i("DepthCameraActivity",
                          "fbPointCloud.capacity() = " + Integer.toString(fbPointCloud.capacity()));

                    for (int idx = 1;
                             idx < fbPointCloud.capacity();
                             idx++)
                    {
                        sbFloatInfo.append(Float.toString(fbPointCloud.get(idx)));
                        sbFloatInfo.append("\n");
                    }

                    Log.i("DepthCameraActivity",
                          "sbFloatInfo.toString().getBytes().length = " +
                          Integer.toString(sbFloatInfo.toString().getBytes().length));

                    try {
                        fos.write(sbFloatInfo.toString().getBytes());
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        fos.flush();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        fos.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    fos = null;

                    Log.i("DepthCameraActivity",
                          strFileName + ".txt was generated!");
                }

                try {
                    fos = new FileOutputStream(strFileName + ".bin");
                }
                catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                if (null != fos)
                {
                    bfPointCloud.position(0);

                    try {
                        Log.i("DepthCameraActivity",
                              "bfPointCloud.array().length = " + Integer.toString(bfPointCloud.array().length));

                        fos.write(bfPointCloud.array());
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        fos.flush();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        fos.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    fos = null;

                    Log.i("DepthCameraActivity",
                          strFileName + ".bin was generated!");
                }
            }

            mDevice.stopStream(ImiDevice.ImiStreamType.DEPTH.toNative());

            mMsgHandler.sendEmptyMessage(MSG_ENABLE_TAKE_A_PHOTO);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_depth_camera_view);

        mTVAttrs      = (TextView)findViewById(R.id.tv_show_attrs);
        mGLDepthPanel = (GLPanel)findViewById(R.id.sv_depth_view);

        Button btn_Exit = (Button)findViewById(R.id.button_Exit);
        btn_Exit.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                DepthCameraActivity.this.showMessageDialog("click exit button");
            }
        });

        Button btn_contrast = (Button)findViewById(R.id.button_contrast);
        btn_contrast.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DepthCameraActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

        mbtn_take_a_photo = (Button)findViewById(R.id.button_take_a_photo);
        mbtn_take_a_photo.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDevice.startStream(ImiDevice.ImiStreamType.DEPTH.toNative());

                new Thread(new ReadDepthFrameRunnable()).start();

                mbtn_take_a_photo.setEnabled(false);
            }
        });
        mbtn_take_a_photo.setEnabled(false);

        // 动态获取权限，Android 6.0+ 新特性，
        // 一些保护权限，除了要在AndroidManifest中声明权限，还要使用如下代码动态获取
        if (Build.VERSION.SDK_INT >= 23)
        {
            int REQUEST_CODE_CONTACT = 101;

            String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};

            // 验证是否许可权限
            for (String str : permissions)
            {
                if (this.checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED)
                {
                    // 申请权限
                    this.requestPermissions(permissions, REQUEST_CODE_CONTACT);
                    break;
                }
            }
        }

        new Thread(new OpenDeviceRunnable()).start();
    }
}
