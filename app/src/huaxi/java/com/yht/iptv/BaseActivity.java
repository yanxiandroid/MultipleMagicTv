package com.yht.iptv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.apkfuns.logutils.LogUtils;
import com.hisilicon.android.tvapi.HitvManager;
import com.hisilicon.android.tvapi.constant.EnumSourceIndex;
import com.tpv.tv.tvmanager.tpvtvinputmgr.TpvTvInputMgr;
import com.yht.iptv.push.MinaClientService;
import com.yht.iptv.utils.AppManagerUtils;
import com.yht.iptv.utils.AppUtils;
import com.yht.iptv.utils.Constants;
import com.yht.iptv.utils.DeviceUtils;
import com.yht.iptv.utils.ServiceUtils;

/**
 * Created by admin on 2017/10/11.
 */

public class BaseActivity extends AppCompatActivity {

    private String TAG = "BaseActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //隐藏标题栏
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        //设置不灭屛
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        //添加activity
        AppManagerUtils.getAppManager().addActivity(this);

        //判断client服务是否启动
        boolean serviceRunning = ServiceUtils.isServiceRunning("com.yht.iptv.minaClientService");
        if (!serviceRunning) {
            ServiceUtils.startService(MinaClientService.class);
        }


//        LogUtils.tag("DEVICES").e(Constants.DeviceInfo);

    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.tag(TAG).e("MagicTV-" + "V" + AppUtils.getAppVersionName() + "." + AppUtils.getAppVersionCode() + "_" + "release.apk");
//        if(Constants.DeviceInfo.equals(Constants.OTHER)) {
//            startTVFirsts(this);
//        }
    }

    public int getDimension(int id) {
        return (int) getResources().getDimension(id);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //移除activity
        AppManagerUtils.getAppManager().finishActivity(this);
    }


    public String getStrings(int id) {
        return getResources().getString(id);
    }

    private void startTVFirst(Context c) {
        int selectSource = HitvManager.getInstance().getSourceManager().getSelectSourceId();
        Log.i(TAG, "=========getSelectSourceId:" + selectSource);
        int curSource = TpvTvInputMgr.getInstance(c).getCurInputSource();//当前信号源
        Log.i(TAG, "=========getCurInputSource：" + curSource);
        if (selectSource != EnumSourceIndex.SOURCE_MEDIA || curSource != EnumSourceIndex.SOURCE_MEDIA) {
            curSource = EnumSourceIndex.SOURCE_MEDIA;
            Log.i(TAG, "========= i am change source to：" + curSource);
            HitvManager.getInstance().getSourceManager().selectSource(curSource, 0);

            Intent i = new Intent("com.tpv.xmic.SOURCECHANGE");
            i.setPackage("com.xmic.sourcechangeservice");
            i.putExtra("name", "MagicTV-" + "V" + AppUtils.getAppVersionName() + "." + AppUtils.getAppVersionCode() + "_" + "release.apk");
            i.putExtra("source", "E_INPUT_SOURCE_STORAGE");
            i.putExtra("event_type", 0);
            i.putExtra("isgotolauncher", "true");
            try {

                c.startService(i);
            } catch (Exception exception) {

                exception.printStackTrace();
            }
            LogUtils.tag(TAG).e("MagicTV-" + "V" + AppUtils.getAppVersionName() + "." + AppUtils.getAppVersionCode() + "_" + "release.apk");
            return;
        }
    }

    private void startTVFirsts(final Context context) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startTVFirst(context);
            }
        }, 500);
    }


}
