package fr.pchab.androidrtc;

import android.app.Activity;
import android.content.DialogInterface;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;

public class FingerPrintActivity extends Activity {
    private static final String TAG = "gryphon";

    private BiometricPrompt mBiometricPrompt;
    private CancellationSignal mCancellationSignal;
    private BiometricPrompt.AuthenticationCallback mAuthenticationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_punch);

        mBiometricPrompt = new BiometricPrompt.Builder(this)
                .setTitle("指纹验证")
                .setDescription("描述")
                .setNegativeButton("取消", getMainExecutor(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Log.i(TAG, "Cancel button clicked");
                    }
                })
                .build();

        mCancellationSignal = new CancellationSignal();
        mCancellationSignal.setOnCancelListener(new CancellationSignal.OnCancelListener() {
            @Override
            public void onCancel() {
                //handle cancel result
                Log.i(TAG, "Canceled");
            }
        });

        mAuthenticationCallback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);

                Log.i(TAG, "onAuthenticationError " + errString);
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);

                Log.i(TAG, "onAuthenticationSucceeded " + result.toString());
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();

                Log.i(TAG, "onAuthenticationFailed ");
            }
        };

        mBiometricPrompt.authenticate(mCancellationSignal, getMainExecutor(), mAuthenticationCallback);

    }
}