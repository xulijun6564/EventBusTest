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

import android.os.Looper;
import android.util.Log;

import com.eventbus.eventbustest.Manager;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * EventBus is a central publish/subscribe event system for Android. Events are posted ({@link #post(Object)}) to the
 * bus, which delivers it to subscribers that have a matching handler method for the event type. To receive events,
 * subscribers must register themselves to the bus using {@link #register(Object)}. Once registered,
 * subscribers receive events until {@link #unregister(Object)} is called. By convention, event handling methods must
 * be named "onEvent", be public, return nothing (void), and have exactly one parameter (the event).
 *
 * @author Markus Junginger, greenrobot
 */
public class EventBus {

    /** Log tag, apps may override it. */
    public static String TAG = "Event";

    /**
     * volatile
     用volatile修饰的变量，
     线程在每次使用变量的时候，
     都会读取变量修改后的最终的值。
     volatile很容易被误用，用来进行原子性操作
     */
    static volatile EventBus defaultInstance;

    /**
     * EventBusBuilder变量，里面有一个线程池对象和6个Boolean变量
     * 用于这个eventBus类初始化，是个单例的
     */
    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();



    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<Class<?>, List<Class<?>>>();

    /**
     * 得到该事件类型所有订阅者信息队列
     * 根据优先级将当前订阅者信息插入到订阅者队列subscriptionsByEventType中，优先级高的订阅着插到前面，默认的优先级为0
     * 比如现在有个AEvent对象，在三个类（也就是订阅者）中都有onEvent(AEvent aEvent);那么这个队列中就有三个订阅者
     */
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;

    /**
     * 得到当前订阅者订阅的所有事件队列,
     * 将此事件保存到队列typesBySubscriber中,⽤于后续取消订阅;
     */

    private final Map<Object, List<Class<?>>> typesBySubscriber;

    /**
     * 如果是则从stickyEvents事件保存队列中取出
     * 该事件类型最后⼀一个事件发送给当前订阅者。
     * sticky 是粘，胶 的意思
     */
    private final Map<Class<?>, Object> stickyEvents;


    /**
     * ThreadLocal是解决线程安全问题一个很好的思路，ThreadLocal类中有一个Map，
     * 用于存储每一个线程的变量副本，Map中元素的键为线程对象，
     * 而值对应线程的变量副本，由于Key值不可重复，
     * 每一个“线程对象”对应线程的“变量副本”，而到达了线程安全
     * PostingThreadState局部变量
     */
    private final ThreadLocal<PostingThreadState> currentPostingThreadState = new ThreadLocal<PostingThreadState>() {
        /**
         * 返回该线程局部变量的初始值
         * 这个方法是一个延迟调用方法，在线程第1次调用get()或set(Object)时才执行，
         * 并且仅执行1次。ThreadLocal中的缺省实现直接返回一个null。
         * @return
         */
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState();
        }
    };


    /**
     * 主线程执行的poster
     */
    private final HandlerPoster mainThreadPoster;
    private final BackgroundPoster backgroundPoster;
    private final AsyncPoster asyncPoster;

    private final SubscriberMethodFinder subscriberMethodFinder;

    private final ExecutorService executorService;

    private final boolean throwSubscriberException;
    private final boolean logSubscriberExceptions;
    private final boolean logNoSubscriberMessages;
    private final boolean sendSubscriberExceptionEvent;
    private final boolean sendNoSubscriberEvent;
    private final boolean eventInheritance;

    /** Convenience singleton for apps using a process-wide EventBus instance.
     *  这个是第一个获取eventbus对象的一个方法
     * */
    public static EventBus getDefault() {
        if (defaultInstance == null) {
            synchronized (EventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new EventBus();
                }
            }
        }
        return defaultInstance;
    }

    /**
     * 这个是第二种获取eventBus的方法
     * 通过EventBusBuilder中的build()方法获取
     * todo 两者有什么区别还需要去发现
     * 个人理解一个返回的对象是单例的，并且有个volatile修饰，下面这个是返回的一个很普通的变量对象
     * @return
     */
    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }

    /** For unit test primarily. */
    public static void clearCaches() {
        SubscriberMethodFinder.clearCaches();
        eventTypesCache.clear();
    }

    /**
     * Creates a new EventBus instance; each instance is a separate scope in which events are delivered. To use a
     * central bus, consider {@link #getDefault()}.
     */
    public EventBus() {
        this(DEFAULT_BUILDER);
    }

    /**
     * 使用EventBusBuilder来进行类中变量的初始化
     * @param builder
     */
    EventBus(EventBusBuilder builder) {
        subscriptionsByEventType = new HashMap<Class<?>, CopyOnWriteArrayList<Subscription>>();
        typesBySubscriber = new HashMap<Object, List<Class<?>>>();
        stickyEvents = new ConcurrentHashMap<Class<?>, Object>();
        mainThreadPoster = new HandlerPoster(this, Looper.getMainLooper(), 10);
        backgroundPoster = new BackgroundPoster(this);
        asyncPoster = new AsyncPoster(this);
        subscriberMethodFinder = new SubscriberMethodFinder(builder.skipMethodVerificationForClasses);
        //下面几个变量都用的builder中的值
        logSubscriberExceptions = builder.logSubscriberExceptions;
        logNoSubscriberMessages = builder.logNoSubscriberMessages;
        sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
        sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
        throwSubscriberException = builder.throwSubscriberException;
        eventInheritance = builder.eventInheritance;
        executorService = builder.executorService;
    }


    /**
     * Registers the given subscriber to receive events. Subscribers must call {@link #unregister(Object)} once they
     * are no longer interested in receiving events.
     * <p/>
     * Subscribers have event handling methods that are identified by their name, typically called "onEvent". Event
     * handling methods must have exactly one parameter, the event. If the event handling method is to be called in a
     * specific thread, a modifier is appended to the method name. Valid modifiers match one of the {@link ThreadMode}
     * enums. For example, if a method is to be called in the UI/main thread by EventBus, it would be called
     * "onEventMainThread".
     * 注册，传入的是一个Object，一般我们使用都传入的是自己本身
     * 这个是没有优先级的
     * register有三个方式
     */
    public void register(Object subscriber) {
        register(subscriber, false, 0);
    }

    /**
     * Like {@link #register(Object)} with an additional subscriber priority to influence the order of event delivery.
     * Within the same delivery thread ({@link ThreadMode}), higher priority subscribers will receive events before
     * others with a lower priority. The default priority is 0. Note: the priority does *NOT* affect the order of
     * delivery among subscribers with different {@link ThreadMode}s!
     * 这个是有优先级的
     */
    public void register(Object subscriber, int priority) {
        register(subscriber, false, priority);
    }

    /**
     * Like {@link #register(Object)}, but also triggers delivery of the most recent sticky event (posted with
     * {@link #postSticky(Object)}) to the given subscriber.
     * 这个是注册一个Sticky事件，没有优先级
     */
    public void registerSticky(Object subscriber) {
        register(subscriber, true, 0);
    }

    /**
     * Like {@link #register(Object, int)}, but also triggers delivery of the most recent sticky event (posted with
     * {@link #postSticky(Object)}) to the given subscriber.
     * 这个是注册一个Sticky事件，有优先级的
     */
    public void registerSticky(Object subscriber, int priority) {
        register(subscriber, true, priority);
    }

    /**
     * 所有的注册都调用这个方法进行初始化
     * @param subscriber m
     * @param sticky
     * @param priority
     *
     */
    private synchronized void register(Object subscriber, boolean sticky, int priority) {
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriber.getClass());
        for (SubscriberMethod subscriberMethod : subscriberMethods) {
            subscribe(subscriber, subscriberMethod, sticky, priority);
        }
    }

    // Must be called in synchronized block

    /**
     * 订阅
     * @param subscriber 订阅者，也就是订阅的那个类
     * @param subscriberMethod ，这个是通过订阅者的类中的方法判断，生成的一个带有消息事件类的SubscriberMethod对象
     * @param sticky
     * @param priority
     */
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod, boolean sticky, int priority) {
        Class<?> eventType = subscriberMethod.eventType;
//        Manager.xujun("eventType=====>"+eventType.getName());
        /**
         * 假设有两个相同对象的event消息，也就是说在两个类中都有，这个时候就一个类（订阅者）添加过，
         * 另一个类（订阅者）就会获取到了
         */
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
//        Manager.xujun("subscriptions----->"+(subscriptions==null));//两个类中都有相同的消息

        /**
         * 如果两个消息来自不同的类（订阅者），这个时候下面这个new的就不一样，因为有不同的subscriber订阅者
         * 这个值永远是不同的
         */
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod, priority);


        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<Subscription>();//这个new，在后面进行了赋值吧，不然没有意义啊
            subscriptionsByEventType.put(eventType, subscriptions);//这个变量唯一put的地方，以这个eventType为key
        } else {
            if (subscriptions.contains(newSubscription)) {//如果不为空新的这个Subscription对象被包含的抛出异常
                throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                        + eventType);
            }
        }

        // Starting with EventBus 2.2 we enforced methods to be public (might change with annotations again)
        // subscriberMethod.method.setAccessible(true);

        int size = subscriptions.size();
//        Manager.xujun("size--->"+size);
        for (int i = 0; i <= size; i++) {//当第一次进来的时候size等于0 这个上时候就会add一个进去newSubscription
            if (i == size || newSubscription.priority > subscriptions.get(i).priority) {//有了这个判断加上上面的获取相同消息有几个的，就会讲权限大的放到前面去，并且这个方式会讲后订阅的类放到最前面去，最新的那个是每次都不同的，权限是默认0，越大权限越高
                subscriptions.add(i, newSubscription);
                break;
            }
        }
        //end 将所有值都放到了subscriptionsByEventType这个当中

        /**
         * 方式同上面一样先看又没有
         * 这个是以订阅者为key的，也就是我们写注册的那个类
         * 假设我这个类有两个消息类型这个时候这个subscribedEvents就会add两个进去
         */
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<Class<?>>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        //这里直接add进去了消息的类
        subscribedEvents.add(eventType);


        /**
         * 如果是sticky事件的话
         * 这个 todo
         */
        if (sticky) {
            if (eventInheritance) {  // 这个值默认是true
                // Existing sticky events of all subclasses of eventType have to be considered.
                // Note: Iterating over all events may be inefficient with lots of sticky events,
                // thus data structure should be changed to allow a more efficient lookup
                // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).

                /**
                 * 在如果是sticky事件的时候订阅的时候
                 * stickyEvents
                 * 是讲之前的消息的类class为key，和消息类存存起来的
                 */
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {//这里是循环的也就是消息对象都会执行
                    Class<?> candidateEventType = entry.getKey();
                    if (eventType.isAssignableFrom(candidateEventType)) {//isAssignableFrom   是用来判断一个类Class1和另一个类Class2是否相同或是另一个类的超类或接口。
                        Object stickyEvent = entry.getValue();//也就是消息类
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }

            } else {
                Object stickyEvent = stickyEvents.get(eventType);
                checkPostStickyEventToSubscription(newSubscription, stickyEvent);
            }
        }
    }

    //sticky消息的执行onevent方法
    private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
        if (stickyEvent != null) {
            // If the subscriber is trying to abort the event, it will fail (event is not tracked in posting state)
            // --> Strange corner case, which we don't take care of here.
            postToSubscription(newSubscription, stickyEvent, Looper.getMainLooper() == Looper.myLooper());
        }
    }

    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    /** Only updates subscriptionsByEventType, not typesBySubscriber! Caller must update typesBySubscriber.
     *
     * 当在取消订阅的时候，通过订阅者将订阅者中的消息给删除
     * */
    private void unubscribeByEventType(Object subscriber, Class<?> eventType) {
        /**
         * 这个方式将这个消息类型的所有订阅者对象取出，然后再通过判断将其删除
         */
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                Subscription subscription = subscriptions.get(i);
                if (subscription.subscriber == subscriber) { //相同订阅者的时候就给删除
                    subscription.active = false; // 标示为不活跃状态
                    subscriptions.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

    /** Unregisters the given subscriber from all event classes.
     *
     * 取消订阅的方法，通过上面存的类（也就是订阅者）因为用其作为的key，有两步，先将这个订阅者中
     * 消息类型删除，然后将订阅者删除
     * */
    public synchronized void unregister(Object subscriber) {
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unubscribeByEventType(subscriber, eventType);
            }
            typesBySubscriber.remove(subscriber);
        } else {
            Log.w(TAG, "Subscriber to unregister was not registered before: " + subscriber.getClass());
        }
    }

    /** Posts the given event to the event bus.
     *
     * 这个方法就是用来发送一个消息的
     * */
    public void post(Object event) {
        PostingThreadState postingState = currentPostingThreadState.get();//每次活得都是新的eventQueue
        List<Object> eventQueue = postingState.eventQueue;
        eventQueue.add(event);
//        Manager.xujun("eventQueue--->"+eventQueue.size());

        if (!postingState.isPosting) {
            postingState.isMainThread = Looper.getMainLooper() == Looper.myLooper();//判断是否为主线程
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                while (!eventQueue.isEmpty()) { //循环发送
                    postSingleEvent(eventQueue.remove(0), postingState);
                }
            } finally {
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }

    /**
     * Called from a subscriber's event handling method, further event delivery will be canceled. Subsequent
     * subscribers
     * won't receive the event. Events are usually canceled by higher priority subscribers (see
     * {@link #register(Object, int)}). Canceling is restricted to event handling methods running in posting thread
     * {@link ThreadMode#PostThread}.
     */
    public void cancelEventDelivery(Object event) {
        PostingThreadState postingState = currentPostingThreadState.get();
        if (!postingState.isPosting) {
            throw new EventBusException(
                    "This method may only be called from inside event handling methods on the posting thread");
        } else if (event == null) {
            throw new EventBusException("Event may not be null");
        } else if (postingState.event != event) {
            throw new EventBusException("Only the currently handled event may be aborted");
        } else if (postingState.subscription.subscriberMethod.threadMode != ThreadMode.PostThread) {
            throw new EventBusException(" event handlers may only abort the incoming event");
        }

        postingState.canceled = true;
    }

    /**
     * Posts the given event to the event bus and holds on to the event (because it is sticky). The most recent sticky
     * event of an event's type is kept in memory for future access. This can be {@link #registerSticky(Object)} or
     * {@link #getStickyEvent(Class)}.
     * map的put是会覆盖的，如果是两个一样的值，就会将前面那个覆盖了
     */
    public void postSticky(Object event) {
        synchronized (stickyEvents) {
            stickyEvents.put(event.getClass(), event);
        }
        // Should be posted after it is putted, in case the subscriber wants to remove immediately
        post(event);
    }

    /**
     * Gets the most recent sticky event for the given type.
     *
     * @see #postSticky(Object)
     */
    public <T> T getStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.get(eventType));
        }
    }

    /**
     * Remove and gets the recent sticky event for the given event type.
     *
     * @see #postSticky(Object)
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }

    /**
     * Removes the sticky event if it equals to the given event.
     *
     * @return true if the events matched and the sticky event was removed.
     * 这个remove方法是获取这个类的
     */
    public boolean removeStickyEvent(Object event) {
        synchronized (stickyEvents) {
            Class<?> eventType = event.getClass();
            Object existingEvent = stickyEvents.get(eventType);
            if (event.equals(existingEvent)) {
                stickyEvents.remove(eventType);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Removes all sticky events.
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            stickyEvents.clear();
        }
    }

    public boolean hasSubscriberForEvent(Class<?> eventClass) {
        List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
        if (eventTypes != null) {
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                CopyOnWriteArrayList<Subscription> subscriptions;
                synchronized (this) {
                    subscriptions = subscriptionsByEventType.get(clazz);
                }
                if (subscriptions != null && !subscriptions.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * post执行的方法
     * @param event TestEvent
     * @param postingState
     * @throws Error
     */
    private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
        Class<?> eventClass = event.getClass();
//        Manager.xujun("eventClass--->"+eventClass);
        /**
         * eventClass--->class com.eventbus.eventbustest.TestEvent
         */
        boolean subscriptionFound = false;
        if (eventInheritance) { //这个值默认是true
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass); //得到消息类相关的所有
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);//如果有这个消息的订阅者就为true了
            }
        } else {
            subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        }
        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                Log.d(TAG, "No subscribers registered for event " + eventClass);
            }
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(this, event));
            }
        }
    }

    /**
     * 找到这个消息相关的订阅者，然后循环的去执行onEvent
     * @param event
     * @param postingState
     * @param eventClass
     * @return
     */
    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);//通过消息类型的key获取当前的订阅者
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted = false;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 通过订阅和和消息，以及线程方式进行出来
     * @param subscription MainActivity
     * @param event  TestEvent
     * @param isMainThread true
     */
    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode) {
            case PostThread:
                invokeSubscriber(subscription, event);
                break;
            case MainThread:
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            case BackgroundThread:
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case Async:
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
        }
    }

    /** Looks up all Class objects including super classes and interfaces. Should also work for interfaces.
     *
     *  传进来的是一个消息类名,第二次进来的生活这个消息类就缓存下来了
     *  缓存的eventTypes 是消息类，以及他的一层一层的父类的类名的list
     * */
    private List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
        synchronized (eventTypesCache) {
            List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
//            Manager.xujun("eventTypes---->"+(eventTypes == null));
            if (eventTypes == null) {
                eventTypes = new ArrayList<Class<?>>();
                Class<?> clazz = eventClass;
                while (clazz != null) {
                    eventTypes.add(clazz);
                    /**
                     * clazz.getInterfaces()
                     * 确定此对象所表示的类或接口实现的接口。
                     如果此对象表示一个类，则返回值是一个数组，
                     它包含了表示该类所实现的所有接口的对象。
                     数组中接口对象顺序与此对象所表示的类的声明的 implements 子句中接口名顺序一致
                     */
                    addInterfaces(eventTypes, clazz.getInterfaces());
                    clazz = clazz.getSuperclass();
                }
                //用一个消息类名为key缓存一个eventTypes
                eventTypesCache.put(eventClass, eventTypes);
            }
            return eventTypes;
        }
    }

    /** Recurses through super interfaces.
     *  目的是将所有的类和接口全部都个加入到eventTypes
     *  比如TestEvent 实现A接口，然后A实现B接口，这个生活将TestEvent A B全部放入ß
     *  获得该类型实现的所有接口
     * */
    static void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
        for (Class<?> interfaceClass : interfaces) {
            if (!eventTypes.contains(interfaceClass)) {
                eventTypes.add(interfaceClass);
                addInterfaces(eventTypes, interfaceClass.getInterfaces());
            }
        }
    }

    /**
     * Invokes the subscriber if the subscriptions is still active. Skipping subscriptions prevents race conditions
     * between {@link #unregister(Object)} and event delivery. Otherwise the event might be delivered after the
     * subscriber unregistered. This is particularly important for main thread delivery and registrations bound to the
     * live cycle of an Activity or Fragment.
     */
    void invokeSubscriber(PendingPost pendingPost) {
        Object event = pendingPost.event;
        Subscription subscription = pendingPost.subscription;
        PendingPost.releasePendingPost(pendingPost);
        if (subscription.active) {
            invokeSubscriber(subscription, event);
        }
    }

    /**
     * 通过反射执行onEvent方法
     * @param subscription
     * @param event
     */

    void invokeSubscriber(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            handleSubscriberException(subscription, event, e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
        if (event instanceof SubscriberExceptionEvent) {
            if (logSubscriberExceptions) {
                // Don't send another SubscriberExceptionEvent to avoid infinite event recursion, just log
                Log.e(TAG, "SubscriberExceptionEvent subscriber " + subscription.subscriber.getClass()
                        + " threw an exception", cause);
                SubscriberExceptionEvent exEvent = (SubscriberExceptionEvent) event;
                Log.e(TAG, "Initial event " + exEvent.causingEvent + " caused exception in "
                        + exEvent.causingSubscriber, exEvent.throwable);
            }
        } else {
            if (throwSubscriberException) {
                throw new EventBusException("Invoking subscriber failed", cause);
            }
            if (logSubscriberExceptions) {
                Log.e(TAG, "Could not dispatch event: " + event.getClass() + " to subscribing class "
                        + subscription.subscriber.getClass(), cause);
            }
            if (sendSubscriberExceptionEvent) {
                SubscriberExceptionEvent exEvent = new SubscriberExceptionEvent(this, cause, event,
                        subscription.subscriber);
                post(exEvent);
            }
        }
    }

    /** For ThreadLocal, much faster to set (and get multiple values).
     *
     * */
    final static class PostingThreadState {
        final List<Object> eventQueue = new ArrayList<Object>();
        boolean isPosting;
        boolean isMainThread;
        Subscription subscription;
        Object event;
        boolean canceled;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    // Just an idea: we could provide a callback to post() to be notified, an alternative would be events, of course...
    /* public */interface PostCallback {
        void onPostCompleted(List<SubscriberExceptionEvent> exceptionEvents);
    }

}
