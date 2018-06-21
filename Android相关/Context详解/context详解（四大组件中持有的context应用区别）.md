# Context详解
1. 什么是Context
2. 四大组件中的Context的生成与使用区别
3. Context应用场景
4. 补充


## 1. 什么是Context

Context，如果是直接从文档翻译过来，就真的是十足的专业了：提供应用环境全局信息的接口，并且这个接口是由抽象类实现的，它的执行被android系统所提供，允许我们获取以应用为特征的资源和类型，同时启动应用级的操作，如启动Activity，broadcasting和接收intent。

从上面描述可知三点，即：

- 它描述的是一个`应用程序`环境的信息，即上下文。
- 该类是一个抽象(abstract class)类，Android提供了该抽象类的具体实现类(ContextIml类)。
- 过它我们可以获取应用程序的资源和类，也包括一些应用级别操作，例如：启动一个Activity，发送广播，接受Intent信息 等




![image](http://o9m6aqy3r.bkt.clouddn.com//context/context_app_activity_service.png)

结合上面的结构图，我们发现，Context类本身是一个纯abstract类，它有两个子类：ContextImpl和ContextWrapper。但是**ContextImpl是唯一做具体工作的，其他实现都是对CI做代理**。

其中**ContextWrapper类，如其名所言，这只是一个包装而已**，ContextWrapper构造函数中必须包含一个真正的Context引用，同时ContextWrapper中提供了attachBaseContext（）用于给ContextWrapper对象中指定真正的Context对象，调用ContextWrapper的方法都会被转向其所包含的真正的Context对象。

**ContextThemeWrapper类，如其名所言，其内部包含了与主题（Theme）相关的接口**，这里所说的主题就是指在AndroidManifest.xml中通过android：theme为Application元素或者Activity元素指定的主题。

当然，只有Activity才需要主题，Service是不需要主题的，因为Service是没有界面的后台场景，所以Service直接继承于ContextWrapper，Application同理。

而ContextImpl类则真正实现了Context中的所以函数，应用程序中所调用的各种Context类的方法，其实现均来自于该类。

**一句话总结：Context的两个子类分工明确，其中ContextImpl是Context的具体实现类，ContextWrapper是Context的包装类。Activity，Application，Service虽都继承自ContextWrapper（Activity继承自ContextWrapper的子类ContextThemeWrapper），但它们初始化的过程中都会创建ContextImpl对象，由ContextImpl实现Context中的方法。**

>#### 一个应用程序有几个Context
>其实这个问题本身并没有什么意义，关键还是在于对Context的理解，从上面的关系图我们已经可以得出答案了，在应用程序中Context的具体实现子类就是：Activity，Service，Application。那么Context数量=Activity数量+Service数量+1。当然如果你足够细心，可能会有疑问：我们常说四大组件，这里怎么**只有Activity，Service持有Context，那Broadcast Receiver，Content Provider呢？Broadcast Receiver，ContentProvider并不是Context的子类，他们所持有的Context都是其他地方传过去的，所以并不计入Context总数**。上面的关系图也从另外一个侧面告诉我们Context类在整个Android系统中的地位是多么的崇高，因为很显然Activity，Service，Application都是其子类，其地位和作用不言而喻。

**只有Activity，Service持有Context，那Broadcast Receiver，Content Provider呢？Broadcast Receiver，ContentProvider并不是Context的子类，他们所持有的Context都是其他地方传过去的，所以并不计入Context总数。**

---
>####  getApplication和getApplicationContext有什么区别呢？
>![image](http://o9m6aqy3r.bkt.clouddn.com//context/getApplication.png)<br>
>通过上面的代码，打印得出两者的内存地址都是相同的，看来它们是同一个对象。Application本身就是一个Context，所以这里获取getApplicationContext()得到的结果就是Application本身的实例

>**这两个方法在作用域上有比较大的区别。getApplication()方法的语义性非常强，一看就知道是用来获取Application实例的，但是这个方法只有在Activity和Service中才能调用的到。那么也许在绝大多数情况下我们都是在Activity或者Service中使用Application的，但是如果在一些其它的场景，比如BroadcastReceiver中也想获得Application的实例，这时就可以借助getApplicationContext()方法了。**


## 2.四大组件中的Context的生成与使用区别

**（1）Activity对象中ContextImpl的创建**

ActivityThread中的performLaunchActivity方法：
```
if (activity != null) {  
    Context appContext = createBaseContextForActivity(r, activity);  
    /** 
     *  createBaseContextForActivity中创建ContextImpl的代码 
     *  ContextImpl appContext = new ContextImpl(); 
     *  appContext.init(r.packageInfo, r.token, this); 
     *  appContext.setOuterContext(activity); 
     */  
    CharSequence title = r.activityInfo.loadLabel(appContext.getPackageManager());  
    Configuration config = new Configuration(mCompatConfiguration);  
    if (DEBUG_CONFIGURATION) Slog.v(TAG, "Launching activity "  
            + r.activityInfo.name + " with config " + config);  
    activity.attach(appContext, this, getInstrumentation(), r.token,  
            r.ident, app, r.intent, r.activityInfo, title, r.parent,  
            r.embeddedID, r.lastNonConfigurationInstances, config);  
  
    if (customIntent != null) {  
        activity.mIntent = customIntent;  
    }  
    ...  
} 
```

**createBaseContextForActivity中创建ContextImpl:**
```
     private ContextImpl createBaseContextForActivity(ActivityClientRecord r) {
        final int displayId;
        try {
            displayId = ActivityManager.getService().getActivityDisplayId(r.token);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        ContextImpl appContext = ContextImpl.createActivityContext(
                this, r.loadedApk, r.activityInfo, r.token, displayId, r.overrideConfig);

        final DisplayManagerGlobal dm = DisplayManagerGlobal.getInstance();
        // For debugging purposes, if the activity's package name contains the value of
        // the "debug.use-second-display" system property as a substring, then show
        // its content on a secondary display if there is one.
        String pkgName = SystemProperties.get("debug.second-display.pkg");
        if (pkgName != null && !pkgName.isEmpty()
                && r.loadedApk.mPackageName.contains(pkgName)) {
            for (int id : dm.getDisplayIds()) {
                if (id != Display.DEFAULT_DISPLAY) {
                    Display display =
                            dm.getCompatibleDisplay(id, appContext.getResources());
                    appContext = (ContextImpl) appContext.createDisplayContext(display);
                    break;
                }
            }
        }
        return appContext;
    }
    
```    
#### 这个步骤中初始化时，将ActivityRecord中相关信息设置进了ContextImpl中！！！

AT.handleLaunchActivity():将有以下操作

**1.AT.performLaunchActivity:这个方法有以下操作:**

- 创建对象LoadedApk(后称LA,一个app只加载一次)
- 创建对象Activity
- 创建对象Application(一个app，只创建一次)
- **创建对象CI:CI.createActivityContext()**
- Application/CI都attach到Activity对象:Activity.attach()
- 执行onCreate():Instrumentation.callActivityOnCreate()-->Activity.performCreate()-->Activity.onCreate()
- 执行onStart():AT.performLaunchActivity-->Activity.performStart()-->>Instrumentation.callActivityOnStart()—>Activity.onStart()

**2.AT.handleResumeActivity():**
- AT.performResumeActivity()-->Activity.performResume()-->Instrumentation.callActivityOnResume()-->Activity.onResume()
- Activity.makeVisible()-->WindowManager.addView():开始进行View的绘制流程。

**从上面我们可以总结一下:在AMS将调用交给app进程之后，三个生命周期都是在app进程被回调的，并且在onResume()之后View才进行绘制**

>Activity在创建的时候会new一个ContextImpl对象并在attach方法中关联它，需要注意的是，**创建Activity使用的数据结构是ActivityClientRecord**


**(二)Application对象中ContextImpl的创建**

ActivityThread中的handleBindApplication方法中，
```
private void handleBindApplication(AppBindData data) {
    //step 1: 创建LoadedApk对象
    data.info = getPackageInfoNoCheck(data.appInfo, data.compatInfo);
    ...
    //step 2: 创建ContextImpl对象;
    final ContextImpl appContext = ContextImpl.createAppContext(this, data.info);

    //step 3: 创建Instrumentation
    mInstrumentation = new Instrumentation();

    //step 4: 创建Application对象;
    Application app = data.info.makeApplication(data.restrictedBackupMode, null);
    mInitialApplication = app;

    //step 5: 安装providers
    List<ProviderInfo> providers = data.providers;
    installContentProviders(app, providers);

    //step 6: 执行Application.Create回调
    mInstrumentation.callApplicationOnCreate(app);
```
1. 创建对象LA
2. 创建对象CI
3. 创建对象Instrumentation
4. 创建对象Application;
5. 安装providers
6. 执行Create回调

此方法内部调用了makeApplication方法：

```
public Application makeApplication(boolean forceDefaultAppClass,  
        Instrumentation instrumentation) {  
    if (mApplication != null) {  
        return mApplication;  
    }  
  
    Application app = null;  
  
    String appClass = mApplicationInfo.className;  
    if (forceDefaultAppClass || (appClass == null)) {  
        appClass = "android.app.Application";  
    }  
  
    try {  
        java.lang.ClassLoader cl = getClassLoader();  
        ContextImpl appContext = new ContextImpl();  
        appContext.init(this, null, mActivityThread);  
        app = mActivityThread.mInstrumentation.newApplication(  
                cl, appClass, appContext);  
        appContext.setOuterContext(app);  
    } catch (Exception e) {  
        if (!mActivityThread.mInstrumentation.onException(app, e)) {  
            throw new RuntimeException(  
                "Unable to instantiate application " + appClass  
                + ": " + e.toString(), e);  
        }  
    }  
    ...  
} 

```
可以发现，这里ContentImpl的产生和Activity中的过程类似，**但是Activity中的Context由createBaseContextForActivity产生，而Application中的Context中则是createAppContext**。

**createBaseContextForActivity**

[-> ActivityThread.java]
```
private Context createBaseContextForActivity(ActivityClientRecord r, final Activity activity) {
    int displayId = Display.DEFAULT_DISPLAY;
    try {
        displayId = ActivityManagerNative.getDefault().getActivityDisplayId(r.token);
    } catch (RemoteException e) {
    }

    //创建ContextImpl对象
    ContextImpl appContext = ContextImpl.createActivityContext(
            this, r.packageInfo, displayId, r.overrideConfig);
    appContext.setOuterContext(activity);
    Context baseContext = appContext;
    ...

    return baseContext;
}
```
[-> ContextImpl.java]
```
    static ContextImpl createActivityContext(ActivityThread mainThread,
            LoadedApk loadedApk, ActivityInfo activityInfo, IBinder activityToken, int displayId,
            Configuration overrideConfiguration) {
        if (loadedApk == null) throw new IllegalArgumentException("loadedApk");

        String[] splitDirs = loadedApk.getSplitResDirs();
        ClassLoader classLoader = loadedApk.getClassLoader();

        if (loadedApk.getApplicationInfo().requestsIsolatedSplitLoading()) {
            Trace.traceBegin(Trace.TRACE_TAG_RESOURCES, "SplitDependencies");
            try {
                classLoader = loadedApk.getSplitClassLoader(activityInfo.splitName);
                splitDirs = loadedApk.getSplitPaths(activityInfo.splitName);
            } catch (NameNotFoundException e) {
                // Nothing above us can handle a NameNotFoundException, better crash.
                throw new RuntimeException(e);
            } finally {
                Trace.traceEnd(Trace.TRACE_TAG_RESOURCES);
            }
        }

        ContextImpl context = new ContextImpl(null, mainThread, loadedApk, activityInfo.splitName,
                activityToken, null, 0, classLoader);

        // Clamp display ID to DEFAULT_DISPLAY if it is INVALID_DISPLAY.
        displayId = (displayId != Display.INVALID_DISPLAY) ? displayId : Display.DEFAULT_DISPLAY;

        final CompatibilityInfo compatInfo = (displayId == Display.DEFAULT_DISPLAY)
                ? loadedApk.getCompatibilityInfo()
                : CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO;

        final ResourcesManager resourcesManager = ResourcesManager.getInstance();

        // Create the base resources for which all configuration contexts for this Activity
        // will be rebased upon.
        context.setResources(resourcesManager.createBaseActivityResources(activityToken,
                loadedApk.getResDir(),
                splitDirs,
                loadedApk.getOverlayDirs(),
                loadedApk.getApplicationInfo().sharedLibraryFiles,
                displayId,
                overrideConfiguration,
                compatInfo,
                classLoader));
        context.mDisplay = resourcesManager.getAdjustedDisplay(displayId,
                context.getResources());
        return context;
    }
```

**createAppContext**

[-> ContextImpl.java]
```
    static ContextImpl createAppContext(ActivityThread mainThread, LoadedApk loadedApk) {
        if (loadedApk == null) throw new IllegalArgumentException("loadedApk");
        ContextImpl context = new ContextImpl(null, mainThread, loadedApk, null, null, null, 0,
                null);
        context.setResources(loadedApk.getResources());
        return context;
    }
```

**(三)Service初始化时创建ContextImpl**

```
private void handleCreateService(CreateServiceData data) {
    ...
    //step 1: 创建LoadedApk
    LoadedApk packageInfo = getPackageInfoNoCheck(
        data.info.applicationInfo, data.compatInfo);

    java.lang.ClassLoader cl = packageInfo.getClassLoader();
    //step 2: 创建Service对象
    service = (Service) cl.loadClass(data.info.name).newInstance();

    //step 3: 创建ContextImpl对象
    ContextImpl context = ContextImpl.createAppContext(this, packageInfo);
    context.setOuterContext(service);

    //step 4: 创建Application对象
    Application app = packageInfo.makeApplication(false, mInstrumentation);

    //step 5: 将Application/ContextImpl都attach到Activity对象 [见小节4.2]
    service.attach(context, this, data.info.name, data.token, app,
            ActivityManagerNative.getDefault());

    //step 6: 执行onCreate回调
    service.onCreate();
    mServices.put(data.token, service);
    ActivityManagerNative.getDefault().serviceDoneExecuting(
            data.token, SERVICE_DONE_EXECUTING_ANON, 0, 0);
    ...
}
```
跟Application一样，由createAppContext产生Context.

**(四)Receiver初始化时赋值ContextImpl**

[-> ActivityThread.java]

```
private void handleReceiver(ReceiverData data) {
    ...
    String component = data.intent.getComponent().getClassName();
    //step 1: 创建LoadedApk对象
    LoadedApk packageInfo = getPackageInfoNoCheck(
            data.info.applicationInfo, data.compatInfo);

    IActivityManager mgr = ActivityManagerNative.getDefault();
    java.lang.ClassLoader cl = packageInfo.getClassLoader();
    data.intent.setExtrasClassLoader(cl);
    data.intent.prepareToEnterProcess();
    data.setExtrasClassLoader(cl);
    //step 2: 创建BroadcastReceiver对象
    BroadcastReceiver receiver = (BroadcastReceiver)cl.loadClass(component).newInstance();

    //step 3: 创建Application对象
    Application app = packageInfo.makeApplication(false, mInstrumentation);

    //step 4: 直接使用App的ContextImpl对象
    ContextImpl context = (ContextImpl)app.getBaseContext();
    sCurrentBroadcastIntent.set(data.intent);
    receiver.setPendingResult(data);

    //step 5: 执行onReceive回调
    receiver.onReceive(context.getReceiverRestrictedContext(), data.intent);
    ...
}
```

**(五)ContentProvider初始化时赋值ContextImpl**

installProvider

```
private IActivityManager.ContentProviderHolder installProvider(Context context, IActivityManager.ContentProviderHolder holder, ProviderInfo info, boolean noisy, boolean noReleaseNeeded, boolean stable) {
    ContentProvider localProvider = null;
    IContentProvider provider;
    if (holder == null || holder.provider == null) {
        Context c = null;
        ApplicationInfo ai = info.applicationInfo;
        if (context.getPackageName().equals(ai.packageName)) {
            c = context;
        } else if (mInitialApplication != null &&
                mInitialApplication.getPackageName().equals(ai.packageName)) {
            c = mInitialApplication;
        } else {
            //step 1 && 2: 创建LoadedApk和ContextImpl对象
            c = context.createPackageContext(ai.packageName,Context.CONTEXT_INCLUDE_CODE);
        }

        final java.lang.ClassLoader cl = c.getClassLoader();
        //step 3: 创建ContentProvider对象
        localProvider = (ContentProvider)cl.loadClass(info.name).newInstance();
        provider = localProvider.getIContentProvider();

        //step 4: ContextImpl都attach到ContentProvider对象 [见小节4.4]
        //step 5: 并执行回调onCreate
        localProvider.attachInfo(c, info);
    } else {
        ...
    }
    ...
    return retHolder;
}
```

#### 总结： 创建ContextImpl的方式有多种, 不同的组件初始化调用不同的方法,如下:

- Activity: 调用createBaseContextForActivity初始化;
- Service/Application: 调用createAppContext初始化;
- Provider: 调用createPackageContext初始化;
- BroadcastReceiver: 直接从Application.getBaseContext()来获取ContextImpl对象;


#### 总结： 各个组件初始化过程,如下:
**Activity : performLaunchActivity**
1. 创建对象LoadedApk;
2. 创建对象Activity;
3. **创建对象Application;**
4. **创建对象ContextImpl;**
5. Application/ContextImpl都attach到Activity对象;
6. 执行onCreate()等回调;


**Service : handleCreateService**
1. 创建对象LoadedApk;
2. 创建对象Service;
3. **创建对象ContextImpl;**
4. **创建对象Application;**
5. Application/ContextImpl分别attach到Service对象;
6. 执行onCreate()回调;


**Receiver : handleReceiver**

1. 创建对象LoadedApk;
2. 创建对象BroadcastReceiver;
3. **创建对象Application;**
4. **创建对象ContextImpl;**
5. 执行onReceive()回调;
 

>说明：
>>- 以上过程是静态广播接收者, 即通过AndroidManifest.xml的标签来申明的BroadcastReceiver;
>>- 如果是动态广播接收者,则不需要再创建那么多对象, 因为动态广播的注册时进程已创建, 基本对象已创建完成. 那么只需要回调BroadcastReceiver的onReceive()方法即可.



**ContentProvider : installProvider**
1. 创建对象LoadedApk;
2. **创建对象ContextImpl;**
3. 创建对象ContentProvider;
4. ContextImpl都attach到ContentProvider对象;
5. 执行onCreate回调;

>PS: **注意，这里并没有创建Application对象。即，没有初始化Application。所以，尽管在Provider中可以通过getContext()拿到Context对象。但是如果调用getContext().getApplicationContext()获取所在Application时，由于并不会初始化所在application。所以返回结果为null.在使用中要注意这个点。**



**Application : handleBindApplication**
1. 创建对象LoadedApk
2. 创建对象ContextImpl;
3. 创建对象Instrumentation;
4. 创建对象Application;
5. 安装providers;
6. 执行Create回调


#### Context attach过程

1. Application:
- 调用attachBaseContext()将新创建ContextImpl赋值到父类ContextWrapper.mBase变量;
- 可通过getBaseContext()获取该ContextImpl;
2. Activity/Service:
- 调用attachBaseContext() 将新创建ContextImpl赋值到父类ContextWrapper.mBase变量;
- 可通过getBaseContext()获取该ContextImpl;
- 可通过getApplication()获取其所在的Application对象;
3. ContentProvider:
- 调用attachInfo()将新创建ContextImpl保存到ContentProvider.mContext变量;
- 可通过getContext()获取该ContextImpl;
4. BroadcastReceiver:
- 在onCreate过程通过参数将ReceiverRestrictedContext传递过去的.
5. ContextImpl:
- 可通过getApplicationContext()获取Application;


#### 组件初始化

类型	| LoadedApk	|ContextImpl	|Application	|创建相应对象	|回调方法
---|---|---|---|---|---
Activity	|√	|√	|√	|Activity	|onCreate
Service	|√	|√	|√	|Service	|onCreate
Receiver|	√	|√	|√	|BroadcastReceiver	|onReceive
Provider	|√	|√	|×	|ContentProvider	|onCreate
Application	|√	|√	|√	|Application	|onCreate


每个Apk都对应唯一的application对象和LoadedApk对象, 当Apk中任意组件的创建过程中, 当其所对应的的LoadedApk和Application没有初始化则会创建, 且只会创建一次.

唯有Provider在初始化过程并不会去创建所相应的Application对象.也就意味着当有多个Apk运行在同一个进程的情况下, 第二个apk通过Provider初始化过程再调用getContext().getApplicationContext()返回的并非Application对象, 而是NULL. 这里要注意会抛出空指针异常.    



## 3.Context应用场景


Application	|Activity	|Service	|ContentProvider	|BroadcastReceiver
---|---|---|---|---
Show a Dialog	|NO	|YES	|NO	|NO	|NO
Start an Activity	|NO1	|YES	|NO1	|NO1	|NO1
Layout Inflation	|NO2	|YES	|NO2	|NO2	|NO2
Start a Service	|YES	|YES	|YES	|YES	|YES
Bind to a Service	|YES	|YES	|YES	|YES	|NO
Send a Broadcast	|YES	|YES	|YES	|YES	|YES
Register BroadcastReceiver	|YES	|YES	|YES	|YES	|NO3
Load Resource Values	|YES	|YES	|YES	|YES	|YES

关于NO1 NO2 NO3的解释

数字1：启动Activity在这些类中是可以的，但是需要创建一个新的task。一般情况不推荐。

数字2：在这些类中去layout inflate是合法的，但是会使用系统默认的主题样式，如果你自定义了某些样式可能不会被使用。

数字3：在receiver为null时允许，在4.2或以上的版本中，用于获取黏性广播的当前值。（可以无视）

**当Context为Receiver的情况下:**
> - 不允许执行bindService()操作, 由于限制性上下文(ReceiverRestrictedContext)所决定的,会直接抛出异常.
>- registerReceiver是否允许取决于receiver;
>>- 当receiver == null用于获取sticky广播, 允许使用;
>>- 否则不允许使用registerReceiver;

**看startActivity操作**
>- 当为Activity Context则可直接使用;
>- 当为其他Context, 则必须带上FLAG_ACTIVITY_NEW_TASK flags才能使用;
>- 另外UI相关要Activity中使用.

注：ContentProvider、BroadcastReceiver之所以在上述表格中，是因为在其内部方法中都有一个context用于使用。


## 4.补充

#### getApplicationContext

绝大多数情况下, `getApplication()`和`getApplicationContext()`这两个方法完全一致, 返回值也相同; 那么两者到底有什么区别呢? 真正理解这个问题的人非常少. 接下来彻底地回答下这个问题:

getApplicationContext()这个的存在是Android历史原因. 我们都知道getApplication()只存在于Activity和Service对象; 那么对于BroadcastReceiver和ContentProvider却无法获取Application, 这时就需要一个能在Context上下文直接使用的方法, 那便是getApplicationContext().

对比使用：
1. 对于Activity/Service来说, getApplication()和getApplicationContext()的返回值完全相同; 除非厂商修改过接口;
2. BroadcastReceiver在onReceive的过程, 能使用getBaseContext().getApplicationContext获取所在Application, 而无法使用getApplication;
3. ContentProvider能使用getContext().getApplicationContext()获取所在Application. 绝大多数情况下没有问题, 但是有可能会出现空指针的问题, 情况如下:
>当同一个进程有多个apk的情况下, 对于第二个apk是由provider方式拉起的, 前面介绍过provider创建过程并不会初始化所在application, 此时执行 getContext().getApplicationContext()返回的结果便是NULL. 所以对于这种情况要做好判空.

#### Application context和Activity context的区别

使用context的时候，小心内存泄露，防止内存泄露，注意一下几个方面:
1. 不要让生命周期长的对象引用activity context，即保证引用activity的对象要与activity本身生命周期是一样的。
2. 对于生命周期长的对象，可以使用application context。
3. 避免非静态的内部类，尽量使用静态类，避免生命周期问题，注意内部类对外部对象引用导致的生命周期变化。