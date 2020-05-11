package com.heiku.kafka;

/**
 * @author Heiku
 * @date 2020/5/11
 **/
public class BytesUtils {

    public static byte[] longToBytes(long res){
        byte[] buffer = new byte[8];
        for (int i = 0; i < 8; i++){
            int offset = 64 - (i + 1) * 8;
            buffer[i] = (byte) ((res >> offset) & 0xff);
        }
        return buffer;
    }

    public static long bytesToLong(byte[] b){
        long values = 0;
        for (int i = 0; i < 8; i++){
            values <<= 8;
            values |= (b[i] & 0xff);
        }
        return values;
    }
}
