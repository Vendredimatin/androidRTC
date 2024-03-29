package fr.pchab.androidrtc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.VideoCapturer;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import cz.msebera.android.httpclient.Header;


public class FingerPrintActivity extends Activity implements WebRtcClient.RtcListener {
    private static final String TAG = "FingerPrint";
    private Button bgn;
    //    private Button check;
    private TextView checkRes,back;
    private FingerprintManagerCompat manager;

    private WebRtcClient mClient;
    private String screen="screen";

    private Intent mMediaProjectionPermissionResultData;
    private int mMediaProjectionPermissionResultCode;
    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;
    private boolean state;

    public JSONObject streamInfo=new JSONObject();
    private static String mac;
    static{
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    break;
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:", b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                mac=res1.toString();
                System.out.println(mac);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private int sDeviceWidth;
    private int sDeviceHeight;
    public static final int SCREEN_RESOLUTION_SCALE = 2;

    private VideoCapturer frontCapturer,backCapturer,screenCapturer;


//    private static Handler handler=new Handler();
    private void initConnection(String id,String code,String name){
        try{
            streamInfo.put("id",id);
            streamInfo.put("code",code);
            streamInfo.put("name",name);
            streamInfo.put("mac",mac);
        }catch (Exception e){e.printStackTrace();}

        // call register API
        JSONObject jsonParams = new JSONObject();
        try {
            jsonParams.put("clientId", "andriod");
            jsonParams.put("devId", screen);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RestClient.post(getApplicationContext(), "register", jsonParams, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                report("RRRRRR");
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable error, JSONObject response) {
                Log.i("HTTP", "onFailure: " + response);
            }
        });


        // WebRTC
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startScreenCapture();
        } else {
            // shouldn't reach here
            init();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        state=false;
        super.onCreate(savedInstanceState);
        startService(new Intent(this,LongRunningService.class));

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
//                WindowManager.LayoutParams.FLAG_FULLSCREEN
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_punch);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        sDeviceWidth = metrics.widthPixels;
        sDeviceHeight = metrics.heightPixels;


        back=(TextView)findViewById(R.id.punch_left_title);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });


        checkRes=(TextView)findViewById(R.id.checkRes);
        bgn=(Button)findViewById(R.id.bgn_button);
        bgn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!state){
                    checkRes.setText("请将手指放置到指纹识别处进行打卡");
                    manager.authenticate(null,0,null,new MyCallBack(),null);
                }
            }
        });
        manager=FingerprintManagerCompat.from(this);

        Intent intent=getIntent();
        initConnection(intent.getStringExtra("roomid"),intent.getStringExtra("roomcode"),intent.getStringExtra("username"));
    }

    private void startScreenCapture() {
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getApplication().getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
    }


    private void init() {
        PeerConnectionClient.PeerConnectionParameters peerConnectionParameters =
                new PeerConnectionClient.PeerConnectionParameters(true, false,
                        true, sDeviceWidth / SCREEN_RESOLUTION_SCALE, sDeviceHeight / SCREEN_RESOLUTION_SCALE, 0,
                        0, "VP8",
                        false,
                        true,
                        0,
                        "OPUS", false, false, false, false, false, false, false, false, null);
//        mWebRtcClient = new WebRtcClient(getApplicationContext(), this, pipRenderer, fullscreenRenderer, createScreenCapturer(), peerConnectionParameters);
//        mWebRtcClientCamera = new WebRtcClient(getApplicationContext(), this, createVideoCapturer(), peerConnectionParameters);
//        mWebRtcClientScreen = new WebRtcClient(getApplicationContext(), this, createScreenCapturer(), peerConnectionParameters);
//        curCapturer=createVideoCapturer();
        initScreenCapturer();
        initCameraCapturer(new Camera1Enumerator(false));
        VideoCapturer[] l={screenCapturer,frontCapturer,backCapturer};
        mClient=new WebRtcClient(getApplicationContext(),this,l,peerConnectionParameters);

    }
    private void initCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        // Trying to find a front facing camera!
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    frontCapturer=videoCapturer;break;
                }
            }
        }
        // We were not able to find a front cam. Look for other cameras
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    backCapturer=videoCapturer;return;
                }
            }
        }

    }

    // @TargetApi(21)
    private void initScreenCapturer() {
        if (mMediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            report("User didn't give permission to capture the screen.");
            return;
        }
        screenCapturer= new ScreenCapturerAndroid(
                mMediaProjectionPermissionResultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                report("User revoked permission to capture the screen.");
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode){
            case CAPTURE_PERMISSION_REQUEST_CODE:
                mMediaProjectionPermissionResultCode = resultCode;
                mMediaProjectionPermissionResultData = data;
                init();
                break;
            default:return;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    public void report(String info) {
        Log.e(TAG, info);
    }


    @Override
    public void onReady(String remoteId) {

        mClient.start(streamInfo);

    }

    @Override
    public void onCall(String applicant) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //null
            }
        });
    }

    @Override
    public void onHandup() {
        //null
    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }
    public class MyCallBack extends FingerprintManagerCompat.AuthenticationCallback{
        private static final  String TAG="MyCallBack";


        // 当指纹验证失败的时候会回调此函数，失败之后允许多次尝试，失败次数过多会停止响应一段时间然后再停止sensor的工作
        @Override
        public void onAuthenticationFailed() {
            checkRes.setText("验证失败,请再次点击按钮尝试验证");
            Log.d(TAG, "onAuthenticationFailed: " + "验证失败");
        }

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            Log.d(TAG, "onAuthenticationHelp: " + helpString);
        }

        // 当验证的指纹成功时会回调此函数，然后不再监听指纹sensor
        @Override
        public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult
                                                      result) {
            checkRes.setText("已验证成功");
            state=true;
            Toast.makeText(getApplicationContext(), "succeed to punch in", Toast.LENGTH_SHORT).show();
            mClient.sendPunch();
            Log.d(TAG, "onAuthenticationSucceeded: " + "验证成功");
        }
    }
}
