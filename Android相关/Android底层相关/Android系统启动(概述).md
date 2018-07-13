# Android系统启动(概述)

参考： <br>
[http://gityuan.com/2016/02/01/android-booting/](http://gityuan.com/2016/02/01/android-booting/)

[一篇文章看明白 Android 系统启动时都干了什么](https://mp.weixin.qq.com/s?__biz=MzUzMDc4OTE0Mg==&mid=2247483750&idx=1&sn=e15a346b1d7771518f7c586e96353c23&chksm=fa4d271fcd3aae0929a9e998bf65365cecbc85022a1b633878a80af03f17d232e97bbae6c01b#rd)

**1. 概述**<br>
**2. init.rc文件**<br>
**3. zygote**<br>
**4. system_server**<br>
**5. app进程**<br>
**6. 总结**<br>


## 1. 概述

Android系统底层基于Linux Kernel, 当Kernel启动过程会创建init进程, 该进程是uoyou用户空间的鼻祖, init进程会启动servicemanager(binder服务管家), Zygote进程(Java进程的鼻祖). 

- **Zygote进程会创建system_server进程以及各种app进程**.

![image](http://o9m6aqy3r.bkt.clouddn.com//Application/android-booting.jpg)

## 2. init.rc文件

init是Linux系统中用户空间的第一个进程(pid=1), Kerner启动后会调用/system/core/init/Init.cpp的main()方法.

` Init.main`


```
int main(int argc, char** argv) {
    ...
    klog_init();  //初始化kernel log
    property_init(); //创建一块共享的内存空间，用于属性服务
    signal_handler_init();  //初始化子进程退出的信号处理过程

    property_load_boot_defaults(); //加载/default.prop文件
    start_property_service();   //启动属性服务器(通过socket通信)
    init_parse_config_file("/init.rc"); //解析init.rc文件

    //执行rc文件中触发器为 on early-init的语句
    action_for_each_trigger("early-init", action_add_queue_tail);
    //执行rc文件中触发器为 on init的语句
    action_for_each_trigger("init", action_add_queue_tail);
    //执行rc文件中触发器为 on late-init的语句
    action_for_each_trigger("late-init", action_add_queue_tail);

    while (true) {
        if (!waiting_for_exec) {
            execute_one_command();
            restart_processes();
        }
        int timeout = -1;
        if (process_needs_restart) {
            timeout = (process_needs_restart - gettime()) * 1000;
            if (timeout < 0)
                timeout = 0;
        }
        if (!action_queue_empty() || cur_action) {
            timeout = 0;
        }

        epoll_event ev;
        //循环 等待事件发生
        int nr = TEMP_FAILURE_RETRY(epoll_wait(epoll_fd, &ev, 1, timeout));
        if (nr == -1) {
            ERROR("epoll_wait failed: %s\n", strerror(errno));
        } else if (nr == 1) {
            ((void (*)()) ev.data.ptr)();
        }
    }
    return 0;
}
```

首先初始化 Kernel log，创建一块共享的内存空间，**加载 /default.prop 文件，解析 init.rc 文件**。

**init.rc 文件**

init.rc 文件是 Android 系统的重要配置文件，位于 /system/core/rootdir/ 目录中。 **主要功能是定义了系统启动时需要执行的一系列 action 及执行特定动作、设置环境变量和属性和执行特定的 service**。

init.rc 脚本文件配置了一些重要的服务，**init 进程通过创建子进程启动这些服务，这里创建的 service 都属于 native 服务，运行在 Linux 空间，通过 socket 向上层提供特定的服务，并以守护进程的方式运行在后台**。

通过 init.rc 脚本系统启动了以下几个重要的服务：

- service_manager：启动 binder IPC，管理所有的 Android 系统服务
- mountd：设备安装 Daemon，负责设备安装及状态通知
- debuggerd：启动 debug system，处理调试进程的请求
- rild：启动 radio interface layer daemon 服务，处理电话相关的事件和请求
- media_server：启动 AudioFlinger，MediaPlayerService 和 CameraService，负责多媒体播放相关的功能，包括音视频解码
- surface_flinger：启动 SurfaceFlinger 负责显示输出
- zygote：进程孵化器，启动 Android Java VMRuntime 和启动 systemserver，负责 Android 应用进程的孵化工作

**以上工作执行完，init 进程就会进入 loop 状态**。


## 3. zygote

当Zygote进程启动后, 便会执行到frameworks/base/cmds/app_process/App_main.cpp文件的main()方法. 整个调用流程:


```
App_main.main
    AndroidRuntime.start
        AndroidRuntime.startVm
        AndroidRuntime.startReg
        ZygoteInit.main (首次进入Java世界)
            registerZygoteSocket
            preload
            startSystemServer
            runSelectLoop
```
**Zygote 进程孵化了所有的 Android 应用进程，是 Android Framework 的基础，该进程的启动也标志着 Framework 框架初始化启动的开始**。

![image](http://o9m6aqy3r.bkt.clouddn.com//Application/zygote_fork.png)

Zygote 服务进程的主要功能：

- 注册底层功能的 JNI 函数到虚拟机
- 预加载 Java 类和资源
- fork 并启动 system_server 核心进程
- 作为守护进程监听处理“孵化新进程”的请求

**Zygote进程创建Java虚拟机,并注册JNI方法, 真正成为Java进程的母体,用于孵化Java进程. 在创建完system_server进程后,zygote功成身退，调用runSelectLoop()，随时待命，当接收到请求创建新进程请求时立即唤醒并执行相应工作**。



## 4. system_server

Zygote通过fork后创建system_server进程。

**handleSystemServerProcess**


```
private static void handleSystemServerProcess( ZygoteConnection.Arguments parsedArgs) throws ZygoteInit.MethodAndArgsCaller {
    ...
    if (parsedArgs.niceName != null) {
         //设置当前进程名为"system_server"
        Process.setArgV0(parsedArgs.niceName);
    }

    final String systemServerClasspath = Os.getenv("SYSTEMSERVERCLASSPATH");
    if (systemServerClasspath != null) {
        //执行dex优化操作,比如services.jar
        performSystemServerDexOpt(systemServerClasspath);
    }

    if (parsedArgs.invokeWith != null) {
        ...
    } else {
        ClassLoader cl = null;
        if (systemServerClasspath != null) {
            cl = new PathClassLoader(systemServerClasspath, ClassLoader.getSystemClassLoader());
            Thread.currentThread().setContextClassLoader(cl);
        }
        //进程初始化！！
        RuntimeInit.zygoteInit(parsedArgs.targetSdkVersion, parsedArgs.remainingArgs, cl);
    }
}
```

system_server进程创建PathClassLoader类加载器.

**RuntimeInit.zygoteInit**


```
public static final void zygoteInit(int targetSdkVersion, String[] argv, ClassLoader classLoader) throws ZygoteInit.MethodAndArgsCaller {

    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "RuntimeInit");
    redirectLogStreams(); //重定向log输出

    commonInit(); // 通用的一些初始化
    nativeZygoteInit(); // zygote初始化
    applicationInit(targetSdkVersion, argv, classLoader);
}
```
nativeZygoteInit()方法经过层层调用,会进入app_main.cpp中的onZygoteInit()方法, Binder线程池的创建也是在这个过程,如下:


```
virtual void onZygoteInit() {
    sp<ProcessState> proc = ProcessState::self();
    proc->startThreadPool(); //启动新binder线程
}
```

applicationInit()方法经过层层调用,会抛出异常ZygoteInit.MethodAndArgsCaller(m, argv), ZygoteInit.main() 会捕捉该异常, 见下文.

**ZygoteInit.main**


```
public static void main(String argv[]) {
    try {
        startSystemServer(abiList, socketName); //抛出MethodAndArgsCaller异常
        ....
    } catch (MethodAndArgsCaller caller) {
        caller.run(); //此处通过反射,会调用SystemServer.main()方法
    } catch (RuntimeException ex) {
        ...
    }
}
```

**SystemServer.main & SystemServer.run**


```
public final class SystemServer {
    ...
    public static void main(String[] args) {
        //先初始化SystemServer对象，再调用对象的run()方法
        new SystemServer().run();
    }
}




private void run() {
    if (System.currentTimeMillis() < EARLIEST_SUPPORTED_TIME) {
        Slog.w(TAG, "System clock is before 1970; setting to 1970.");
        SystemClock.setCurrentTimeMillis(EARLIEST_SUPPORTED_TIME);
    }
    ...

    Slog.i(TAG, "Entered the Android system server!");
    EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_SYSTEM_RUN, SystemClock.uptimeMillis());

    Looper.prepareMainLooper();// 准备主线程looper

    //加载android_servers.so库，该库包含的源码在frameworks/base/services/目录下
    System.loadLibrary("android_servers");

    //检测上次关机过程是否失败，该方法可能不会返回[见小节3.6.1]
    performPendingShutdown();

    createSystemContext(); //初始化系统上下文

    //创建系统服务管理
    mSystemServiceManager = new SystemServiceManager(mSystemContext);
    LocalServices.addService(SystemServiceManager.class, mSystemServiceManager);

    //启动各种系统服务[见小节3.7]
    try {
        startBootstrapServices(); // 启动引导服务
        startCoreServices();      // 启动核心服务
        startOtherServices();     // 启动其他服务
    } catch (Throwable ex) {
        Slog.e("System", "************ Failure starting system services", ex);
        throw ex;
    }

    //一直循环执行
    Looper.loop();
    throw new RuntimeException("Main thread loop unexpectedly exited");
}
```

**服务启动**


```
public final class SystemServer {

    private void startBootstrapServices() {
      ...
      //phase100
      mSystemServiceManager.startBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);
      ...
    }

    private void startOtherServices() {
        ...
        //phase480 和phase500
        mSystemServiceManager.startBootPhase(SystemService.PHASE_LOCK_SETTINGS_READY);
        mSystemServiceManager.startBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        ...
        //[见小节4.7]
        mActivityManagerService.systemReady(new Runnable() {
           @Override
           public void run() {
               //phase550
               mSystemServiceManager.startBootPhase(
                       SystemService.PHASE_ACTIVITY_MANAGER_READY);
               ...
               //phase600
               mSystemServiceManager.startBootPhase(
                       SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
            }
        }
    }
}
```

- start: 创建AMS, PMS, LightsService, DMS.
- phase100: 进入Phase100, 创建PKMS, WMS, IMS, DBMS, LockSettingsService, JobSchedulerService, MmsService等服务;
- phase480 && 500: 进入Phase480, 调用WMS, PMS, PKMS, DisplayManagerService这4个服务的systemReady();
- Phase550: 进入phase550, 执行AMS.systemReady(), 启动SystemUI, WebViewFactory, Watchdog.
- Phase600: 进入phase600, 执行AMS.systemReady(), 执行各种服务的systemRunning().
- Phase1000: 进入1000, 执行finishBooting, 启动启动on-hold进程.

**AMS.systemReady**


```
public final class ActivityManagerService extends ActivityManagerNative implements Watchdog.Monitor, BatteryStatsImpl.BatteryCallback {

    public void systemReady(final Runnable goingCallback) {
        ... //update相关
        mSystemReady = true;

        //杀掉所有非persistent进程
        removeProcessLocked(proc, true, false, "system update done");
        mProcessesReady = true;

        goingCallback.run(); 

        addAppLocked(info, false, null); //启动所有的persistent进程
        mBooting = true;

        //启动home
        startHomeActivityLocked(mCurrentUserId, "systemReady");
        //恢复栈顶的Activity
        mStackSupervisor.resumeTopActivitiesLocked();
    }
}
```
System_server主线程的启动工作,总算完成, 进入Looper.loop()状态,等待其他线程通过handler发送消息再处理.


## 5. app进程启动

对于普通的app进程,跟system_server进程的启动过来有些类似.不同的是app进程是向发消息给system_server进程, 由system_server向zygote发出创建进程的请求.

![image](http://o9m6aqy3r.bkt.clouddn.com//Application/start_app_process.jpg)

**针对Activity的启动，具体的启动流程为：**

![image](http://o9m6aqy3r.bkt.clouddn.com//Activity/start_activity_process.jpg)

**可知进程创建后 接下来会进入ActivityThread.main()过程。**

 **ActivityThread.main**
 
 
```
public static void main(String[] args) {
    ...
    Environment.initForCurrentUser();
    ...
    Process.setArgV0("<pre-initialized>");
    //创建主线程looper
    Looper.prepareMainLooper();

    ActivityThread thread = new ActivityThread();
    thread.attach(false); //attach到系统进程

    if (sMainThreadHandler == null) {
        sMainThreadHandler = thread.getHandler();
    }

    //主线程进入循环状态
    Looper.loop();
    throw new RuntimeException("Main thread loop unexpectedly exited");
}
```

### APP进程与System_server进程调用栈对比：

**app进程的主线程调用栈的栈底如下:**


```
...
at android.app.ActivityThread.main(ActivityThread.java:5442)
at java.lang.reflect.Method.invoke!(Native method)
at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:738)
at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:628)
```


**system_server进程调用栈对比:**


```
at com.android.server.SystemServer.main(SystemServer.java:175)
at java.lang.reflect.Method.invoke!(Native method)
at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:738)
at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:628)
```





## 6. 总结

**当我们开机时，首先是启动Linux内核，在Linux内核中首先启动的是init进程，这个进程会去读取配置文件system\core\rootdir\init.rc配置文件，这个文件中配置了Android系统中第一个进程Zygote进程**。

**启动Zygote进程 --> 创建AppRuntime（Android运行环境） --> 启动虚拟机 --> 在虚拟机中注册JNI方法 --> 初始化进程通信使用的Socket（用于接收AMS的请求） --> 启动系统服务进程 --> 初始化时区、键盘布局等通用信息 --> 启动Binder线程池 --> 初始化系统服务（包括PMS，AMS等等） --> 启动Launcher**


各大核心进程启动后，都会进入各种对象所相应的main()方法，如下：

进程	| 主方法
---|---
init进程	|Init.main()
zygote进程	|ZygoteInit.main()
app_process进程	|RuntimeInit.main
system_server进程	|SystemServer.main()
app进程	|ActivityThread.main()

app_process进程是指通过/system/bin/app_process来启动的进程，且后面跟的参数不带–zygote，即并非启动zygote进程。 比如常见的有通过adb shell方式来执行am,pm等命令，便是这种方式。


重要进程重启的过程，会触发哪些关联进程重启名单：

- zygote：触发media、netd以及子进程(包括system_server进程)重启
- system_server: 触发zygote重启;
- surfaceflinger：触发zygote重启;
- servicemanager: 触发zygote、healthd、media、surfaceflinger、drm重启