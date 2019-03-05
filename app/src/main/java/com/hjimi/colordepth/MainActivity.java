package com.hjimi.colordepth;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
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

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity
{
    private SimpleViewer mColorViewer;
    private SimpleViewer mDepthViewer;

    private GLPanel     mGLColorPanel;
    private GLPanel     mGLDepthPanel;

    private TextView    mTVAttrs;

    private boolean bExiting = false;

    private ImiDevice          mDevice;
    private ImiDeviceAttribute mDeviceAttribute = null;

    private static final int MSG_DEVICE_OPEN_SUCCESS = 0;
    private static final int MSG_DEVICE_OPEN_FALIED  = 1;
    private static final int MSG_DEVICE_DISCONNECT   = 2;
    private static final int MSG_EXIT_APP            = 3;

    static class MyHandler extends Handler
    {
        WeakReference<MainActivity> mActivity;

        MyHandler(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg)
        {
            MainActivity mainActivity = mActivity.get();

            switch (msg.what)
            {
                case MSG_DEVICE_OPEN_SUCCESS:
                    mainActivity.runViewer();
                    break;

                case MSG_DEVICE_OPEN_FALIED:
                case MSG_DEVICE_DISCONNECT:
                    mainActivity.showMessageDialog((String)msg.obj);
                    break;

                case MSG_EXIT_APP:
                    mainActivity.Exit();
                    break;
            }
        }
    }

    private MyHandler mMsgHandler = new MyHandler(this);

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

                bExiting = true;

                new Thread(new ExitAppRunnable()).start();
            }
        });
        builder.show();
    }

    private class ExitAppRunnable implements Runnable
    {
        @Override
        public void run()
        {
            if (null != mDepthViewer) {
                mDepthViewer.toPause();
                mDepthViewer.toDestroy();
            }

            if (null != mColorViewer) {
                mColorViewer.toPause();
                mColorViewer.toDestroy();
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

    private void runViewer()
    {

        mTVAttrs.setText("Device SerialNumber : " + mDeviceAttribute.getSerialNumber());

        mColorViewer = new SimpleViewer(mDevice,
                                        ImiDevice.ImiStreamType.COLOR);
        mColorViewer.setGLPanel(mGLColorPanel);
        mColorViewer.toStart();

        mDepthViewer = new SimpleViewer(mDevice,
                                        ImiDevice.ImiStreamType.DEPTH);
        mDepthViewer.setGLPanel(mGLDepthPanel);
        mDepthViewer.toStart();
    }

    private void Exit()
    {
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
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
                Toast.makeText(MainActivity.this,
                               deviceInfo + " CONNECT",
                               Toast.LENGTH_LONG)
                     .show();
            }
            else {
                Toast.makeText(MainActivity.this,
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

            ImiFrameMode colorMode = new ImiFrameMode(ImiPixelFormat.IMI_PIXEL_FORMAT_IMAGE_RGB24, 640, 480, 30);
            mDevice.setFrameMode(ImiDevice.ImiStreamType.COLOR,
                                 colorMode);

            ImiFrameMode depthMode = new ImiFrameMode(ImiPixelFormat.IMI_PIXEL_FORMAT_DEP_16BIT, 640, 480, 30);
            mDevice.setFrameMode(ImiDevice.ImiStreamType.DEPTH,
                                 depthMode);

            // 设置数据流启用同步(仅对A200系列设备有效)
            mDevice.setFramesSync(true);
            // 开启深度图像配准 - 以生成配准深度图像，
            // 让深度图和彩色图重合在一起，即将深度图像的图像坐标系转换到彩色图像的图像坐标系下
            mDevice.setImageRegistration(true);

            mDevice.open(MainActivity.this,
                         ImiDevice.ImiStreamType.COLOR.toNative() |
                         ImiDevice.ImiStreamType.DEPTH.toNative(),
                         mainlistener);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_color_depth_view);

        mTVAttrs      = (TextView)findViewById(R.id.tv_show_attrs);
        mGLColorPanel = (GLPanel)findViewById(R.id.sv_color_view);
        mGLDepthPanel = (GLPanel)findViewById(R.id.sv_depth_view);

        Button btn_Exit = (Button)findViewById(R.id.button_Exit);
        btn_Exit.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(bExiting) {
                    //Log.d("@@@@@", "EXITING...");
                    return;
                }

                bExiting = true;

                MainActivity.this.showMessageDialog("click exit button");
            }
        });

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

    @Override
    protected void onResume() {
        super.onResume();

        if (null != mDepthViewer) {
            mDepthViewer.toResume();
        }

        if (null != mColorViewer) {
            mColorViewer.toResume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    public void onBackPressed() {
        //Log.d("@@@@@", "refuse back to exit");
        return;
    }

}
