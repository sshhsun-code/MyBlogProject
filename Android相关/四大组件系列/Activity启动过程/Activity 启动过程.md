# Activity 启动过程

1. 概述
2. 主要对象及流程介绍
3. 具体过程
4. 总结

> 参考文章 
>> [【凯子哥带你学Framework】Activity启动过程全解析](https://blog.csdn.net/zhaokaiqiang1992/article/details/49428287#%E4%B8%BB%E8%A6%81%E5%AF%B9%E8%B1%A1%E5%8A%9F%E8%83%BD%E4%BB%8B%E7%BB%8D)<br>
>>[startActivity启动过程分析](http://gityuan.com/2016/03/12/start-activity/)


## 1. 概述

Activity启动发起后，通过Binder最终交由system进程中的AMS来完成，则启动流程如下图：

![image](http://o9m6aqy3r.bkt.clouddn.com//Activity/start_activity.jpg)

## 2. 主要对象及流程介绍

- ActivityManagerServices，简称AMS，服务端对象，负责系统中所有Activity的生命周期

- ActivityThread，App的真正入口。当开启App之后，会调用main()开始运行，开启消息循环队列，这就是传说中的UI线程或者叫主线程。与ActivityManagerServices配合，一起完成Activity的管理工作

- ApplicationThread，用来实现ActivityManagerService与ActivityThread之间的交互。在ActivityManagerService需要管理相关Application中的Activity的生命周期时，通过ApplicationThread的代理对象与ActivityThread通讯。

- ApplicationThreadProxy，是ApplicationThread在服务器端的代理，负责和客户端的ApplicationThread通讯。AMS就是通过该代理与ActivityThread进行通信的。

- Instrumentation，每一个应用程序只有一个Instrumentation对象，每个Activity内都有一个对该对象的引用。Instrumentation可以理解为应用进程的管家，ActivityThread要创建或暂停某个Activity时，都需要通过Instrumentation来进行具体的操作。

- ActivityStack，Activity在AMS的栈管理，用来记录已经启动的Activity的先后关系，状态信息等。通过ActivityStack决定是否需要启动新的进程。

- ActivityRecord，ActivityStack的管理对象，每个Activity在AMS对应一个ActivityRecord，来记录Activity的状态以及其他的管理信息。其实就是服务器端的Activity对象的映像。

- TaskRecord，AMS抽象出来的一个“任务”的概念，是记录ActivityRecord的栈，一个“Task”包含若干个ActivityRecord。AMS用TaskRecord确保Activity启动和退出的顺序。如果你清楚Activity的4种launchMode，那么对这个概念应该不陌生。


**zygote是什么？**

zygote意为“受精卵“。Android是基于Linux系统的，而在Linux中，所有的进程都是由init进程直接或者是间接fork出来的，zygote进程也不例外。

我们都知道，每一个App其实都是

- 一个单独的dalvik虚拟机
- 一个单独的进程

当系统里面的第一个zygote进程运行之后，在这之后再开启App，就相当于开启一个新的进程。而为了实现资源共用和更快的启动速度，Android系统开启新进程的方式，是通过fork第一个zygote进程实现的。所以说，除了第一个zygote进程，其他应用所在的进程都是zygote的子进程，这下你明白为什么这个进程叫“受精卵”了吧？因为就像是一个受精卵一样，它能快速的分裂，并且产生遗传物质一样的细胞！


**SystemServer是什么**

SystemServer也是一个进程，而且是由zygote进程fork出来的。系统里面重要的服务都是在这个进程里面开启的，比如 
ActivityManagerService、PackageManagerService、WindowManagerService等等.

系统服务是怎么开启起来的呢？

在zygote开启的时候，会调用ZygoteInit.main()进行初始化

```
public static void main(String argv[]) {

     ...ignore some code...

    //在加载首个zygote的时候，会传入初始化参数，使得startSystemServer = true
     boolean startSystemServer = false;
     for (int i = 1; i < argv.length; i++) {
                if ("start-system-server".equals(argv[i])) {
                    startSystemServer = true;
                } else if (argv[i].startsWith(ABI_LIST_ARG)) {
                    abiList = argv[i].substring(ABI_LIST_ARG.length());
                } else if (argv[i].startsWith(SOCKET_NAME_ARG)) {
                    socketName = argv[i].substring(SOCKET_NAME_ARG.length());
                } else {
                    throw new RuntimeException("Unknown command line argument: " + argv[i]);
                }
            }

            ...ignore some code...

         //开始fork我们的SystemServer进程
     if (startSystemServer) {
                startSystemServer(abiList, socketName);
         }

     ...ignore some code...

}
```

## 3. 具体启动流程

## (1)调用方所在进程

**Activity.startActivity**


```
public void startActivity(Intent intent) {
    this.startActivity(intent, null);
}

public void startActivity(Intent intent, @Nullable Bundle options) {
    if (options != null) {
        startActivityForResult(intent, -1, options);
    } else {
        startActivityForResult(intent, -1);
    }
}
```

**startActivityForResult**


```
public void startActivityForResult(Intent intent, int requestCode) {
    startActivityForResult(intent, requestCode, null);
}

public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
    if (mParent == null) {
        Instrumentation.ActivityResult ar =
            mInstrumentation.execStartActivity(
                this, mMainThread.getApplicationThread(), mToken, this,
                intent, requestCode, options);
        if (ar != null) {
            mMainThread.sendActivityResult(
                mToken, mEmbeddedID, requestCode, ar.getResultCode(),
                ar.getResultData());
        }
        //此时requestCode =-1
        if (requestCode >= 0) {
            mStartedActivity = true;
        }
        cancelInputsAndStartExitTransition(options);
    } else {
        ...
    }
}
```
execStartActivity()方法的参数:

- `mAppThread`: 数据类型为ApplicationThread，通过mMainThread.getApplicationThread()方法获取。
- `mToken`: 数据类型为IBinder.

**execStartActivity**

[-> Instrumentation.java]


```
public ActivityResult execStartActivity( Context who, IBinder contextThread, IBinder token, Activity target, Intent intent, int requestCode, Bundle options) {

    IApplicationThread whoThread = (IApplicationThread) contextThread;
    ...

    if (mActivityMonitors != null) {
        synchronized (mSync) {
            final int N = mActivityMonitors.size();
            for (int i=0; i<N; i++) {
                final ActivityMonitor am = mActivityMonitors.get(i);
                if (am.match(who, null, intent)) {
                    am.mHits++;
                    //当该monitor阻塞activity启动,则直接返回
                    if (am.isBlocking()) {
                        return requestCode >= 0 ? am.getResult() : null;
                    }
                    break;
                }
            }   
        }
    }
    try {
        intent.migrateExtraStreamToClipData();
        intent.prepareToLeaveProcess();
        //[见小节2.4]
        int result = ActivityManagerNative.getDefault()
            .startActivity(whoThread, who.getBasePackageName(), intent,
                    intent.resolveTypeIfNeeded(who.getContentResolver()),
                    token, target != null ? target.mEmbeddedID : null,
                    requestCode, 0, null, options);
        //检查activity是否启动成功
        checkStartActivityResult(result, intent);
    } catch (RemoteException e) {
        throw new RuntimeException("Failure from system", e);
    }
    return null;
}
```

**AMP.startActivity**


```
class ActivityManagerProxy implements IActivityManager {
    ...
    public int startActivity(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int startFlags, ProfilerInfo profilerInfo, Bundle options) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeString(callingPackage);
        intent.writeToParcel(data, 0);
        data.writeString(resolvedType);
        data.writeStrongBinder(resultTo);
        data.writeString(resultWho);
        data.writeInt(requestCode);
        data.writeInt(startFlags);
        if (profilerInfo != null) {
            data.writeInt(1);
            profilerInfo.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        //[见流程2.5]
        mRemote.transact(START_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        reply.recycle();
        data.recycle();
        return result;
    }
    ...
}
```

**AMP.startActivity**

[-> ActivityManagerNative.java :: ActivityManagerProxy]


```
class ActivityManagerProxy implements IActivityManager {
    ...
    public int startActivity(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int startFlags, ProfilerInfo profilerInfo, Bundle options) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(IActivityManager.descriptor);
        data.writeStrongBinder(caller != null ? caller.asBinder() : null);
        data.writeString(callingPackage);
        intent.writeToParcel(data, 0);
        data.writeString(resolvedType);
        data.writeStrongBinder(resultTo);
        data.writeString(resultWho);
        data.writeInt(requestCode);
        data.writeInt(startFlags);
        if (profilerInfo != null) {
            data.writeInt(1);
            profilerInfo.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        } else {
            data.writeInt(0);
        }
        if (options != null) {
            data.writeInt(1);
            options.writeToParcel(data, 0);
        } else {
            data.writeInt(0);
        }
        
        mRemote.transact(START_ACTIVITY_TRANSACTION, data, reply, 0);
        reply.readException();
        int result = reply.readInt();
        reply.recycle();
        data.recycle();
        return result;
    }
    ...
}
```
## (2)进入System_server进程
AMP经过binder IPC,进入ActivityManagerNative(简称AMN)。接下来程序进入了system_servr进程，开始继续执行。

**AMN.onTransact**

[-> ActivityManagerNative.java]


```
public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
    switch (code) {
    case START_ACTIVITY_TRANSACTION:
    {
      data.enforceInterface(IActivityManager.descriptor);
      IBinder b = data.readStrongBinder();
      IApplicationThread app = ApplicationThreadNative.asInterface(b);
      String callingPackage = data.readString();
      Intent intent = Intent.CREATOR.createFromParcel(data);
      String resolvedType = data.readString();
      IBinder resultTo = data.readStrongBinder();
      String resultWho = data.readString();
      int requestCode = data.readInt();
      int startFlags = data.readInt();
      ProfilerInfo profilerInfo = data.readInt() != 0
              ? ProfilerInfo.CREATOR.createFromParcel(data) : null;
      Bundle options = data.readInt() != 0
              ? Bundle.CREATOR.createFromParcel(data) : null;
      
      int result = startActivity(app, callingPackage, intent, resolvedType,
              resultTo, resultWho, requestCode, startFlags, profilerInfo, options);
      reply.writeNoException();
      reply.writeInt(result);
      return true;
    }
    ...
    }    }
```

**AMS.startActivity**


```
public final int startActivity(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int startFlags, ProfilerInfo profilerInfo, Bundle options) {
    return startActivityAsUser(caller, callingPackage, intent, resolvedType, resultTo,
        resultWho, requestCode, startFlags, profilerInfo, options,
        UserHandle.getCallingUserId());
}

public final int startActivityAsUser(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int startFlags, ProfilerInfo profilerInfo, Bundle options, int userId) {
    enforceNotIsolatedCaller("startActivity");
    userId = handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(), userId,
            false, ALLOW_FULL_ONLY, "startActivity", null);
    
    return mStackSupervisor.startActivityMayWait(caller, -1, callingPackage, intent,
            resolvedType, null, null, resultTo, resultWho, requestCode, startFlags,
            profilerInfo, null, null, options, false, userId, null, null);
}
```

此处mStackSupervisor的数据类型为`ActivityStackSupervisor`


**ASS.startActivityMayWait**

当程序运行到这里时, ASS.startActivityMayWait的各个参数取值如下:

- caller = ApplicationThreadProxy, 用于跟调用者进程ApplicationThread进行通信的binder代理类.
- callingUid = -1;
- callingPackage = ContextImpl.getBasePackageName(),获取调用者Activity所在包名
- intent: 这是启动Activity时传递过来的参数;
- resolvedType = intent.resolveTypeIfNeeded
- voiceSession = null;
- voiceInteractor = null;
- resultTo = Activity.mToken, 其中Activity是指调用者所在Activity, mToken对象保存自己所处的ActivityRecord信息
- resultWho = Activity.mEmbeddedID, 其中Activity是指调用者所在Activity
- requestCode = -1;
- startFlags = 0;
- profilerInfo = null;
- outResult = null;
- config = null;
- options = null;
- ignoreTargetSecurity = false;
- userId = AMS.handleIncomingUser, 当调用者userId跟当前处于同一个userId,则直接返回该userId;当不相等时则根据调用者userId来决定是否需要将callingUserId转换为mCurrentUserId.
- iContainer = null;
- inTask = null;

[-> ActivityStackSupervisor.java]


```
final int startActivityMayWait(IApplicationThread caller, int callingUid, String callingPackage, Intent intent, String resolvedType, IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor, IBinder resultTo, String resultWho, int requestCode, int startFlags, ProfilerInfo profilerInfo, WaitResult outResult, Configuration config, Bundle options, boolean ignoreTargetSecurity, int userId, IActivityContainer iContainer, TaskRecord inTask) {
    ...
    boolean componentSpecified = intent.getComponent() != null;
    //创建新的Intent对象，即便intent被修改也不受影响
    intent = new Intent(intent);

    //收集Intent所指向的Activity信息, 当存在多个可供选择的Activity,则直接向用户弹出resolveActivity
    ActivityInfo aInfo = resolveActivity(intent, resolvedType, startFlags, profilerInfo, userId);

    ActivityContainer container = (ActivityContainer)iContainer;
    synchronized (mService) {
        if (container != null && container.mParentActivity != null &&
                container.mParentActivity.state != RESUMED) {
            ... //不进入该分支, container == nul
        }

        final int realCallingPid = Binder.getCallingPid();
        final int realCallingUid = Binder.getCallingUid();
        int callingPid;
        if (callingUid >= 0) {
            callingPid = -1;
        } else if (caller == null) {
            callingPid = realCallingPid;
            callingUid = realCallingUid;
        } else {
            callingPid = callingUid = -1;
        }

        final ActivityStack stack;
        if (container == null || container.mStack.isOnHomeDisplay()) {
            stack = mFocusedStack; // 进入该分支
        } else {
            stack = container.mStack;
        }

        //此时mConfigWillChange = false
        stack.mConfigWillChange = config != null && mService.mConfiguration.diff(config) != 0;

        final long origId = Binder.clearCallingIdentity();

        if (aInfo != null &&
                (aInfo.applicationInfo.privateFlags
                        &ApplicationInfo.PRIVATE_FLAG_CANT_SAVE_STATE) != 0) {
            // heavy-weight进程处理流程, 一般情况下不进入该分支
            if (aInfo.processName.equals(aInfo.applicationInfo.packageName)) {
                ...
            }
        }

        int res = startActivityLocked(caller, intent, resolvedType, aInfo,
                voiceSession, voiceInteractor, resultTo, resultWho,
                requestCode, callingPid, callingUid, callingPackage,
                realCallingPid, realCallingUid, startFlags, options, ignoreTargetSecurity,
                componentSpecified, null, container, inTask);

        Binder.restoreCallingIdentity(origId);

        if (stack.mConfigWillChange) {
            ... //不进入该分支
        }

        if (outResult != null) {
            ... //不进入该分支
        }

        return res;
    }
}
```

**ASS.startActivityLocked**


```
final int startActivityLocked(IApplicationThread caller, Intent intent, String resolvedType, ActivityInfo aInfo, IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor, IBinder resultTo, String resultWho, int requestCode, int callingPid, int callingUid, String callingPackage, int realCallingPid, int realCallingUid, int startFlags, Bundle options, boolean ignoreTargetSecurity, boolean componentSpecified, ActivityRecord[] outActivity, ActivityContainer container, TaskRecord inTask) {
    int err = ActivityManager.START_SUCCESS;

    //获取调用者的进程记录对象
    ProcessRecord callerApp = null;
    if (caller != null) {
        callerApp = mService.getRecordForAppLocked(caller);
        if (callerApp != null) {
            callingPid = callerApp.pid;
            callingUid = callerApp.info.uid;
        } else {
            err = ActivityManager.START_PERMISSION_DENIED;
        }
    }

    final int userId = aInfo != null ?  UserHandle.getUserId(aInfo.applicationInfo.uid) : 0;

    ActivityRecord sourceRecord = null;
    ActivityRecord resultRecord = null;
    if (resultTo != null) {
        //获取调用者所在的Activity
        sourceRecord = isInAnyStackLocked(resultTo);
        if (sourceRecord != null) {
            if (requestCode >= 0 && !sourceRecord.finishing) {
                ... //requestCode = -1 则不进入
            }
        }
    }

    final int launchFlags = intent.getFlags();

    if ((launchFlags & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0 && sourceRecord != null) {
        ... // activity执行结果的返回由源Activity转换到新Activity, 不需要返回结果则不会进入该分支
    }

    if (err == ActivityManager.START_SUCCESS && intent.getComponent() == null) {
        //从Intent中无法找到相应的Component
        err = ActivityManager.START_INTENT_NOT_RESOLVED;
    }

    if (err == ActivityManager.START_SUCCESS && aInfo == null) {
        //从Intent中无法找到相应的ActivityInfo
        err = ActivityManager.START_INTENT_NOT_RESOLVED;
    }

    if (err == ActivityManager.START_SUCCESS
            && !isCurrentProfileLocked(userId)
            && (aInfo.flags & FLAG_SHOW_FOR_ALL_USERS) == 0) {
        //尝试启动一个后台Activity, 但该Activity对当前用户不可见
        err = ActivityManager.START_NOT_CURRENT_USER_ACTIVITY;
    }
    ...

    //执行后resultStack = null
    final ActivityStack resultStack = resultRecord == null ? null : resultRecord.task.stack;

    ... //权限检查

    // ActivityController不为空的情况，比如monkey测试过程
    if (mService.mController != null) {
        Intent watchIntent = intent.cloneFilter();
        abort |= !mService.mController.activityStarting(watchIntent,
                aInfo.applicationInfo.packageName);
    }

    if (abort) {
        ... //权限检查不满足,才进入该分支则直接返回；
        return ActivityManager.START_SUCCESS;
    }

    // 创建Activity记录对象
    ActivityRecord r = new ActivityRecord(mService, callerApp, callingUid, callingPackage,
            intent, resolvedType, aInfo, mService.mConfiguration, resultRecord, resultWho,
            requestCode, componentSpecified, voiceSession != null, this, container, options);
    if (outActivity != null) {
        outActivity[0] = r;
    }

    if (r.appTimeTracker == null && sourceRecord != null) {
        r.appTimeTracker = sourceRecord.appTimeTracker;
    }
    // 将mFocusedStack赋予当前stack
    final ActivityStack stack = mFocusedStack;

    if (voiceSession == null && (stack.mResumedActivity == null
            || stack.mResumedActivity.info.applicationInfo.uid != callingUid)) {
        // 前台stack还没有resume状态的Activity时, 则检查app切换是否允许
        if (!mService.checkAppSwitchAllowedLocked(callingPid, callingUid,
                realCallingPid, realCallingUid, "Activity start")) {
            PendingActivityLaunch pal =
                    new PendingActivityLaunch(r, sourceRecord, startFlags, stack);
            // 当不允许切换,则把要启动的Activity添加到mPendingActivityLaunches对象, 并且直接返回.
            mPendingActivityLaunches.add(pal);
            ActivityOptions.abort(options);
            return ActivityManager.START_SWITCHES_CANCELED;
        }
    }

    if (mService.mDidAppSwitch) {
        //从上次禁止app切换以来,这是第二次允许app切换,因此将允许切换时间设置为0,则表示可以任意切换app
        mService.mAppSwitchesAllowedTime = 0;
    } else {
        mService.mDidAppSwitch = true;
    }

    //处理 pendind Activity的启动, 这些Activity是由于app switch禁用从而被hold的等待启动activity
    doPendingActivityLaunchesLocked(false);

    
    err = startActivityUncheckedLocked(r, sourceRecord, voiceSession, voiceInteractor,
            startFlags, true, options, inTask);

    if (err < 0) {
        notifyActivityDrawnForKeyguard();
    }
    return err;
}
```

**ASS.startActivityUncheckedLocked**

找到或创建新的Activit所属于的Task对象，之后调用AS.startActivityLocked

**Launch Mode<br>**
>ActivityInfo.java中定义了4类Launch Mode：
 - LAUNCH_MULTIPLE(standard)：最常见的情形，每次启动Activity都是创建新的Activity;
 - LAUNCH_SINGLE_TOP: 当Task顶部存在同一个Activity则不再重新创建；其余情况同上；
 - LAUNCH_SINGLE_TASK：当Task栈存在同一个Activity(不在task顶部)，则不重新创建，而移除该Activity上面其他的Activity；其余情况同上；
 - LAUNCH_SINGLE_INSTANCE：每个Task只有一个Activity.<br>

再来说说几个常见的flag含义：

- FLAG_ACTIVITY_NEW_TASK：将Activity放入一个新启动的Task；
- FLAG_ACTIVITY_CLEAR_TASK：启动Activity时，将目标Activity关联的Task清除，再启动新Task，将该Activity放入该Task。该flags跟FLAG_ACTIVITY_NEW_TASK配合使用
- FLAG_ACTIVITY_CLEAR_TOP：启动非栈顶Activity时，先清除该Activity之上的Activity。例如Task已有A、B、C3个Activity，启动A，则清除B，C。类似于SingleTop。

最后再说说：设置`FLAG_ACTIVITY_NEW_TASK`的几个情况：

- 调用者并不是Activity context；
- 调用者activity带有single instance；
- 目标activity带有single instance或者single task；
- 调用者处于finishing状态；

**AS.startActivityLocked**

**ASS.resumeTopActivitiesLocked**

**AS.resumeTopActivityLocked**

inResumeTopActivity用于保证每次只有一个Activity执行resumeTopActivityLocked()操作.

**AS.resumeTopActivityInnerLocked**

主要分支功能：
- 当找不到需要resume的Activity，则直接回到桌面；
- 否则，当mResumedActivity不为空，则执行startPausingLocked()暂停该activity;
- 然后再进入startSpecificActivityLocked环节，接下来从这里继续往下说


>PS : 需要等待暂停当前activity完成，再resume top activity,当前resumd状态activity不为空，则需要先暂停该Activity.

**ASS.startSpecificActivityLocked**


```
void startSpecificActivityLocked(ActivityRecord r, boolean andResume, boolean checkConfig) {
    ProcessRecord app = mService.getProcessRecordLocked(r.processName,
            r.info.applicationInfo.uid, true);

    r.task.stack.setLaunchTime(r);

    if (app != null && app.thread != null) {
        try {
            if ((r.info.flags&ActivityInfo.FLAG_MULTIPROCESS) == 0
                    || !"android".equals(r.info.packageName)) {
                app.addPackage(r.info.packageName, r.info.applicationInfo.versionCode,
                        mService.mProcessStats);
            }
            //真正的启动Activity!!!!!!!!!!!!!!
            realStartActivityLocked(r, app, andResume, checkConfig);
            return;
        } catch (RemoteException e) {
            Slog.w(TAG, "Exception when starting activity "
                    + r.intent.getComponent().flattenToShortString(), e);
        }

    }
    //当进程不存在则创建进程!!!使用Socket通信通知zygote进程
    mService.startProcessLocked(r.processName, r.info.applicationInfo, true, 0,
            "activity", r.intent.getComponent(), false, false, true);
}
```
#### 当进程不存在则创建进程：

**AMS.startProcessLocked**

AMS.startProcessLocked()整个过程，创建完新进程后会在新进程中调用AMP.attachApplication ，该方法经过binder ipc后调用到AMS.attachApplicationLocked。


```
private final boolean attachApplicationLocked(IApplicationThread thread, int pid) {
    ...
    ////只有当系统启动完，或者app允许启动过程允许，则会true
    boolean normalMode = mProcessesReady || isAllowedWhileBooting(app.info);
    thread.bindApplication(...);

    if (normalMode) {
        
        if (mStackSupervisor.attachApplicationLocked(app)) {
            didSomething = true;
        }
    }
    ...
}
```

在执行完bindApplication()之后进入ASS.attachApplicationLocked()

**ASS.attachApplicationLocked**

```
boolean attachApplicationLocked(ProcessRecord app) throws RemoteException {
    final String processName = app.processName;
    boolean didSomething = false;
    for (int displayNdx = mActivityDisplays.size() - 1; displayNdx >= 0; --displayNdx) {
        ArrayList<ActivityStack> stacks = mActivityDisplays.valueAt(displayNdx).mStacks;
        for (int stackNdx = stacks.size() - 1; stackNdx >= 0; --stackNdx) {
            final ActivityStack stack = stacks.get(stackNdx);
            if (!isFrontStack(stack)) {
                continue;
            }
            //获取前台stack中栈顶第一个非finishing的Activity
            ActivityRecord hr = stack.topRunningActivityLocked(null);
            if (hr != null) {
                if (hr.app == null && app.uid == hr.info.applicationInfo.uid
                        && processName.equals(hr.processName)) {
                    try {
                        //真正的启动Activity
                        if (realStartActivityLocked(hr, app, true, true)) {
                            didSomething = true;
                        }
                    } catch (RemoteException e) {
                        throw e;
                    }
                }
            }
        }
    }
    if (!didSomething) {
        //启动Activity不成功，则确保有可见的Activity
        ensureActivitiesVisibleLocked(null, 0);
    }
    return didSomething;
}
```

#### 进程存在时，真正的启动Activity
**ASS.realStartActivityLocked**


```
final boolean realStartActivityLocked(ActivityRecord r, ProcessRecord app, boolean andResume, boolean checkConfig) throws RemoteException {

    if (andResume) {
        r.startFreezingScreenLocked(app, 0);
        mWindowManager.setAppVisibility(r.appToken, true);
        //调度启动ticks用以收集应用启动慢的信息
        r.startLaunchTickingLocked();
    }

    if (checkConfig) {
        Configuration config = mWindowManager.updateOrientationFromAppTokens(
                mService.mConfiguration,
                r.mayFreezeScreenLocked(app) ? r.appToken : null);
        //更新Configuration
        mService.updateConfigurationLocked(config, r, false, false);
    }

    r.app = app;
    app.waitingToKill = null;
    r.launchCount++;
    r.lastLaunchTime = SystemClock.uptimeMillis();

    int idx = app.activities.indexOf(r);
    if (idx < 0) {
        app.activities.add(r);
    }
    mService.updateLruProcessLocked(app, true, null);
    mService.updateOomAdjLocked();

    final TaskRecord task = r.task;
    if (task.mLockTaskAuth == LOCK_TASK_AUTH_LAUNCHABLE ||
            task.mLockTaskAuth == LOCK_TASK_AUTH_LAUNCHABLE_PRIV) {
        setLockTaskModeLocked(task, LOCK_TASK_MODE_LOCKED, "mLockTaskAuth==LAUNCHABLE", false);
    }

    final ActivityStack stack = task.stack;
    try {
        if (app.thread == null) {
            throw new RemoteException();
        }
        List<ResultInfo> results = null;
        List<ReferrerIntent> newIntents = null;
        if (andResume) {
            results = r.results;
            newIntents = r.newIntents;
        }
        if (r.isHomeActivity() && r.isNotResolverActivity()) {
            //home进程是该栈的根进程
            mService.mHomeProcess = task.mActivities.get(0).app;
        }
        mService.ensurePackageDexOpt(r.intent.getComponent().getPackageName());
        ...

        if (andResume) {
            app.hasShownUi = true;
            app.pendingUiClean = true;
        }
        //将该进程设置为前台进程PROCESS_STATE_TOP
        app.forceProcessStateUpTo(mService.mTopProcessState);
        //向目标进程发送LaunchActivity的命令
        app.thread.scheduleLaunchActivity(new Intent(r.intent), r.appToken,
                System.identityHashCode(r), r.info, new Configuration(mService.mConfiguration),
                new Configuration(stack.mOverrideConfig), r.compat, r.launchedFromPackage,
                task.voiceInteractor, app.repProcState, r.icicle, r.persistentState, results,
                newIntents, !andResume, mService.isNextTransitionForward(), profilerInfo);

        if ((app.info.privateFlags&ApplicationInfo.PRIVATE_FLAG_CANT_SAVE_STATE) != 0) {
            ... //处理heavy-weight进程
        }

    } catch (RemoteException e) {
        if (r.launchFailed) {
            //第二次启动失败，则结束该activity
            mService.appDiedLocked(app);
            stack.requestFinishActivityLocked(r.appToken, Activity.RESULT_CANCELED, null,
                    "2nd-crash", false);
            return false;
        }
        //这是第一个启动失败，则重启进程
        app.activities.remove(r);
        throw e;
    }

    //将该进程加入到mLRUActivities队列顶部
    stack.updateLRUListLocked(r)；

    if (andResume) {
        //启动过程的一部分
        stack.minimalResumeActivityLocked(r);
    } else {
        r.state = STOPPED;
        r.stopped = true;
    }

    if (isFrontStack(stack)) {
        //当系统发生更新时，只会执行一次的用户向导
        mService.startSetupActivityLocked();
    }
    //更新所有与该Activity具有绑定关系的Service连接
    mService.mServices.updateServiceConnectionActivitiesLocked(r.app);

    return true;
}
```

该方法向Activity所在进程即目标进程发送LaunchActivity的命令；

**ATP.scheduleLaunchActivity**

[-> ApplicationThreadProxy.java]

```
public final void scheduleLaunchActivity(Intent intent, IBinder token, int ident, ActivityInfo info, Configuration curConfig, Configuration overrideConfig, CompatibilityInfo compatInfo, String referrer, IVoiceInteractor voiceInteractor, int procState, Bundle state, PersistableBundle persistentState, List<ResultInfo> pendingResults, List<ReferrerIntent> pendingNewIntents, boolean notResumed, boolean isForward, ProfilerInfo profilerInfo) throws RemoteException {
     Parcel data = Parcel.obtain();
     data.writeInterfaceToken(IApplicationThread.descriptor);
     intent.writeToParcel(data, 0);
     data.writeStrongBinder(token);
     data.writeInt(ident);
     info.writeToParcel(data, 0);
     curConfig.writeToParcel(data, 0);
     if (overrideConfig != null) {
         data.writeInt(1);
         overrideConfig.writeToParcel(data, 0);
     } else {
         data.writeInt(0);
     }
     compatInfo.writeToParcel(data, 0);
     data.writeString(referrer);
     data.writeStrongBinder(voiceInteractor != null ? voiceInteractor.asBinder() : null);
     data.writeInt(procState);
     data.writeBundle(state);
     data.writePersistableBundle(persistentState);
     data.writeTypedList(pendingResults);
     data.writeTypedList(pendingNewIntents);
     data.writeInt(notResumed ? 1 : 0);
     data.writeInt(isForward ? 1 : 0);
     if (profilerInfo != null) {
         data.writeInt(1);
         profilerInfo.writeToParcel(data, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
     } else {
         data.writeInt(0);
     }
     //【见流程2.19】
     mRemote.transact(SCHEDULE_LAUNCH_ACTIVITY_TRANSACTION, data, null,
             IBinder.FLAG_ONEWAY);
     data.recycle();
 }
```

## (3)进入目标Activity所在进程
Activity所在进程接收System_server所在进程传来的命令，**此次Binder通信中，待启动Activity所在进程为服务端，而System_server进程为客户端**。

现在开始，进入目标Activity所在进程，接收`SCHEDULE_LAUNCH_ACTIVITY_TRANSACTION`命令：

**ATN.onTransact**


```
public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
    switch (code) {
    case SCHEDULE_LAUNCH_ACTIVITY_TRANSACTION:
    {
        data.enforceInterface(IApplicationThread.descriptor);
        Intent intent = Intent.CREATOR.createFromParcel(data);
        IBinder b = data.readStrongBinder();
        int ident = data.readInt();
        ActivityInfo info = ActivityInfo.CREATOR.createFromParcel(data);
        Configuration curConfig = Configuration.CREATOR.createFromParcel(data);
        Configuration overrideConfig = null;
        if (data.readInt() != 0) {
            overrideConfig = Configuration.CREATOR.createFromParcel(data);
        }
        CompatibilityInfo compatInfo = CompatibilityInfo.CREATOR.createFromParcel(data);
        String referrer = data.readString();
        IVoiceInteractor voiceInteractor = IVoiceInteractor.Stub.asInterface(
                data.readStrongBinder());
        int procState = data.readInt();
        Bundle state = data.readBundle();
        PersistableBundle persistentState = data.readPersistableBundle();
        List<ResultInfo> ri = data.createTypedArrayList(ResultInfo.CREATOR);
        List<ReferrerIntent> pi = data.createTypedArrayList(ReferrerIntent.CREATOR);
        boolean notResumed = data.readInt() != 0;
        boolean isForward = data.readInt() != 0;
        ProfilerInfo profilerInfo = data.readInt() != 0
                ? ProfilerInfo.CREATOR.createFromParcel(data) : null;
       
        scheduleLaunchActivity(intent, b, ident, info, curConfig, overrideConfig, compatInfo,
                referrer, voiceInteractor, procState, state, persistentState, ri, pi,
                notResumed, isForward, profilerInfo);
        return true;
    }
    ...
    }
}
```

**AT.scheduleLaunchActivity**

[-> ApplicationThread.java]

```
public final void scheduleLaunchActivity(Intent intent, IBinder token, int ident, ActivityInfo info, Configuration curConfig, Configuration overrideConfig, CompatibilityInfo compatInfo, String referrer, IVoiceInteractor voiceInteractor, int procState, Bundle state, PersistableBundle persistentState, List<ResultInfo> pendingResults, List<ReferrerIntent> pendingNewIntents, boolean notResumed, boolean isForward, ProfilerInfo profilerInfo) {

     updateProcessState(procState, false);

     ActivityClientRecord r = new ActivityClientRecord();

     r.token = token;
     r.ident = ident;
     r.intent = intent;
     r.referrer = referrer;
     r.voiceInteractor = voiceInteractor;
     r.activityInfo = info;
     r.compatInfo = compatInfo;
     r.state = state;
     r.persistentState = persistentState;

     r.pendingResults = pendingResults;
     r.pendingIntents = pendingNewIntents;

     r.startsNotResumed = notResumed;
     r.isForward = isForward;

     r.profilerInfo = profilerInfo;

     r.overrideConfig = overrideConfig;
     updatePendingConfiguration(curConfig);
     
     sendMessage(H.LAUNCH_ACTIVITY, r);
 }
 
 
 
 
 public void handleMessage(Message msg) {
    switch (msg.what) {
        case LAUNCH_ACTIVITY: {
            final ActivityClientRecord r = (ActivityClientRecord) msg.obj;
            r.packageInfo = getPackageInfoNoCheck(
                    r.activityInfo.applicationInfo, r.compatInfo);
            
            handleLaunchActivity(r, null);
        } break;
        ...
    }
 
```

**ActivityThread.handleLaunchActivity**


```
private void handleLaunchActivity(ActivityClientRecord r, Intent customIntent) {
    unscheduleGcIdler();
    mSomeActivitiesChanged = true;

    //最终回调目标Activity的onConfigurationChanged()
    handleConfigurationChanged(null, null);
    //初始化wms
    WindowManagerGlobal.initialize();
    //最终回调目标Activity的onCreate
    Activity a = performLaunchActivity(r, customIntent);
    if (a != null) {
        r.createdConfig = new Configuration(mConfiguration);
        Bundle oldState = r.state;
        //最终回调目标Activity的onStart,onResume.
        handleResumeActivity(r.token, false, r.isForward,
                !r.activity.mFinished && !r.startsNotResumed);

        if (!r.activity.mFinished && r.startsNotResumed) {
            r.activity.mCalled = false;
            mInstrumentation.callActivityOnPause(r.activity);
            r.paused = true;
        }
    } else {
        //存在error则停止该Activity
        ActivityManagerNative.getDefault()
            .finishActivity(r.token, Activity.RESULT_CANCELED, null, false);
    }
}
```
- performLaunchActivity(r, customIntent):回调目标Activity的onCreate
- handleResumeActivity : 回调目标Activity的onStart,onResume.


**ActivityThread.performLaunchActivity**

[-> ActivityThread.java]


```
private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {

    ActivityInfo aInfo = r.activityInfo;
    if (r.packageInfo == null) {
        r.packageInfo = getPackageInfo(aInfo.applicationInfo, r.compatInfo,
                Context.CONTEXT_INCLUDE_CODE);
    }

    ComponentName component = r.intent.getComponent();
    if (component == null) {
        component = r.intent.resolveActivity(
            mInitialApplication.getPackageManager());
        r.intent.setComponent(component);
    }

    if (r.activityInfo.targetActivity != null) {
        component = new ComponentName(r.activityInfo.packageName,
                r.activityInfo.targetActivity);
    }

    Activity activity = null;
    try {
        java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
        activity = mInstrumentation.newActivity(
                cl, component.getClassName(), r.intent);
        StrictMode.incrementExpectedActivityCount(activity.getClass());
        r.intent.setExtrasClassLoader(cl);
        r.intent.prepareToEnterProcess();
        if (r.state != null) {
            r.state.setClassLoader(cl);
        }
    } catch (Exception e) {
        ...
    }

    try {
        //创建Application对象
        Application app = r.packageInfo.makeApplication(false, mInstrumentation);

        if (activity != null) {
            Context appContext = createBaseContextForActivity(r, activity);
            CharSequence title = r.activityInfo.loadLabel(appContext.getPackageManager());
            Configuration config = new Configuration(mCompatConfiguration);

            activity.attach(appContext, this, getInstrumentation(), r.token,
                    r.ident, app, r.intent, r.activityInfo, title, r.parent,
                    r.embeddedID, r.lastNonConfigurationInstances, config,
                    r.referrer, r.voiceInteractor);

            if (customIntent != null) {
                activity.mIntent = customIntent;
            }
            r.lastNonConfigurationInstances = null;
            activity.mStartedActivity = false;
            int theme = r.activityInfo.getThemeResource();
            if (theme != 0) {
                activity.setTheme(theme);
            }

            activity.mCalled = false;
            if (r.isPersistable()) {
                mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);
            } else {
                mInstrumentation.callActivityOnCreate(activity, r.state);
            }
            ...
            r.activity = activity;
            r.stopped = true;
            if (!r.activity.mFinished) {
                activity.performStart();
                r.stopped = false;
            }
            if (!r.activity.mFinished) {
                if (r.isPersistable()) {
                    if (r.state != null || r.persistentState != null) {
                        mInstrumentation.callActivityOnRestoreInstanceState(activity, r.state,
                                r.persistentState);
                    }
                } else if (r.state != null) {
                    mInstrumentation.callActivityOnRestoreInstanceState(activity, r.state);
                }
            }
            if (!r.activity.mFinished) {
                activity.mCalled = false;
                if (r.isPersistable()) {
                    mInstrumentation.callActivityOnPostCreate(activity, r.state,
                            r.persistentState);
                } else {
                    mInstrumentation.callActivityOnPostCreate(activity, r.state);
                }
                ...
            }
        }
        r.paused = true;

        mActivities.put(r.token, r);

    }  catch (Exception e) {
        ...
    }

    return activity;
}
```
到此，正式进入了Activity的onCreate, onStart, onResume这些生命周期的过程。

## 4. 总结

本文详细startActivity的整个启动流程，

-  `Activity.startActivity`到`AMP.startActivity` 运行在调用者所在进程，比如从桌面启动Activity，则**调用者所在进程为launcher进程**，launcher进程利用ActivityManagerProxy作为Binder Client，进入system_server进程(AMS相应的Server端)。

-  `AMN.onTransact` 到 `ATP.scheduleLaunchActivity`**运行在system_server系统进程**，整个过程最为复杂、核心的过程，下面其中部分步骤：
> - ASS.startActivityMayWait 会调用到resolveActivity()，借助PackageManager来查询系统中所有符合要求的Activity，当存在多个满足条件的Activity则会弹框让用户来选择;
>- ASS.startActivityLocked 创建ActivityRecord对象，并检查是否运行App切换，然后再处理mPendingActivityLaunches中的activity;
>- ASS.startActivityUncheckedLocked 为Activity找到或创建新的Task对象，设置flags信息；
>- AS.resumeTopActivityInnerLocked 当没有处于非finishing状态的Activity，则直接回到桌面； 否则，当mResumedActivity不为空则执行startPausingLocked()暂停该activity;然后再进入startSpecificActivityLocked()环节;
>- ASS.startSpecificActivityLocked: 当目标进程已存在则直接进入流程`ASS.realStartActivityLocked`,当进程不存在则创建进程，经过层层调用还是会进入流程`ASS.realStartActivityLocked`;
>- ASS.realStartActivityLocked: system_server进程利用的ATP(Binder Client)，经过Binder，程序接下来进入目标进程。


- ATN.onTransact(第二个) 到 ActivityThread.performLaunchActivity：**运行在目标进程**，通过Handler消息机制，该进程中的Binder线程向主线程发送H.LAUNCH_ACTIVITY，最终会通过反射创建目标Activity，然后进入onCreate()生命周期。





![image](http://o9m6aqy3r.bkt.clouddn.com//Activity/start_activity_process.jpg)

启动流程：
1. 点击桌面App图标，Launcher进程采用Binder IPC向system_server进程发起startActivity请求；

2. system_server进程接收到请求后，向zygote进程发送创建进程的请求；

3. Zygote进程fork出新的子进程，即App进程；

4. App进程，通过Binder IPC向sytem_server进程发起attachApplication请求；

5. system_server进程在收到请求后，进行一系列准备工作后，再通过binder IPC向App进程发送scheduleLaunchActivity请求；

6. App进程的binder线程（ApplicationThread）在收到请求后，通过handler向主线程发送LAUNCH_ACTIVITY消息；

7. 主线程在收到Message后，通过发射机制创建目标Activity，并回调Activity.onCreate()等方法。

**到此，App便正式启动，开始进入Activity生命周期，执行完onCreate/onStart/onResume方法，UI渲染结束后便可以看到App的主界面**。 