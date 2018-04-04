/*
 * Copyright (C) 2012 Markus Junginger, greenrobot (http://greenrobot.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package event;

import android.util.Log;

import com.eventbus.eventbustest.Manager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 这个类的终于是通过传来的订阅者，也就是类名，然后
 * 找出有多少个onEvent，并且onEvent方法中参数的消息类型
 */

class SubscriberMethodFinder {
    /**
     * 我们在复写的时候就是写的这个名字的方法，也是消息传递过来唯一接受处理的方法
     */
    private static final String ON_EVENT_METHOD_NAME = "onEvent";

    /*
     * In newer class files, compilers may add methods. Those are called bridge or synthetic methods.
     * EventBus must ignore both. There modifiers are not public but defined in the Java class file format:
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
     */
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;

    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
    /**
     * 一个方法list的缓存变量
     * key 为类名
     */
    private static final Map<String, List<SubscriberMethod>> methodCache = new HashMap<String, List<SubscriberMethod>>();

    private final Map<Class<?>, Class<?>> skipMethodVerificationForClasses;

    SubscriberMethodFinder(List<Class<?>> skipMethodVerificationForClassesList) {
        skipMethodVerificationForClasses = new ConcurrentHashMap<Class<?>, Class<?>>();
        if (skipMethodVerificationForClassesList != null) {
            for (Class<?> clazz : skipMethodVerificationForClassesList) {
                skipMethodVerificationForClasses.put(clazz, clazz);
            }
        }
    }

    /**
     * 通过订阅者，寻找这个订阅者中SubscriberMethod的list
     * @param subscriberClass 订阅者，也就是我们register的时候的那个类
     * @return
     */
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        String key = subscriberClass.getName(); // 获取类名,这里获取的com.eventbus.eventbustest.MainActivity
//        Manager.xujun(key);
        List<SubscriberMethod> subscriberMethods;
        synchronized (methodCache) {
            subscriberMethods = methodCache.get(key);
        }
        if (subscriberMethods != null) {
            return subscriberMethods;
        }
        subscriberMethods = new ArrayList<SubscriberMethod>();
        Class<?> clazz = subscriberClass;
        HashSet<String> eventTypesFound = new HashSet<String>();
        StringBuilder methodKeyBuilder = new StringBuilder();
        while (clazz != null) {
            String name = clazz.getName();
//            Manager.xujun("clazz.getName()---->"+name);//com.eventbus.eventbustest.MainActivity;;;android.app.Activity
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.")) {
                //如果为这三种类型就直接返回，就是往上找父类的时候，自然会找到这几个，在往上循环的时候
                //可以测试往上找还不是这几个的情况
                // Skip system classes, this just degrades performance
                break;
            }

            // Starting with EventBus 2.2 we enforced methods to be public (might change with annotations again)
            Method[] methods = clazz.getDeclaredMethods();//注意和getmethods（）的区别这个方法貌似之获取包括继承类在类的所有共有方法，
//            Manager.xujun("clazz.getDeclaredMethods()---->"+methods.length);//5个，获取所有的方法数这个时候，这个时候是存在5个

            for (Method method : methods) {
                String methodName = method.getName();
//                Manager.xujun("method.getName()---->"+methodName);
                /**
                 * 就是这5个方法
                 * method.getName()---->onClick---->onCreate---->onEvent---->onEvent---->init
                 */
                if (methodName.startsWith(ON_EVENT_METHOD_NAME)) {//如果方法名是一这个开始的："onEvent" ，暂时使用了两个 并且都是public

                    int modifiers = method.getModifiers(); // 获取这个方法的权限，是public == 1 还是private == 2,如果默认不写就是0，protected=4
//                    Manager.xujun("method.getModifiers()---->"+modifiers);
                    /**
                     * 这个时候method.getModifiers()----> 1/1
                     */

                 // 也就是说只有modifiers & Modifier.PUBLIC判断出来只有为public的才行
                    if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {

                        Class<?>[] parameterTypes = method.getParameterTypes();//获取的是参数对象有几个，看网上说String返回的是QString
//                        Manager.xujun("method.getParameterTypes()--->"+method.getParameterTypes().length);

                        /**
                         * 从这里看在onevent的时候只能传一个参数才有用的，不传是没有用的
                         * parameterTypes.length == 1、
                         */
                        if (parameterTypes.length == 1) {
                            String modifierString = methodName.substring(ON_EVENT_METHOD_NAME.length());//将onevent长度前面的全面截取掉留下后面的，这个时候想要想在什么线程里面执行就可以这样写了oneventMainThread
//                            Manager.xujun("modifierString---->"+modifierString);
                            /**
                             * MainThread，如果方法名是onevent开始，后面不是跟着后面三个的话，方法就是错的
                             * 后面只能跟MainThread，BackgroundThread，Async，要不然不跟，就是默认的
                             */

                            ThreadMode threadMode;
                            if (modifierString.length() == 0) {
                                threadMode = ThreadMode.PostThread;
                            } else if (modifierString.equals("MainThread")) {
                                threadMode = ThreadMode.MainThread;
                            } else if (modifierString.equals("BackgroundThread")) {
                                threadMode = ThreadMode.BackgroundThread;
                            } else if (modifierString.equals("Async")) {
                                threadMode = ThreadMode.Async;
                            } else {
                                if (skipMethodVerificationForClasses.containsKey(clazz)) {
                                    continue;
                                } else {
                                    throw new EventBusException("Illegal onEvent method, check for typos: " + method);
                                }
                            }
                            Class<?> eventType = parameterTypes[0];//参数的类也就是消息的类型
//                            Manager.xujun("eventType---->"+eventType);
                            /**
                             * eventType---->class com.eventbus.eventbustest.TestEvent
                             */
                            methodKeyBuilder.setLength(0);
                            methodKeyBuilder.append(methodName);
                            methodKeyBuilder.append('>').append(eventType.getName());
                            String methodKey = methodKeyBuilder.toString();
//                            Manager.xujun("methodKey---->"+methodKey);
                            /**
                             * 这里存的方法key是用方法名加上消息的类名进行合成的
                             * methodKey---->onEventMainThread>com.eventbus.eventbustest.TestEvent
                             */
                            if (eventTypesFound.add(methodKey)) {
                                // Only add if not already found in a sub class
                                subscriberMethods.add(new SubscriberMethod(method, threadMode, eventType));
                            }
                        }
                    } else if (!skipMethodVerificationForClasses.containsKey(clazz)) {
                        Log.d(EventBus.TAG, "Skipping method (not public, static or abstract): " + clazz + "."
                                + methodName);
                    }
                }
            }
            clazz = clazz.getSuperclass(); //将这个类变成了他的父类，如果他的父类没有就将为null
        }
        if (subscriberMethods.isEmpty()) {
            throw new EventBusException("Subscriber " + subscriberClass + " has no public methods called "
                    + ON_EVENT_METHOD_NAME);
        } else {
            synchronized (methodCache) {
                methodCache.put(key, subscriberMethods);
            }
            return subscriberMethods;
        }
    }

    static void clearCaches() {
        synchronized (methodCache) {
            methodCache.clear();
        }
    }

}
