package com.yht.iptv.view.main;

import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.apkfuns.logutils.LogUtils;
import com.danikula.videocache.CacheListener;
import com.danikula.videocache.HttpProxyCacheServer;
import com.yht.iptv.MainFragment;
import com.yht.iptv.R;
import com.yht.iptv.callback.IDialogIPSetClick;
import com.yht.iptv.callback.IFragmentOnclickListenr;
import com.yht.iptv.callback.IPresenterBase;
import com.yht.iptv.callback.IPresenterDownloadBase;
import com.yht.iptv.model.AdvertInfo;
import com.yht.iptv.model.AdvertVideoInfo;
import com.yht.iptv.model.BaseModel;
import com.yht.iptv.model.EventNetwork;
import com.yht.iptv.model.EventRefreshMainInfo;
import com.yht.iptv.model.FoodCarInfo;
import com.yht.iptv.model.MainPageInfo;
import com.yht.iptv.presenter.AdRecordPresenter;
import com.yht.iptv.presenter.AdvertDownloadPresenter;
import com.yht.iptv.presenter.MainPagePresenter;
import com.yht.iptv.push.MinaClientService;
import com.yht.iptv.service.CheckNewVersionService;
import com.yht.iptv.service.TimerService;
import com.yht.iptv.service.WeatherService;
import com.yht.iptv.socket.MinaService;
import com.yht.iptv.tools.IP_SET_CustomDialog;
import com.yht.iptv.utils.AppUtils;
import com.yht.iptv.utils.Constants;
import com.yht.iptv.utils.DBUtils;
import com.yht.iptv.utils.DialogUtils;
import com.yht.iptv.utils.FileUtils;
import com.yht.iptv.utils.HttpConstants;
import com.yht.iptv.utils.LanguageUtils;
import com.yht.iptv.utils.OkHttpUtils;
import com.yht.iptv.utils.SPUtils;
import com.yht.iptv.utils.ServiceUtils;
import com.yht.iptv.utils.ShowImageUtils;
import com.yht.iptv.utils.ToastUtils;
import com.yht.iptv.view.BaseActivity;
import com.yht.iptv.view.BaseFragment;
import com.yht.iptv.view.MainActivity;
import com.yht.iptv.view.MyApplication;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

public class TexureViewActivity extends BaseActivity implements CacheListener, IFragmentOnclickListenr, IPresenterBase<List<MainPageInfo>>, MediaPlayer.OnInfoListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener, TextureView.SurfaceTextureListener {

    private TextureView mTextureView;
    private HttpProxyCacheServer proxy;
    private String room_id;
    private MainPagePresenter presenter;
    private final int MAINPAGE = 1;
    private final int DELAY = 2;
    private final int ADVERT_DELAY = 3;
    private MyHandler handler;
    private ImageView iv_bg;
    private String lastCacheUrl;
    private int currentPosition;
    private boolean isMainFragmentUI = false;//是否mainFragment处于前端展示
    private int advertPosition;
    private int playTimes = 0;
    private int currentAdvertId = -1;//广告ID
    private boolean isDownload = false;
    private boolean isNotNeedPlay = false;
    private TextView tv_adv;
    private MediaPlayer mediaPlayer;
    private int mediaPlayerState;
    private Surface mSurface;
    private boolean isFirst;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_texureview);

        DBUtils.delete(this, FoodCarInfo.class);
//        SPUtils.put(this, Constants.PAY_CHOOSE, Constants.ONLINE_PAY);
        mTextureView = (TextureView) findViewById(R.id.textureView);

        tv_adv = (TextView) findViewById(R.id.tv_adv);
        iv_bg = (ImageView) findViewById(R.id.iv_bg);            //设置背景
        proxy = MyApplication.getProxy(this);
        handler = new MyHandler(this);
        presenter = new MainPagePresenter(this, this);

        //获取视频url
        lastCacheUrl = (String) SPUtils.get(this, Constants.CACHE_VIDEO, "");

        if(savedInstanceState == null) {
            ShowImageUtils.showImageView(this, R.drawable.main_bg, iv_bg);

            if (getSupportFragmentManager().findFragmentByTag("guide") == null) {
                //添加GUIfragment
                GuideFragment guideFragment = new GuideFragment();
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.add(R.id.fragment_container, guideFragment, "guide");
                transaction.commit();
            }
            initApp();
        }else {
            if (Constants.currentLanguage.equals("zh")) {
                LanguageUtils.getInstance().changeLanguage("zh", this);
            } else {
                LanguageUtils.getInstance().changeLanguage("en", this);
            }
            currentPosition = savedInstanceState.getInt("currentPosition");
            advertPosition = savedInstanceState.getInt("advertPosition");
            mediaPlayerState = savedInstanceState.getInt("mediaPlayerState");
            isMainFragmentUI = savedInstanceState.getBoolean("isMainFragmentUI");
            isFirst = savedInstanceState.getBoolean("isFirst");
        }

        mTextureView.setSurfaceTextureListener(this);
        //设置16:9
        int screenWeight = AppUtils.getScreenWidth();
        ViewGroup.LayoutParams layoutParams = mTextureView.getLayoutParams();
        layoutParams.height = screenWeight * 9 / 16;
        mTextureView.setLayoutParams(layoutParams);

        //启动MINA客户端服务
        ServiceUtils.startService(MinaClientService.class);

        room_id = (String) SPUtils.get(this, Constants.ROOM_ID, "");
    }


    private void initApp() {
//        删除本地已经有的APK
        FileUtils.delfile(FileUtils.getDownLoadPath());

        String ipAddress = (String) SPUtils.get(this, Constants.IP_ADDRESS, "");
        String room_id = (String) SPUtils.get(this, Constants.ROOM_ID, "");

        //读取文件信息
        String files_room_id = FileUtils.readTxtFile(FileUtils.getInfoPath() + Constants.ROOM_INFO + ".info");
        String files_ipAddress = FileUtils.readTxtFile(FileUtils.getInfoPath() + Constants.IP_INFO + ".info");

        boolean isFilesEmp = files_room_id == null || files_room_id.equals("") || files_ipAddress == null || files_ipAddress.equals("");
        boolean isSpEmp = ipAddress == null || ipAddress.equals("") || room_id == null || room_id.equals("");

        if (isSpEmp && isFilesEmp) {//两者都为空
            showDialog();
        } else if (isSpEmp) {//文件不为空,sp为空
            HttpConstants.HTTP_HOST = "http://" + files_ipAddress + ":8080/";
            this.room_id = files_room_id;
            Constants.IP_VALUE = files_ipAddress;
            SPUtils.put(this, Constants.IP_ADDRESS, files_ipAddress);
            SPUtils.put(this, Constants.ROOM_ID, files_room_id);
            handler.removeMessages(MAINPAGE);
            handler.sendEmptyMessage(MAINPAGE);
        } else if (isFilesEmp) {
            //保存IP room信息
            FileUtils.saveTextFiles(room_id, Constants.ROOM_INFO + ".info");
            FileUtils.saveTextFiles(ipAddress, Constants.IP_INFO + ".info");
            HttpConstants.HTTP_HOST = "http://" + ipAddress + ":8080/";
            this.room_id = room_id;
            Constants.IP_VALUE = ipAddress;
            handler.removeMessages(MAINPAGE);
            handler.sendEmptyMessage(MAINPAGE);
        } else {//都不为空
            HttpConstants.HTTP_HOST = "http://" + ipAddress + ":8080/";
            this.room_id = room_id;
            Constants.IP_VALUE = ipAddress;
            handler.removeMessages(MAINPAGE);
            handler.sendEmptyMessage(MAINPAGE);
        }
    }

    private void showDialog() {
        IP_SET_CustomDialog dialog = new IP_SET_CustomDialog(this, new IDialogIPSetClick() {
            @Override
            public void onClick(IP_SET_CustomDialog dialog, String tag, String ip_address, String roomId) {
                SPUtils.put(TexureViewActivity.this, Constants.IP_ADDRESS, ip_address);
                SPUtils.put(TexureViewActivity.this, Constants.ROOM_ID, roomId);

                //保存IP room信息
                FileUtils.saveTextFiles(roomId, Constants.ROOM_INFO + ".info");
                FileUtils.saveTextFiles(ip_address, Constants.IP_INFO + ".info");

                DialogUtils.dismissDialog(dialog);
                HttpConstants.HTTP_HOST = "http://" + ip_address + ":8080/";
                Constants.IP_VALUE = ip_address;
                TexureViewActivity.this.room_id = roomId;
                handler.removeMessages(MAINPAGE);
                handler.sendEmptyMessage(MAINPAGE);

            }
        });
        DialogUtils.showDialog(dialog);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogUtils.tag("video").e("onStart");
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.tag("video").e("onResume");
        try {
            if(Constants.mainPageInfo.getAdSetting().getMainPage() == 1){
                if(isMainFragmentUI){
                    advertPosition = (int) (Math.random() * 100);
                    String advertPath = FileUtils.getAdvertPath() + Constants.key[advertPosition % Constants.key.length]+".mp4";
                    if(new File(advertPath).exists()) {
                        tv_adv.setVisibility(View.VISIBLE);
//                        mVideoView.setVideoPath(advertPath);
//                        mVideoView.start();
                        playAdv(advertPath);
                        playTimes = 0;
                        handler.removeMessages(ADVERT_DELAY);
                        handler.sendEmptyMessageDelayed(ADVERT_DELAY,1000);
                        if(!isDownload) {
                            //请求下一条广告
                            isDownload = true;
                            AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(advertPath, new MyAdvertListener());
                            presenter.request(this, 30, room_id);
                        }

                    }else{
                        advertPath = FileUtils.getAdvertPath() + Constants.key[(advertPosition+1) % Constants.key.length]+".mp4";
                        if(new File(advertPath).exists()) {
                            tv_adv.setVisibility(View.VISIBLE);
                            playAdv(advertPath);
//                            mVideoView.setVideoPath(advertPath);
//                            mVideoView.start();
                            playTimes = 0;
                            handler.removeMessages(ADVERT_DELAY);
                            handler.sendEmptyMessageDelayed(ADVERT_DELAY,1000);
                            //请求下一条广告
                            if(!isDownload) {
                                isDownload = true;
                                AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(advertPath, new MyAdvertListener());
                                presenter.request(this, 30, room_id);
                            }
                        }else{
                            tv_adv.setVisibility(View.GONE);
                            playVideo();
                            //请求下一条广告
                            if(!isDownload) {
                                isDownload = true;
                                AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(new MyAdvertListener());
                                presenter.request(this, 30, room_id);
                            }

                        }
                    }
                }else{
                    if (mediaPlayerState == 2) {
                        playVideo();
                        tv_adv.setVisibility(View.GONE);
                    }
                }
            }else{
                if (mediaPlayerState == 2) {
                    playVideo();
                    tv_adv.setVisibility(View.GONE);
                }
            }
        }catch (Exception e){
            if (mediaPlayerState == 2) {
                playVideo();
                tv_adv.setVisibility(View.GONE);
            }
        }
        if (Constants.mainPageInfo == null && !OkHttpUtils.isRunning(this)) {
            handler.removeMessages(MAINPAGE);
            handler.sendEmptyMessage(MAINPAGE);
            LogUtils.e("请求网络");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtils.tag("video").e("onPause");
        if (mediaPlayer != null) {
            currentPosition = mediaPlayer.getCurrentPosition();
        }
        stop();
        OkHttpUtils.cancel();
        proxy.unregisterCacheListener(this);
        handler.removeMessages(MAINPAGE);
        handler.removeMessages(ADVERT_DELAY);
        handler.removeMessages(DELAY);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogUtils.tag("video").e("onStop");
        EventBus.getDefault().unregister(this);
        try {
            if(Constants.mainPageInfo.getAdSetting().getMainPage() == 1) {
                if (isMainFragmentUI) {
                    if (currentAdvertId != -1) {
                        AdRecordPresenter adRecordPresenter = new AdRecordPresenter();
                        adRecordPresenter.request(this, currentAdvertId, 1,room_id,0, playTimes);
                        LogUtils.e(currentAdvertId+"请求完成");
                    }
                }
            }
        }catch (Exception e){

        }
        isDownload = false;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtils.tag("video").e("onDestroy");
        EventBus.getDefault().unregister(this);
        handler.removeMessages(MAINPAGE);
        //关闭服务开始socket服务
        Intent intent = new Intent(this, MinaService.class);
        stopService(intent);
        proxy.unregisterCacheListener(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        return super.onKeyDown(keyCode, event);
    }

    private long mKeyTime;
    private int upCount = 0;
    private int downCount = 0;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                return true;
            } else {
                List<Fragment> fragments = getSupportFragmentManager().getFragments();
                for (int i = 0; i < fragments.size(); i++) {
                    if (!(fragments.get(i) instanceof GuideFragment)) {
                        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                        transaction.remove(fragments.get(i));
                    }
                }
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
            if ((System.currentTimeMillis() - mKeyTime) < 1000) {
                upCount++;
            } else {
                upCount = 0;
                downCount = 0;
            }
            mKeyTime = System.currentTimeMillis();
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (upCount == 4) {
                if ((System.currentTimeMillis() - mKeyTime) < 1000) {
                    downCount++;
                    if (downCount == 5) {
                        downCount = 0;
                        showIpSetDialog();
                    }
                } else {
                    downCount = 0;
                    upCount = 0;
                }
            } else if (upCount == 7) {
                if ((System.currentTimeMillis() - mKeyTime) < 1000) {
                    downCount++;
                    if (downCount == 8) {
                        downCount = 0;
                        launchApp(Constants.packageSettings);
//                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
//                        startActivity(intent);
                    }
                } else {
                    downCount = 0;
                    upCount = 0;
                }
            }
            mKeyTime = System.currentTimeMillis();
        }

        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (int i = 0; i < fragments.size(); i++) {
            Fragment fragment = fragments.get(i);
            if (fragment != null) {
                if (fragment instanceof BaseFragment) {
                    if(((BaseFragment) fragment).dispatchKeyEvent(event)) {
                        return true;
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }


    @Override
    public void onCacheAvailable(File cacheFile, String url, int percentsAvailable) {
        Log.e("videocache", "cacheFile" + cacheFile.toString() + "\nurl" + url + "\npercentsAvailable" + percentsAvailable);
    }

    @Override
    public void onMainClick() {
        MainMenuDialogFragment fragment = new MainMenuDialogFragment();
        fragment.show(getSupportFragmentManager(), "MainMenuDialogFragment");
    }

    @Override
    public void onGuideClick() {
        Fragment guideFragment = getSupportFragmentManager().findFragmentByTag("guide");
        Fragment fragmentByTag = getSupportFragmentManager().findFragmentByTag("main");
        if (fragmentByTag == null) {
            FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
            tx.hide(guideFragment);
            MainFragment fragment = new MainFragment();
            tx.add(R.id.fragment_container, fragment, "main");
            tx.addToBackStack(null);
            tx.commit();
        }
        isMainFragmentUI = true;
        if (mediaPlayer != null) {
            currentPosition = mediaPlayer.getCurrentPosition();
        }
        try {
            if (Constants.mainPageInfo.getAdSetting().getMainPage() == 1) {
                //播放广告
                if (FileUtils.fileNumber(FileUtils.getAdvertPath()) != 0) {
                    //生成一个0-10
                    advertPosition = (int) (Math.random() * 10);
                    //播本地
                    String advertPath = FileUtils.getAdvertPath() + Constants.key[advertPosition % Constants.key.length] + ".mp4";
                    if (new File(advertPath).exists()) {
                        tv_adv.setVisibility(View.VISIBLE);
                        //获取数据库记录
                        AdvertInfo advertInfo = DBUtils.find(this, AdvertInfo.class, "id", "=", FileUtils.getAdvertPath() + Constants.key[advertPosition % Constants.key.length]);
                        if(advertInfo != null) {
                            currentAdvertId = advertInfo.getCurrentAdvId();
                        }
                        playAdv(advertPath);
//                        mVideoView.setVideoPath(advertPath);
//                        mVideoView.start();
                        //请求下一条广告
                        if(!isDownload) {
                            isDownload = true;
                            AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(advertPath, new MyAdvertListener());
                            presenter.request(this, 30, room_id);
                        }
                        playTimes = 0;
                        handler.removeMessages(ADVERT_DELAY);
                        handler.sendEmptyMessageDelayed(ADVERT_DELAY,1000);
                    } else {
                        advertPath = FileUtils.getAdvertPath() + Constants.key[(advertPosition + 1) % Constants.key.length] + ".mp4";
                        if (new File(advertPath).exists()) {
                            tv_adv.setVisibility(View.VISIBLE);
                            //获取数据库记录
                            AdvertInfo advertInfo = DBUtils.find(this, AdvertInfo.class, "id", "=", FileUtils.getAdvertPath() + Constants.key[advertPosition % Constants.key.length]);
                            if(advertInfo != null) {
                                currentAdvertId = advertInfo.getCurrentAdvId();
                            }
                            isMainFragmentUI = true;
//                            mVideoView.setVideoPath(advertPath);
//                            mVideoView.start();
                            playAdv(advertPath);
                            //请求下一条广告
                            if(!isDownload) {
                                isDownload = true;
                                AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(advertPath, new MyAdvertListener());
                                presenter.request(this, 30, room_id);
                            }
                            playTimes = 0;
                            handler.removeMessages(ADVERT_DELAY);
                            handler.sendEmptyMessageDelayed(ADVERT_DELAY,1000);
                        }else{
                            tv_adv.setVisibility(View.GONE);
                        }
                    }
                    Log.e("download_success", "最开始播放的信息:" + advertPath);
                }
            }else{
                tv_adv.setVisibility(View.GONE);
            }
        }catch (Exception e){

        }
    }

    /**
     * 先判断是否安装，已安装则启动目标应用程序，否则先安装
     *
     * @param packageName 目标应用安装后的包名
     * @author yanxi
     */
    private void launchApp(String packageName) {
        // 启动目标应用
        if (isInstallByread(packageName)) {
            // 获取目标应用安装包的Intent
            Intent intent = getPackageManager().getLaunchIntentForPackage(
                    packageName);
            startActivity(intent);
        } else {
            ToastUtils.showShort("找不到设置!");
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        try {
            super.startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            // fixes Google Maps bug: http://stackoverflow.com/a/20905954/2075875
            ToastUtils.showShort("系统无法调用电视直播!");
        }
    }

    /**
     * 判断是否安装目标应用
     *
     * @param packageName 目标应用安装后的包名
     * @return 是否已安装目标应用
     */
    private boolean isInstallByread(String packageName) {
        return new File("/data/data/" + packageName).exists();
    }


    //弹出对话框
    private void showIpSetDialog() {
        //获取IP地址和房间号
        String currentIp = (String) SPUtils.get(TexureViewActivity.this, Constants.IP_ADDRESS, "");
        String currentRoom = (String) SPUtils.get(this, Constants.ROOM_ID, "");
        IP_SET_CustomDialog dialog = new IP_SET_CustomDialog(this, currentIp, currentRoom, new IDialogIPSetClick() {
            @Override
            public void onClick(IP_SET_CustomDialog dialog, String tag, String ip_address, String roomId) {
                if (tag.equals(Constants.CONFIRM)) {
                    DialogUtils.dismissDialog(dialog);
                    SPUtils.put(TexureViewActivity.this, Constants.IP_ADDRESS, ip_address);
                    SPUtils.put(TexureViewActivity.this, Constants.ROOM_ID, roomId);
                    HttpConstants.HTTP_HOST = "http://" + ip_address + ":8080/";
                    Constants.IP_VALUE = ip_address;
                    TexureViewActivity.this.room_id = roomId;
                    //保存IP room信息
                    FileUtils.saveTextFiles(roomId, Constants.ROOM_INFO + ".info");
                    FileUtils.saveTextFiles(ip_address, Constants.IP_INFO + ".info");
                    handler.removeMessages(MAINPAGE);
                    handler.sendEmptyMessageDelayed(MAINPAGE, 0);
                }
            }
        });
        DialogUtils.showDialog(dialog);
    }


    @Override
    public void onSuccess(BaseModel<List<MainPageInfo>> dataList) {
        List<MainPageInfo> mainPageInfos = dataList.data;
        if (mainPageInfos != null && mainPageInfos.size() != 0 && mainPageInfos.get(0) != null) {
            LogUtils.e("成功请求网络检测服务信息");
            Constants.mainPageInfo = mainPageInfos.get(0);
            //通知GUIDEfragment更新
            MainPageInfo mainPageInfo = mainPageInfos.get(0);

            Constants.REAL_TIME = mainPageInfo.getSystemTime();

            EventBus.getDefault().post(mainPageInfo);
            //启动检查更新service
            ServiceUtils.startService(CheckNewVersionService.class);
            //启动服务更新天气
            ServiceUtils.startService(WeatherService.class);
            //启动时间服务
            ServiceUtils.startService(TimerService.class);
            //启动MINA客户端服务
            ServiceUtils.startService(MinaClientService.class);
            //启动MINA服务端
            ServiceUtils.startService(MinaService.class);
            //播放背景视频
            String startingVideo = mainPageInfo.getStartingVideo();
            if (lastCacheUrl != null && !lastCacheUrl.equals("")) {
                if (lastCacheUrl.equals(startingVideo)) {
                    Log.e("mVideoView","1请求网络成功"+mediaPlayer.isPlaying());
                    if (!mediaPlayer.isPlaying() && !isNotNeedPlay) {
                        //播放
                        String proxyUrl = proxy.getProxyUrl(lastCacheUrl);
                        proxy.registerCacheListener(this, lastCacheUrl);
                        play(proxyUrl);
                        LogUtils.tag("play").e("test2");

                    }
                } else {
                    //保存视频URL
                    SPUtils.put(this, Constants.CACHE_VIDEO, startingVideo);
                    lastCacheUrl = startingVideo;
                    Log.e("mVideoView","2请求网络成功"+mediaPlayer.isPlaying());
                    if (!mediaPlayer.isPlaying() && !isNotNeedPlay) {
                        //播放
                        String proxyUrl = proxy.getProxyUrl(lastCacheUrl);
                        proxy.registerCacheListener(this, lastCacheUrl);
                        play(proxyUrl);
                        LogUtils.tag("play").e("test3");
                    }
                }
            } else {
                Log.e("mVideoView","3请求网络成功"+mediaPlayer.isPlaying());
                //保存视频URL
                SPUtils.put(this, Constants.CACHE_VIDEO, startingVideo);
                lastCacheUrl = startingVideo;
                //播放
                String proxyUrl = proxy.getProxyUrl(lastCacheUrl);
                proxy.registerCacheListener(this, lastCacheUrl);
                play(proxyUrl);
                LogUtils.tag("play").e("test4");
            }
            //下载30秒视频
            String videoPath1 = FileUtils.getAdvertPath() + Constants.key[advertPosition % Constants.key.length]+".mp4";
            String videoPath2 = FileUtils.getAdvertPath() + Constants.key[(advertPosition+1) % Constants.key.length]+".mp4";
            if(!(new File(videoPath1).exists() || new File(videoPath2).exists())) {
                if(!isDownload) {
                    isDownload = true;
                    AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(new MyAdvertListener());
                    presenter.request(this, 30, room_id);
                }
            }

        } else {
            ToastUtils.showShort("主页信息有误");
        }
    }


    @Override
    public void onError() {
        handler.removeMessages(MAINPAGE);
        handler.sendEmptyMessageDelayed(MAINPAGE, 10 * 1000);
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        LogUtils.d(what + "---" + extra);
        if(what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            //第一帧已经开始渲染了
            iv_bg.setVisibility(View.GONE);
        }
        if (!isFirst) {
            isFirst = true;
            if (lastCacheUrl != null && !lastCacheUrl.equals("")) {
                String proxyUrl = proxy.getProxyUrl(lastCacheUrl);
                Log.e("cache", proxyUrl);
                play(proxyUrl);
            }
        }
        return false;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        iv_bg.setVisibility(View.VISIBLE);
        //设置背景
        ShowImageUtils.showImageView(this, R.drawable.main_bg, iv_bg);
        handler.removeMessages(DELAY);
        handler.sendEmptyMessageDelayed(DELAY,2000);
        /*if(isMainFragmentUI){
            if(Constants.mainPageInfo.getAdSetting().getMainPage() == 1) {
                //在线播
                String videoPath1 = FileUtils.getAdvertPath() + Constants.key[advertPosition % Constants.key.length] + ".mp4";
                String videoPath2 = FileUtils.getAdvertPath() + Constants.key[(advertPosition + 1) % Constants.key.length] + ".mp4";
                if (new File(videoPath1).exists()) {
                    mVideoView.setVideoPath(videoPath2);
                    mVideoView.start();
                    playTimes = 0;
                    handler.removeMessages(ADVERT_DELAY);
                    handler.sendEmptyMessageDelayed(ADVERT_DELAY, 1000);
                    if(!isDownload) {
                        isDownload = true;
                        AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(videoPath1, new MyAdvertListener());
                        presenter.request(this, 30, room_id);
                    }
                } else if (new File(videoPath2).exists()) {
                    mVideoView.setVideoPath(videoPath2);
                    mVideoView.start();
                    playTimes = 0;
                    handler.removeMessages(ADVERT_DELAY);
                    handler.sendEmptyMessageDelayed(ADVERT_DELAY, 1000);
                    if(!isDownload) {
                        isDownload = true;
                        AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(videoPath2, new MyAdvertListener());
                        presenter.request(this, 30, room_id);
                    }
                } else {
                    playVideo();
                    if(!isDownload) {
                        isDownload = true;
                        AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(new MyAdvertListener());
                        presenter.request(this, 30, room_id);
                    }
                }
            }else{
                iv_bg.setVisibility(View.VISIBLE);
                //设置背景
                ShowImageUtils.showImageView(this, R.drawable.main_bg, iv_bg);
                mVideoView.stopPlayback();
                handler.removeMessages(DELAY);
                handler.sendEmptyMessageDelayed(DELAY, 1000);
            }
        }else {
            iv_bg.setVisibility(View.VISIBLE);
            //设置背景
            ShowImageUtils.showImageView(this, R.drawable.main_bg, iv_bg);
            mVideoView.stopPlayback();
            handler.removeMessages(DELAY);
            handler.sendEmptyMessageDelayed(DELAY, 1000);
        }*/
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        try {
            if(Constants.mainPageInfo.getAdSetting().getMainPage() == 1){
                if(isMainFragmentUI){
                    //广告记录
                    if(currentAdvertId != -1) {
                        AdRecordPresenter adRecordPresenter = new AdRecordPresenter();
                        adRecordPresenter.request(this, currentAdvertId, 1,room_id,0, 30);
                        LogUtils.e(currentAdvertId+"请求完成");
                    }
                    advertPosition = advertPosition + 1;
                    String advertPath = FileUtils.getAdvertPath() + Constants.key[advertPosition % Constants.key.length]+".mp4";
                    if(new File(advertPath).exists()) {
                        tv_adv.setVisibility(View.VISIBLE);
                        AdvertInfo advertInfo = DBUtils.find(this, AdvertInfo.class, "id", "=", FileUtils.getAdvertPath() + Constants.key[advertPosition % Constants.key.length]);
                        if(advertInfo != null) {
                            currentAdvertId = advertInfo.getCurrentAdvId();
                        }
//                        mVideoView.setVideoPath(advertPath);
//                        mVideoView.start();
                        playAdv(advertPath);
                        playTimes = 0;
                        handler.removeMessages(ADVERT_DELAY);
                        handler.sendEmptyMessageDelayed(ADVERT_DELAY,1000);
                        //请求下一条广告
                        if(!isDownload) {
                            isDownload = true;
                            AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(advertPath, new MyAdvertListener());
                            presenter.request(this, 30, room_id);
                        }
                    }else{
                        advertPath = FileUtils.getAdvertPath() + Constants.key[(advertPosition+1) % Constants.key.length]+".mp4";
                        if(new File(advertPath).exists()) {
                            tv_adv.setVisibility(View.VISIBLE);
                            AdvertInfo advertInfo = DBUtils.find(this, AdvertInfo.class, "id", "=", FileUtils.getAdvertPath() + Constants.key[advertPosition % Constants.key.length]);
                            if(advertInfo != null) {
                                currentAdvertId = advertInfo.getCurrentAdvId();
                            }
//                            mVideoView.setVideoPath(advertPath);
//                            mVideoView.start();
                            playAdv(advertPath);
                            //请求下一条广告
                            if(!isDownload) {
                                isDownload = true;
                                AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(advertPath, new MyAdvertListener());
                                presenter.request(this, 30, room_id);
                            }
                            playTimes = 0;
                            handler.removeMessages(ADVERT_DELAY);
                            handler.sendEmptyMessageDelayed(ADVERT_DELAY,1000);
                        }else{
                            tv_adv.setVisibility(View.GONE);
                            currentPosition = 0;
                            playVideo();
                            //请求下一条广告
                            if(!isDownload) {
                                isDownload = true;
                                AdvertDownloadPresenter presenter = new AdvertDownloadPresenter(new MyAdvertListener());
                                presenter.request(this, 30, room_id);
                            }
                        }
                    }
                    Log.e("download_success","播放的地址信息:" + advertPath);
                }else {
                    currentPosition = 0;
                    playVideo();
                    tv_adv.setVisibility(View.GONE);
                }
            }else{
                currentPosition = 0;
                playVideo();
                tv_adv.setVisibility(View.GONE);
            }
        }catch (Exception e){
            currentPosition = 0;
            playVideo();
            tv_adv.setVisibility(View.GONE);
        }

    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        LogUtils.d("MP");
        mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                mp.setVolume(1.0f,1.0f);
                mp.start();
                mp.setLooping(true);
            }
        });

    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mSurface = new Surface(surface);
        initMediaPlayer();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if(mediaPlayer !=null && mediaPlayerState == 1) {
            mediaPlayerState = 2;
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private static class MyHandler extends Handler {
        private WeakReference<Activity> weakReference;

        MyHandler(Activity activity) {
            weakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            TexureViewActivity activity = (TexureViewActivity) weakReference.get();
            if (msg.what == activity.MAINPAGE) {
                activity.requestMainPage();
            }
            if (msg.what == activity.DELAY) {
                activity.playVideo();
            }
            if (msg.what == activity.ADVERT_DELAY) {
                activity.advertTime();
            }
        }
    }

    private void requestMainPage() {
        if(room_id == null){
            room_id = (String) SPUtils.get(this, Constants.ROOM_ID, "");
        }
        presenter.request(room_id, this);//启动mina客户端
    }

    private void playVideo() {
        if (lastCacheUrl != null && !lastCacheUrl.equals("")) {
            String proxyUrl = proxy.getProxyUrl(lastCacheUrl);
            Log.e("cache", proxyUrl);
            play(proxyUrl);
        } else {
            iv_bg.setVisibility(View.VISIBLE);
//            //设置背景
            ShowImageUtils.showImageView(this, R.drawable.main_bg, iv_bg);
        }
    }

    /**
     * 初始化播放器
     */
    public void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setSurface(mSurface);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnInfoListener(this);
            mediaPlayer.setOnErrorListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void stop() {
        if(mediaPlayer !=null && mediaPlayerState == 1) {
            mediaPlayerState = 2;
            mediaPlayer.stop();
            mediaPlayer.release();
        }
    }

    /**
     * 播放
     */
    public void play(String localUrl) {
        try {
            if(mediaPlayerState != 1){
                initMediaPlayer();
            }
            mediaPlayer.reset();
            mediaPlayer.setDataSource(localUrl);
            mediaPlayer.prepare();
            mediaPlayer.seekTo(currentPosition);
            mediaPlayer.start();
            mediaPlayerState = 1;
        } catch (Exception e) {
            e.printStackTrace();
            handler.removeMessages(DELAY);
            handler.sendEmptyMessageDelayed(DELAY,2000);
        }
    }

    /**
     * 播放
     */
    public void playAdv(String localUrl) {
        try {
            if(mediaPlayerState != 1){
                initMediaPlayer();
            }
            mediaPlayer.reset();
            mediaPlayer.setDataSource(localUrl);
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayerState = 1;
        } catch (IOException e) {
            e.printStackTrace();
            handler.removeMessages(DELAY);
            handler.sendEmptyMessageDelayed(DELAY,1000);
        }
    }

    private void advertTime(){
        playTimes++;
        if(playTimes >= 30){
            playTimes = 30;
        }
        handler.removeMessages(ADVERT_DELAY);
        handler.sendEmptyMessageDelayed(ADVERT_DELAY,1000);
    }


    /**
     * 获取NetWorkReceiver网络是否连接正常
     *
     * @param eventNetwork
     */
    @Subscribe
    public void onNetworkNormal(EventNetwork eventNetwork) {
        if (eventNetwork.isNetEnable()) {
            handler.removeMessages(MAINPAGE);
            if(room_id == null){
                room_id = (String) SPUtils.get(this, Constants.ROOM_ID, "");
            }
            presenter.request(room_id, this);
            LogUtils.d("NETok");
        }
    }

    /**
     * 获取GuideFragment网络更新
     */
    @Subscribe
    public void onRefreshUI(EventRefreshMainInfo refreshMainInfo) {
        handler.removeMessages(MAINPAGE);
        if(room_id == null){
            room_id = (String) SPUtils.get(this, Constants.ROOM_ID, "");
        }
        presenter.request(room_id, this);
    }

//    /**
//     * 接收Mina发送的连接成功消息
//     * @param status
//     */
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onMinaReciver(MinaStatus status){
//        if(status.isStatus()){
//            //连接成功
//            //发送房间号
//            String room_id = (String) SPUtils.get(this, Constants.ROOM_ID, "");
//            SessionManager.getInstance().WriteToSession("%mina_sessionCreated%:" + room_id);
//        }
//
//    }

    @Override
    public void finish() {
        /**
         * 记住不要执行此句 super.finish(); 因为这是父类已经实现了改方法
         * 设置该activity永不过期，即不执行onDestroy()

         */
        moveTaskToBack(true);
        LogUtils.e("finishmoveback");
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentPosition",currentPosition);
        outState.putInt("advertPosition",advertPosition);
        outState.putBoolean("isMainFragmentUI",isMainFragmentUI);
        outState.putInt("mediaPlayerState", mediaPlayerState);
        outState.putBoolean("isFirst", isFirst);
    }

    public void onMainDestroy(){
        try {
            tv_adv.setVisibility(View.GONE);
            if(isMainFragmentUI && Constants.mainPageInfo.getAdSetting().getMainPage() == 1) {
                isMainFragmentUI = false;
                //播放酒店视频
                playVideo();
                if (currentAdvertId != -1) {
                    AdRecordPresenter adRecordPresenter = new AdRecordPresenter();
                    adRecordPresenter.request(this, currentAdvertId, 1,room_id,0, playTimes);
                    LogUtils.e(currentAdvertId+"请求完成");
                    playTimes = 0;
                    handler.removeMessages(ADVERT_DELAY);
                }
            }else {
                if(!mediaPlayer.isPlaying()){
                    //播放酒店视频
                    playVideo();
                    if (currentAdvertId != -1) {
                        AdRecordPresenter adRecordPresenter = new AdRecordPresenter();
                        adRecordPresenter.request(this, currentAdvertId, 1,room_id,0, playTimes);
                        LogUtils.e(currentAdvertId+"请求完成");
                        playTimes = 0;
                        handler.removeMessages(ADVERT_DELAY);
                    }
                }
            }
        }catch (Exception e){

        }

    }


    private class MyAdvertListener implements IPresenterDownloadBase<List<AdvertVideoInfo>> {

        private int advertId = -1;//广告ID

        @Override
        public void onSuccess(BaseModel<List<AdvertVideoInfo>> dataList) {
            advertId = dataList.data.get(0).getId();
        }

        @Override
        public void onDownLoadSuccess(String fileName) {
            isDownload = false;
            //保存ID到数据库
            String id = FileUtils.getAdvertPath() + fileName;
            AdvertInfo advertInfo = DBUtils.find(TexureViewActivity.this, AdvertInfo.class, "id", "=", id);
            if(advertInfo == null){
                advertInfo = new AdvertInfo();
                advertInfo.setId(id);
                advertInfo.setCurrentAdvId(advertId);
                DBUtils.save(TexureViewActivity.this,advertInfo);
            }else{
                advertInfo.setCurrentAdvId(advertId);
                DBUtils.update(TexureViewActivity.this,advertInfo,"currentAdvId");
            }

        }

        @Override
        public void onError() {
            isDownload = false;
        }
    }

}
