package com.eventbus.eventbustest;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import event.EventBus;


public class EFirstActivity extends EActivity implements View.OnClickListener{
    private Button btn_test;
    private Button btn_test_a;
    private TextView text_test;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);
        EventBus.getDefault().register(this);// 第一种实现方式
//        EventBus.getDefault().registerSticky(this); // 订阅一个sticky事件
        initFirst();

    }

    private void initFirst() {
        btn_test = (Button) findViewById(R.id.btn_test);
        btn_test.setOnClickListener(this);
        btn_test_a = (Button) findViewById(R.id.btn_test_a);
        btn_test_a.setOnClickListener(this);
        text_test = (TextView) findViewById(R.id.text_test);

    }

    //这里的返利类型为什么都应该是可以的跟返回类型貌似没有关系
    public boolean onEventMainThread(TestEvent testEvent){
        text_test.setText(testEvent.testStr);
        return true;

    }

    protected void onEvent(TestAEvent testAEvent){
        text_test.setText("testAEvent---AAAAA");
    }



    @Override
    public void onClick(View v) {
        if(v == btn_test){
//          EventBus.getDefault().post(new TestEvent());
//            EventBus.getDefault().postSticky(new TestEvent("我是第一个吗？"));
        }else if(v == btn_test_a){
//          EventBus.getDefault().post(new TestAEvent());
            //如果post了两个是以后面那个为准
            EventBus.getDefault().postSticky(new TestAEvent());
            TestEvent event1 = new TestEvent("我想传一个bean1111到第二个activity");
            EventBus.getDefault().postSticky(event1 );
            TestEvent event2 = new TestEvent("我想传一个bean2222到第二个activity");
            EventBus.getDefault().postSticky(event2);//这个会覆盖前面那个
//            EventBus.getDefault().removeStickyEvent(event2);//这样remove为啥第一个也不在了呢？因为事件实际只有一个，后面一个覆盖了前面那个
//            EventBus.getDefault().removeStickyEvent(new TestEvent());//这样remove是没有用的，因为两个都不是一个对象为啥？

//            EventBus.getDefault().removeStickyEvent(event2.getClass());//这样也可以remove掉
            Intent intent = new Intent(EFirstActivity.this,EStickyActivity.class);
            this.startActivity(intent);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
//        EventBus.getDefault().unregister(this);
    }
}
