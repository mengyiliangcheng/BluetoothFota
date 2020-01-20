package com.inuker.bluetooth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.inuker.bluetooth.library.connect.listener.BleConnectStatusListener;
import com.inuker.bluetooth.library.connect.response.BleMtuResponse;
import com.inuker.bluetooth.library.connect.response.BleNotifyResponse;
import com.inuker.bluetooth.library.connect.response.BleReadResponse;
import com.inuker.bluetooth.library.connect.response.BleUnnotifyResponse;
import com.inuker.bluetooth.library.connect.response.BleWriteResponse;
import com.inuker.bluetooth.library.utils.BluetoothLog;
import com.inuker.bluetooth.library.utils.ByteUtils;

import org.json.JSONObject;

import static com.inuker.bluetooth.library.Constants.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * Created by dingjikerbo on 2016/9/6.
 */
public class CharacterActivity extends Activity implements View.OnClickListener {

    private String mMac;
    private UUID mService;
    private UUID mCharacter;

    private TextView mTvTitle;

    private Button mBtnRead;

    private Button mBtnWrite;
    private EditText mEtInput;

    private Button mBtnNotify;
    private Button mBtnUnnotify;
    private EditText mEtInputMtu;
    private Button mBtnRequestMtu;
    private Button mBtnSendPackage;
    private TextView progressText;

    private ProgressBar progressBar;
    private Handler mHandler;//用于处理消息的Handler对象
    //private long start_fota = 0;

    private boolean mtu_succ = false;
    private boolean notify_succ = false;

    private int mProgressStatus =0;

    private Fota fota;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.character_activity);

        Intent intent = getIntent();
        mMac = intent.getStringExtra("mac");
        mService = (UUID) intent.getSerializableExtra("service");
        mCharacter = (UUID) intent.getSerializableExtra("character");

        mTvTitle = (TextView) findViewById(R.id.title);
        mTvTitle.setText(String.format("%s", mMac));

        mBtnRead = (Button) findViewById(R.id.read);

        mBtnWrite = (Button) findViewById(R.id.write);
        mEtInput = (EditText) findViewById(R.id.input);

        mBtnNotify = (Button) findViewById(R.id.notify);
        mBtnUnnotify = (Button) findViewById(R.id.unnotify);

        mEtInputMtu = (EditText) findViewById(R.id.et_input_mtu);
        mBtnRequestMtu = (Button) findViewById(R.id.btn_request_mtu);

        mBtnSendPackage = (Button) findViewById(R.id.send_package);
        mBtnSendPackage.setOnClickListener(this);
        mBtnSendPackage.setEnabled(true);

        progressText = (TextView) findViewById(R.id.progressText);
        progressText.setText("0%");

        progressBar = (ProgressBar) findViewById(R.id.progressBar2);
        mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg){
                if (msg.what==0x111){
                    System.out.println("set progress :" + msg.arg1);
                    progressBar.setProgress(msg.arg1);
                    int rate = msg.arg2;
                    progressText.setText(msg.arg1+"% " + rate + "b/s" );
                }else if(msg.what == 0x100){
                    //SendFotaPackage();
                    System.out.println("-------start fota");
                    fota = new Fota(new String("/sdcard/Download/update.zip"),
                            ClientManager.getClient(),mMac,mService.toString(),mCharacter.toString(),mHandler);
                    mBtnSendPackage.setEnabled(false);
                    mBtnSendPackage.setText("start send fota package");
                    progressBar.setVisibility(View.VISIBLE);
                    fota.start();
                }
                else if(msg.what==0x110) {
                    Toast.makeText(CharacterActivity.this,
                            "send package finish", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    mBtnSendPackage.setEnabled(true);
                    mBtnSendPackage.setText("SEND PACKAGE");
                    progressText.setText("SEND PACKAGE OVER,USE SECONDES:"+msg.arg2);
                }
            }
        };

        mBtnRead.setOnClickListener(this);
        mBtnWrite.setOnClickListener(this);

        mBtnNotify.setOnClickListener(this);
        mBtnNotify.setEnabled(true);

        mBtnUnnotify.setOnClickListener(this);
        mBtnUnnotify.setEnabled(false);

        mBtnRequestMtu.setOnClickListener(this);
    }

    private final BleReadResponse mReadRsp = new BleReadResponse() {
        @Override
        public void onResponse(int code, byte[] data) {
            if (code == REQUEST_SUCCESS) {
                mBtnRead.setText(String.format("read: %s", ByteUtils.byteToString(data)));
                CommonUtils.toast("success");
            } else {
                CommonUtils.toast("failed");
                mBtnRead.setText("read");
            }
        }
    };

    private final BleWriteResponse mWriteRsp = new BleWriteResponse() {
        @Override
        public void onResponse(int code) {
            if (code == REQUEST_SUCCESS) {
                CommonUtils.toast("success");
            } else {
                CommonUtils.toast("failed");
            }
        }
    };

    private final BleWriteResponse mSendPackageRequestRsp = new BleWriteResponse() {
        @Override
        public void onResponse(int code) {
            if (code == REQUEST_SUCCESS) {
                CommonUtils.toast("---------------send package request success");
                System.out.println("---------------send package request success");
            } else {
                CommonUtils.toast("--------------failed");
            }
        }
    };

    private final BleNotifyResponse mNotifyRsp = new BleNotifyResponse() {
        @Override
        public void onNotify(UUID service, UUID character, byte[] value) {
            System.out.println("----------on notify");
            BleUtils.getInstance().setNotify(value);
        }

        @Override
        public void onResponse(int code) {
            if (code == REQUEST_SUCCESS) {
                mBtnNotify.setEnabled(false);
                mBtnUnnotify.setEnabled(true);
                CommonUtils.toast("success");
                System.out.println("----------set notify succ");
                notify_succ = true;
            } else {
                CommonUtils.toast("failed");
                System.out.println("----------set notify failed");
            }
        }
    };

    private final BleUnnotifyResponse mUnnotifyRsp = new BleUnnotifyResponse() {
        @Override
        public void onResponse(int code) {
            if (code == REQUEST_SUCCESS) {
                CommonUtils.toast("success");
                mBtnNotify.setEnabled(true);
                mBtnUnnotify.setEnabled(false);
                notify_succ = false;
            } else {
                CommonUtils.toast("failed");
            }
        }
    };

    private final BleMtuResponse mMtuResponse = new BleMtuResponse() {
        @Override
        public void onResponse(int code, Integer data) {
            if (code == REQUEST_SUCCESS) {
                CommonUtils.toast("request mtu success,mtu = " + data);
                System.out.println("-----------request mtu success,mtu = " + data);
                Message m = new Message();
                m.what = 0x100;
                mHandler.sendMessage(m);
                mtu_succ = true;
            } else {
                CommonUtils.toast("request mtu failed");
            }
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.read:
                ClientManager.getClient().read(mMac, mService, mCharacter, mReadRsp);
                break;
            case R.id.write:
                ClientManager.getClient().write(mMac, mService, mCharacter,
                        ByteUtils.stringToBytes(mEtInput.getText().toString()), mWriteRsp);
                break;
            case R.id.notify:
                ClientManager.getClient().notify(mMac, mService, mCharacter, mNotifyRsp);
                break;
            case R.id.unnotify:
                ClientManager.getClient().unnotify(mMac, mService, mCharacter, mUnnotifyRsp);
                break;
            case R.id.btn_request_mtu:
                String mtuStr = mEtInputMtu.getText().toString();
                if (TextUtils.isEmpty(mtuStr)) {
                    CommonUtils.toast("MTU不能为空");
                    return;
                }
                int mtu = Integer.parseInt(mtuStr);
                if (mtu < GATT_DEF_BLE_MTU_SIZE || mtu > GATT_MAX_MTU_SIZE) {
                    CommonUtils.toast("MTU不不在范围内");
                    return;
                }
                ClientManager.getClient().requestMtu(mMac, mtu, mMtuResponse);
                break;
            case R.id.send_package:
                if(notify_succ == false) {
                    ClientManager.getClient().notify(mMac, mService, mCharacter, mNotifyRsp);
                }
                if(mtu_succ == false) {
                    ClientManager.getClient().requestMtu(mMac, 512, mMtuResponse);
                }else{
                    Message m = new Message();
                    m.what = 0x100;
                    mHandler.sendMessage(m);
                }
/*
                ByteBuffer bb = ByteBuffer.allocate(250).order(
                        ByteOrder.BIG_ENDIAN);
                if(false == BleUtils.isFileExit("/sdcard/Download/update.zip"))
                {
                    CommonUtils.toast("/sdcard/Download/update.zip文件不存在");
                    return;
                }
                System.out.println("--------------------calc md5:"+BleUtils.getFileMD5("/sdcard/Download/update.zip"));
                System.out.println("--------------------file size:"+BleUtils.getFileSize("/sdcard/Download/update.zip"));
                //String str = "{\"fota_data_size\":" +  BleUtils.getFileSize("/sdcard/Download/update.zip") + ",\"fota_data_hash_value\":\"245f86fa97e77f113bcc75772237f07c\"}";
                JSONObject fota_json = new JSONObject();
                try {
                    fota_json.put("fota_data_size", BleUtils.getFileSize("/sdcard/Download/update.zip"));
                    fota_json.put("fota_data_hash_value",BleUtils.getFileMD5("/sdcard/Download/update.zip"));
                }catch(Exception e){
                    CommonUtils.toast("create JSON failed");
                    return;
                }
                String str = fota_json.toString();
                System.out.println("--------------------json :"+str);
                System.out.println("--------------------json size:"+Integer.toHexString(str.length()));
                bb.put(ByteUtils.stringToBytes("F583"+Integer.toHexString(str.length())));
                bb.put(str.getBytes());
                System.out.println("fota json write:"+ new String(bb.array()));
                ClientManager.getClient().write(mMac, mService, mCharacter,
                    bb.array(), mSendPackageRequestRsp);

                CommonUtils.toast("start send fota package");
                mBtnSendPackage.setEnabled(false);
                mBtnSendPackage.setText("start send fota package");
                progressBar.setVisibility(View.VISIBLE);
                BleUtils.getInstance().clearNotify();
                xmodem x = new xmodem(ClientManager.getClient(),mMac,mService.toString(),mCharacter.toString());
                x.send("/sdcard/Download/update.zip",mHandler);
                */

                break;
        }
    }

    private final BleConnectStatusListener mConnectStatusListener = new BleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            BluetoothLog.v(String.format("CharacterActivity.onConnectStatusChanged status = %d", status));

            if (status == STATUS_DISCONNECTED) {
                CommonUtils.toast("disconnected");
                mBtnRead.setEnabled(false);
                mBtnWrite.setEnabled(false);
                mBtnNotify.setEnabled(false);
                mBtnUnnotify.setEnabled(false);

                mTvTitle.postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        finish();
                    }
                }, 300);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        ClientManager.getClient().registerConnectStatusListener(mMac, mConnectStatusListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ClientManager.getClient().unregisterConnectStatusListener(mMac, mConnectStatusListener);
    }
}
