package com.example.lmq.testsensor3;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.NetworkOnMainThreadException;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.server.converter.StringToIntConverter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.Math;

/**
 * Created by lmq on 2017/2/26.
 * 重力加速度的误差？
 * 传感器的是以左下角为原点的，x向右，y向上
 * 文件中加速度传感器在后
 * 不保存到文件的版本
 */

public class Accelerometer extends AppCompatActivity {
    private TextView accelerometerView;
    private TextView gyroscopeView;
    private SensorManager sensorManager;
    private MySensorEventListener sensorEventListener;

    private Button back;
    private Button start;
    private Button stop;
    private Button open;
    private Button settings;
    private Button sample_time;
    private String sample_button_text = "采样数据时间间隔：";
    private String[] areas = new String[]{"FASTEST", "GAME", "UI", "NORMAL" };
    //对应SensorManager的0,1,2,3
    private RadioOnClick radioOnClick = new RadioOnClick(0);
    private ListView areaListView;
    private boolean isPaused = true;
    private TextView now_time;
    private String start_time;
    private String filedetail;
    private int data_num;
    private Context mContext;
    SDFileHelper fHelper;

    private String urlPath="192.168.56.1";
    private Socket socket;
    private boolean stopClicked = false;
    private boolean useNet = true;
    //！！！使用时手机要和服务器处于同一局域网下。。。


    private float[] gyroOrientation = new float[3];
    private float[] accel = new float[3];
    private float[] magnet = new float[3];
    public int TIME_CONSTANT = 30;

    private Handler uiHandle = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    if (!isPaused) {
                        updateClockUI();
                    }
                    uiHandle.sendEmptyMessageDelayed(1, 1000);
                    break;
                default:
                    break;
            }
        }
    };
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accelerometer);
        sensorEventListener = new MySensorEventListener();
        accelerometerView = (TextView) this.findViewById(R.id.accelerometerView);
        gyroscopeView = (TextView) this.findViewById(R.id.gyroscopeView);
        //获取感应器管理器
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        back = (Button) findViewById(R.id.accelerometer_back);
        start = (Button) findViewById(R.id.start_test);
        stop = (Button) findViewById(R.id.stop_test);
        open = (Button) findViewById(R.id.open);
        settings = (Button) findViewById(R.id.settings);
        sample_time = (Button) findViewById(R.id.sample_time);
        sample_time.setText(sample_button_text + String.valueOf(TIME_CONSTANT) + "ms");
        now_time = (TextView) findViewById(R.id.now_time);
        mContext = getApplicationContext();

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(Accelerometer.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
        //为按钮Start注册监听器
        start.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                SimpleDateFormat sDateFormat = new SimpleDateFormat("HH-mm-ss");
                start_time = sDateFormat.format(new Date());
                try {
                    fHelper = new SDFileHelper(mContext, start_time + areas[radioOnClick.getIndex()]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                filedetail = new String();
                data_num = 0;
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if(isPaused) return;
                        writeFile(false);
                        // TODO Auto-generated method stub
                    }},0, TIME_CONSTANT);
                onResume();
                uiHandle.removeMessages(1);
                isPaused = false;
                uiHandle.sendEmptyMessageDelayed(1, 0);
            }
        });

        //为按钮stop注册监听器
        stop.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                stopClicked = true;
                isPaused = true;
                if (useNet){
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                writeFile(true);
                sensorManager.unregisterListener(sensorEventListener);
            }
        });

        open.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                final AlertDialog.Builder builder=new AlertDialog.Builder(Accelerometer.this);
                builder.setTitle("输入IP");
//                    builder.setIcon(android.R.drawable.ic_dialog_info);
                final EditText ed = new EditText(Accelerometer.this);
                ed.setText(urlPath);
                builder.setView(ed);

                builder.setPositiveButton("确定", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //设置url，IP地址、端口
                        urlPath = ed.getText().toString();
                        Log.i("debug click", start_time+ Environment.getExternalStorageDirectory());
                    }
                });

                builder.setNegativeButton("连接", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Looper.prepare();
                                try {
                                    socket=new Socket(urlPath, 12345);
                                    Toast.makeText(Accelerometer.this, "Socket创建成功", Toast.LENGTH_LONG).show();
                                } catch (IOException e) {
                                    Log.i("ipsss",urlPath);
                                    Toast.makeText(Accelerometer.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    }
                });
                AlertDialog dialog=builder.create();
                dialog.show();
            }
        });
        sample_time.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                final AlertDialog.Builder builder=new AlertDialog.Builder(Accelerometer.this);
                builder.setTitle("输入时间间隔");
                final EditText ed = new EditText(Accelerometer.this);
                ed.setText(String.valueOf(TIME_CONSTANT));
                builder.setView(ed);
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        TIME_CONSTANT = Integer.valueOf(ed.getText().toString());
                        sample_time.setText(sample_button_text + String.valueOf(TIME_CONSTANT) + "ms");
                    }
                });

                AlertDialog dialog=builder.create();
                dialog.show();
            }
        });
        settings.setOnClickListener(new RadioClickListener());
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    class RadioClickListener implements View.OnClickListener{
        @Override
        public void onClick(View v){
            if(isPaused){
                AlertDialog ad =new AlertDialog.Builder(Accelerometer.this).setTitle("选择区域")
                        .setSingleChoiceItems(areas,radioOnClick.getIndex(),radioOnClick).create();
                areaListView=ad.getListView();
                ad.show();
            }
        }
    }

    class RadioOnClick implements DialogInterface.OnClickListener{
        private int index;

        public RadioOnClick(int index){
            this.index = index;
        }
        public void setIndex(int index){
            this.index=index;
        }
        public int getIndex(){
            return index;
        }

        public void onClick(DialogInterface dialog, int whichButton){
            setIndex(whichButton);
            Toast.makeText(Accelerometer.this, "您已经选择了 " +  ":" + areas[index], Toast.LENGTH_LONG).show();
            dialog.dismiss();
        }
    }

    private void updateClockUI() {
        SimpleDateFormat sDateFormat = new SimpleDateFormat("HH:mm:ss");
        now_time.setText(sDateFormat.format(new Date()));
        gyroscopeView.setText("Gyroscope: " + gyroOrientation[0] + ", " + gyroOrientation[1] + ", " + gyroOrientation[2]);
        accelerometerView.setText("Accelerometer: " + accel[0] + ", " + accel[1] + ", " + accel[2]);
    }

    private void writeFile(boolean force){
        sendDataToServer();
        data_num++;
    }

    private void sendDataToServer(){
        try
        {
            String content = new String(String.valueOf(System.currentTimeMillis()) + ' ' +
                    String.valueOf(gyroOrientation[0]) + ' ' +
                    String.valueOf(gyroOrientation[1]) + ' ' +
                    String.valueOf(gyroOrientation[2]) + ' ' +
                    String.valueOf(accel[0]) + ' ' +
                    String.valueOf(accel[1]) + ' ' +
                    String.valueOf(accel[2]) + ' ' +
                    String.valueOf(magnet[0]) + ' ' +
                    String.valueOf(magnet[1]) + ' ' +
                    String.valueOf(magnet[2]) + '\n');
            BufferedWriter os= new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            //os.write(int)意思是发送一个char字符
            os.write(content);
            os.flush();
        }  catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        //获取陀螺仪传感器
        Sensor gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(sensorEventListener, gyroscopeSensor, radioOnClick.getIndex());
        //获取加速度传感器
        Sensor accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(sensorEventListener, accelerometerSensor, radioOnClick.getIndex());
        //获取磁力传感器
        Sensor magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(sensorEventListener, magneticSensor, radioOnClick.getIndex());
        super.onResume();
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("Accelerometer Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.disconnect();
        AppIndex.AppIndexApi.end(client, getIndexApiAction());
    }

    private final class MySensorEventListener implements SensorEventListener {
        //可以得到传感器实时测量出来的变化值
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && !isPaused) {
                System.arraycopy(event.values, 0, accel, 0, 3);
            }
            else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD && !isPaused) {
                System.arraycopy(event.values, 0, magnet, 0, 3);
            }
            else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && !isPaused) {
                System.arraycopy(event.values, 0, gyroOrientation, 0, 3);
            }
        }
        //重写变化
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }
}

