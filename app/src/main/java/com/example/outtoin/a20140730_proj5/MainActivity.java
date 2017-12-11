package com.example.outtoin.a20140730_proj5;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.ipaulpro.afilechooser.utils.FileUtils;

public class MainActivity extends AppCompatActivity {

    private static final int READ_REQUEST_CODE = 1;
    private static final int WRITE_REQUEST_CODE = 2;
    private static final int DELETE_REQUEST_CODE = 3;

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static final String TAG = "FileChooserActivity";

    protected Uri myUri = null;

    private Handler nativeHandler;

    private Socket socket;

    private BufferedReader networkReader;
    private BufferedWriter networkWriter;

    private InputStream networkin;

    String ext = Environment.getExternalStorageState();
    String sdpath = Environment.getExternalStorageDirectory().getAbsolutePath();
    String rootdir = Environment.getRootDirectory().getAbsolutePath();
    String datadir = Environment.getDataDirectory().getAbsolutePath();
    String cachedir = Environment.getDownloadCacheDirectory().getAbsolutePath();

    /*
    @Override
    protected void onStop() {
        super.onStop();
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set button
        Button btn_file = (Button) findViewById(R.id.btn_file);
        Button btn_connect = (Button) findViewById(R.id.btn_connect);

        // Set EditText
        final EditText et_ip = (EditText) findViewById(R.id.text_ip);
        final EditText et_port = (EditText) findViewById(R.id.text_port);
        final EditText et_output = (EditText) findViewById(R.id.text_output);
        final EditText et_key = (EditText) findViewById(R.id.text_key);

        // Set TextView
        final TextView tv_result = (TextView) findViewById(R.id.text_result);
        final TextView tv_file = (TextView) findViewById(R.id.text_filename);

        // Set RadioGroup
        final RadioGroup rg = (RadioGroup) findViewById(R.id.rg_encrypt);

        // Set Progressbar
        final ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar);

        nativeHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                switch(inputMessage.what){
                    case 2:
                        String msg2 = (String) inputMessage.obj;
                        tv_file.setText(msg2.split("%3A")[1]);
                        break;

                }
            }
        };

        btn_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*int chooser;
                int radioId = rg_file.getCheckedRadioButtonId();
                RadioButton rb = (RadioButton) findViewById(radioId);
                if (rb.getId() == R.id.radio_delete) {
                    chooser = DELETE_REQUEST_CODE;
                }
                else {
                    chooser = READ_REQUEST_CODE;
                }*/
                showChooser(READ_REQUEST_CODE);

                if (myUri != null) {
                    String name = myUri.toString().split("%3A")[1];
                    tv_file.setText(name);
                }
            }
        });

        btn_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Activity Settings
                pb.setProgress(0);

                // Default Parameter Settings
                short op = -1;
                String ip = "";
                int port = 0;
                String output = "";
                String key = "";

                // Parameter Settings
                if (!et_ip.getText().toString().matches("")) {
                    ip = et_ip.getText().toString();
                }
                if (!et_port.getText().toString().matches("")) {
                    port = Integer.parseInt(et_port.getText().toString());
                }
                if (!et_output.getText().toString().matches("")) {
                    output = et_output.getText().toString();
                }

                if (!et_key.getText().toString().matches("")) {
                    key = et_key.getText().toString();
                }
                int radioId = rg.getCheckedRadioButtonId();
                RadioButton rb = (RadioButton) findViewById(radioId);

                switch(rb.getText().toString()) {
                    case "Encrypt":
                        op = 0;
                        break;
                    case "Decrypt":
                        op = 1;
                        break;
                };

                final File outfile = new File(sdpath, output);

                final String finalOutput = output;
                Handler mHandler = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message inputMessage) {
                        switch(inputMessage.what){
                            case 0:
                                String msg = (String) inputMessage.obj;
                                pb.setProgress(pb.getProgress() + 1);

                                // do something with UI
                                break;

                            case 1:
                                String msg1 = (String) inputMessage.obj;
                                Toast.makeText(getApplicationContext(),
                                        "File saved at sdcard/" + finalOutput, Toast.LENGTH_SHORT).show();
                                try {
                                    String textview = "Output Overview... : \n";
                                    textview += msg1 + "...";
                                    tv_result.setText(textview);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                break;

                            case 2:
                                break;

                        }
                    }
                };

                byte[] rbuf = new byte[0];
                int filelen = 0;
                try {
                    networkin = readStreamromUri(myUri);
                    rbuf = new byte[networkin.available()];
                    filelen = networkin.available();
                    networkin.read(rbuf);

                    pb.setMax(filelen);

                    SimpleSocket ssocket = new SimpleSocket(ip, port, mHandler, op, key, rbuf, filelen, outfile);
                    ssocket.start();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        networkin.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

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

    private void writeFile(String filename, String data) {
        File file = new File(sdpath, filename);
        try {
            FileWriter wr= new FileWriter(file,true);
            PrintWriter writer= new PrintWriter(wr);
            writer.println(data);
            writer.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void showChooser(int type) {
        Intent target = FileUtils.createGetContentIntent();
        Intent intent = Intent.createChooser(
                target, getString(R.string.choose_file)
        );
        switch (type) {
            case READ_REQUEST_CODE:
                try {
                    startActivityForResult(intent, READ_REQUEST_CODE);
                } catch (ActivityNotFoundException e) {
                    // hello world!
                }
                break;
            case DELETE_REQUEST_CODE:
                try {
                    startActivityForResult(intent, DELETE_REQUEST_CODE);
                } catch (ActivityNotFoundException e) {
                    // hello world!
                }
                break;
        }
    }



    private String readTextFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        return stringBuilder.toString();
    }

    private InputStream readStreamromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        //Log.i("readReaderFromUri", String.valueOf(inputStream.available()));
        return inputStream;
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case READ_REQUEST_CODE:
                // If the file selection was successful
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        // Get the URI of the selected file
                        Uri uri = data.getData();
                        myUri = uri;
                        makeMessage(2, myUri.toString());
                    }
                }
                break;
            case DELETE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        Uri uri = data.getData();
                        try {
                            DocumentsContract.deleteDocument(getContentResolver(), uri);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void createFile(String mimeType, String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

        // Filter to only show results that can be "opened", such as
        // a file (as opposed to a list of contacts or timezones).
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Create a file with the requested MIME type.
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, WRITE_REQUEST_CODE);
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    short CalculateCheckSum( byte[] bytes ){
        short CheckSum = 0, i = 0;
        for( i = 0; i < bytes.length; i++){
            CheckSum += (short)(bytes[i] & 0xFF);
        }
        //Log.i("Checksum", Integer.toHexString(CheckSum));
        return CheckSum;
    }

    private byte[] encrypt(byte[] file, String key) {
        char[] keyChars = key.toCharArray();
        byte[] bytes = file;
        for (int i = 0; i < file.length; i++) {
            int keyNR = keyChars[i % keyChars.length] - 32;
            int c = bytes[i] & 255;
            if ((c >= 32) && (c <= 127)) {
                int x = c - 32;
                x = (x + keyNR) % 96;
                bytes[i] = (byte) (x + 32);
            }
        }
        return bytes;
    }

    private byte[] decrypt(byte[] file, String key) {
        char[] keyChars = key.toCharArray();
        byte[] bytes = file;
        for (int i = 0; i < file.length; i++) {
            int keyNR = keyChars[i % keyChars.length] - 32;
            int c = bytes[i] & 255;
            if ((c >= 32) && (c <= 127)) {
                int x = c - 32;
                x = (x - keyNR + 96) % 96;
                bytes[i] = (byte) (x + 32);
            }
        }
        return bytes;
    }

    private void makeMessage(int what, Object obj)
    {
        Message msg = Message.obtain();
        msg.what = what;
        msg.obj  = obj;
        nativeHandler.sendMessage(msg);
    }
}
