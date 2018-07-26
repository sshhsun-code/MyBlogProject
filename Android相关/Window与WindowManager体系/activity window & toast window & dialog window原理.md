# Activity Window & Toast Window & Dialog Window原理

参考：<<Android 开发艺术探索>> 第八章

**1. Activity的Window创建过程**

**2. Dialog的Window创建过程**

**3. Toast的Window创建过程**

**4. 问题扩展(Dialog & Toast)**



## 1. Activity的Window创建过程

Activity的启动过程比较复杂，最终会由ActiviyThread中的peformLaunchActivity()来完成整个启动过程，在这个方法中通过类加载器创建Activity的实例对象，调用其attach方法为其关联运行过程中所依赖的一系列上下文环境变量。

在Activity的attach方法中，系统会创建Activity所属的Window对象并为其设置回调接口，Window的创建是通过Policymanager的makeNewWindow方法实现。由于Activity中实现了Window的Callback接口，因此当Window接收到外界的状态改变时就会回调Activiy中的callback方法。方法很多，其中我们熟悉的几个Callback方法为：onAttachedToWindow,onDetachedFromWindow,dispatchTouchEvent等等，代码如下：


```
final void attach(Context context, ActivityThread aThread,
            Instrumentation instr, IBinder token, int ident,
            Application application, Intent intent, ActivityInfo info,
            CharSequence title, Activity parent, String id,
            NonConfigurationInstances lastNonConfigurationInstances,
            Configuration config, String referrer, IVoiceInteractor voiceInteractor) {
        attachBaseContext(context);

        mFragments.attachHost(null /*parent*/);

        mWindow = new PhoneWindow(this);
        mWindow.setCallback(this);
        mWindow.setOnWindowDismissedCallback(this);
        mWindow.getLayoutInflater().setPrivateFactory(this);
        if (info.softInputMode != WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED) {
            mWindow.setSoftInputMode(info.softInputMode);
        }
        ....
                
    }
```

到这里，Activity中的Window已经创建完成了，下面分析Activity的视图是怎样附属在Window上，而Activity中视图是通过`setContentView`提供的，所以在接下来，看看setContentView的执行逻辑：


```
    public void setContentView(View view) {
        getWindow().setContentView(view);
        initWindowDecorActionBar();
    }

```
从Activity的setContentView中看，实际操作是由Window进行操作，而Window的实现类是PhoneWindow.

而在PhoneWindow中：


```
    public void setContentView(int layoutResID) {
        // Note: FEATURE_CONTENT_TRANSITIONS may be set in the process of installing the window
        // decor, when theme attributes and the like are crystalized. Do not check the feature
        // before this happens.
        if (mContentParent == null) {
            installDecor();//1 安装Decor
        } else if (!hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            mContentParent.removeAllViews();
        }

        if (hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            final Scene newScene = Scene.getSceneForLayout(mContentParent, layoutResID,
                    getContext());
            transitionTo(newScene);
        } else {
            mLayoutInflater.inflate(layoutResID, mContentParent);//2.将View添加到DecorView的mContentParent中
        }
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();//3.通知Activity视图发生改变
        }
    }
    
```

PhoneWindow中的setContentView方法主要分三步进行完成：

**1. 如果没有DecorView，那就创建它**

首先，DecorView是定义在PhoneWindow内部的一个类，继承与FrameLayout .


```
    private final class DecorView extends FrameLayout implements RootViewSurfaceTaker {

        /* package */int mDefaultOpacity = PixelFormat.OPAQUE;

        /** The feature ID of the panel, or -1 if this is the application's DecorView */
        private final int mFeatureId;

        private final Rect mDrawingBounds = new Rect();
        ....
        
    }

```

DecorView是Activity的顶层View， 其内部一般包含一个顶部栏和内容栏，不管怎样，内容栏一定存在，且其id是固定的："content",完整id是 `android.id.content`。DecorView的创建过程由installDecor 方法来完成：


```
private void installDecor() {
        if (mDecor == null) {
            mDecor = generateDecor();
            mDecor.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            mDecor.setIsRootNamespace(true);
            if (!mInvalidatePanelMenuPosted && mInvalidatePanelMenuFeatures != 0) {
                mDecor.postOnAnimation(mInvalidatePanelMenuRunnable);
            }
        }
        if (mContentParent == null) {
            mContentParent = generateLayout(mDecor);

            // Set up decor part of UI to ignore fitsSystemWindows if appropriate.
            mDecor.makeOptionalFitsSystemWindows();
            .....
        }

```



```
    protected DecorView generateDecor() {
        return new DecorView(getContext(), -1);
    }

```

同时，为了初始化DecorView,还需要通过generateLayout来加载具体的布局到DecorView中，过程如下所示：


```
        View in = mLayoutInflater.inflate(layoutResource, null);
        decor.addView(in, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        mContentRoot = (ViewGroup) in;

        ViewGroup contentParent = (ViewGroup)findViewById(ID_ANDROID_CONTENT);
        if (contentParent == null) {
            throw new RuntimeException("Window couldn't find content container view");
        }
```


```
    public static final int ID_ANDROID_CONTENT = com.android.internal.R.id.content;

```



**2. 将View添加到DecorView的mContentParent中**

在步骤1中，生产DecorView和mContentParent后，通过：


```
mLayoutInflater.inflate(layoutResID, mContentParent)
```
直接将Activity的视图添加到DecorView的mContentParent中即可。由此可以理解，为什么这个方法叫做“setContentView”。因为Activity的布局文件确实是被添加到了DecorView的mContentParent中。

**3. 通知Activity视图发生改变**
    由于Activity中实现了Window的Callback接口，所以这里表示Activity的布局文件已经被添加到DecorView中。于是需要通知Activity,使其做相应的处理。我们可以在子Activity中处理回调。
    
    
```
 final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();
        }
```


---
经过以上三个步骤，DecorView已经被创建并初始化完成，Activity的布局文件也已经成功添加到DecorView中，但是这个时候，DecorView还没有被WindowManager正式添加到Widow中。

Window更多表示的是一种抽象的功能集合，**虽说在Activiy中的attach中，Window已经被创建，但是这时候，DecorView还没被WindowManager所识别，所以这个Window无法提供具体功能，因为它还没有绘制界面且无法接收外界的输入信息。在ActivityThread的handleResumeActivity中，首先会调用Activity的onResume方法，接着会调用Activity的makeVisible().正是在makeVisible中，DecorView才真正的完成添加和显示过程。到这里，Activity的视图才会被用户看到**，如下所示：


```
    void makeVisible() {
        if (!mWindowAdded) {
            ViewManager wm = getWindowManager();
            wm.addView(mDecor, getWindow().getAttributes());
            mWindowAdded = true;
        }
        mDecor.setVisibility(View.VISIBLE);
    }
```
至此，Activity的Window才正常显示使用。


## 2. Dialog的Window创建过程

Dialog的Window创建过程与Activity类似，如下几个步骤：

**1. 创建Window**


```
    Dialog(@NonNull Context context, @StyleRes int themeResId, boolean createContextThemeWrapper) {
        if (createContextThemeWrapper) {
            if (themeResId == 0) {
                final TypedValue outValue = new TypedValue();
                context.getTheme().resolveAttribute(R.attr.dialogTheme, outValue, true);
                themeResId = outValue.resourceId;
            }
            mContext = new ContextThemeWrapper(context, themeResId);
        } else {
            mContext = context;
        }

        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        final Window w = new PhoneWindow(mContext);
        mWindow = w;
        w.setCallback(this);
        w.setOnWindowDismissedCallback(this);
        w.setWindowManager(mWindowManager, null, null);
        w.setGravity(Gravity.CENTER);

        mListenersHandler = new ListenersHandler(this);
    }
```

Dialog中Window同样是是PhoneWindow,这个过程与Activity的Window创建过程一致。

**2. 初始化DecorView并将Dialog视图添加到DecorView中**


```
    public void setContentView(View view) {
        mWindow.setContentView(view);
    }
```

也是通过Window去添加视图。

**3. 将DecorView添加到Window中并显示**


```
    public void show() {
        if (mShowing) {
            if (mDecor != null) {
                if (mWindow.hasFeature(Window.FEATURE_ACTION_BAR)) {
                    mWindow.invalidatePanelMenu(Window.FEATURE_ACTION_BAR);
                }
                mDecor.setVisibility(View.VISIBLE);
            }
            return;
        }

        mCanceled = false;
        
        if (!mCreated) {
            dispatchOnCreate(null);
        }

        onStart();
        mDecor = mWindow.getDecorView();

        if (mActionBar == null && mWindow.hasFeature(Window.FEATURE_ACTION_BAR)) {
            final ApplicationInfo info = mContext.getApplicationInfo();
            mWindow.setDefaultIcon(info.icon);
            mWindow.setDefaultLogo(info.logo);
            mActionBar = new WindowDecorActionBar(this);
        }

        WindowManager.LayoutParams l = mWindow.getAttributes();
        if ((l.softInputMode
                & WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION) == 0) {
            WindowManager.LayoutParams nl = new WindowManager.LayoutParams();
            nl.copyFrom(l);
            nl.softInputMode |=
                    WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
            l = nl;
        }

        try {
            mWindowManager.addView(mDecor, l);
            mShowing = true;
    
            sendShowMessage();
        } finally {
        }
    }
```

在Dialog的show方法中，通过WindowManager将DecorView 添加到View中。

---
从以上三个过程中发现，Dialog的Window创建和Activiy 的Window创建过程很类似，二者其实没什么区别。当Dialog被关闭时，会通过WindowManager来移除DecorView:


```
    void dismissDialog() {
        if (mDecor == null || !mShowing) {
            return;
        }

        if (mWindow.isDestroyed()) {
            Log.e(TAG, "Tried to dismissDialog() but the Dialog's window was already destroyed!");
            return;
        }

        try {
            mWindowManager.removeViewImmediate(mDecor);
        } finally {
            if (mActionMode != null) {
                mActionMode.finish();
            }
            mDecor = null;
            mWindow.closeAllPanels();
            onStop();
            mShowing = false;

            sendDismissMessage();
        }
    }
```


普通的Dialog有一个特殊之处，必须采用Activity的Context，如果采用Application的Context就会报错：

如下代码：


```
Dialog dialog = new Dialog(MainActivity.this.getApplication());
                TextView view1 = new TextView(MainActivity.this);
                view1.setText("This is Test");
                dialog.setContentView(view1);
                dialog.show();
```
执行后，报错信息如下：


```
 Process: com.sunqi.test.cm.toastdemo, PID: 8534
            android.view.WindowManager$BadTokenException: Unable to add window -- token null is not for an application
                at android.view.ViewRootImpl.setView(ViewRootImpl.java:815)
                at android.view.WindowManagerGlobal.addView(WindowManagerGlobal.java:361)
                at android.view.WindowManagerImpl.addView(WindowManagerImpl.java:93)
                at android.app.Dialog.show(Dialog.java:322)
                at com.sunqi.test.cm.toastdemo.MainActivity$2.onClick(MainActivity.java:47)
                at android.view.View.performClick(View.java:5727)
                at android.view.View$PerformClick.run(View.java:22762)
                at android.os.Handler.handleCallback(Handler.java:836)
                at android.os.Handler.dispatchMessage(Handler.java:103)
                at android.os.Looper.loop(Looper.java:203)
                at android.app.ActivityThread.main(ActivityThread.java:6406)
                at java.lang.reflect.Method.invoke(Native Method)
                at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:1113)
            com.android.internal.os.ZygoteInit.main(ZygoteInit:974

```

根据报错信息显示，没有windowToken结果报错。而应用token一般只有Activity拥有，所以，这里只需要Activity作为Context来显示对话框就可。

此外，系统Window比较特殊，可以不需要token .所以可以针对这个dialog，设置相关WindowType,这样就不需要token即可展示：


```
Dialog dialog = new Dialog(MainActivity.this.getApplication());
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
```
最后，展示系统Window的弹窗，需要悬浮窗权限：


```
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>

```


## 3. Toast的Window创建过程

Toast和Dialog不同，它的工作过程就稍显复杂。首先Toast也是基于Window来实现的，但是**由于Toast具有定时取消这一功能，所以系统采用了Handler**。在Toast的内部有两类IPC过程，第一类是Toast访问NotificationManagerService, 第二类是Notification-ManagerService回调Toast里的TN接口。为了便于描述，下 面将NotificationManagerService简称为**NMS**。
 
Toast属于系统Window,它内部的视图由两种方式指定，一种是系统默认的样式，另-种是通过setView方法来指定一一个自定义View,不管如何，它们都对应Toast的一一个View类型的内部成员mNextView。Toast提供了show和cancel分别用于显示和隐藏Toast,它们的内部是一个IPC过程，show方法和cancel方法的实现如下:

show：


```
    /**
     * Show the view for the specified duration.
     */
    public void show() {
        if (mNextView == null) {
            throw new RuntimeException("setView must have been called");
        }

        INotificationManager service = getService();
        String pkg = mContext.getOpPackageName();
        TN tn = mTN;
        tn.mNextView = mNextView;

        try {
            service.enqueueToast(pkg, tn, mDuration);
        } catch (RemoteException e) {
            // Empty
        }
    }
```



```

    /**
     * Close the view if it's showing, or don't show it if it isn't showing yet.
     * You do not normally have to call this.  Normally view will disappear on its own
     * after the appropriate duration.
     */
    public void cancel() {
        mTN.hide();

        try {
            getService().cancelToast(mContext.getPackageName(), mTN);
        } catch (RemoteException e) {
            // Empty
        }
    }
    
```

从_上面的代码可以看到，**显示和隐藏Toast都需要通过NMS来实现**，由于NMS运行在系统的进程中，所以只能通过远程调用的方式来显示和隐藏Toast。  需要**注意的是TN这个类，它是一个Binder类，在Toast和NMS进行IPC的过程中，当NMS处理Toast的显示或隐藏请求时会跨进程回调TN中的方法，这个时候由于TN运行在Binder线程池中**，**所以需要通过Handler将其切换到当前线程中。这里的当前线程是指发送Toast请求所在的线程**。

注意，由于这里使用了Handler,所以这意味着Toast无法在没有Looper的线程中弹出，这是因为Handler需要使用Looper才能完成切换线程的功能。

首先看，Toast的显示过程，它调用了NMS的enqueueToast方法：


```
        public void enqueueToast(String pkg, ITransientNotification callback, int duration)
        {
            if (DBG) {
                Slog.i(TAG, "enqueueToast pkg=" + pkg + " callback=" + callback
                        + " duration=" + duration);
            }

            if (pkg == null || callback == null) {
                Slog.e(TAG, "Not doing toast. pkg=" + pkg + " callback=" + callback);
                return ;
            }

            final boolean isSystemToast = isCallerSystem() || ("android".equals(pkg));

            if (ENABLE_BLOCKED_TOASTS && !noteNotificationOp(pkg, Binder.getCallingUid())) {
                if (!isSystemToast) {
                    Slog.e(TAG, "Suppressing toast from package " + pkg + " by user request.");
                    return;
                }
            }

            synchronized (mToastQueue) {
                int callingPid = Binder.getCallingPid();
                long callingId = Binder.clearCallingIdentity();
                try {
                    ToastRecord record;
                    int index = indexOfToastLocked(pkg, callback);
                    // If it's already in the queue, we update it in place, we don't
                    // move it to the end of the queue.
                    if (index >= 0) {
                        record = mToastQueue.get(index);
                        record.update(duration);
                    } else {
                        // Limit the number of toasts that any given package except the android
                        // package can enqueue.  Prevents DOS attacks and deals with leaks.
                        if (!isSystemToast) {
                            int count = 0;
                            final int N = mToastQueue.size();
                            for (int i=0; i<N; i++) {
                                 final ToastRecord r = mToastQueue.get(i);
                                 if (r.pkg.equals(pkg)) {
                                     count++;
                                     if (count >= MAX_PACKAGE_NOTIFICATIONS) {
                                         Slog.e(TAG, "Package has already posted " + count
                                                + " toasts. Not showing more. Package=" + pkg);
                                         return;
                                     }
                                 }
                            }
                        }

                        record = new ToastRecord(callingPid, pkg, callback, duration);
                        mToastQueue.add(record);
                        index = mToastQueue.size() - 1;
                        keepProcessAliveLocked(callingPid);
                    }
                    // If it's at index 0, it's the current toast.  It doesn't matter if it's
                    // new or just been updated.  Call back and tell it to show itself.
                    // If the callback fails, this will remove it from the list, so don't
                    // assume that it's valid after this.
                    if (index == 0) {
                        showNextToastLocked();
                    }
                } finally {
                    Binder.restoreCallingIdentity(callingId);
                }
            }
        }
```

---


- NMS的enqueueToast方法的**第一个参数表示当前应用的包名，第二个参数tn表示远程回调，第三个参数表示Toast的时长**。enqueueToast首先将Toast请求封装为ToastRecord对象并将其添加到-一个名为mToastQueue的队列中。mToastQueue 其实是-一个ArrayList。对于非系统应用来说，mToastQueue中最多能同时存在50个ToastRecord,这样做是为了防止DOS (Denial of Service)。如果不这么做，试想一下，  如果我们通过大量的循环去连续弹出Toast,这将会导致其他应用没有机会弹出Toast,那么对于其他应用的Toast请求，系统的行为就是拒绝服务，这就是拒绝服务攻击的含义，这种手段常用于网络攻击中。

- 正常情况下，**一个应用不可能达到上限，当ToastRecord被添加到mToastQueue中后，NMS就会通过showNextToastl ocked方法来显示当前的Toast**。  Toast的显示是由ToastRecord的callback 来完成的，这个callback 实际上就是Toast中的TN对象的远程Binder,通过callback来访问TN中的方法是需要跨进程来完成的，  最终被调用的TN中的方法会运行在发起Toast请求的应用的Binder线程池中。

**showNextToastLocked()**

```
    void showNextToastLocked() {
        ToastRecord record = mToastQueue.get(0);
        while (record != null) {
            if (DBG) Slog.d(TAG, "Show pkg=" + record.pkg + " callback=" + record.callback);
            try {
                record.callback.show();
                scheduleTimeoutLocked(record);
                return;
            } catch (RemoteException e) {
                Slog.w(TAG, "Object died trying to show notification " + record.callback
                        + " in package " + record.pkg);
                // remove it from the list and let the process die
                int index = mToastQueue.indexOf(record);
                if (index >= 0) {
                    mToastQueue.remove(index);
                }
                keepProcessAliveLocked(record.pid);
                if (mToastQueue.size() > 0) {
                    record = mToastQueue.get(0);
                } else {
                    record = null;
                }
            }
        }
    }
```
Toast显示后，NMS还会通过scheduleTimeoutLocked来控制发送延时消息，具体延时时长取决于Toast时长。


```
    private void scheduleTimeoutLocked(ToastRecord r)
    {
        mHandler.removeCallbacksAndMessages(r);
        Message m = Message.obtain(mHandler, MESSAGE_TIMEOUT, r);
        long delay = r.duration == Toast.LENGTH_LONG ? LONG_DELAY : SHORT_DELAY;
        mHandler.sendMessageDelayed(m, delay);
    }

```

延时相应时长后，NMS会通过cancelToastLocked方法来隐藏Toast并将其从mToastQueue中移除，接着显示mToastQueue中的其他Toast.

Toast的隐藏也是通过ToastRecord 的 callback完成。同样也是一次IPC进程：


```
    void cancelToastLocked(int index) {
        ToastRecord record = mToastQueue.get(index);
        try {
            record.callback.hide();
        } catch (RemoteException e) {
            Slog.w(TAG, "Object died trying to hide notification " + record.callback
                    + " in package " + record.pkg);
            // don't worry about this, we're about to remove it from
            // the list anyway
        }
        mToastQueue.remove(index);
        keepProcessAliveLocked(record.pid);
        if (mToastQueue.size() > 0) {
            // Show the next one. If the callback fails, this will remove
            // it from the list, so don't assume that the list hasn't changed
            // after this point.
            showNextToastLocked();
        }
    }
```

 通过上面的分析，大家知道**Toast的显示和影响过程实际上是通过Toast中的TN这个类来实现的**，它有两个方法show和hide,分别对应Toast的显示和隐藏。
 
 **由于这两个方法是被NMS以跨进程的方式调用的，因此它们运行在Binder线程池中。为了将执行环境切换到Toast请求所在的线程，在它们的内部使用了Handler, 如下所示**。


```
 private static class TN extends ITransientNotification.Stub {
        final Runnable mShow = new Runnable() {
            @Override
            public void run() {
                handleShow();
            }
        };

        final Runnable mHide = new Runnable() {
            @Override
            public void run() {
                handleHide();
                // Don't do this in handleHide() because it is also invoked by handleShow()
                mNextView = null;
            }
        };

        private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
        final Handler mHandler = new Handler();    
        ....
        
        
                /**
         * schedule handleShow into the right thread
         */
        @Override
        public void show() {
            if (localLOGV) Log.v(TAG, "SHOW: " + this);
            mHandler.post(mShow);
        }

        /**
         * schedule handleHide into the right thread
         */
        @Override
        public void hide() {
            if (localLOGV) Log.v(TAG, "HIDE: " + this);
            mHandler.post(mHide);
        }
        }
```

上述代码中，TN中的mShow和mHide 是两个Runnable,内部分别调用handleShow和handleHide方法。由此可见，Toast内部类TN中的handleShow和handleHide才是分别真正完成显示和隐藏Toast的地方。


```
        public void handleShow() {
            if (localLOGV) Log.v(TAG, "HANDLE SHOW: " + this + " mView=" + mView
                    + " mNextView=" + mNextView);
            if (mView != mNextView) {
                // remove the old view if necessary
                handleHide();
                mView = mNextView;
                Context context = mView.getContext().getApplicationContext();
                String packageName = mView.getContext().getOpPackageName();
                if (context == null) {
                    context = mView.getContext();
                }
                mWM = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
                // We can resolve the Gravity here by using the Locale for getting
                // the layout direction
                final Configuration config = mView.getContext().getResources().getConfiguration();
                final int gravity = Gravity.getAbsoluteGravity(mGravity, config.getLayoutDirection());
                mParams.gravity = gravity;
                if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.FILL_HORIZONTAL) {
                    mParams.horizontalWeight = 1.0f;
                }
                if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.FILL_VERTICAL) {
                    mParams.verticalWeight = 1.0f;
                }
                mParams.x = mX;
                mParams.y = mY;
                mParams.verticalMargin = mVerticalMargin;
                mParams.horizontalMargin = mHorizontalMargin;
                mParams.packageName = packageName;
                if (mView.getParent() != null) {
                    if (localLOGV) Log.v(TAG, "REMOVE! " + mView + " in " + this);
                    mWM.removeView(mView);
                }
                if (localLOGV) Log.v(TAG, "ADD! " + mView + " in " + this);
                mWM.addView(mView, mParams);
                trySendAccessibilityEvent();
            }
        }
```



```
        public void handleHide() {
            if (localLOGV) Log.v(TAG, "HANDLE HIDE: " + this + " mView=" + mView);
            if (mView != null) {
                // note: checking parent() just to make sure the view has
                // been added...  i have seen cases where we get here when
                // the view isn't yet added, so let's try not to crash.
                if (mView.getParent() != null) {
                    if (localLOGV) Log.v(TAG, "REMOVE! " + mView + " in " + this);
                    mWM.removeView(mView);
                }

                mView = null;
            }
        }
```
至此，Toast中Window的创建过程结束。

## 4. 问题扩展(Dialog & Toast)

**(1)Dialog为什么不能使用Application的Context？**

根据上述Dialog中使用Application的Context后，LogCat的异常信息如下：


```
Caused by: android.view.WindowManager$BadTokenException: Unable to add window -- token null is not for an application
                           at android.view.ViewRootImpl.setView(ViewRootImpl.java:685)
                           at android.view.WindowManagerGlobal.addView(WindowManagerGlobal.java:342)
                           at android.view.WindowManagerImpl.addView(WindowManagerImpl.java:93)
                           at android.app.Dialog.show(Dialog.java:316)
```

从字面上也很容易理解“BadTokenException: Unable to add window -- token null is not for an application”，发生一个BadTokenException的异常，不能添加Window。

在解释这个问题前，有必要先理清一些概念：

**Window** ：
定义窗口样式和行为的抽象基类，用于作为顶层的view加到WindowManager中，其实现类是PhoneWindow。
每个Window都需要指定一个Type（应用窗口、子窗口、系统窗口）。Activity对应的窗口是应用窗口；PopupWindow，ContextMenu，OptionMenu是常用的子窗口；像Toast和系统警告提示框（如ANR）就是系窗口，还有很多应用的悬浮框也属于系统窗口类型。

**WindowManager**：
用来在应用与window之间的管理接口，管理窗口顺序，消息等。

**WindowManagerService**：
简称Wms，WindowManagerService管理窗口的创建、更新和删除，显示顺序等，是WindowManager这个管理接品的真正的实现类。它运行在System_server进程，作为服务端，客户端（应用程序）通过IPC调用和它进行交互。

**Token**：这里提到的Token主是指窗口令牌（Window Token），是一种特殊的Binder令牌，Wms用它唯一标识系统中的一个窗口。

下图显示了Activity的Window和Wms的关系：

![image](http://o9m6aqy3r.bkt.clouddn.com//Window/Activity&Window&Wms.png)

Activity有一个PhoneWindow，当我们调用setContentView时，其实最终结果是把我们的DecorView作为子View添加到PhoneWindow的DecorView中。而最终这个DecorView，过WindowMnagerImpl的addView方法添加到WMS中去的，由WMS负责管理和绘制（真正的绘制在SurfaceFlinger服务中）。

![image](http://o9m6aqy3r.bkt.clouddn.com//Window/Activity&PhoneWindow&ContentView.png)

>**Dialog的窗口属于什么类型？**

跟Activity对应的窗口一样，Dialog有一个PhoneWindow的实例。Dialog 的类型是TYPE_APPLICATION，属于应用窗口类型。


```
Dialog(@NonNull Context context, @StyleRes int themeResId, boolean createContextThemeWrapper) {
        // 忽略一些代码
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        final Window w = new PhoneWindow(mContext);
        mWindow = w;
        w.setCallback(this);
        w.setOnWindowDismissedCallback(this);
        w.setWindowManager(mWindowManager, null, null);
        w.setGravity(Gravity.CENTER);

        mListenersHandler = new ListenersHandler(this);
    }
```
注意w.setWindowManager(mWindowManager, null, null)这句，把appToken设置为null。这也是**Dialog和Activity窗口的一个区别，Activity会将这个appToken设置为ActivityThread传过来的token**.

```
public void setWindowManager(WindowManager wm, IBinder appToken, String appName)
```

在Dialog的show方法中：

```
public void show() {
        // 忽略一些代码
        mDecor = mWindow.getDecorView();

        WindowManager.LayoutParams l = mWindow.getAttributes();
        if ((l.softInputMode
                & WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION) == 0) {
            WindowManager.LayoutParams nl = new WindowManager.LayoutParams();
            nl.copyFrom(l);
            nl.softInputMode |=
                    WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
            l = nl;
        }

        try {
            mWindowManager.addView(mDecor, l);
            mShowing = true;
    
            sendShowMessage();
        } finally {
        }
    }
```

mWindow是PhoneWindow类型，mWindow.getAttributes()默认获取到的Type为TYPE_APPLICATION。

Dialog最终也是通过系统的WindowManager把自己的Window添加到WMS上。在addView前，Dialog的token是null（**上面提到过的w.setWindowManager第二参数为空**）。

Dialog初化始时是通过Context.getSystemServer 来获取 WindowManager，而如果用Application或者Service的Context去获取这个WindowManager服务的话，会得到一个WindowManagerImpl的实例，这个实例里token也是空的。之后在Dialog的show方法中将Dialog的View(PhoneWindow.getDecorView())添加到WindowManager时会给token设置默认值还是null。


**如果这个Context是Activity，则直接返回Activity的mWindowManager，这个mWindowManager在Activity的attach方法被创建，Token指向此Activity的Token，mParentWindow为Activity的Window本身**。如下的代码Activity重写了getSystemService这个方法：


```
@Override
    public Object getSystemService(@ServiceName @NonNull String name) {
        if (getBaseContext() == null) {
            throw new IllegalStateException(
                    "System services not available to Activities before onCreate()");
        }

        if (WINDOW_SERVICE.equals(name)) {
            return mWindowManager;
        } else if (SEARCH_SERVICE.equals(name)) {
            ensureSearchManager();
            return mSearchManager;
        }
        return super.getSystemService(name);
    }

```
系统对TYPE_APPLICATION类型的窗口，要求必需是Activity的Token，不是的话系统会抛出BadTokenException异常。Dialog 是应用窗口类型，Token必须是Activity的Token。


>**答案总结**


那为什么一定要是Activity的Token呢？我想使用**Token应该是为了安全问题，通过Token来验证WindowManager服务请求方是否是合法的。如果我们可以使用Application的Context，或者说Token可以不是Activity的Token，那么用户可能已经跳转到别的应用的Activity界面了，但我们却可以在别人的界面上弹出我们的Dialog，想想就觉得很危险**。

如你跳到了微信界面了，这时在后台的某个应用里调用Dialog的show，那么微信的界面上会显示一个Dialog，这个Dialog可能会让用户输入密码什么的，而用户完全无法区分是不是微信弹出的。

**(2)根据对Toast Window的理解，怎么实现反射Toast弹出？**

根据上述的分析，已知，Toast的显示和隐藏都受到NMS 的控制，而其内部类TN作为Binder对象，被NMS调用，实现真正的显示/隐藏逻辑。

所以，Toast中的内部类真正实现展示/隐藏逻辑，具体代码如下：


```
    private static class TN extends ITransientNotification.Stub {
        final Runnable mShow = new Runnable() {
            @Override
            public void run() {
                handleShow();
            }
        };

        final Runnable mHide = new Runnable() {
            @Override
            public void run() {
                handleHide();
                // Don't do this in handleHide() because it is also invoked by handleShow()
                mNextView = null;
            }
        };

        private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
        final Handler mHandler = new Handler();    

        int mGravity;
        int mX, mY;
        float mHorizontalMargin;
        float mVerticalMargin;


        View mView;
        View mNextView;

        WindowManager mWM;

        TN() {
            // XXX This should be changed to use a Dialog, with a Theme.Toast
            // defined that sets up the layout params appropriately.
            final WindowManager.LayoutParams params = mParams;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.format = PixelFormat.TRANSLUCENT;
            params.windowAnimations = com.android.internal.R.style.Animation_Toast;
            params.type = WindowManager.LayoutParams.TYPE_TOAST;
            params.setTitle("Toast");
            params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        /**
         * schedule handleShow into the right thread
         */
        @Override
        public void show() {
            if (localLOGV) Log.v(TAG, "SHOW: " + this);
            mHandler.post(mShow);
        }

        /**
         * schedule handleHide into the right thread
         */
        @Override
        public void hide() {
            if (localLOGV) Log.v(TAG, "HIDE: " + this);
            mHandler.post(mHide);
        }

        public void handleShow() {
            if (localLOGV) Log.v(TAG, "HANDLE SHOW: " + this + " mView=" + mView
                    + " mNextView=" + mNextView);
            if (mView != mNextView) {
                // remove the old view if necessary
                handleHide();
                mView = mNextView;
                Context context = mView.getContext().getApplicationContext();
                String packageName = mView.getContext().getOpPackageName();
                if (context == null) {
                    context = mView.getContext();
                }
                mWM = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
                // We can resolve the Gravity here by using the Locale for getting
                // the layout direction
                final Configuration config = mView.getContext().getResources().getConfiguration();
                final int gravity = Gravity.getAbsoluteGravity(mGravity, config.getLayoutDirection());
                mParams.gravity = gravity;
                if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.FILL_HORIZONTAL) {
                    mParams.horizontalWeight = 1.0f;
                }
                if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.FILL_VERTICAL) {
                    mParams.verticalWeight = 1.0f;
                }
                mParams.x = mX;
                mParams.y = mY;
                mParams.verticalMargin = mVerticalMargin;
                mParams.horizontalMargin = mHorizontalMargin;
                mParams.packageName = packageName;
                if (mView.getParent() != null) {
                    if (localLOGV) Log.v(TAG, "REMOVE! " + mView + " in " + this);
                    mWM.removeView(mView);
                }
                if (localLOGV) Log.v(TAG, "ADD! " + mView + " in " + this);
                mWM.addView(mView, mParams);
                trySendAccessibilityEvent();
            }
        }

        private void trySendAccessibilityEvent() {
            AccessibilityManager accessibilityManager =
                    AccessibilityManager.getInstance(mView.getContext());
            if (!accessibilityManager.isEnabled()) {
                return;
            }
            // treat toasts as notifications since they are used to
            // announce a transient piece of information to the user
            AccessibilityEvent event = AccessibilityEvent.obtain(
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
            event.setClassName(getClass().getName());
            event.setPackageName(mView.getContext().getPackageName());
            mView.dispatchPopulateAccessibilityEvent(event);
            accessibilityManager.sendAccessibilityEvent(event);
        }        

        public void handleHide() {
            if (localLOGV) Log.v(TAG, "HANDLE HIDE: " + this + " mView=" + mView);
            if (mView != null) {
                // note: checking parent() just to make sure the view has
                // been added...  i have seen cases where we get here when
                // the view isn't yet added, so let's try not to crash.
                if (mView.getParent() != null) {
                    if (localLOGV) Log.v(TAG, "REMOVE! " + mView + " in " + this);
                    mWM.removeView(mView);
                }

                mView = null;
            }
        }
    }
```

**所以，如果我们想实现控制Toast展示时长，自定义动画等效果时，即可以通过java反射拿到Toast内的TN对象后进行相关设置**

比如：


```
    private void initVar() {
        if (mIsInited) {
            return;
        }

        try {

            Field tnField = mToast.getClass().getDeclaredField("mTN");
            tnField.setAccessible(true);
            mToast.setGravity(mGravity, mXOffset, mYOffset);

            mTN = tnField.get(mToast);
            mShowMethod = mTN.getClass().getMethod("show");
            mHideMethod = mTN.getClass().getMethod("hide");

            mParamsField = mTN.getClass().getDeclaredField("mParams");
            mParamsField.setAccessible(true);
            mParams = (WindowManager.LayoutParams) mParamsField.get(mTN);
            mParams.height = mHeight;
            mParams.width = mWidth;
            mParams.gravity = Gravity.CENTER;
            if (!mEnableToastAnimation) {
                mParams.windowAnimations = android.R.style.Animation;
            }else {
                mParams.windowAnimations = mAnimation;
            }

            mParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

            if (mPermissionType == ANTIHARASS_PERMISSION_TYPE || mPermissionType == FLOAT_WINDOWS_PERMISSION_TYPE){
                mParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                if (EMuiHelper.isEMUI4_0AndAbove()) {
                    mParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                }
            } else if (mPermissionType == FLOAT_WINDOWS_PERMISSION_TYPE2) {
                mParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            } else if (mPermissionType == FLOAT_WINDOWS_PERMISSION_TYPE3) {
                mParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            } else {
                if (mNotInterceptEvent) {
                    // 弹窗不处理任何事件，事件透传给下面的窗口
                    mParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                } else {
                    if (!mFocusable) {
                        mParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    }
                }
            }

            mIsInited = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

- 反射拿到`mTN`对象后，再得到其show/hide方法。
- 反射拿到Window配置相关的内部变量`mParams`
- 根据需要设置自己需要的参数：位置，动画，WindowType相关,flag等

最后，封装展示方法，调用反射后的show


```
    public void showViewOther(View view) {
        initVar();

        if (isShow) return;
        mToast.setView(view);
        try {
            /**调用tn.mShowMethod()之前一定要先设置mNextView*/
            Field tnNextViewField = mTN.getClass().getDeclaredField("mNextView");
            tnNextViewField.setAccessible(true);
            tnNextViewField.set(mTN, mToast.getView());
            mShowMethod.invoke(mTN);
            isShow = true;
            mView = view;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

```
这样，反射后的Toast就可以正常展示了，而且**整个过程没有NMS的参与，自己灵活控制hide时机**。