// TestAidlInterface.aidl
package com.test.aidl;

// Declare any non-default types here with import statements
// 进程间通信，要和提供服务的模块（服务器相同的接口）

interface TestAidlInterface {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    String doLink(String a,String b);
}
