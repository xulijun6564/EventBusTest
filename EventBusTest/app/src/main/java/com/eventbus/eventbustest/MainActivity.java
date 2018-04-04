package com.eventbus.eventbustest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.test.aidl.TestAidlInterface;

import event.EventBus;


public class MainActivity extends Activity implements View.OnClickListener {
    private Button btn_test;
    private Button btn_test_a;
    private Button btn_test_jump;
    private Button btn_test_handler;
    private TextView text_test;
//    private TestAidlInterface mService;
//    private ServiceConnection mServiceConnection = new ServiceConnection() {
//
//        @Override
//        public void onServiceDisconnected(ComponentName name) {
//            // TODO Auto-generated method stub
//            mService = null;
//        }
//
//        @Override
//        public void onServiceConnected(ComponentName name, IBinder service) {
//            // TODO Auto-generated method stub
//            mService = TestAidlInterface.Stub.asInterface(service);
//        }
//    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EventBus.getDefault().register(this);// 第一种实现方式
        EventBus.builder().build().register(this);// 第2种实现方式
//        if (getPackageInfo(this, "cn.etouch.ecalendar") != null) {
//        Intent intent = new Intent();
//        ComponentName componentName = new ComponentName("cn.etouch.ecalendar", "cn.etouch.ecalendar.service.MyService");
//        intent.setComponent(componentName);
//        startService(intent);
//        }
        //绑定远程的service，然后得到其实现的aidl接口，这个是来自服务器的
//        Bundle args = new Bundle();
//        Intent intent = new Intent("com.test.aidl.LinkService");//服务器service的拦截器定义
//        intent.putExtras(args);
////        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        init();

//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    if(mService != null){
//                        text_test.setText(mService.doLink("加油！！","徐俊你无敌了啊"));
//                    }
//                } catch (RemoteException e) {
//                    e.printStackTrace();
//                }
//            }
//        },1000);


    }

    private void init() {
        btn_test = (Button) findViewById(R.id.btn_test);
        btn_test.setOnClickListener(this);
        btn_test_a = (Button) findViewById(R.id.btn_test_a);
        btn_test_a.setOnClickListener(this);
        btn_test_jump = (Button) findViewById(R.id.btn_test_jump);
        btn_test_jump.setOnClickListener(this);
        btn_test_handler = (Button) findViewById(R.id.btn_test_handler);
        btn_test_handler.setOnClickListener(this);
        text_test = (TextView) findViewById(R.id.text_test);
    }

    //这里的返利类型为什么都应该是可以的跟返回类型貌似没有关系
    public boolean onEventMainThread(TestEvent testEvent) {
        text_test.setText("testEvent---");
        return true;

    }

    public void onEvent(TestAEvent testAEvent) {
        text_test.setText("testAEvent---AAAAA");
    }


    @Override
    public void onClick(View v) {
        if (v == btn_test) {
//            try {
//                if(mService != null){
//                    text_test.setText(mService.doLink("点击事件","两者相加"));
//                }
//            } catch (RemoteException e) {
//                e.printStackTrace();
//            }
//            EventBus.getDefault().post(new TestEvent());
        } else if (v == btn_test_a) {
            EventBus.getDefault().post(new TestAEvent());
        } else if (v == btn_test_jump) {
            Intent intent = new Intent(MainActivity.this, EFirstActivity.class);
            this.startActivity(intent);
        } else if (v == btn_test_handler) {
            if (!thread.isAlive()) {
                thread.start();
            }

        }
    }

    /**
     * 如果我不传参数默认为当前线程的looper
     * 如果要刷新那么就需要在主线程下跑
     * looper在这个线程中轮询，从消息队列中去取出
     */
    Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1000:
                    break;
                default:
                    break;
            }
        }
    };


    Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {

            //这个是在主线程中轮询，主线程默认有一个looper的prepare和loop
            final Handler handler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    switch (msg.what) {
                        case 1000:
                            text_test.setText("测试一下handler");
                            break;

                    }
                }
            };

            // 这个在thread中进行轮询，
            Looper.prepare();
            Handler handler1 = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if (msg.what == 1000) {
                        handler.sendEmptyMessage(1000);
                    }
                }
            };
            handler1.sendEmptyMessage(1000);
            Looper.loop();

            //写在Looper.loop()之后的代码不会被立即执行，
            // 当调用后 mHandler.getLooper().quit()后，
            // loop才会中止，其后的代码才能得以运行。
            // Looper对象通过MessageQueue 来存放消息和事件。
            // 一个线程只能有一个Looper，对应一个MessageQueue。

        }
    });

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
//        this.unbindService(mServiceConnection);
    }
}
