package com.inuker.bluetooth;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.inuker.bluetooth.CRC16;
import com.inuker.bluetooth.library.BluetoothClient;
import com.inuker.bluetooth.library.Constants;
import com.inuker.bluetooth.library.connect.response.BleWriteResponse;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

public class xmodem {

    // 开始
    public final byte SOH = 0x01;
    // 开始
    public final byte STX = 0x02;
    // 结束
    public final byte EOT = 0x04;
    // 应答
    public final byte ACK = 0x06;
    // 重传
    public final byte NAK = 0x15;
    // 无条件结束
    public final byte CAN = 0x18;

    // 以128字节块的形式传输数据
    //private final int SECTOR_SIZE = 128;
    public final int SECTOR_SIZE = 1024;
    // 最大错误（无应答）包数
    public final int MAX_ERRORS = 10;

    // 输入流，用于读取串口数据
    private InputStream inputStream;
    // 输出流，用于发送串口数据
    private OutputStream outputStream;

    public xmodem(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    protected BluetoothClient Client;
    protected String MAC;
    protected String serviceUUID;
    protected String w_UUID;

    public xmodem(BluetoothClient mClient, String MAC, String serviceUUID, String w_UUID) {
        Client = mClient;
        this.MAC = MAC;
        this.serviceUUID = serviceUUID;
        this.w_UUID = w_UUID;
    }

    /**
     * 发送数据
     *
     * @param filePath
     *            文件路径
     */
    public void send(final String filePath, final Handler mHandler) {
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
                    long currentSize = 0;
                    long totalSize = 0;
                    long last_percent = 0;
                    long start_fota = System.currentTimeMillis();

                    Message m = new Message();
                    m.arg1 = 0;
                    m.arg2 = 0;
                    m.what = 0x111;
                    mHandler.sendMessage(m);

                    errorCount = 0;
                    //CommonUtils.toast("wait c");
                    while(errorCount < 100 && 67 != BleUtils.getInstance().getNotify())
                    {
                        System.out.println("wait cccccc");
                        BleUtils.getInstance().clearNotify();
                        //CommonUtils.toast("wait c");
                        errorCount ++;
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if(errorCount >= 100) {
                        System.out.println("wait c fail");
                        return;
                    }else{
                        System.out.println("wait c succ");
                        BleUtils.getInstance().clearNotify();
                    }
                    // 读取文件初始化
                    DataInputStream inputStream = new DataInputStream(
                            new FileInputStream(filePath));

                    totalSize = BleUtils.getFileSize(filePath);
                    start_fota = System.currentTimeMillis();
                    while ((nbytes = inputStream.read(sector)) > 0) {
                        System.out.println("read bytes:" + nbytes);
                        // 如果最后一包数据小于128个字节，以0xff补齐
                        if (nbytes < SECTOR_SIZE) {
                            for (int i = nbytes; i < SECTOR_SIZE; i++) {
                                sector[i] = (byte) 0xff;
                            }
                        }
                        currentSize += nbytes;
                        long percent = currentSize * 100 / totalSize;
                        if(last_percent != percent) {
                            Message m1 = new Message();
                            m1.arg1 = (int) percent;
                            int use_time = (int)((System.currentTimeMillis()-start_fota)/1000);
                            if(0 != use_time) {
                                m1.arg2 = (int) currentSize / use_time;
                            }else{
                                m1.arg2 = 0;
                            }
                            m1.what = 0x111;
                            mHandler.sendMessage(m1);
                            last_percent = percent;
                        }

                        // 同一包数据最多发送10次
                        errorCount = 0;
                        while (errorCount < MAX_ERRORS) {
                            // 组包
                            // 控制字符 + 包序号 + 包序号的反码 + 数据 + 校验和
                            //putData(SOH);
                            //putData(blockNumber);
                            //putData(~blockNumber);
                            System.out.println("---------send package num:"+(int)blockNumber);
                            checkSum = CRC16.calc(sector) & 0x00ffff;
                            //checkSum = CRC16.calc_sum(sector);
                            //putChar(SOH,blockNumber,(byte)(~blockNumber),sector, (short) checkSum);
                            putChar(STX,blockNumber,(byte)(~blockNumber),sector, (short) checkSum);
                            //outputStream.flush();
                            //try {
                            //    Thread.sleep(1000);
                           // } catch (InterruptedException e) {
                            //    e.printStackTrace();
                           // }
                            // 获取应答数据
                            //byte data = getData();
                            byte data = 0;
                            int count = 0;
                            while(count < 10000 && ACK != (data = BleUtils.getInstance().getNotify()))
                            {
                                System.out.println("wait ack");
                                count ++;
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            if(count >= 10000)
                            {
                                System.out.println("timeout");
                                return;
                            }
                            BleUtils.getInstance().clearNotify();

                            // 如果收到应答数据则跳出循环，发送下一包数据
                            // 未收到应答，错误包数+1，继续重发
                            if (data == ACK) {
                                break;
                            } else {
                                ++errorCount;
                            }
                        }
                        if(errorCount >= MAX_ERRORS)
                        {
                            System.out.println("---------timeout!!!!!!!!!!");
                            return ;
                        }
                        // 包序号自增
                        blockNumber = (byte) ((++blockNumber) % 256);
                    }

                    Message m2 = new Message();
                    m2.what = 0x110;
                    m2.arg1 = 0;
                    m2.arg2 = (int)(System.currentTimeMillis()-start_fota)/1000;
                    mHandler.sendMessage(m2);

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

    /**
     * 接收数据
     *
     * @param filePath
     *            文件路径
     * @return 是否接收完成
     * @throws IOException
     *             异常
     */
    public boolean receive(String filePath) throws Exception {
        // 错误包数
        int errorCount = 0;
        // 包序号
        byte blocknumber = 0x01;
        // 数据
        byte data;
        // 校验和
        int checkSum;
        // 初始化数据缓冲区
        byte[] sector = new byte[SECTOR_SIZE];

        // 写入文件初始化
        DataOutputStream outputStream = new DataOutputStream(
                new FileOutputStream(filePath));

        // 发送字符C，CRC方式校验
        putData((byte) 0x43);

        while (true) {
            if (errorCount > MAX_ERRORS) {
                outputStream.close();
                return false;
            }

            // 获取应答数据
            data = getData();
            if (data != EOT) {
                try {
                    // 判断接收到的是否是开始标识
                    if (data != SOH) {
                        errorCount++;
                        continue;
                    }

                    // 获取包序号
                    data = getData();
                    // 判断包序号是否正确
                    if (data != blocknumber) {
                        errorCount++;
                        continue;
                    }

                    // 获取包序号的反码
                    byte _blocknumber = (byte) ~getData();
                    // 判断包序号的反码是否正确
                    if (data != _blocknumber) {
                        errorCount++;
                        continue;
                    }

                    // 获取数据
                    for (int i = 0; i < SECTOR_SIZE; i++) {
                        sector[i] = getData();
                    }

                    // 获取校验和
                    checkSum = (getData() & 0xff) << 8;
                    checkSum |= (getData() & 0xff);
                    // 判断校验和是否正确
                    int crc = CRC16.calc(sector);
                    if (crc != checkSum) {
                        errorCount++;
                        continue;
                    }

                    // 发送应答
                    putData(ACK);
                    // 包序号自增
                    blocknumber++;
                    // 将数据写入本地
                    outputStream.write(sector);
                    // 错误包数归零
                    errorCount = 0;

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    // 如果出错发送重传标识
                    if (errorCount != 0) {
                        putData(NAK);
                    }
                }
            } else {
                break;
            }
        }

        // 关闭输出流
        outputStream.close();
        // 发送应答
        putData(ACK);

        return true;
    }

    /**
     * 获取数据
     *
     * @return 数据
     * @throws IOException
     *             异常
     */
    protected byte getData() throws IOException {
        //return (byte) inputStream.read();
        return (byte)BleUtils.getInstance().getNotify();
    }

    /**
     * 发送数据
     *
     * @param data
     *            数据
     * @throws IOException
     *             异常
     */
    protected void putData(int data) throws IOException {
        System.out.println("---------write:" + data);
        Client.write(MAC, UUID.fromString(serviceUUID), UUID.fromString(w_UUID), new byte[]{(byte) data}, new BleWriteResponse() {
            public void onResponse(int code) {
                if (code == Constants.REQUEST_SUCCESS) {
                    Log.e("aris", "putData  write  请求成功 ");
                } else if (code == Constants.REQUEST_FAILED) {
                    Log.e("aris", "putData  write  请求失败 ");
                }
            }
        });
    }

    /**
     * 发送数据
     *
     * @param data
     *            数据
     * @param checkSum
     *            校验和
     * @throws IOException
     *             异常
     */
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

        while(total_len > 0) {
            sendbytes = (total_len < 500) ? total_len : 500;
            array = Arrays.copyOfRange(bb.array(),pos,pos+sendbytes) ;
            pos += sendbytes;
            //System.out.println("------send sub:"+BleUtils.bytes2hex(array));
            Client.writeNoRsp(MAC, UUID.fromString(serviceUUID), UUID.fromString(w_UUID), array, new BleWriteResponse() {
                public void onResponse(int code) {
                    if (code == Constants.REQUEST_SUCCESS) {
                        Log.e("aris", "putData  write  请求成功 ");
                    } else if (code == Constants.REQUEST_FAILED) {
                        Log.e("aris", "putData  write  请求失败 ");
                    }
                }
            });
            System.out.println("--------send bytes:" + sendbytes);
            total_len -= sendbytes;
        }
    }
}