package com.eventbus.eventbustest;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import event.EventBus;

/**
 * 测试一下sticky事件可以进行bean传递了，不需要序列化了
 * 这里需要注意是，粘性Sticky事件需要你手动进行移除！
 */
public class EStickyActivity extends Activity implements View.OnClickListener{
    private Button btn_test;
    private Button btn_test_a;
    private TextView text_test,text_test_a,text_test_interface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticky);
        init();
        EventBus.getDefault().registerSticky(this); // 订阅一个sticky事件
    }

    private void init() {
        btn_test = (Button) findViewById(R.id.btn_test);
        btn_test.setOnClickListener(this);
        btn_test_a = (Button) findViewById(R.id.btn_test_a);
        btn_test_a.setOnClickListener(this);
        text_test = (TextView) findViewById(R.id.text_test);
        text_test_a = (TextView) findViewById(R.id.text_test_a);
        text_test_interface = (TextView) findViewById(R.id.text_test_interface);

    }

    /**
     * 如果有两个接受了testEvent
     * 必须是onevent方法名
     * @param testEvent
     */
    public void onEvent(TestEvent testEvent) {
        text_test.setText(testEvent.testStr);
    }
    public void onEvent(TestAEvent testAEvent) {
        text_test_a.setText("testAEvent----->");
    }

    /**
     * testEvent 实现了这个EBaseEvent 接口，
     * 如果是发TestEvent这个时候这个接口一样可以收到消息，从源码也可以看出来
     * @param eBaseEvent
     */
    public void onEvent(EBaseEvent eBaseEvent){
        text_test_interface.setText("两个会同时响应吗");
    }

    /**
     * 这样写是没有用的
     * @param testEvent
     */
    private void onEventSticky(TestEvent testEvent){
        text_test.setText("接收到事件 : " + testEvent.testStr);
    }



    @Override
    public void onClick(View v) {
        if(v == btn_test){
//          EventBus.getDefault().postSticky(new TestEvent("我是第二个吗？"));
        }else if(v == btn_test_a){
//          EventBus.getDefault().post(new TestAEvent());
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
