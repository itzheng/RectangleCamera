package org.itzheng.demo.rectanglecamera;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    public static final String EXTRA_STR_FILE_PATH = "EXTRA_STR_FILE_PATH";
    LinearLayout llFocus;
    Rect mRect = new Rect();
    SensorController sensorControler;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera mCamera;
    private Camera.Parameters mParams;
    private DisplayMetrics dm = new DisplayMetrics();
    private boolean mIsStop;
    private Handler mHandler = new Handler();

    private static void saveImage(String path, Bitmap bitmap) {

        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if ((bitmap != null) && (!bitmap.isRecycled())) {
            bitmap.recycle();
        }
    }

    /**
     * @param currentActivity
     * @param sizes           最理想的预览分辨率的宽和高
     * @param targetRatio
     * @return 获得最理想的预览尺寸
     */
    public static Camera.Size getOptimalPreviewSize(Activity currentActivity,
                                                    List<Camera.Size> sizes, double targetRatio) {
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.001;
        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of mSurfaceView. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size

        Display display = currentActivity.getWindowManager()
                .getDefaultDisplay();
        int targetHeight = Math.min(display.getHeight(), display.getWidth());

        if (targetHeight <= 0) {
            // We don't know the size of SurfaceView, use screen height
            targetHeight = display.getHeight();
        }

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSize == null) {
            System.out.println("No preview size match the aspect ratio");
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        //设置全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        sensorControler = SensorController.getInstance();
        initView();
        sensorControler.setCameraFocusListener(new SensorController.CameraFocusListener() {
            @Override
            public void onFocus() {
                Log.d(TAG, "onFocus");
                autoFocus();
            }
        });

    }

    private void initView() {
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        surfaceView = (SurfaceView) this.findViewById(R.id.surfaceview_camera);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.addCallback(new SurfaceHolderCallBack());
        llFocus = (LinearLayout) this.findViewById(R.id.llFocus);
        llFocus.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                autoFocus();
                return false;
            }
        });
    }

    /**
     * 点击拍照
     *
     * @param v
     */
    public void imgCamera(View v) {
        try {
            mIsStop = true;
            //将正方形的大小映射到mRect上,为了截取大小
            llFocus.getGlobalVisibleRect(mRect);
            mCamera.takePicture(null, null, null, new CropPictureCallback());
        } catch (Throwable t) {
            t.printStackTrace();
            try {
                mCamera.startPreview();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

    }

    public Activity getActivity() {
        return this;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsStop = true;
        sensorControler.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsStop = false;
        sensorControler.onStart();
    }

    private Camera.Size getOptimalPictureSize(List<Camera.Size> pictureSizes) {
        Camera.Size pictureSize = null;
        for (int i = 0; i < pictureSizes.size(); i++) {
            pictureSize = pictureSizes.get(i);
            if (pictureSize.width == dm.widthPixels && pictureSize.height == dm.heightPixels) {
                return pictureSize;
            }
        }

        for (int i = 0; i < pictureSizes.size(); i++) {
            pictureSize = pictureSizes.get(i);
            if (pictureSize.width > dm.widthPixels && pictureSize.height > dm.heightPixels) {
                return pictureSize;
            }
        }
        return null;
    }

    /**
     * 按正方形裁切图片
     */
    public void imageCrop(String filePath, Bitmap bitmap, Rect focusRect) {
        Log.d(TAG, "imageCrop heightPixels=" + dm.heightPixels + " widthPixels=" + dm.widthPixels);
        Log.d(TAG, "imageCrop bitmap w=" + bitmap.getWidth() + " h=" + bitmap.getHeight());
        Log.d(TAG, "imageCrop focusRect left=" + focusRect.left + " top=" + focusRect.top);
        // 下面这句是关键
        float wScale = (float) bitmap.getWidth() / dm.heightPixels;
        float hScale = (float) bitmap.getHeight() / dm.widthPixels;
        Log.d(TAG, "imageCrop wScale=" + wScale + " hScale=" + hScale);
        int x = (int) (focusRect.left * wScale);
        int y = (int) (focusRect.top * hScale);
        Log.d(TAG, "imageCrop x=" + x + " y=" + y);
        int width = focusRect.width();
        int height = focusRect.height();
        Log.d(TAG, "imageCrop width=" + width + " height=" + height);
        // Camera.Size size = mCamera.getParameters().getPreviewSize();
        Bitmap bitmapTemp = Bitmap.createBitmap(bitmap, focusRect.left, focusRect.top, width, height);
        //saveImage(filePath, toGrayscale(bitmapTemp));
        saveImage(filePath, bitmapTemp);
    }

    /**
     * @return ${return_type} 返回类型
     * @throws
     * @Title: 关闭相机
     * @Description: 释放相机资源
     */
    public Camera closeCamera(Camera camera) {
        try {
            if (camera != null) {
                mParams = null;
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
                camera = null;
            }
        } catch (Exception e) {
            Log.i("TAG", e.getMessage());
        }
        return camera;
    }

    /**
     * 打开闪光灯
     */
    public void openLight() {
        if (mCamera != null) {
            Camera.Parameters parameter = mCamera.getParameters();
            parameter.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            mCamera.setParameters(parameter);
        }
    }

    /**
     * 关闭闪光灯
     */
    public void offLight() {
        if (mCamera != null) {
            Camera.Parameters parameter = mCamera.getParameters();
            parameter.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(parameter);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCamera(mCamera);
    }

    public void autoFocus() {
        if (mCamera != null) {
            try {
                if (mCamera.getParameters().getSupportedFocusModes() != null && mCamera.getParameters()
                        .getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        public void onAutoFocus(boolean success, Camera camera) {
                            mIsStop = success;
                        }
                    });
                } else {
//                    Log.e(TAG, getString(R.string.unsupport_auto_focus));
                }
            } catch (Exception e) {
                e.printStackTrace();
                mCamera.stopPreview();
                mCamera.startPreview();
//                Log.e(TAG, getString(R.string.toast_autofocus_failure));
            }
        }
    }

    /**
     * 拍照完成的回调
     */
    private final class CropPictureCallback implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            new SavePicTask(data).execute();
            try {
                mIsStop = false;
                camera.startPreview(); // 拍完照后，重新开始预览
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private class SavePicTask extends AsyncTask<Void, Void, String> {
        long currentTime = 0;
        private byte[] data;

        SavePicTask(byte[] data) {
            this.data = data;
        }

        protected void onPreExecute() {
            // showProgressDialog("处理中");
        }

        @Override
        protected String doInBackground(Void... params) {
            Bitmap bitmap = null;
            try {
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
            }
            String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/myPhoto.jpg";
            if (bitmap != null) {
                if (bitmap.getWidth() > dm.widthPixels && bitmap.getHeight() > dm.heightPixels) {
                    //获取宽高比
                    Matrix matrix = new Matrix();
                    int width = bitmap.getWidth();//获取资源位图的宽
                    int height = bitmap.getHeight();//获取资源位图的高
                    int newWidth = dm.widthPixels;
                    int newHeight = dm.heightPixels;

                    float scaleW = (float) newWidth / (float) width;
                    float scaleH = (float) newHeight / (float) height;
                    if (scaleW < 1 && scaleH < 1) {//需要裁剪才裁剪
                        //避免图片变形,缩放比例保持一致
                        if (scaleW > scaleH) {
                            matrix.postScale(scaleW, scaleW);//获取缩放比例
                        } else {
                            matrix.postScale(scaleH, scaleH);//获取缩放比例
                        }
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                                width, height, matrix, true); //根据缩放比例获取新的位图
                    }
                    //
                    int left = (bitmap.getWidth() - newWidth) / 2;
                    int top = (bitmap.getHeight() - newHeight) / 2;
                    bitmap = Bitmap.createBitmap(bitmap, 0, top, newWidth, newHeight);
                    Log.d(TAG, "doInBackground DisplayMetrics" + "newWidth " + newWidth + " newHeight" + newHeight);
                }
                imageCrop(filePath, bitmap, mRect);
                Intent intent = new Intent();
                intent.putExtra(EXTRA_STR_FILE_PATH, filePath);
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
            return filePath;
        }

        @Override
        protected void onPostExecute(final String result) {
            super.onPostExecute(result);

        }
    }

    public class SurfaceHolderCallBack implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            try {
                if (null == mCamera) {
                    mCamera = Camera.open();
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!mIsStop) {
                            autoFocus();
                            mHandler.postDelayed(this, 2500);
                        }

                    }
                }, 1000);
            } catch (Exception e) {
                Toast.makeText(App.getInstance(), "暂未获取到拍照权限", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }


        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
            if (mCamera != null) {
                mParams = mCamera.getParameters();
                mParams.setPictureFormat(PixelFormat.JPEG);//设置拍照后存储的图片格式
                //设置PreviewSize和PictureSize
                List<Camera.Size> pictureSizes = mParams.getSupportedPictureSizes();
                Camera.Size size = getOptimalPictureSize(pictureSizes);
                if (size == null) {
                    Toast.makeText(getApplication(), "相机出错,请尝试换一台手机!", Toast.LENGTH_SHORT).show();
                } else {
                    System.out.println("surfaceChanged picture size width=" + size.width + " height=" + size.height);
                    mParams.setPictureSize(size.width, size.height);
                }
                if (mParams.getSupportedFocusModes().contains(
                        mParams.FOCUS_MODE_AUTO)) {
                    mParams.setFocusMode(mParams.FOCUS_MODE_AUTO);
                }
                Log.d("surfaceChanged", "widthPixels=" + dm.widthPixels + " heightPixels=" + dm.heightPixels);
                Camera.Size optimalPreviewSize = getOptimalPreviewSize(getActivity(),
                        mParams.getSupportedPreviewSizes(),
                        (float) dm.widthPixels / dm.heightPixels);
                mParams.setPreviewSize(optimalPreviewSize.width,
                        optimalPreviewSize.height);
                try {
                    mCamera.setPreviewDisplay(surfaceHolder);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                mCamera.setParameters(mParams);
                try {
//                mCamera.setParameters(mParams);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mCamera.startPreview();
                Log.d(TAG, "mParams heightPixels=" + mParams.getPictureSize().height + " widthPixels=" + mParams.getPictureSize().width);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        }
    }
}
