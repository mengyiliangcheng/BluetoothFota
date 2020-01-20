package com.inuker.bluetooth.test;


import com.inuker.bluetooth.BleUtils;

public class BleUtilsTestTest{

    public static void main(String args[]){
        BleUtils b = BleUtils.getInstance();
        char[] c = {0x12,0x23};
        b.setNotify(c);
        System.out.println(b.getNotify());
        System.out.println(b.getNotify());
    }
}