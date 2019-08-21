package fr.pchab.androidrtc;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toolbar;

public class FingerPrintActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "FingerPrint";
//    private Button check;
    private TextView checkRes,back;
    private Toolbar tbar;
    private FingerprintManagerCompat manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        setResult(0);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_punch);
//        check=(Button)findViewById(R.id.btn_check);
        checkRes=(TextView)findViewById(R.id.checkRes);
        back=(TextView)findViewById(R.id.punch_left_title);
        back.setOnClickListener(this);
//        check.setOnClickListener(this);
        manager=FingerprintManagerCompat.from(this);
        manager.authenticate(null,0,null,new MyCallBack(),null);
    }

    @Override
    public void onClick(View v){
        switch (v.getId()){
            case R.id.punch_left_title:
                finish();
                break;
            default:break;
        }
    }
    public class MyCallBack extends FingerprintManagerCompat.AuthenticationCallback{
        private static final  String TAG="MyCallBack";

        @Override
        public void onAuthenticationError(int errMsgId,CharSequence errString){
            Log.d(TAG, "onAuthenticationError: " + errString);
        }

        // 当指纹验证失败的时候会回调此函数，失败之后允许多次尝试，失败次数过多会停止响应一段时间然后再停止sensor的工作
        @Override
        public void onAuthenticationFailed() {
            checkRes.setText("验证失败,请再次尝试验证");
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
            setResult(1);
            Log.d(TAG, "onAuthenticationSucceeded: " + "验证成功");
        }
    }
}