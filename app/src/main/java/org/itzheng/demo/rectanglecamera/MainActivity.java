package org.itzheng.demo.rectanglecamera;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 0x0001;
    private ImageView imgMyPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imgMyPhoto = (ImageView) findViewById(R.id.imgMyPhoto);
        setTitle("this is title");
    }

    public void takePic(View view) {
        startActivityForResult(new Intent(this, CameraActivity.class), REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (REQUEST_CODE == requestCode && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                Log.e(TAG, "onActivityResult: data == null");
            } else {
                String imgPath = data.getStringExtra(CameraActivity.EXTRA_STR_FILE_PATH);
                if (TextUtils.isEmpty(imgPath)) {
                    Log.e(TAG, "onActivityResult: imgPath is Empty");
                } else
                    imgMyPhoto.setImageBitmap(getLoacalBitmap(imgPath));
            }
        } else {
            Log.e(TAG, "onActivityResult: request fail");
        }
    }


    /**
     * 加载本地图片
     *
     * @param url
     * @return
     */
    public static Bitmap getLoacalBitmap(String url) {
        try {
            FileInputStream fis = new FileInputStream(url);
            return BitmapFactory.decodeStream(fis);  ///把流转化为Bitmap图片
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}
