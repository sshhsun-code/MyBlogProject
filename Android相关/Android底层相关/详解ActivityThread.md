# 详解ActivityThread

1. Android中为什么主线程不会因为Looper.loop()里的死循环卡死？
2. ActivityThread是主线程吗？
3. ActivityThread的main方法讲解



参考： 

[https://www.zhihu.com/question/34652589/answer/90344494](https://www.zhihu.com/question/34652589/answer/90344494)

[https://www.jianshu.com/p/0efc71f349c8](https://www.jianshu.com/p/0efc71f349c8)

[https://www.cnblogs.com/younghao/p/5126408.html](https://www.cnblogs.com/younghao/p/5126408.html)

## 1. Android中为什么主线程不会因为Looper.loop()里的死循环卡死？

**(1) Android中为什么主线程不会因为Looper.loop()里的死循环卡死？**

这里涉及线程，先说说说进程/线程，

**进程**：每个app运行时前首先创建一个进程，该进程是由Zygote fork出来的，用于承载App上运行的各种Activity/Service等组件。进程对于上层应用来说是完全透明的，这也是google有意为之，让App程序都是运行在Android Runtime。大多数情况一个App就运行在一个进程中，除非在AndroidManifest.xml中配置Android:process属性，或通过native代码fork进程。

**线程**：线程对应用来说非常常见，比如每次new Thread().start都会创建一个新的线程。该线程与App所在进程之间资源共享，从Linux角度来说进程与线程除了是否共享资源外，并没有本质的区别，都是一个task_struct结构体，在CPU看来进程或线程无非就是一段可执行的代码，CPU采用CFS调度算法，保证每个task都尽可能公平的享有CPU时间片。

有了这么准备，再说说死循环问题：

对于线程既然是一段可执行的代码，当可执行代码执行完成后，线程生命周期便该终止了，线程退出。而对于主线程，我们是绝不希望会被运行一段时间，自己就退出，那么如何保证能一直存活呢？简单做法就是可执行代码是能一直执行下去的，死循环便能保证不会被退出，例如，binder线程也是采用死循环的方法，通过循环方式不同与Binder驱动进行读写操作，当然并非简单地死循环，无消息时会休眠。但这里可能又引发了另一个问题，既然是死循环又如何去处理其他事务呢？通过创建新线程的方式。

真正会卡死主线程的操作是在回调方法onCreate/onStart/onResume等操作时间过长，会导致掉帧，甚至发生ANR，looper.loop本身不会导致应用卡死。

**(2) 没看见哪里有相关代码为这个死循环准备了一个新线程去运转？**

事实上，会在进入进程初始化时便创建了新binder线程，该Binder线程通过Handler将Message发送给主线程。

另外，**ActivityThread实际上并非线程**，不像HandlerThread类，ActivityThread并没有真正继承Thread类，只是往往运行在主线程，该人以线程的感觉，其实承载ActivityThread的主线程就是由Zygote fork而创建的进程。

**主线程的死循环一直运行是不是特别消耗CPU资源呢**？其实不然，这里就涉及到**Linux pipe/epoll机制**，简单说就是在主线程的MessageQueue没有消息时，便阻塞在loop的queue.next()中的nativePollOnce()方法里，此时主线程会释放CPU资源进入休眠状态，直到下个消息到达或者有事务发生，通过往pipe管道写端写入数据来唤醒主线程工作。这里采用的epoll机制，是一种IO多路复用机制，可以同时监控多个描述符，当某个描述符就绪(读或写就绪)，则立刻通知相应程序进行读或写操作，本质同步I/O，即读写是阻塞的。 所以说，主线程大多数时候都是处于休眠状态，并不会消耗大量CPU资源。

**(3) Activity的生命周期是怎么实现在死循环体外能够执行起来的？**

ActivityThread的内部类H继承于Handler，通过handler消息机制，简单说Handler机制用于同一个进程的线程间通信。

**Activity的生命周期都是依靠主线程的Looper.loop，当收到不同Message时则采用相应措施：在H.handleMessage(msg)方法中，根据接收到不同的msg，执行相应的生命周期**。  

比如收到msg=H.LAUNCH_ACTIVITY，则调用ActivityThread.handleLaunchActivity()方法，最终会通过反射机制，创建Activity实例，然后再执行Activity.onCreate()等方法；    再比如收到msg=H.PAUSE_ACTIVITY，则调用ActivityThread.handlePauseActivity()方法，最终会执行Activity.onPause()等方法。

**主线程的消息又是哪来的呢？当然是App进程中的其他线程通过Handler发送给主线程**。





## 2. ActivityThread是主线程吗？

Android的UI主线程是ActivityThread吗？严格来说，不是的。

ActivityThread类是Android APP进程的初始类，它的main函数是这个APP进程的入口。APP进程中UI事件的执行代码段都是由ActivityThread提供的。也就是说，Main Thread
实例是存在的，只是创建它的代码我们不可见。ActivityThread的main函数就是在这个Main Thread里被执行的。

准确得说，运行ActivityThread的线程是UI线程或者是主线程。换句话说，at运行在应用程序主线程。

>辅助理解：
1. Java程序初始类中的main()方法，将作为该程序初始线程的起点，任何其他的线程都是由这个初始线程启动的。这个线程就是程序的主线程。 
2. 在Thread.java文件头部的说明中，有这样的介绍：Each application has at least one thread running when it is started, the main thread, in the main {@link ThreadGroup}.

## 3. ActivityThread的main方法讲解

从上面已知，main方法是整个ActivityThread的入口。而整个进程内**组件的生命周期等**方法都有其内部的Handler执行，即UI线程内部执行。所以其实`main`方法，是整个应用进程的入口。

### (1)ActivityThread管理着什么

![image](http://o9m6aqy3r.bkt.clouddn.com//Application/ActivityThread_Manage.png)

从图中可以知道，**mActivities**、**mServices**和**mProviderMap** 这三个变量都被保存在**ArrayMap**之中，他们分别保存了应用中所有的**Activity**对象、**Services**对象、和**ContentProvider**对象。 **BroadcastReceiver对象没有必要用任何数据结构来保存，因为BroadcastReceiver对象的生命周期很短暂，只在调用它时，再创建运行，因此不需要保存BroadcastReceiver的对象**。

我们都知道应用中Applicaiton对象是唯一的，而**mInitialApplication**变量是恰恰是Application对象。当你的应用自定义一个派生Applicaiton类，则它就是mInitialApplication了。

**ApplicationThread**类型变量mAppThread是一个**Binder**实体对象，**ActivityManagerService**作为Client端调用ApplicationThread的接口，目的是用来调度管理Activity，这个我们未来会细说。

变量**mResourcesManager**管理着应用中的资源。

**ActivityThread**，管理调度着几乎所有的Android应用进程的资源和四大组件。

### (2)ActivityThread的main方法


```
public static void More ...main(String[] args) {
    SamplingProfilerIntegration.start();

    // CloseGuard defaults to true and can be quite spammy.  We
    // disable it here, but selectively enable it later (via
    // StrictMode) on debug builds, but using DropBox, not logs.
    CloseGuard.setEnabled(false);
    // 初始化应用中需要使用的系统路径
    Environment.initForCurrentUser();

    // Set the reporter for event logging in libcore
    EventLogger.setReporter(new EventLoggingReporter());
    //增加一个保存key的provider
    Security.addProvider(new AndroidKeyStoreProvider());

    // Make sure TrustedCertificateStore looks in the right place for CA certificates
    //为应用设置当前用户的CA证书保存的位置
    final File configDir = Environment.getUserConfigDirectory(UserHandle.myUserId());
    TrustedCertificateStore.setDefaultUserDirectory(configDir);
    //设置进程的名称
    Process.setArgV0("<pre-initialized>");

    Looper.prepareMainLooper();
    //创建ActivityThread 对象
    ActivityThread thread = new ActivityThread();
    thread.attach(false);

    if (sMainThreadHandler == null) {
        sMainThreadHandler = thread.getHandler();
    }

    if (false) {
        Looper.myLooper().setMessageLogging(new
                LogPrinter(Log.DEBUG, "ActivityThread"));
    }

    Looper.loop();

    throw new RuntimeException("Main thread loop unexpectedly exited");
}

```
着重注意：


```
Looper.prepareMainLooper();
            //创建ActivityThread 对象
            ActivityThread thread = new ActivityThread();
            thread.attach(false);
            
            if (sMainThreadHandler == null) {
                    sMainThreadHandler =thread.getHandler();
            }

            if (false) {
                Looper.myLooper().setMessageLogging(new
                        LogPrinter(Log.DEBUG, "ActivityThread"));
            }
    
            Looper.loop();
    
            throw new RuntimeException("Main thread loop unexpectedly exited");
        }

```

首先Looper.prepareMainLooper();是为主线程创建了Looper，然后thread.getHandler();是保存了主线程的Handler，最后Looper.loop();进入消息循环。

之后调用 `thread.attach(false);`即：


```
if (!system) {
    ViewRootImpl.addFirstDrawHandler(new Runnable() {
        @Override
        public void More ...run() {
            ensureJitEnabled();
        }
    });
    android.ddm.DdmHandleAppName.setAppName("<pre-initialized>",
                                            UserHandle.myUserId());
    //将mAppThread放到RuntimeInit类中的静态变量
    RuntimeInit.setApplicationObject(mAppThread.asBinder());
    final IActivityManager mgr = ActivityManagerNative.getDefault();
    try {
        //将mAppThread传入ActivityThreadManager中
        mgr.attachApplication(mAppThread);
    } catch (RemoteException ex) {
        // Ignore
    }
    // Watch for getting close to heap limit.
    BinderInternal.addGcWatcher(new Runnable() {
        @Override public void More ...run() {
            if (!mSomeActivitiesChanged) {
                return;
            }
            Runtime runtime = Runtime.getRuntime();
            long dalvikMax = runtime.maxMemory();
            long dalvikUsed = runtime.totalMemory() - runtime.freeMemory();
            if (dalvikUsed > ((3*dalvikMax)/4)) {
                if (DEBUG_MEMORY_TRIM) Slog.d(TAG, "Dalvik max=" + (dalvikMax/1024)
                        + " total=" + (runtime.totalMemory()/1024)
                        + " used=" + (dalvikUsed/1024));
                mSomeActivitiesChanged = false;
                try {
                    mgr.releaseSomeActivities(mAppThread);
                } catch (RemoteException e) {
                }
            }
        }
    });
}

```
此时主要完成两件事：
1. 调用 RuntimeInit.setApplicationObject() 方法，把对象mAppThread（Binder）放到了RuntimeInit类中的静态变量mApplicationObject中
2. ` mgr.attachApplication(mAppThread);`将mAppThread传入ActivityThreadManager中.**ActivityManagerService通过attachApplication将ApplicationThread对象绑定到ActivityManagerService，而ApplicationThread作为Binder实现ActivityManagerService对应用进程的通信和控制**.

在ActivityManagerService内部，attachApplication实际是通过调用attachApplicationLocked实现的，这里采用了synchronized关键字保证同步。

attachApplicationLocked的实现较为复杂，其主要功能分为两部分：

- thread.bindApplication
- mStackSupervisor.attachApplicationLocked(app)


```
private final boolean attachApplicationLocked(IApplicationThread thread,
            int pid) {

        // Find the application record that is being attached...  either via
        // the pid if we are running in multiple processes, or just pull the
        // next app record if we are emulating process with anonymous threads.
        ProcessRecord app;
        if (pid != MY_PID && pid >= 0) {
            synchronized (mPidsSelfLocked) {
                app = mPidsSelfLocked.get(pid);
            }
        } else {
            app = null;
        }
       // ……
        try {
           // ……
            thread.bindApplication(processName, appInfo, providers, app.instrumentationClass,
                    profilerInfo, app.instrumentationArguments, app.instrumentationWatcher,
                    app.instrumentationUiAutomationConnection, testMode, enableOpenGlTrace,
                    enableTrackAllocation, isRestrictedBackupMode || !normalMode, app.persistent,
                    new Configuration(mConfiguration), app.compat,
                    getCommonServicesLocked(app.isolated),
                    mCoreSettingsObserver.getCoreSettingsLocked());
            updateLruProcessLocked(app, false, null);
            app.lastRequestedGc = app.lastLowMemory = SystemClock.uptimeMillis();
        } catch (Exception e) {
           
            app.resetPackageList(mProcessStats);
            app.unlinkDeathRecipient();
            startProcessLocked(app, "bind fail", processName);
            return false;
        }

        // See if the top visible activity is waiting to run in this process...
        if (normalMode) {
            try {
                if (mStackSupervisor.attachApplicationLocked(app)) {
                    didSomething = true;
                }
            } catch (Exception e) {
                Slog.wtf(TAG, "Exception thrown launching activities in " + app, e);
                badApp = true;
            }
        }
    // ……
    }
```

thread.bindApplication，最终会调用ApplicationThread的bindApplication方法。该bindApplication方法的实质是通过向ActivityThread的消息队列发送`BIND_APPLICATION`消息，消息的处理调用handleBindApplication方法，handleBindApplication方法比较重要的是会调用如下方法：


```
mInstrumentation.callApplicationOnCreate(app);
```
**callApplicationOnCreate即调用应用程序Application的onCreate()方法**，说明Application的onCreate()方法会比所有activity的onCreate()方法先调用。

mStackSupervisor为ActivityManagerService的成员变量，类型为ActivityStackSupervisor。


```
/** Run all ActivityStacks through this */
ActivityStackSupervisor mStackSupervisor;
```

注释可以看出，mStackSupervisor为Activity堆栈管理辅助类实例。ActivityStackSupervisor的attachApplicationLocked()方法的调用了realStartActivityLocked()方法，在realStartActivityLocked()方法中，会调用`scheduleLaunchActivity()`方法：


```
final boolean realStartActivityLocked(ActivityRecord r,
        ProcessRecord app, boolean andResume, boolean checkConfig)
        throws RemoteException {
 
    //...  
    try {
        //...
        app.thread.scheduleLaunchActivity(new Intent(r.intent), r.appToken,
                System.identityHashCode(r), r.info,
                new Configuration(mService.mConfiguration),
                r.compat, r.icicle, results, newIntents, !andResume,
                mService.isNextTransitionForward(), profileFile, profileFd,
                profileAutoStop);
 
        //...
 
    } catch (RemoteException e) {
        //...
    }
    //...    
    return true;
}
```
同bindApplication()方法，最终是通过向ActivityThread的消息队列发送消息，在ActivityThread完成实际的LAUNCH_ACTIVITY的操作


```
public void handleMessage(Message msg) {
    if (DEBUG_MESSAGES) Slog.v(TAG, ">>> handling: " + codeToString(msg.what));
    switch (msg.what) {
        case LAUNCH_ACTIVITY: {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "activityStart");
            final ActivityClientRecord r = (ActivityClientRecord) msg.obj;

            r.packageInfo = getPackageInfoNoCheck(
                r.activityInfo.applicationInfo, r.compatInfo);
            handleLaunchActivity(r, null);
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            } break;
    ……
}
```

**handleLaunchActivity()用于启动Activity**。


### (3)main方法调用路径，执行时机

Android进程创建流程中，当子进程被fork出来后，会执行相关的初始化方法，我们从`zygoteInit`开始说起：

zygoteInit

[–>RuntimeInit.java]


```
public static final void zygoteInit(int targetSdkVersion, String[] argv, ClassLoader classLoader) throws ZygoteInit.MethodAndArgsCaller {

    redirectLogStreams(); //重定向log输出

    commonInit(); // 通用的一些初始化
    nativeZygoteInit(); // zygote初始化 
    applicationInit(targetSdkVersion, argv, classLoader); // 应用初始化
}
```

#### [1] nativeZygoteInit在cpp层完成进程初始化工作

nativeZygoteInit()所对应的jni方法如下：

[–>AndroidRuntime.cpp]


```
static void com_android_internal_os_RuntimeInit_nativeZygoteInit(JNIEnv* env, jobject clazz) {
    //此处的gCurRuntime为AppRuntime，是在AndroidRuntime.cpp中定义的
    gCurRuntime->onZygoteInit();
}
```


```
virtual void onZygoteInit() {
    sp<ProcessState> proc = ProcessState::self();
    proc->startThreadPool(); //启动新binder线程
}
```
- ProcessState::self():主要工作是调用open()打开/dev/binder驱动设备，再利用mmap()映射内核的地址空间，将Binder驱动的fd赋值ProcessState对象中的变量mDriverFD，用于交互操作

- startThreadPool()是创建一个新的binder线程，启动Binder线程池，不断进行talkWithDriver()。

#### [2] applicationInit在Java层完成初始化操作，进入进程真正的入口

[–>RuntimeInit.java]


```
private static void applicationInit(int targetSdkVersion, String[] argv, ClassLoader classLoader) throws ZygoteInit.MethodAndArgsCaller {
    //true代表应用程序退出时不调用AppRuntime.onExit()，否则会在退出前调用
    nativeSetExitWithoutCleanup(true);

    //设置虚拟机的内存利用率参数值为0.75
    VMRuntime.getRuntime().setTargetHeapUtilization(0.75f);
    VMRuntime.getRuntime().setTargetSdkVersion(targetSdkVersion);

    final Arguments args;
    try {
        args = new Arguments(argv); //解析参数
    } catch (IllegalArgumentException ex) {
        return;
    }

    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);

    //调用startClass的static方法 main() 
    invokeStaticMain(args.startClass, args.startArgs, classLoader);
}
```

**此处args.startClass为”android.app.ActivityThread”**

 invokeStaticMain
 
 
```
private static void invokeStaticMain(String className, String[] argv, ClassLoader classLoader) throws ZygoteInit.MethodAndArgsCaller {
    Class<?> cl = Class.forName(className, true, classLoader);

    Method m = cl.getMethod("main", new Class[] { String[].class });

    int modifiers = m.getModifiers();
    ...

    //通过抛出异常，回到ZygoteInit.main()。这样做好处是能清空栈帧，提高栈帧利用率。
    throw new ZygoteInit.MethodAndArgsCaller(m, argv);
}
```

**反射执行了`ActivityThread.main`**。

ActivityThread.main

[–> ActivityThread.java]


```
public static void main(String[] args) {
    ...
    Environment.initForCurrentUser();
    ...
    Process.setArgV0("<pre-initialized>");
    //创建主线程looper
    Looper.prepareMainLooper();

    ActivityThread thread = new ActivityThread();
    //attach到系统进程
    thread.attach(false);

    if (sMainThreadHandler == null) {
        sMainThreadHandler = thread.getHandler();
    }

    //主线程进入循环状态
    Looper.loop();

    throw new RuntimeException("Main thread loop unexpectedly exited");
}
```


