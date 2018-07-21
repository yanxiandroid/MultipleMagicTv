package com.yht.iptv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;

import com.apkfuns.logutils.LogUtils;
import com.yht.iptv.utils.Constants;
import com.yht.iptv.utils.DeviceUtils;
import com.yht.iptv.view.MainActivity;
import com.yht.iptv.view.main.TexureViewActivity;

import java.lang.ref.WeakReference;

/**
 * Created by admin on 2017/11/21.
 */

public class WelcomeActivity extends AppCompatActivity {

    private MyHandler handler;


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new MyHandler(this);
//        String manufacturer = DeviceUtils.getManufacturer();
//        if (manufacturer.contains("MStar")) {
//            Constants.DeviceInfo = Constants.MSTAR_TV;
//        } else if (manufacturer.contains("PHILIPS")) {
//            Constants.DeviceInfo = Constants.PHILIPS;
//        } else {
//            Constants.DeviceInfo = Constants.OTHER;
//        }
//        LogUtils.tag("devices").e(Constants.DeviceInfo);
        handler.sendEmptyMessage(0);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        handler = null;
    }

    private static class MyHandler extends Handler {

        private WeakReference<WelcomeActivity> activity;

        public MyHandler(WelcomeActivity activity) {
            this.activity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            WelcomeActivity welcomeFragment = this.activity.get();
            switch (msg.what) {
                case 0:
                    welcomeFragment.startActivity();
            }
        }
    }

    private void startActivity() {
        Intent intent;
//        switch (Constants.DeviceInfo){
//            case Constants.PHILIPS:
//                intent = new Intent(this,TexureViewActivity.class);
//                startActivity(intent);
//                finish();
//                break;
//            case Constants.MSTAR_TV:
//                intent = new Intent(this,MainActivity.class);
//                startActivity(intent);
//                finish();
//                break;
//            default:
                intent = new Intent(this,TexureViewActivity.class);
                startActivity(intent);
                finish();
//                break;
//        }
    }

}
