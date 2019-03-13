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

            ImiFrameMode colorMode = new ImiFrameMode(ImiPixelFormat.IMI_PIXEL_FORMAT_IMAGE_RGB24,
                                                      640,
                                                      480,
                                                      30);
            mDevice.setFrameMode(ImiDevice.ImiStreamType.COLOR,
                                 colorMode);

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

    public static byte[] getBmpFileHeader(final int width, final int height)
    {
        // bmp文件头
        byte[] baBmpFileHeader = new byte[14];

        // 2bytes - 说明文件的类型，该值必需是 0x42 0x4D ，也就是字符'BM'，否则表示根本不是BMP
        baBmpFileHeader[0]  = 'B';
        baBmpFileHeader[1]  = 'M';

        // 4bytes - 说明该位图文件的大小，用字节为单位(JAVA字节序是BIG-ENDIAN)
        final int iFileSize = 14 + 40 + (width * height * 3);
        for (int idx = 0;
                 idx < 4;
                 idx++)
        {
            baBmpFileHeader[2 + idx] = (byte)(iFileSize >>> (idx * 8));
        }

        // 2bytes - 保留，必须设置为0
        baBmpFileHeader[6]  = 0;
        baBmpFileHeader[7]  = 0;

        // 2bytes - 保留，必须设置为0
        baBmpFileHeader[8]  = 0;
        baBmpFileHeader[9]  = 0;

        // 4bytes - 说明从文件头开始到实际的图像数据之间的字节的偏移量
        baBmpFileHeader[10]  = 54;
        baBmpFileHeader[11]  = 0;
        baBmpFileHeader[12]  = 0;
        baBmpFileHeader[13]  = 0;

        return baBmpFileHeader;
    }

    public static byte[] getBmpInfoHeader(final int width, final int height)
    {
        // 位图信息头
        byte[] baBmpInfoHeader = new byte[40];

        // 4bytes - 说明 BitmapInfoHeader 结构所需要的字节数
        baBmpInfoHeader[0] = 40;
        baBmpInfoHeader[1] = 0;
        baBmpInfoHeader[2] = 0;
        baBmpInfoHeader[3] = 0;

        // 4bytes - 说明图像的宽度，以像素为单位
        for (int idx = 0;
                 idx < 4;
                 idx++)
        {
            baBmpInfoHeader[4 + idx] = (byte)(width >>> (idx * 8));
        }

        // 4bytes - 说明图像的高度，以像素为单位
        // 注：这个值除了用于描述图像的高度之外，它还有另一个用处，就是指明该图像是倒向的位图，还是正向的位图。
        // 如果该值是一个正数，说明图像是倒向的，即：数据的第一行其实是图像的最后一行。
        // 如果该值是一个负数，则说明图像是正向的。
        // 大多数的BMP文件都是倒向的位图，也就是时，高度值是一个正数。
        for (int idx = 0;
                 idx < 4;
                 idx++)
        {
            baBmpInfoHeader[8 + idx] = (byte)(height >>> (idx * 8));
        }

        // 2bytes - 表示bmp图片的平面属，显然显示器只有一个平面，所以恒等于1
        baBmpInfoHeader[12] = 1;
        baBmpInfoHeader[13] = 0;

        // 2bytes - 说明比特数/像素，其值为1、4、8、16、24、32
        baBmpInfoHeader[14] = 24;
        baBmpInfoHeader[15] = 0;

        // 4bytes - 说明图像数据压缩的类型，其中：
        // 0 - BI_RGB       - 没有压缩
        // 1 - BI_RLE8      - 每个像素8比特的RLE压缩编码，压缩格式由2字节组成(重复像素计数和颜色索引)；
        // 2 - BI_RLE4      - 每个像素4比特的RLE压缩编码，压缩格式由2字节组成
        // 3 - BI_BITFIELDS - 每个像素的比特由指定的掩码决定
        // 4 - BI_JPEG      - JPEG格式
        // 5 - BI_PNG       - PNG格式
        baBmpInfoHeader[16] = 0;
        baBmpInfoHeader[17] = 0;
        baBmpInfoHeader[18] = 0;
        baBmpInfoHeader[19] = 0;

        // 4bytes - 说明图像的大小，以字节为单位。当用BI_RGB格式时，可设置为0。
        baBmpInfoHeader[20] = 0;
        baBmpInfoHeader[21] = 0;
        baBmpInfoHeader[22] = 0;
        baBmpInfoHeader[23] = 0;

        // 4bytes - 说明水平分辨率，用像素/米表示。
        baBmpInfoHeader[24] = 0;
        baBmpInfoHeader[25] = 0;
        baBmpInfoHeader[26] = 0;
        baBmpInfoHeader[27] = 0;

        // 4bytes - 说明垂直分辨率，用像素/米表示。
        baBmpInfoHeader[28] = 0;
        baBmpInfoHeader[29] = 0;
        baBmpInfoHeader[30] = 0;
        baBmpInfoHeader[31] = 0;

        // 4bytes - 说明位图实际使用的彩色表中的颜色索引数（设为0的话，则说明使用所有调色板项）。
        baBmpInfoHeader[32] = 0;
        baBmpInfoHeader[33] = 0;
        baBmpInfoHeader[34] = 0;
        baBmpInfoHeader[35] = 0;

        // 4bytes - 说明对图像显示有重要影响的颜色索引的数目，如果是0，表示都重要。
        baBmpInfoHeader[36] = 0;
        baBmpInfoHeader[37] = 0;
        baBmpInfoHeader[38] = 0;
        baBmpInfoHeader[39] = 0;

        return baBmpInfoHeader;
    }

    public static void generate_200X200_bmp(String     strFileName,
                                            final  int iWidth,
                                            final  int iHeight,
                                            ByteBuffer bbRGB888)
    {
        Log.i("DepthCameraActivity", strFileName + " was inserting into generate_200X200_bmp!");

        // RGB888 字节缓冲区
        byte[] baPatchRGB24 = new byte[200 * 200 * 3]; // 中心框的大小，三字节代表RGB;

        final int rowStart = (iHeight - 200) / 2;
        final int colStart = (iWidth  - 200) / 2;

        int idxRGB = 0;

        // RGB24 字节缓冲区
        byte[] baRGB24 = new byte[iWidth * iHeight * 3];

        bbRGB888.position(0);

        for (int idx = 0;
                 idx < baRGB24.length;
                 idx++)
        {
            baRGB24[idx] = bbRGB888.get(idx);
        }

        for (int idxRow = 0;
                 idxRow < 200;
                 idxRow++)
        {
            int cutPixelPos = ((rowStart + idxRow) * iWidth) + colStart;

            for (int idxCol = 0;
                     idxCol < 200;
                     idxCol++)
            {
                cutPixelPos++;

                baPatchRGB24[idxRGB++] = baRGB24[(3 * cutPixelPos)];
                baPatchRGB24[idxRGB++] = baRGB24[(3 * cutPixelPos) + 1];
                baPatchRGB24[idxRGB++] = baRGB24[(3 * cutPixelPos) + 2];
            }
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(strFileName);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (null != fos)
        {
            try {
                fos.write(DepthCameraActivity.getBmpFileHeader(200, 200));
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            try {
                fos.write(DepthCameraActivity.getBmpInfoHeader(200, -200));
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            try {
                fos.write(baPatchRGB24);
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

            Log.i("DepthCameraActivity", strFileName + " was generated!");
        }

//        Bitmap bmp = Bitmap.createBitmap(iaARGB,
//                                         200,
//                                         200,
//                                         Bitmap.Config.ARGB_8888);
//        if (null != bmp) {
//            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
//            if (!bmp.isRecycled()) {
//                // 回收图片所占的内存
//                bmp.recycle();
//                bmp = null;
//
//                //提醒系统及时回收
//                System.gc();
//            }
//        }
    }

    public static void generate_bmp(String     strFileName,
                                    final  int iWidth,
                                    final  int iHeight,
                                    ByteBuffer bbRGB888)
    {
        Log.i("DepthCameraActivity", strFileName + " was inserting into generate_bmp!");

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(strFileName);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (null != fos)
        {
            try {
                fos.write(DepthCameraActivity.getBmpFileHeader(iWidth, iHeight));
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            try {
                fos.write(DepthCameraActivity.getBmpInfoHeader(iWidth, -iHeight));
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            // RGB24 字节缓冲区
            byte[] baRGB24 = new byte[iWidth * iHeight * 3];

            bbRGB888.position(0);

            for (int idx = 0;
                     idx < baRGB24.length;
                     idx++)
            {
                baRGB24[idx] = bbRGB888.get(idx);
            }

            try {
                fos.write(baRGB24);
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

            Log.i("DepthCameraActivity", strFileName + " was generated!");
        }
    }

    public static void generate_txt(String      strFileName,
                                    final  int  iWidth,
                                    final  int  iHeight,
                                    FloatBuffer fbPointCloud)
    {
        Log.i("DepthCameraActivity", strFileName + " was inserting into generate_txt!");

        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(strFileName);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (null != fos)
        {
            StringBuilder sbFloatDepth = new StringBuilder();

            fbPointCloud.position(0);

            int idxPos = 0;
            for (int idxHeight = 0;
                     idxHeight < iHeight;
                     idxHeight++)
            {
                for (int idxWidth = 0;
                         idxWidth < iWidth;
                         idxWidth++)
                {
                    sbFloatDepth.append(Float.toString(fbPointCloud.get(idxPos++)));
                    sbFloatDepth.append(" ");
                    sbFloatDepth.append(Float.toString(fbPointCloud.get(idxPos++)));
                    sbFloatDepth.append(" ");
                    sbFloatDepth.append(Float.toString(fbPointCloud.get(idxPos++)));
                    sbFloatDepth.append(" ");
                }

                sbFloatDepth.append("\n");
            }

            try {
                fos.write(sbFloatDepth.toString().getBytes());
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

            Log.i("DepthCameraActivity", strFileName + " was generated!");
        }
    }

    public static void generate_bin(String      strFileName,
                                    final  int  iWidth,
                                    final  int  iHeight,
                                    ByteBuffer  bfPointCloud)
    {
        Log.i("DepthCameraActivity", strFileName + " was inserting into generate_bin!");

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(strFileName);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (null != fos)
        {
            byte[] baDepthFloat = new byte[iWidth * iHeight * 3 * 4];

            bfPointCloud.position(0);

            for (int idx = 0;
                     idx < baDepthFloat.length;
                     idx++)
            {
                baDepthFloat[idx] = bfPointCloud.get(idx);
            }

            try {
                fos.write(baDepthFloat);
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

            Log.i("DepthCameraActivity", strFileName + " was generated!");
        }
    }

    private class ReadDepthFrameRunnable implements Runnable
    {
        @Override
        public void run()
        {
            Log.i("DepthCameraActivity", "Prepare to get Depth image...");
            ImiDevice.ImiFrame nextDepthFrame = null;
            while (null == nextDepthFrame)
            {
                nextDepthFrame = mDevice.readNextFrame(ImiDevice.ImiStreamType.DEPTH,
                                                       25); // 1秒30帧，每帧33毫秒

                if (null == nextDepthFrame)
                {
                    try {
                        Thread.sleep(1);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            Log.i("DepthCameraActivity", "have got Depth image!");

            Log.i("DepthCameraActivity", "Prepare to get Color image...");
            ImiDevice.ImiFrame nextColorFrame = null;
            while (null == nextColorFrame)
            {
                nextColorFrame = mDevice.readNextFrame(ImiDevice.ImiStreamType.COLOR,
                                                       25); // 1秒30帧，每帧33毫秒

                if (null == nextColorFrame)
                {
                    try {
                        Thread.sleep(1);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            Log.i("DepthCameraActivity", "have got Color image!");

            ByteBuffer bbRGB888 = Utils.depth2RGB888(nextDepthFrame, true, false);

            mGLDepthPanel.paint(null,
                                bbRGB888,
                                nextDepthFrame.getWidth(),
                                nextDepthFrame.getHeight());

            final int width  = nextDepthFrame.getWidth();
            final int height = nextDepthFrame.getHeight();

            ByteBuffer bfPointCloud = ByteBuffer.allocateDirect(width * height * 3 * 4);
            bfPointCloud.order(ByteOrder.nativeOrder());
            FloatBuffer fbPointCloud = bfPointCloud.asFloatBuffer();

            int iResult = mDevice.imiConvertDepthToPointCloud(nextDepthFrame,
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
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_");
                String strDateTime = formatter.format(new Date(System.currentTimeMillis()));
                String strFileName = "/sdcard/Download/imidepth/" +
                                     strDateTime +
                                     Long.toString(System.currentTimeMillis());

                generate_200X200_bmp(strFileName + "_200X200.bmp",
                                     width,
                                     height,
                                     bbRGB888);

                generate_bmp(strFileName + "_depth.bmp",
                             width,
                             height,
                             bbRGB888);

                generate_bmp(strFileName + "_color.bmp",
                             width,
                             height,
                             nextColorFrame.getData());

                generate_txt(strFileName + "_depth_float.txt",
                             width,
                             height,
                             fbPointCloud);

                generate_bin(strFileName + "_depth_float.bin",
                             width,
                             height,
                             bfPointCloud);
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
                mDevice.startStream(ImiDevice.ImiStreamType.COLOR.toNative() |
                                    ImiDevice.ImiStreamType.DEPTH.toNative());

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
