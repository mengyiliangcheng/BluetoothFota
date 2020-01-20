package com.inuker.bluetooth;
import android.os.ConditionVariable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class BleUtils {
    private static BleUtils instance = new BleUtils();
    private BleUtils(){};
    LinkedList<Byte> queue = new LinkedList<Byte>();
    ConditionVariable mCV = new ConditionVariable();

    public static BleUtils getInstance(){
        return instance;
    }

    public void setNotify(byte[] value){
        for(int i = 0;i < value.length;i ++) {
            System.out.println("add value:"+value[i]);
            queue.addLast(value[i]);
            mCV.open();
        }
        return ;
    }

    public byte[] getAllNotify(){
        ByteBuffer bb = ByteBuffer.allocate(128).order(
                ByteOrder.BIG_ENDIAN);
        byte b;
        int size = queue.size();
        int i = 0;
        try {
            mCV.block(1000);  /* 1s */
            //System.out.println("queue size:" + queue.size());
            for(i = 0;i < size;i ++) {
                b = queue.get(i);
                bb.put(b);
                //System.out.println("queue:" + b);
            }
            b = 0;
            bb.put(b);
            System.out.println("get value:"+  BleUtils.bytes2hex(bb.array()));
        }catch (Exception e) {
            //e.printStackTrace();
            queue.clear();
            return Arrays.copyOfRange(bb.array(),0,i);
        }
        queue.clear();
        return Arrays.copyOfRange(bb.array(),0,size);
    }

    public byte getNotify(){
        byte b = 0;
        try {
            mCV.block(1000);  /* 1s */
            b = queue.getFirst();
            System.out.println("get value:"+  b);
        }catch (Exception e) {
            //e.printStackTrace();
            return b;
        }
        queue.removeFirst();
        return b;
    }

    public void clearNotify(){
        System.out.println("clear notify");
        queue.clear();
        mCV.close();
    }

    public static String getFileMD5(String path){
        File file = new File(path);
        return getFileMD5(file);
    }

    public static boolean isFileExit(String path){
        File file = new File(path);
        return file.exists();
    }

    public static long getFileSize(String path){
        File file = new File(path);
        return file.length();
    }

    public static String getFileMD5(File file){
        BigInteger bigInt = null;
        try {
            FileInputStream fis = new FileInputStream(file);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int length = -1;
            while ((length = fis.read(buffer, 0, 1024)) != -1) {
                md.update(buffer, 0, length);
            }
            bigInt = new BigInteger(1, md.digest());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bigInt.toString(16);
    }

    public static String bytes2hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        String tmp = null;
        for (byte b : bytes) {
            // 将每个字节与0xFF进行与运算，然后转化为10进制，然后借助于Integer再转化为16进制
            tmp = Integer.toHexString(0xFF & b);
            if (tmp.length() == 1) {
                tmp = "0" + tmp;
            }
            sb.append(tmp);
        }
        return sb.toString();

    }
}
