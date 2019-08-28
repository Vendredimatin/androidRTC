package fr.pchab.androidrtc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

public class RegistrationActivity extends Activity{
    private Button regButton;
    private EditText roomID,roomCode,userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, LongRunningService.class);

        startService(intent);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
//                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_reg);

        regButton = (Button) findViewById(R.id.reg_button);

        roomID = (EditText) findViewById(R.id.edit_roomID);
        roomCode=(EditText)findViewById(R.id.edit_roomCode);
        userName=(EditText)findViewById(R.id.edit_userName);

        regButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent;
                String id=roomID.getText().toString();
                if(id.charAt(0)>='0'&&id.charAt(0)<='9'){
                    intent=new Intent(RegistrationActivity.this,MonitorActivity.class);
                }else{
                    intent=new Intent(RegistrationActivity.this,FingerPrintActivity.class);
                }
                intent.putExtra("roomid",roomID.getText().toString());
                intent.putExtra("roomcode",roomCode.getText().toString());
                intent.putExtra("username",userName.getText().toString());
                startActivity(intent);
            }
        });

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

}
