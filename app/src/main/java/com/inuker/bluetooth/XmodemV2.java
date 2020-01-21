package com.inuker.bluetooth;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.inuker.bluetooth.library.BluetoothClient;
import com.inuker.bluetooth.library.Constants;
import com.inuker.bluetooth.library.connect.response.BleWriteResponse;
import com.inuker.bluetooth.library.utils.BluetoothLog;
import com.inuker.bluetooth.library.utils.ByteUtils;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

import static java.util.Arrays.copyOfRange;

public class XmodemV2 extends xmodem {

    private int last_percent = 0;
    private long start_fota = 0;
    private String packagePath;
    private Handler mHandler;

    public XmodemV2(BluetoothClient mClient, String MAC, String serviceUUID, String w_UUID) {
        super(mClient, MAC, serviceUUID, w_UUID);
    }

    public XmodemV2(BluetoothClient mClient, String MAC, String serviceUUID, String w_UUID,String filePath,Handler handler) {
        super(mClient, MAC, serviceUUID, w_UUID);
        packagePath = filePath;
        mHandler = handler;
    }

    public boolean waitC()
    {
        int errorCount = 0;
        while(errorCount < 1000 && 67 != BleUtils.getInstance().getNotify())
        {
            System.out.println("wait cccccc");
            //BleUtils.getInstance().clearNotify();
            //CommonUtils.toast("wait c");
            errorCount ++;
            /*
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }*/
        }
        if(errorCount >= 1000) {
            System.out.println("wait c fail");
            return false;
        }else{
            System.out.println("wait c succ");
            BleUtils.getInstance().clearNotify();
            return true;
        }
    }

    public ByteBuffer readPackage(String path){
        ByteBuffer bb;
        long size = 0;
        int read_size = 0;
        int block_size = 2048;

        size = BleUtils.getFileSize(path);
        bb = ByteBuffer.allocate((int)size).order(
                ByteOrder.BIG_ENDIAN);
        byte[] byteTmp = new byte[block_size];

        try{
            DataInputStream inputStream = new DataInputStream(
                    new FileInputStream(path));
            while((read_size = inputStream.read(byteTmp)) > 0)
            {
                bb.put(byteTmp,0,read_size);
            }
        }catch (Exception e){
            e.printStackTrace();
            BluetoothLog.e("read file failed:" + path);
            return null;
        }
        return bb;
    }

    private void notifyOver(){
        Message m2 = new Message();
        m2.what = 0x110;
        m2.arg1 = 0;
        m2.arg2 = (int)(System.currentTimeMillis()-start_fota)/1000;
        mHandler.sendMessage(m2);
    }

    private void notifyProgress(int percent,int sendBytes){
        if(last_percent != percent) {
            Message m1 = new Message();
            m1.arg1 = (int) percent;
            int use_time = (int)((System.currentTimeMillis()-start_fota)/1000);
            if(0 != use_time) {
                m1.arg2 = sendBytes / use_time;
            }else{
                m1.arg2 = 0;
            }
            m1.what = 0x111;
            mHandler.sendMessage(m1);
            last_percent = percent;
        }
    }
    public void send() {
        new Thread() {
            public void run() {
                try {
                    // 错误包数
                    int errorCount;
                    // 包序号
                    byte blockNumber = 0x01;
                    // 校验和
                    int checkSum;
                    // 读取到缓冲区的字节数量
                    int nbytes;
                    // 初始化数据缓冲区
                    byte[] sector = new byte[SECTOR_SIZE];
                    int totalSize = 0;
                    int sendBytes = 0;

                    boolean receive_nak = false;
                    ByteBuffer bb;

                    /* 设置进度为0 */
                    Message m = new Message();
                    m.arg1 = 0;
                    m.arg2 = 0;
                    m.what = 0x111;
                    mHandler.sendMessage(m);

                    /* 等c */
                    if(false == waitC()){
                        return ;
                    }

                    /* 读升级包 */
                    bb = readPackage(packagePath);
                    if(null == bb){
                        return ;
                    }
/*
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
*/
                    /* 记录开始升级时间 */
                    start_fota = System.currentTimeMillis();

                    /*  */
                    BleUtils.getInstance().clearNotify();
                    totalSize = bb.capacity();
                    while (totalSize > sendBytes) {
                        nbytes = totalSize - sendBytes > SECTOR_SIZE ? SECTOR_SIZE : (totalSize - sendBytes);
                        //bb.get(sector,sendBytes,nbytes);
                        byte[] tmp = Arrays.copyOfRange(bb.array(),sendBytes,sendBytes+nbytes) ;
                        System.out.println("read bytes:" + nbytes);
                        for(int i = 0;i < nbytes;i ++){
                            sector[i] = tmp[i];
                        }
                        // 如果最后一包数据小于128个字节，以0xff补齐
                        if (nbytes < SECTOR_SIZE) {
                            for (int i = nbytes; i < SECTOR_SIZE; i++) {
                                sector[i] = (byte) 0xff;
                            }
                        }
                        sendBytes += nbytes;

                        /* 通知升级进度 */
                        long percent = sendBytes * 100 / totalSize;
                        System.out.println("-------all send bytes:"+sendBytes);
                        notifyProgress((int)percent,sendBytes);

                        // 同一包数据最多发送10次
                        errorCount = 0;
                        while (errorCount < MAX_ERRORS) {
                            System.out.println("---------send package num:"+(int)blockNumber);
                            checkSum = CRC16.calc(sector) & 0x00ffff;
                            putChar(STX,blockNumber,(byte)(~blockNumber),sector, (short) checkSum);

                            byte data = 0;
                            int count = 0;
                            while(count < 10000 && ACK != (data = BleUtils.getInstance().getNotify()))
                            {
                                if(data == NAK){
                                    byte[] nak_info = BleUtils.getInstance().getAllNotify();
                                    Log.i("xmodem v2","recvie NAK");
                                    int i = 0;
                                    for(i = 0;i < nak_info.length;i ++){
                                        if('}' == nak_info[i]){
                                            break;
                                        }
                                    }
                                    if(i >= nak_info.length)
                                    {
                                        Log.i("xmodem v2","nak info err:"+ new String(nak_info));
                                        break;
                                    }
                                    byte[] s = Arrays.copyOfRange(nak_info,0,i+1);
                                    String str = new String(s);
                                    Log.i("xmodem v2","nak info:" + str);
                                    NakInfo nakInfo = JSON.parseObject(str,NakInfo.class);
                                    Log.i("xmodem v2","number:" + nakInfo.getNamber());
                                    Log.i("xmodem v2","len:" + nakInfo.getLen());
                                    blockNumber = (byte)nakInfo.getNamber();
                                    sendBytes = nakInfo.getLen();
                                    receive_nak = true;
                                    if(false == waitC()){
                                        return ;
                                    }
                                    break;
                                }

                                System.out.println("wait ack...");
                                count ++;
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            if(count >= 10000)
                            {
                                System.out.println("timeout");
                                return;
                            }


                            // 如果收到应答数据则跳出循环，发送下一包数据
                            // 未收到应答，错误包数+1，继续重发
                            if (data == ACK) {
                                break;
                            } else {
                                ++errorCount;
                            }
                        }
                        if(receive_nak) {
                            receive_nak = false;
                            continue;
                        }
                        if(errorCount >= MAX_ERRORS)
                        {
                            System.out.println("---------timeout!!!!!!!!!!");
                            return ;
                        }
                        // 包序号自增
                        blockNumber = (byte) ((++blockNumber) % 256);
                    }

                    /* 通知传输结束 */
                    notifyOver();

                    // 所有数据发送完成后，发送结束标识
                    boolean isAck = false;
                    while (!isAck) {
                        putData(EOT);
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        isAck = getData() == ACK;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
        }.start();
    }

    private int ack_num = 0;
    protected void putChar(byte head,byte num,byte num2,byte[] data, short checkSum) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(data.length + 5).order(
                ByteOrder.BIG_ENDIAN);
        int pos = 0;
        byte[] array;
        bb.put(head);
        bb.put(num);
        bb.put(num2);
        bb.put(data);
        bb.putShort(checkSum);
        //System.out.println("------send:"+BleUtils.bytes2hex(bb.array()));
        int sendbytes = 0;
        int total_len = data.length + 5;
        //outputStream.write(bb.array());
        ack_num = 0;
        while(total_len > 0) {
            sendbytes = (total_len < 500) ? total_len : 500;
            array = copyOfRange(bb.array(),pos,pos+sendbytes) ;
            pos += sendbytes;
            Log.e("xmodem v2","------send sub:"+BleUtils.bytes2hex(array));
            Client.writeNoRsp(MAC, UUID.fromString(serviceUUID), UUID.fromString(w_UUID), array, new BleWriteResponse() {
                public void onResponse(int code) {
                    if (code == Constants.REQUEST_SUCCESS) {
                        Log.e("xmodem v2", "putData  write  请求成功 ");
                        ack_num ++;
                        if(ack_num >= 3) {
                            byte[] b = new byte[1];
                            b[0] = ACK;
                            BleUtils.getInstance().setNotify(b);
                            ack_num = 0;
                        }
                    } else if (code == Constants.REQUEST_FAILED) {
                        Log.e("xmodem v2", "putData  write  请求失败 ");
                    }
                }
            });
            System.out.println("--------send bytes:" + sendbytes);
            total_len -= sendbytes;

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
