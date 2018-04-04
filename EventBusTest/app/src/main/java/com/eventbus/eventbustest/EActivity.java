package com.eventbus.eventbustest;

import android.app.Activity;

/**
 * 测试继承这个类后会有什么有效果，他会一个一个往上找，如果这个类中有public void onEvent一样的效果
 * Created by xujun on 2015/10/10.
 */
public class EActivity extends Activity{
    public void methodTest1(){

    }
    public void methodTest2(){

    }

    private void onEvent(TestAEvent testAEvent){

    }
}
