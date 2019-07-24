package fr.pchab.androidrtc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.VideoCapturer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;

import cz.msebera.android.httpclient.Header;

import static android.content.ContentValues.TAG;
import static android.os.SystemClock.sleep;

public class RegistrationActivity extends Activity implements WebRtcClient.RtcListener {

    private WebRtcClient mWebRtcClientCamera;
    private WebRtcClient mWebRtcClientScreen;
    private WebRtcClient mClient;
    private double latitude=0.0,longitude =0.0;


    private static Intent mMediaProjectionPermissionResultData;
    private static int mMediaProjectionPermissionResultCode;
    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;

    public static String STREAM_NAME_PREFIX = "android_stream";

   // public static String STREAM_NAME_PREFIX = "android_camera_stream";
   // public static String STREAM_NAME_PREFIX = "android_camera_stream";
   // public static String STREAM_NAME_PREFIX = "android_camera_stream";

    public static int sDeviceWidth;
    public static int sDeviceHeight;
    public static final int SCREEN_RESOLUTION_SCALE = 2;

    private Button regButton;
    private EditText serverEditText;
    private Switch gpsSwitch,screenSwitch,frontSwitch,backSwitch;
    private Button punchButton;

    private LocationManager locationManager;
    private LocationListener locationListener;

    private boolean cameraOn;
    private boolean screenOn;
    private static Handler handler=new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, LongRunningService.class);

        startService(intent);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_reg);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        sDeviceWidth = metrics.widthPixels;
        sDeviceHeight = metrics.heightPixels;


        regButton = (Button) findViewById(R.id.button);
        serverEditText = (EditText) findViewById(R.id.edit_username);

        punchButton=(Button)findViewById(R.id.button2);
        gpsSwitch=(Switch)findViewById(R.id.GPSSwitch);

        punchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent iExp = new Intent(RegistrationActivity.this, FingerPrint.class); //TODO  Replace 'ActivityToCall' with the class name of the activity being called

                startActivity(iExp);
//                public static FingerprintManager getFingerprintManager(Context context) {
//                    FingerprintManager fingerprintManager = null;
//                    try {
//                        fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
//                    } catch (Throwable e) {
//                        L.v("have not class FingerprintManager");
//                    }
//                    return fingerprintManager;
//                }
            }
        });
        gpsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
             @Override
             public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                 // TODO Auto-generated method stub
                 if (isChecked) {
                     startGPSCapture();//打开GPS共享
                 } else {
                     endGPSCapture();// 关闭GPS共享
                 }
             }
        });
/*
        new Thread(new Runnable() {
                  @Override
                   public void run() {

                      while(true) {
                          Log.d("TTTTT", "executed at" + new Date().toString());

                          try {
                              URL url = new URL("http://10.150.139.20:5005/getdevsharing");
                              HttpURLConnection connection = (HttpURLConnection)
                                      url.openConnection();
                              connection.setRequestMethod("GET");
                              connection.connect();
                              int code = connection.getResponseCode();
                              Log.i("XXXXX", "code: " + code);
                          } catch (IOException e) {
                              e.printStackTrace();
                          }
                          sleep(1000);
                      }
                     }
               }).start();
*/

        regButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                final String serverAddr = serverEditText.getText().toString();

                if (serverAddr.isEmpty()) {
                    // TODO:
                    //return;
                }


                // TODO:
                // call register API
                JSONObject jsonParams = new JSONObject();
                try {
                    jsonParams.put("clientId", "andriod");
                    jsonParams.put("devId", "screen");
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


                // TODO:
                // WebRTC
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startScreenCapture();
                } else {
                    // shouldn't reach here
                    init();
                }
            }
        });

    }

    private void startGPSCapture()
            throws SecurityException{
    //获取定位服务
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            // Provider的状态在可用、暂时不可用和无服务三个状态直接切换时触发此函数
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            // Provider被enable时触发此函数，比如GPS被打开
            @Override
            public void onProviderEnabled(String provider) {
            }

            // Provider被disable时触发此函数，比如GPS被关闭
            @Override
            public void onProviderDisabled(String provider) {
            }

            //当坐标改变时触发此函数，如果Provider传进相同的坐标，它就不会被触发
            @Override
            public void onLocationChanged(Location location) {
                if (location != null) {
                    latitude=location.getLatitude();
                    longitude=location.getLongitude();
                    mClient.setLocation(latitude,longitude);
                    Log.e("Map", "Location changed : Lat: "
                            + location.getLatitude() + " Lng: "
                            + location.getLongitude());
                    mClient.sendGPS();
                }
            }
        };
        //获取当前可用的位置控制器
        List<String> providers = locationManager.getProviders(true);
        if(providers.size()==0){
            Toast.makeText(this, "请检查网络或GPS是否打开",
                    Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
            return;
        }
        Location bestLocation = null;
        String bestProvider="";
        for (String provider : providers) {
            Location l = locationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                // Found best last known location: %s", l);
                bestLocation = l;
                bestProvider=provider;
            }
        }
        if (bestLocation != null) {
            //获取当前位置，这里只用到了经纬度
            latitude=bestLocation.getLatitude();
            longitude=bestLocation.getLongitude();
        }
        //绑定定位事件，监听位置是否改变
        //第一个参数为控制器类型第二个参数为监听位置变化的时间间隔（单位：毫秒）
        //第三个参数为位置变化的间隔（单位：米）第四个参数为位置监听器
        locationManager.requestLocationUpdates(bestProvider, 2000, 2,locationListener);
        startScreenCapture();
//        mClient.sendGPS(latitude,longitude);
//        mClient=new WebRtcClient(getApplicationContext(),this);
    }
    private void endGPSCapture(){
        //关闭时解除监听器
            if (locationManager != null) {
                locationManager.removeUpdates(locationListener);
            }
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
        mClient=new WebRtcClient(getApplicationContext(),this,createScreenCapturer(),peerConnectionParameters);
        mClient.setLocation(latitude,longitude);
    }

    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer;
        videoCapturer = createCameraCapturer(new Camera1Enumerator(false));
        return videoCapturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // Trying to find a front facing camera!
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // We were not able to find a front cam. Look for other cameras
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }


   // @TargetApi(21)
    private VideoCapturer createScreenCapturer() {
        if (mMediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            report("User didn't give permission to capture the screen.");
            return null;
        }
        return new ScreenCapturerAndroid(
                mMediaProjectionPermissionResultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                report("User revoked permission to capture the screen.");
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
            return;
        mMediaProjectionPermissionResultCode = resultCode;
        mMediaProjectionPermissionResultData = data;
        init();
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
        mClient.start(STREAM_NAME_PREFIX);
//        mWebRtcClientCamera.start(STREAM_NAME_PREFIX);
//        mWebRtcClientScreen.start(STREAM_NAME_PREFIX);

    }

    @Override
    public void onCall(String applicant) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    @Override
    public void onHandup() {

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
}
