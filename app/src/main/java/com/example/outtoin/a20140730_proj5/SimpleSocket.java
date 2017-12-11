package com.example.outtoin.a20140730_proj5;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Created by outtoin on 2017-12-03.
 */

public class SimpleSocket extends Thread {
    //private Socket  mSocket;
    private SocketChannel socketChannel;

    private BufferedReader buffRecv;
    private BufferedWriter buffSend;

    private String  mAddr = "localhost";
    private int     mPort = 8080;
    private short     mop = -1;
    private String   mkey = "";
    private boolean mConnected = false;
    private Handler mHandler = null;
    private byte[] mrbuf = null;
    private byte[] mrbufnow = null;

    private File moutFile = null;
    private int mFilelen = 0;
    private int mIterFilelen = 0;

    private ByteBuffer header = ByteBuffer.allocate(16);
    private ByteBuffer result = ByteBuffer.allocate(mFilelen);

    private ArrayList<ByteBuffer> resultList;

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    static class MessageTypeClass {
        public static final int SIMSOCK_CONNECTED = 1;
        public static final int SIMSOCK_DATA = 2;
        public static final int SIMSOCK_DISCONNECTED = 3;
    };
    public enum MessageType { SIMSOCK_CONNECTED, SIMSOCK_DATA, SIMSOCK_DISCONNECTED };

    public SimpleSocket(String addr, int port, Handler handler, short op, String key, byte[] rbuf, int filelen, File outfile)
    {
        // need input, output filestream
        mAddr = addr;
        mPort = port;
        mHandler = handler;
        mop = op;
        mkey = key;
        mrbuf = rbuf;
        moutFile = outfile;
        mFilelen = filelen;
    }

    private void makeMessage(int what, Object obj)
    {
        Message msg = Message.obtain();
        msg.what = what;
        msg.obj  = obj;
        mHandler.sendMessage(msg);
    }

    private boolean connect (String addr, int port)
    {
        try {
            InetSocketAddress socketAddress  = new InetSocketAddress (InetAddress.getByName(addr), port);
            socketChannel = SocketChannel.open(socketAddress);
            //mSocket = new Socket();
            //mSocket.connect(socketAddress, 5000);
        } catch (IOException e) {
            e.printStackTrace();
            return connect(addr, port);
        }
        return true;
    }


    private ByteBuffer Packet(short op, String key, int filelen, byte[] rbuf) {
        long length = 16 + filelen;

        ByteBuffer bop = ByteBuffer.allocate(2);
        bop.putShort((short) op);
        bop.order(ByteOrder.BIG_ENDIAN);
        bop.flip();
        //Log.i("op", bytesToHex(bop.array()));

        ByteBuffer bchksum = ByteBuffer.allocate(2);
        bchksum.putShort((short) 0);
        bchksum.order(ByteOrder.BIG_ENDIAN);
        bchksum.flip();
        //Log.i("checksum", bytesToHex(bchksum.array()));

        ByteBuffer bkey = ByteBuffer.allocate(4);
        bkey.put(key.getBytes());
        bkey.order(ByteOrder.BIG_ENDIAN);
        bkey.flip();
        //Log.i("key", bytesToHex(bkey.array()));

        ByteBuffer blen = ByteBuffer.allocate(8);
        blen.putLong(length);
        blen.order(ByteOrder.BIG_ENDIAN);
        blen.flip();
        //Log.i("length", (bytesToHex(blen.array()) + " (" + String.valueOf(length) + ")"));

        ByteBuffer brbuf = ByteBuffer.allocate(filelen);
        brbuf.put(rbuf);
        brbuf.order(ByteOrder.BIG_ENDIAN);
        brbuf.flip();
        //Log.i("rbuf", bytesToHex(brbuf.array()));

        ByteBuffer packet = ByteBuffer.allocate(16+filelen);
        packet.put(bop);
        packet.put(bchksum);
        packet.put(bkey);
        packet.put(blen);
        packet.put(brbuf);

        short chksum1 = CalculateCheckSum(packet.array());
        packet.putShort(2, chksum1);
        packet.flip();

        return packet;
    }

    private void sendPacket(SocketChannel socketChannel, int filelen, byte[] rbuf, String key) {
        ByteBuffer packet = Packet(mop, key, filelen, rbuf);
        try {
            socketChannel.write(packet);
        } catch (IOException e) {
            e.printStackTrace();
            //Log.i("lost", "sendpacket");
            String res = new String(result.array());
            //Log.i("result", decrypt(res, "cake"));
            handlingDisrupt((mIterFilelen - filelen), filelen, key);

            return;
        }
    }

    private void readPacket(SocketChannel socketChannel, int filelen, String key) {
        ByteBuffer wbuf = ByteBuffer.allocate(16+filelen);

        long timeout = 5000;
        long stime = new Date().getTime();
        long rtime = 0L;

        ByteBuffer readByte = ByteBuffer.allocate(1);

        int rvalue = 0;
        int rvalues = 0;

        try {
            while (rtime < timeout) {
                rtime = System.currentTimeMillis() - stime;
                rvalue = socketChannel.read(readByte);
                if(rvalue == -1) {
                    throw new Exception("connect lost");
                }

                rvalues = rvalues + rvalue;

                if (rvalue >= 1) {
                    readByte.flip();
                    wbuf.put(readByte);
                    readByte.clear();
                    if (rvalues > 16) {
                        makeMessage(0, "");
                    }
                }
                if(rtime > timeout)
                    throw new Exception("read timeout exception");

                if(rvalues >= (16 + filelen)){
                    break;
                }
            }
        } catch (Exception e) {
            if (rvalues <= 16) {
                //Log.i("lost", "rvalues<=16");
                String res = new String(result.array());
                //Log.i("result", decrypt(res, "cake"));
                handlingDisrupt((mIterFilelen - filelen), filelen, key);

                return;
            }
            else {
                //Log.i("lost", "rvalues>16");
                wbuf.flip();

                byte[] packetheader = new byte[16];
                wbuf.get(packetheader); // make position 17
                header.put(packetheader);

                int alive = rvalues - 16;
                byte[] aliveData = new byte[alive];

                wbuf.get(aliveData);
                result.put(aliveData);

                //Log.d("aliveData", bytesToHex(aliveData));

                int remain = 16 + filelen - rvalues;

                String input = new String(mrbufnow);

                int newKeyOffset = calculate_key_offset(input, alive);
                String newKey = key.substring(newKeyOffset) + key.substring(0, newKeyOffset);

                //String res = new String(result.array());
                //Log.i("resulthex", bytesToHex(result.array()));
                //Log.i("result", decrypt(res, "cake"));

                int offset = alive + (mIterFilelen - filelen);
                handlingDisrupt(offset, remain, newKey);

                return;
            }
        }
        //Log.i("wbuf1", bytesToHex(wbuf.array()));
        wbuf.flip();

        byte[] packetheader = new byte[16];
        wbuf.get(packetheader);
        header.put(packetheader);

        byte[] resData = new byte[filelen];
        wbuf.get(resData);
        result.put(resData);
    }

    public int calculate_key_offset(String string, int idx) {
        int res = 0;
        for (int i = 0; i < idx; i++) {
            char c = string.charAt(i);
            if (c >= 'a' && c <= 'z') {
                res++;
            }
        }
        //Log.i("caculate_key_offset", String.valueOf(idx) + " " + String.valueOf(res));
        return res % 4;
    }

    @Override
    public void run() {
        if(! connect(mAddr, mPort)) return; // connect failed
        //if(mSocket == null)         return;

        String realInput = new String(mrbuf);
        realInput = realInput.toLowerCase();
        mrbuf = realInput.getBytes();
        resultList = new ArrayList<ByteBuffer>();

        String nowKey = mkey;
        int divider = 100;
        int iter = (int) Math.ceil((double) mFilelen / (double) divider);
        try {
            for (int i = 0; i < iter; i++ ) {
                int end = Math.min((i+1) * divider, mFilelen);
                byte[] slice = Arrays.copyOfRange(mrbuf ,i*divider, end);
                int iterFilelen = Math.min(divider, mFilelen - (i * divider)) ;

                mrbufnow = slice;
                mIterFilelen = iterFilelen;

                //Log.i("slice len", String.valueOf(slice.length));
               // Log.i("iter", String.valueOf(iterFilelen));

                header = ByteBuffer.allocate(16);
                result = ByteBuffer.allocate(iterFilelen);

                sendPacket(socketChannel, iterFilelen, slice, nowKey);
                readPacket(socketChannel, iterFilelen, nowKey);

                byte[] data = result.array();
                resultList.add(result);

                //Log.d("res", bytesToHex(data));

                String dec = new String(data);
                //Log.d("decrypt", decrypt(dec, nowKey));

                String s = new String(slice);
                nowKey = make_new_key(s, nowKey);
            }
            boolean append = false;
            //Log.i("length-list", String.valueOf(resultList.size()));
            for (ByteBuffer bb : resultList) {
                String writeString = new String(bb.array());
                writeFile(moutFile, writeString, append);
                append = true;
            }


            BufferedReader in = new BufferedReader(new FileReader(moutFile));
            String s;

            s = in.readLine();
            if (s.length() > 50)
                s = s.substring(0, 50);

            makeMessage(1, s);
            in.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        /*try {
            buffRecv.close();
            buffSend.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }*/
        mConnected = false;
    }

    public String make_new_key(String string, String key) {
        int res = 0;
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c >= 'a' && c <= 'z') {
                res++;
            }
        }
        //Log.i("caculate_key_offset", String.valueOf(idx) + " " + String.valueOf(res));

        int idx = res % 4;
        String newKey = key.substring(idx) + key.substring(0, idx);
        return newKey;
    }

    public void handlingDisrupt(int offset, int filelen, String key) {

        //Log.i("handlingDisrupt", String.valueOf(offset) + " / " + String.valueOf(filelen) + " / " + key);

        //Log.i("handlingDisrupt", "lost, -------------------------------------------");
        if(! connect(mAddr, mPort)) return; // connect failed
        //if(mSocket == null)         return;

        try {
            byte[] off = new byte[offset];
            ByteBuffer bb_rb = ByteBuffer.wrap(mrbufnow);
            bb_rb.get(off);

            String cb = new String(off);
            //Log.i("complete bytes", cb);

            byte[] new_rbuf = new byte[filelen];
            bb_rb.get(new_rbuf);

            String nb = new String(new_rbuf);
            //Log.i("now bytes", nb);
            //Log.i("new_rbuf", bytesToHex(new_rbuf));

            header = ByteBuffer.allocate(16);

            sendPacket(socketChannel, filelen, new_rbuf, key);
            readPacket(socketChannel, filelen, key);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    synchronized public boolean isConnected(){
        return mConnected;
    }


    public short CalculateCheckSum( byte[] bytes ){
        short CheckSum = 0, i = 0;
        for( i = 0; i < bytes.length; i++){
            CheckSum += (short)(bytes[i] & 0xFF);
        }
        //Log.i("Checksum", Integer.toHexString(CheckSum));
        return CheckSum;
    }
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    static String encrypt(String text, final String key) {
        String res = "";
        text = text.toLowerCase();
        for (int i = 0, j = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z') {
                res += (char)((c + key.charAt(j) - 2 * 'a') % 26 + 'a');
                j = ++j % key.length();
            }
            else {
                res += (char)  c;
            }
        }
        return res;
    }

    static String decrypt(String text, final String key) {
        String res = "";
        text = text.toLowerCase();
        for (int i = 0, j = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z') {
                res += (char)((c - key.charAt(j) + 26) % 26 + 'a');
                j = ++j % key.length();
            }
            else {
                res += (char)  c;
            }
        }
        return res;
    }
    private void writeFile(File file, String data, boolean append) {
        try {
            FileWriter wr= new FileWriter(file,append);
            PrintWriter writer= new PrintWriter(wr);
            writer.print(data);
            writer.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}

