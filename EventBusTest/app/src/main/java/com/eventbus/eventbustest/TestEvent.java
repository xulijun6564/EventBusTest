package com.eventbus.eventbustest;

/**
 * Created by xujun on 2015/10/10.
 */
public class TestEvent implements EBaseEvent{
    public String testStr;
    public TestEvent(String testStr){
        this.testStr = testStr;
    }
    public TestEvent(){

    }
}
