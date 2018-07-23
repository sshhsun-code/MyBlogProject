# WindowManagerService解析

**1. WMS概述**

**2. WindowManager体系**

**3. WMS启动过程**

参考：
<br>[https://blog.csdn.net/itachi85/article/details/78186741](https://blog.csdn.net/itachi85/article/details/78186741)

[http://gityuan.com/2017/01/08/windowmanger/](http://gityuan.com/2017/01/08/windowmanger/)

[http://liuwangshu.cn/framework/wm/1-windowmanager.html](http://liuwangshu.cn/framework/wm/1-windowmanager.html)




## 1. WMS概述

WMS继承于IWindowManager.Stub, 作为Binder服务端;处理WindowManager的操作。

其继承以及内部成员关系如下：

![http://o9m6aqy3r.bkt.clouddn.com//Window/wms_relation.jpg](http://o9m6aqy3r.bkt.clouddn.com//Window/wms_relation.jpg)

- 成员变量mSessions保存着所有的Session对象,Session继承于IWindowSession.Stub, 作为Binder服务端;
- 成员变量mPolicy: 实例对象为PhoneWindowManager,用于实现各种窗口相关的策略;
- 成员变量mChoreographer: 用于控制窗口动画,屏幕旋转等操作;
- 成员变量mDisplayContents: 记录一组DisplayContent对象,这个跟多屏输出相关;
- 成员变量mTokenMap: 保存所有的WindowToken对象; 以IBinder为key,可以是IAppWindowToken或者其他Binder的Bp端;
>- 另一端情况:ActivityRecord.Token extends IApplicationToken.Stub
- 成员变量mWindowMap: 保存所有的WindowState对象;以IBinder为key, 是IWindow的Bp端;
>- 另一端情况: ViewRootImpl.W extends IWindow.Stub
- 一般地,每一个窗口都对应一个WindowState对象, 该对象的成员变量mClient用于跟应用端交互,成员变量mToken用于跟AMS交互.

**总结来说，WMS主要有一下职责：**

**1. 窗口管理**

WMS是窗口的管理者，它负责窗口的启动、添加和删除，另外窗口的大小和层级也是由WMS进行管理的。窗口管理的核心成员有DisplayContent、WindowToken和WindowState。

**2. 窗口动画**

窗口间进行切换时，使用窗口动画可以显得更炫一些，窗口动画由WMS的动画子系统来负责，动画子系统的管理者为WindowAnimator。

**3. 输入系统的中转站**

通过对窗口的触摸从而产生触摸事件，InputManagerService（IMS）会对触摸事件进行处理，它会寻找一个最合适的窗口来处理触摸反馈信息，WMS是窗口的管理者，因此，WMS“理所应当”的成为了输入系统的中转站。

**4. Surface管理**

窗口并不具备有绘制的功能，因此每个窗口都需要有一块Surface来供自己绘制。为每个窗口分配Surface是由WMS来完成的。

WMS的职责可以简单总结为下图：

![http://o9m6aqy3r.bkt.clouddn.com//Window/WMS_Features.png](http://o9m6aqy3r.bkt.clouddn.com//Window/WMS_Features.png)

## 2. WindowManager体系

![image](http://o9m6aqy3r.bkt.clouddn.com//Window/WMS&WindowManager&Window&View.png)

**Window包含了View并对View进行管理，Window用虚线来表示是因为Window是一个抽象概念，并不是真实存在，Window的实体其实也是View。WindowManager用来管理Window，而WindowManager所提供的功能最终会由WMS来进行处理**。


从源码角度来分析WindowManager体系以及Window和WindowManager的关系：

**WindowManager**是一个接口类，继承自接口ViewManager，ViewManager中定义了三个方法，分别用来添加、更新和删除View： 

```
public interface ViewManager
{
    public void addView(View view, ViewGroup.LayoutParams params);
    public void updateViewLayout(View view, ViewGroup.LayoutParams params);
    public void removeView(View view);
}
```
WindowManager也继承了这些方法，而这些方法传入的参数都是View，说明WindowManager具体管理的是以View形式存在的Window。WindowManager在继承ViewManager的同时，又加入很多功能，包括Window的类型和层级相关的常量、内部类以及一些方法，其中有两个方法是根据Window的特性加入的，如下所示。


```
 public Display getDefaultDisplay();
 public void removeViewImmediate(View view);
```

- getDefaultDisplay方法会得知这个WindowManager实例将Window添加到哪个屏幕上了，换句话说，就是得到WindowManager所管理的屏幕（Display）
- removeViewImmediate方法则规定在这个方法前要立即执行View.onDetachedFromWindow()，来完成传入的View相关的销毁工作。

**Window**是一个抽象类，它的具体实现类为PhoneWindow。在Activity启动过程中会调用ActivityThread的performLaunchActivity方法，performLaunchActivity方法中又会调用Activity的attach方法。

从Activity的attach方法开始入手，如下所示：


```
final void attach(Context context, ActivityThread aThread,
            Instrumentation instr, IBinder token, int ident,
            Application application, Intent intent, ActivityInfo info,
            CharSequence title, Activity parent, String id,
            NonConfigurationInstances lastNonConfigurationInstances,
            Configuration config, String referrer, IVoiceInteractor voiceInteractor,
            Window window) {
        attachBaseContext(context);
        mFragments.attachHost(null /*parent*/);
        mWindow = new PhoneWindow(this, window);//1
        ...
         /**
         *2
         */
        mWindow.setWindowManager(
                (WindowManager)context.getSystemService(Context.WINDOW_SERVICE),
                mToken, mComponent.flattenToString(),
                (info.flags & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0);
        
        mWindowManager = mWindow.getWindowManager();
      ...
      
      
```

**注释1**处创建了PhoneWindow，在注释2处调用了PhoneWindow的setWindowManager方法，这个方法的具体的实现在PhoneWindow的父类Window中。 



```
    public void setWindowManager(WindowManager wm, IBinder appToken, String appName,
            boolean hardwareAccelerated) {
        mAppToken = appToken;
        mAppName = appName;
        mHardwareAccelerated = hardwareAccelerated
                || SystemProperties.getBoolean(PROPERTY_HARDWARE_UI, false);
        if (wm == null) {
            wm = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);//1
        }
        mWindowManager = ((WindowManagerImpl)wm).createLocalWindowManager(this);//2
    }

```
如果传入的WindowManager为null，就会在注释1处调用Context的getSystemService方法，并传入服务的名称Context.WINDOW_SERVICE（”window”），具体的实现在ContextImpl中：


```
    @Override
    public Object getSystemService(String name) {
        return SystemServiceRegistry.getSystemService(this, name);
    }

    @Override
    public String getSystemServiceName(Class<?> serviceClass) {
        return SystemServiceRegistry.getSystemServiceName(serviceClass);
    }

```

SystemServiceRegistry 的静态代码块中会调用多个registerService方法，这里只列举了和本文有关的一个：


```
final class SystemServiceRegistry {
...
 private SystemServiceRegistry() { }
 static {
 ...
   registerService(Context.WINDOW_SERVICE, WindowManager.class,
                new CachedServiceFetcher<WindowManager>() {
            @Override
            public WindowManager createService(ContextImpl ctx) {
                return new WindowManagerImpl(ctx);
            }});
...
 }
}
```
registerService方法会将传入的服务的名称存入到SYSTEM_SERVICE_NAMES中。从上面代码可以看出，传入的Context.WINDOW_SERVICE对应的就是WindowManagerImpl实例，因此得出结论，Context的getSystemService方法得到的是WindowManagerImpl实例。


再回到Window的setWindowManager方法，在**注释1**处得到WindowManagerImpl实例后转为WindowManager类型，在**注释2**处调用了WindowManagerImpl的createLocalWindowManager方法：


```
 public WindowManagerImpl createLocalWindowManager(Window parentWindow) {
        return new WindowManagerImpl(mContext, parentWindow);
    }
```

createLocalWindowManager方法同样也是创建WindowManagerImpl，不同的是这次创建WindowManagerImpl时将创建它的Window作为参数传了进来，这样WindowManagerImpl就持有了Window的引用，就可以对Window进行操作，比如 
在Window中添加View，来查看WindowManagerImpl的addView方法：


```
   @Override
    public void addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        applyDefaultToken(params);
        mGlobal.addView(view, params, mContext.getDisplay(), mParentWindow);//1
    }
```

注释1处调用了WindowManagerGlobal的addView方法，其中最后一个参数mParentWindow就是Window，可以看出**WindowManagerImpl虽然是WindowManager的实现类，但是却没有实现什么功能，而是将功能实现委托给了WindowManagerGlobal，这里用到的是桥接模式**。

查看WindowManagerImpl中如何定义的WindowManagerGlobal： 


```
public final class WindowManagerImpl implements WindowManager {
    private final WindowManagerGlobal mGlobal = WindowManagerGlobal.getInstance();
    private final Context mContext;
    private final Window mParentWindow;//1
...
  private WindowManagerImpl(Context context, Window parentWindow) {
        mContext = context;
        mParentWindow = parentWindow;
    }
 ...   
}
```

可以看出WindowManagerGlobal是一个单例，说明在一个进程中只有一个WindowManagerGlobal实例。**注释1处说明WindowManagerImpl可能会实现多个Window，也就是说在一个进程中WindowManagerImpl可能会有多个实例**。

**WindowManagerImpl中的成员变量mParentWindow指定对应的Window,即一个Window对应一个WindowManager;**

通过如上的源码分析，Window和WindowManager的关系如下图所示。

![http://o9m6aqy3r.bkt.clouddn.com//Window/Window&WindowManager.png](http://o9m6aqy3r.bkt.clouddn.com//Window/Window&WindowManager.png)

**PhoneWindow继承自Window，Window通过setWindowManager方法与WindowManager发生关联。WindowManager继承自接口ViewManager，WindowManagerImpl是WindowManager接口的实现类，但是具体的功能都会委托给WindowManagerGlobal来实现**。

## 3. WMS启动过程

WMS是在SyetemServer进程中启动的。

查看SyetemServer的main方法： 


```
public static void main(String[] args) {
       new SystemServer().run();
}
```
main方法中只调用了SystemServer的run方法，如下所示：


```
  private void run() {
         try {
            System.loadLibrary("android_servers");//1
            ...
            mSystemServiceManager = new SystemServiceManager(mSystemContext);//2
            mSystemServiceManager.setRuntimeRestarted(mRuntimeRestart);
            LocalServices.addService(SystemServiceManager.class, mSystemServiceManager);
            // Prepare the thread pool for init tasks that can be parallelized
            SystemServerInitThreadPool.get();
        } finally {
            traceEnd();  // InitBeforeStartServices
        }
        try {
            traceBeginAndSlog("StartServices");
            startBootstrapServices();//3
            startCoreServices();//4
            startOtherServices();//5
            SystemServerInitThreadPool.shutdown();
        } catch (Throwable ex) {
            Slog.e("System", "******************************************");
            Slog.e("System", "************ Failure starting system services", ex);
            throw ex;
        } finally {
            traceEnd();
        }
    ...
    }
```

**注释1**处加载了libandroid_servers.so

**注释2**处创建SystemServiceManager，它会**对系统的服务进行创建、启动和生命周期管理。接下来的代码会启动系统的各种服务**

**注释3**中的startBootstrapServices方法中用**SystemServiceManager启动了ActivityManagerService、PowerManagerService、PackageManagerService等服务**

**注释4**处的方法中则启动**了BatteryService、UsageStatsService和WebViewUpdateService等服务**

**注释5**处的startOtherServices方法中则启动了**CameraService、AlarmManagerService、VrManagerService等服务**，这些服务的父类为SystemService。

从注释3、4、5的方法名称可以看出，**官方把大概80多个系统服务分为了三种类型，分别是引导服务、核心服务和其他服务，其中其他服务为一些非紧要和一些不需要立即启动的服务，WMS就是其他服务的一种**。

startOtherServices方法是如何启动WMS：


```
 private void startOtherServices() {
 ...
            traceBeginAndSlog("InitWatchdog");
            final Watchdog watchdog = Watchdog.getInstance();//1
            watchdog.init(context, mActivityManagerService);//2
            traceEnd();
            traceBeginAndSlog("StartInputManagerService");
            inputManager = new InputManagerService(context);//3
            traceEnd();
            traceBeginAndSlog("StartWindowManagerService");
            ConcurrentUtils.waitForFutureNoInterrupt(mSensorServiceStart, START_SENSOR_SERVICE);
            mSensorServiceStart = null;
            wm = WindowManagerService.main(context, inputManager,
                    mFactoryTestMode != FactoryTest.FACTORY_TEST_LOW_LEVEL,
                    !mFirstBoot, mOnlyCore, new PhoneWindowManager());//4
            ServiceManager.addService(Context.WINDOW_SERVICE, wm);//5
            ServiceManager.addService(Context.INPUT_SERVICE, inputManager);//6
            traceEnd();   
           ... 
           try {
            wm.displayReady();//7
               } catch (Throwable e) {
            reportWtf("making display ready", e);
              }
           ...
           try {
            wm.systemReady();//8
               } catch (Throwable e) {
            reportWtf("making Window Manager Service ready", e);
              }
            ...      
}
```
注释1、2处分别得到Watchdog实例并对它进行初始化，Watchdog用来监控系统的一些关键服务的运行状况

注释3处创建了IMS，并赋值给IMS类型的inputManager对象

注释4处执行了**WMS的main方法，其内部会创建WMS，需要注意的是main方法其中一个传入的参数就是注释1处创建的IMS，WMS是输入事件的中转站，其内部包含了IMS引用并不意外**结合上文，我们可以得知**WMS的main方法是运行在SystemServer的run方法中，换句话说就是运行在”system_server”线程”中**

注释5和注释6处**分别将WMS和IMS注册到ServiceManager**中，这样如果某个客户端想要使用WMS，就需要先去ServiceManager中查询信息，然后根据信息与WMS所在的进程建立通信通路，客户端就可以使用WMS了

注释7处用来**初始化显示信息**

注释8处则用来**通知WMS，系统的初始化工作已经完成，其内部调用了WindowManagerPolicy的systemReady方法**



WMS的main方法，如下所示：


```
 public static WindowManagerService main(final Context context, final InputManagerService im,
            final boolean haveInputMethods, final boolean showBootMsgs, final boolean onlyCore,
            WindowManagerPolicy policy) {
        DisplayThread.getHandler().runWithScissors(new Runnable() {
            @Override
            public void run() {
             sInstance = new WindowManagerService(context, im, haveInputMethods, showBootMsgs,
                        onlyCore, policy);//2
            }
        }, 0);

        return sInstance;
    }
```

其中创建了WMS的实例，这个过程运行在Runnable的run方法中，而Runnable则传入到了DisplayThread对应Handler的runWithScissors方法中，说明WMS的创建是运行在“android.display”线程中。需要注意的是，runWithScissors方法的第二个参数传入的是0，后面会提到。来查看Handler的runWithScissors方法里做了什么：


```
 public final boolean runWithScissors(final Runnable r, long timeout) {
        if (r == null) {
            throw new IllegalArgumentException("runnable must not be null");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }
        if (Looper.myLooper() == mLooper) {//1
            r.run();
            return true;
        }
        BlockingRunnable br = new BlockingRunnable(r);
        return br.postAndWait(this, timeout);
    }
```

开头对传入的Runnable和timeout进行了判断，如果Runnable为null或者timeout小于0则抛出异常。注释1处根据每个线程只有一个Looper的原理来判断当前的线程（”system_server”线程）是否是Handler所指向的线程（”android.display”线程），如果是则直接执行Runnable的run方法，如果不是则调用BlockingRunnable的postAndWait方法，并将当前线程的Runnable作为参数传进去 ，BlockingRunnable是Handler的内部类，代码如下所示：


```
private static final class BlockingRunnable implements Runnable {
        private final Runnable mTask;
        private boolean mDone;
        public BlockingRunnable(Runnable task) {
            mTask = task;
        }
        @Override
        public void run() {
            try {
                mTask.run();//1
            } finally {
                synchronized (this) {
                    mDone = true;
                    notifyAll();
                }
            }
        }
        public boolean postAndWait(Handler handler, long timeout) {
            if (!handler.post(this)) {//2
                return false;
            }
            synchronized (this) {
                if (timeout > 0) {
                    final long expirationTime = SystemClock.uptimeMillis() + timeout;
                    while (!mDone) {
                        long delay = expirationTime - SystemClock.uptimeMillis();
                        if (delay <= 0) {
                            return false; // timeout
                        }
                        try {
                            wait(delay);
                        } catch (InterruptedException ex) {
                        }
                    }
                } else {
                    while (!mDone) {
                        try {
                            wait();//3
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
            return true;
        }
    }
```

注释2处将**当前的BlockingRunnable添加到Handler的任务队列中**。前面**runWithScissors方法的第二个参数为0，因此timeout等于0**，这样**如果mDone为false的话会一直调用注释3处的wait方法使得当前线程（”system_server”线程）进入等待状态**，那么等待的是哪个线程呢？我们往上看，注释1处，执行了传入的Runnable的run方法（运行在”android.display”线程），**执行完毕后在finally代码块中将mDone设置为true**，并调用notifyAll方法唤醒处于等待状态的线程，这样就不会继续调用注释3处的wait方法。

**因此得出结论，”system_server”线程线程等待的就是”android.display”线程，一直到”android.display”线程执行完毕再执行”system_server”线程，这是因为”android.display”线程内部执行了WMS的创建，显然WMS的创建优先级更高些**。 


查看WMS的构造方法：


```
   private WindowManagerService(Context context, InputManagerService inputManager,
            boolean haveInputMethods, boolean showBootMsgs, boolean onlyCore) {
       ...
       mInputManager = inputManager;//1
       ...
        mDisplayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        mDisplays = mDisplayManager.getDisplays();//2
        for (Display display : mDisplays) {
            createDisplayContentLocked(display);//3
        }
       ...
        mActivityManager = ActivityManagerNative.getDefault();//4
       ...
        mAnimator = new WindowAnimator(this);//5
        mAllowTheaterModeWakeFromLayout = context.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromWindowLayout);
        LocalServices.addService(WindowManagerInternal.class, new LocalService());
        initPolicy();//6
        // Add ourself to the Watchdog monitors.
        Watchdog.getInstance().addMonitor(this);//7
     ...
    }
```

注释1处用来保存传进来的IMS，这样WMS就持有了IMS的引用;

注释2处通过DisplayManager的getDisplays方法得到Display数组（每个显示设备都有一个Display实例），接着遍历Display数组;

在注释3处的createDisplayContentLocked方法会将Display封装成DisplayContent，DisplayContent用来描述一块屏幕；

注释4处得到AMS实例，并赋值给mActivityManager ，这样WMS就持有了AMS的引用；

释5处创建了WindowAnimator，它用于管理所有的窗口动画；

注释6处初始化了窗口管理策略的接口类WindowManagerPolicy（WMP），它用来定义一个窗口策略所要遵循的通用规范；

注释7处将自身也就是WMS通过addMonitor方法添加到Watchdog中，Watchdog用来监控系统的一些关键服务的运行状况（比如传入的WMS的运行状况），这些被监控的服务都会实现Watchdog.Monitor接口。Watchdog每分钟都会对被监控的系统服务进行检查，如果被监控的系统服务出现了死锁，则会杀死Watchdog所在的进程，也就是SystemServer进程。


查看注释6处的initPolicy方法，如下所示。 


```
   private void initPolicy() {
        UiThread.getHandler().runWithScissors(new Runnable() {
            @Override
            public void run() {
                WindowManagerPolicyThread.set(Thread.currentThread(), Looper.myLooper());
                mPolicy.init(mContext, WindowManagerService.this, WindowManagerService.this);//1
            }
        }, 0);
    }
```

initPolicy方法和此前讲的WMS的main方法的实现类似，

**注释1处执行了WMP的init方法，WMP是一个接口，init方法的具体实现在PhoneWindowManager（PWM）中**。PWM的init方法运行在”android.ui”线程中，它的优先级要高于initPolicy方法所在的”android.display”线程，**因此”android.display”线程要等PWM的init方法执行完毕后，处于等待状态的”android.display”线程才会被唤醒从而继续执行下面的代码**。

整个启动过程涉及3个线程: system_server主线程, “android.display”, “android.ui”, 整个过程是采用阻塞方式(利用Handler.runWithScissors)执行的. 其中WindowManagerService.mH的Looper运行在 “android.display”进程，也就意味着WMS.H.handleMessage()在该线程执行。 

流程如下：

![image](http://o9m6aqy3r.bkt.clouddn.com//Window/wms_startup.jpg)


---

1. system_server线程中会调用WMS的main方法，main方法中会创建WMS，创建WMS的过程运行在”android.display”线程中，它的优先级更高一些，因此要等创建WMS完毕后才会唤醒处于等待状态的”system_server”线程.

2. WMS初始化时会执行initPolicy方法，initPolicy方法会调用PWM的init方法，这个init方法运行在”android.ui”线程，并且优先级更高，因此要先执行完PWM的init方法后，才会唤醒处于等待状态的”android.display”线程。

3. PWM的init方法执行完毕后会接着执行运行在”system_server”线程的代码，比如本文前部分提到WMS的 
systemReady方法。
