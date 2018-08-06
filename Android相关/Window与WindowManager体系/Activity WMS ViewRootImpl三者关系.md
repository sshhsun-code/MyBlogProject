# Activity WMS ViewRootImpl三者关系

参考： [https://blog.csdn.net/kc58236582/article/details/52088224](https://blog.csdn.net/kc58236582/article/details/52088224)

1. Activity
2. PhoneWindow
3. ViewRootImpl
4. WindowManagerService


## 1. Activity

在ActivityThread中的performLaunchActivity函数中, 先创建了Activity，然后调用了Activity的attach函数.


```
        ......
        Activity activity = null;
        try {
            java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
            activity = mInstrumentation.newActivity(
                    cl, component.getClassName(), r.intent);
	......
 
            if (activity != null) {
                Context appContext = createBaseContextForActivity(r, activity);
                CharSequence title = r.activityInfo.loadLabel(appContext.getPackageManager());
                Configuration config = new Configuration(mCompatConfiguration);
                if (DEBUG_CONFIGURATION) Slog.v(TAG, "Launching activity "
                        + r.activityInfo.name + " with config " + config);
                activity.attach(appContext, this, getInstrumentation(), r.token,
                        r.ident, app, r.intent, r.activityInfo, title, r.parent,
                        r.embeddedID, r.lastNonConfigurationInstances, config,
                        r.referrer, r.voiceInteractor);

```
Activity的attach函数，先创建了PhoneWindow对象给了mWindow，然后调用其setWindowManager设置其WindowManager，最后再调用mWindow的getWindowManager方法作为Activity的mWindowManager成员变量


```
	......
        mWindow = new PhoneWindow(this);//新建PhoneWindow对象
        mWindow.setCallback(this);//这window中设置回调，在按键事件分发的时候中用到。如果有这个回调，就调用Activity的dispatchKeyEvent
        mWindow.setOnWindowDismissedCallback(this);
	......
	mWindow.setWindowManager(
		(WindowManager)context.getSystemService(Context.WINDOW_SERVICE),
		mToken, mComponent.flattenToString(),
		(info.flags & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0);
        if (mParent != null) {
            mWindow.setContainer(mParent.getWindow());
        }
        mWindowManager = mWindow.getWindowManager();

```
Window类的setWindowManager方法，也是最后调用了WindowManagerImpl的createLocalWindowManager


```
    public void setWindowManager(WindowManager wm, IBinder appToken, String appName,
            boolean hardwareAccelerated) {
        mAppToken = appToken;
        mAppName = appName;
        mHardwareAccelerated = hardwareAccelerated
                || SystemProperties.getBoolean(PROPERTY_HARDWARE_UI, false);
        if (wm == null) {
            wm = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        }
        mWindowManager = ((WindowManagerImpl)wm).createLocalWindowManager(this);
    }

```

而createLocalWindowManager函数就是创建了一个WindowManagerImpl对象，因此Activity的mWindowManager成员变量最后就是WindowManagerImpl对象。


```
    public WindowManagerImpl createLocalWindowManager(Window parentWindow) {
        return new WindowManagerImpl(mDisplay, parentWindow);
    }

```
在WindowManagerImpl中有一个mGlobal的变量，最后都是通过这个变量调用的方法，因此我们来看看这个类。


```
public final class WindowManagerImpl implements WindowManager {
    private final WindowManagerGlobal mGlobal = WindowManagerGlobal.getInstance();

```
WindowManagerGlobal类中主要有3个非常重要的变量

```
    private final ArrayList<View> mViews = new ArrayList<View>();
    private final ArrayList<ViewRootImpl> mRoots = new ArrayList<ViewRootImpl>();
    private final ArrayList<WindowManager.LayoutParams> mParams =
            new ArrayList<WindowManager.LayoutParams>();

```
>其中：
- mViews保存的是View对象，DecorView

- mRoots保存和顶层View关联的ViewRootImpl对象

- mParams保存的是创建顶层View的layout参数。

**而WindowManagerGlobal类也负责和WMS通信。**

## 2. PhoneWindow

在上面Activity代码讲解中，在Activity的attach函数中新建了PhoneWindow对象。

#### 创建DecorView

在PhoneWindow的setContentView函数中会调用installDector来创建DecorView对象


```
    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        // Note: FEATURE_CONTENT_TRANSITIONS may be set in the process of installing the window
        // decor, when theme attributes and the like are crystalized. Do not check the feature
        // before this happens.
        if (mContentParent == null) {
            installDecor();
        } else if (!hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            mContentParent.removeAllViews();
        }
 
        if (hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            view.setLayoutParams(params);
            final Scene newScene = new Scene(mContentParent, view);
            transitionTo(newScene);
        } else {
            mContentParent.addView(view, params);
        }
        mContentParent.requestApplyInsets();
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();
        }
    }

```
在installDecor函数中调用了generateDecor函数来创建按DecorView对象。


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
......

```

```
    protected DecorView generateDecor() {
        return new DecorView(getContext(), -1);
    }

```

#### DecorView的按键处理

DecorView是PhoneWindow类的一个内部类。我们来看下按键事件在DecorView的处理过程，先是在ViewRootImpl的processKeyEvent函数：


```
    final class ViewPostImeInputStage extends InputStage {
        public ViewPostImeInputStage(InputStage next) {
            super(next);
        }
 
        @Override
        protected int onProcess(QueuedInputEvent q) {
            if (q.mEvent instanceof KeyEvent) {
                return processKeyEvent(q);
            } else {
                // If delivering a new non-key event, make sure the window is
                // now allowed to start updating.
                handleDispatchWindowAnimationStopped();
                final int source = q.mEvent.getSource();
                if ((source & InputDevice.SOURCE_CLASS_POINTER) != 0) {
                    return processPointerEvent(q);
                } else if ((source & InputDevice.SOURCE_CLASS_TRACKBALL) != 0) {
                    return processTrackballEvent(q);
                } else {
                    return processGenericMotionEvent(q);
                }
            }
        }

```
在processKeyEvent调用了了mView的dispatchKeyEvent函数，**即调用了DecorView的dispatchKeyEvent函数**


```
        private int processKeyEvent(QueuedInputEvent q) {
            final KeyEvent event = (KeyEvent)q.mEvent;
 
            if (event.getAction() != KeyEvent.ACTION_UP) {
                // If delivering a new key event, make sure the window is
                // now allowed to start updating.
                handleDispatchWindowAnimationStopped();
            }
 
            // Deliver the key to the view hierarchy.
            if (mView.dispatchKeyEvent(event)) {
                return FINISH_HANDLED;
            }

```
然后在DecorView的dispatchKeyEvent中，**最后调用getCallBack，如果有回调，就调用回调的dispatchKeyEvent，这个就是在Activity的attach中将Activity设置为PhoneWindow的回调，因此最后dispatchKeyEvent就会到Activity的dispatchKeyEvent函数中**


```
        public boolean dispatchKeyEvent(KeyEvent event) {
            final int keyCode = event.getKeyCode();
            final int action = event.getAction();
            final boolean isDown = action == KeyEvent.ACTION_DOWN;
 
            if (isDown && (event.getRepeatCount() == 0)) {
                // First handle chording of panel key: if a panel key is held
                // but not released, try to execute a shortcut in it.
                if ((mPanelChordingKey > 0) && (mPanelChordingKey != keyCode)) {
                    boolean handled = dispatchKeyShortcutEvent(event);
                    if (handled) {
                        return true;
                    }
                }
 
                // If a panel is open, perform a shortcut on it without the
                // chorded panel key
                if ((mPreparedPanel != null) && mPreparedPanel.isOpen) {
                    if (performPanelShortcut(mPreparedPanel, keyCode, event, 0)) {
                        return true;
                    }
                }
            }
 
            if (!isDestroyed()) {
                final Callback cb = getCallback();
                final boolean handled = cb != null && mFeatureId < 0 ? cb.dispatchKeyEvent(event)//Activity中回调函数dispatchKeyEvent
                        : super.dispatchKeyEvent(event);
                if (handled) {
                    return true;
                }
            }
 
            return isDown ? PhoneWindow.this.onKeyDown(mFeatureId, event.getKeyCode(), event)
                    : PhoneWindow.this.onKeyUp(mFeatureId, event.getKeyCode(), event);
        }

```
而Activity的attach中调用了PhoneWindow的setCallBack函数将回调设置成Activity，所以，**最后dispatchKeyEvent就会调用到Activity的dispatchKeyEvent中**。


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
        mWindow.setCallback(this);//设置CallBack回调到Activity中

```

## 3. ViewRootImpl

**ActivityThread的handleResumeActivity函数中会调用WindowManager的addView函数，而这个WindowManager就是Activity的mWindowManager**


```
            if (r.window == null && !a.mFinished && willBeVisible) {
                r.window = r.activity.getWindow();
                View decor = r.window.getDecorView();
                decor.setVisibility(View.INVISIBLE);
                ViewManager wm = a.getWindowManager();
                WindowManager.LayoutParams l = r.window.getAttributes();
                a.mDecor = decor;
                l.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
                l.softInputMode |= forwardBit;
                if (a.mVisibleFromClient) {
                    a.mWindowAdded = true;
                    wm.addView(decor, l);
                }
 
            // If the window has already been added, but during resume
            // we started another activity, then don't yet make the
            // window visible.
            }

```
最后addView函数在WindowManagerImpl中实现，我们再来看看WindowManagerImpl的addView函数。


```
    @Override
    public void addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        applyDefaultToken(params);
        mGlobal.addView(view, params, mDisplay, mParentWindow);
    }

```
在WindowManagerImpl最后是调用了WindowManagerGlobal的addView函数, 在这个函数中我们新建ViewRootImpl对象，然后调用了ViewRootImpl的setView函数，这里的view就是Activity的Window对象的DecorView。并且**在这个函数中，把view root param这3个保存在mViews mRoots mParams这3个成员变量中**了。

```
        ......
        ViewRootImpl root;
        View panelParentView = null;
 
        synchronized (mLock) {
            // Start watching for system property changes.
            if (mSystemPropertyUpdater == null) {
                mSystemPropertyUpdater = new Runnable() {
                    @Override public void run() {
                        synchronized (mLock) {
                            for (int i = mRoots.size() - 1; i >= 0; --i) {
                                mRoots.get(i).loadSystemProperties();
                            }
                        }
                    }
                };
                SystemProperties.addChangeCallback(mSystemPropertyUpdater);
            }
 
            int index = findViewLocked(view, false);
            if (index >= 0) {
                if (mDyingViews.contains(view)) {
                    // Don't wait for MSG_DIE to make it's way through root's queue.
                    mRoots.get(index).doDie();
                } else {
                    throw new IllegalStateException("View " + view
                            + " has already been added to the window manager.");
                }
                // The previous removeView() had not completed executing. Now it has.
            }
 
            // If this is a panel window, then find the window it is being
            // attached to for future reference.
            if (wparams.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW &&
                    wparams.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                final int count = mViews.size();
                for (int i = 0; i < count; i++) {
                    if (mRoots.get(i).mWindow.asBinder() == wparams.token) {
                        panelParentView = mViews.get(i);
                    }
                }
            }
 
            root = new ViewRootImpl(view.getContext(), display);//创建ViewRootImpl
 
            view.setLayoutParams(wparams);
 
            mViews.add(view);
            mRoots.add(root);//保存各个成员变量
            mParams.add(wparams);
        }
 
        // do this last because it fires off messages to start doing things
        try {
            root.setView(view, wparams, panelParentView);//调用ViewRootImpl的setView函数
        } catch (RuntimeException e) {
            // BadTokenException or InvalidDisplayException, clean up.
            synchronized (mLock) {
                final int index = findViewLocked(view, false);
                if (index >= 0) {
                    removeViewLocked(index, true);
                }
            }
            throw e;
        }
    }

```
在ViewRootImpl的setView函数中，先**调用了requestLayout来绘制view**，然后调用了**mWindowSession的addToDisplay函数和WMS通信**


```
                ......
                requestLayout();//开始绘制View，出发ViewRootImpl的绘制流程
                if ((mWindowAttributes.inputFeatures
                        & WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL) == 0) {
                    mInputChannel = new InputChannel();
                }
                try {
                    mOrigWindowType = mWindowAttributes.type;
                    mAttachInfo.mRecomputeGlobalAttributes = true;
                    collectViewAttributes();
                    res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
                            getHostVisibility(), mDisplay.getDisplayId(),
                            mAttachInfo.mContentInsets, mAttachInfo.mStableInsets,
                            mAttachInfo.mOutsets, mInputChannel);
                } 

```
**mWindowSession**，就是通过WMS获取到WindowSession的Binder，并且自己设置了一个回调在WMS中


```
    public static IWindowSession getWindowSession() {
        synchronized (WindowManagerGlobal.class) {
            if (sWindowSession == null) {
                try {
                    InputMethodManager imm = InputMethodManager.getInstance();
                    IWindowManager windowManager = getWindowManagerService();
                    sWindowSession = windowManager.openSession(
                            new IWindowSessionCallback.Stub() {
                                @Override
                                public void onAnimatorScaleChanged(float scale) {
                                    ValueAnimator.setDurationScale(scale);
                                }
                            },
                            imm.getClient(), imm.getInputContext());
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to open window session", e);
                }
            }
            return sWindowSession;
        }
    }

```
最后Binder调用到Session的addToDisplay，也会调用WMS的addWindow函数


```
    public int addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs,
            int viewVisibility, int displayId, Rect outContentInsets, Rect outStableInsets,
            Rect outOutsets, InputChannel outInputChannel) {
        return mService.addWindow(this, window, seq, attrs, viewVisibility, displayId,
                outContentInsets, outStableInsets, outOutsets, outInputChannel);
    }

```
ViewRootImpl中调用mWindowSession.addToDisplay函数传入的一个参数mWindow，


```
    mWindow = new W(this);
```

W是ViewRootImpl的一个嵌入类，也是一个Binder服务。通过mWindowSession.addToDisplay函数传入WMS，用来在WMS中通过Binder回调.


```
    static class W extends IWindow.Stub {
        private final WeakReference<ViewRootImpl> mViewAncestor;
        private final IWindowSession mWindowSession;
 
        W(ViewRootImpl viewAncestor) {
            mViewAncestor = new WeakReference<ViewRootImpl>(viewAncestor);
            mWindowSession = viewAncestor.mWindowSession;
        }
 
        @Override
        public void resized(Rect frame, Rect overscanInsets, Rect contentInsets,
                Rect visibleInsets, Rect stableInsets, Rect outsets, boolean reportDraw,
                Configuration newConfig) {
            final ViewRootImpl viewAncestor = mViewAncestor.get();
            if (viewAncestor != null) {
                viewAncestor.dispatchResized(frame, overscanInsets, contentInsets,
                        visibleInsets, stableInsets, outsets, reportDraw, newConfig);
            }
        }
......

```

## 4. WindowManagerService



```
public class WindowManagerService extends IWindowManager.Stub
        implements Watchdog.Monitor, WindowManagerPolicy.WindowManagerFuncs {
		......
		final WindowManagerPolicy mPolicy = new PhoneWindowManager();
		......
		/**
		 * All currently active sessions with clients.
		 */
		final ArraySet<Session> mSessions = new ArraySet<>();
 
		/**
		 * Mapping from an IWindow IBinder to the server's Window object.
		 * This is also used as the lock for all of our state.
		 * NOTE: Never call into methods that lock ActivityManagerService while holding this object.
		 */
		final HashMap<IBinder, WindowState> mWindowMap = new HashMap<>();
 
		/**
		 * Mapping from a token IBinder to a WindowToken object.
		 */
		final HashMap<IBinder, WindowToken> mTokenMap = new HashMap<>();

```
---

- **mPolicy**是WMS所执行的窗口管理策略类，现在android只有PhoneWindowManager一个策略类。

- **mSessions**存储的是Session服务类，每个应用都在WMS中有一个对应的Session对象保存在mSessions中。

- **mTokenMap**保存的是所有窗口的WindowToken对象。

- **mWindowMap**保存的是所有窗口的WindowState对象。


#### WindowToken对象

在WMS中有两种常见的Token，**WindowToken和AppWindowToken**。先来看WindowToken的定义：


```
class WindowToken {
    // The window manager!
    final WindowManagerService service;
 
    // The actual token.
    final IBinder token;
 
    // The type of window this token is for, as per WindowManager.LayoutParams.
    final int windowType;
 
    // Set if this token was explicitly added by a client, so should
    // not be removed when all windows are removed.
    final boolean explicit;
 
    // For printing.
    String stringName;
 
    // If this is an AppWindowToken, this is non-null.
    AppWindowToken appWindowToken;
 
    // All of the windows associated with this token.
    final WindowList windows = new WindowList();

```
**WindowToken的成员变量token是IBinder对象，具有系统唯一性。因此，向WMS的mWindowMap或者mTokenMap插入对象时都是使用token值作为索**引。

**WindowToken用来表示一组窗口对象，windowType表示窗口类型**。

explicit为false表示这个Token是在WMS中创建的，true表示在其他模块通过调用addAppToken或者addWindowToken方法显示创建的。

windows类型是WindowList，包含一个WindowState对象的列表，所有拥有相同WindowToken的窗口都在这个列表中。一般而言窗口和依附于它的子窗口拥有相同的WindowToken对象。

**APPWindowToken从WindowToken类派生，是一种比较特殊的WindowToken，代表应用窗口，主要是Activity中创建的顶层窗口。一个WindowToken对象的成员变量APPWindowToken如果为NULL，那么它就不是APPWindowToken，否则就是APPWindowToken对象**。

APPWindowToken中增加了几个特有的成员变量，如下：


```
class AppWindowToken extends WindowToken {
    // Non-null only for application tokens.
    final IApplicationToken appToken;
 
    // All of the windows and child windows that are included in this
    // application token.  Note this list is NOT sorted!
    final WindowList allAppWindows = new WindowList();
    final AppWindowAnimator mAppAnimator;
 
    final WindowAnimator mAnimator;

```
其**中appToken用来表示应用的Token，它是在AMS中创建的，代表一个应用**。

allAppWindows也是一个WindowList，保存了所有相同APPWindowToken的应用窗口及其子窗口。

在WMS中定义了addAppToken用来向WMS添加一个APPWindowToken类型的Token。

#### 新建窗口的过程

每次新建Activity会增加窗口，弹出菜单同样会在WMS增加窗口。而最终都是通过Session的addToDisplay向WMS增加一个窗口。WMS最终会调用addWindow来实现这个功能。

我们来回顾下之前Activity是如何调用增加一个窗口的。

**新建Activity的时候先调用handleLaunchActivity，然后在这个函数中会调用handleResumeActivity函数然后在这个函数中会调用WindowManagerGlobal的addView函数，而在WindowManagerGlobal的addView函数中会新加ViewRootImpl对象，然后调用其setView函数，在setView中会调用mWindowSession.addToDisplay函数，最后到WMS的addWindow函数**。

**(1)参数检查**


```
    public int addWindow(Session session, IWindow client, int seq,
            WindowManager.LayoutParams attrs, int viewVisibility, int displayId,
            Rect outContentInsets, Rect outStableInsets, Rect outOutsets,
            InputChannel outInputChannel) {
	    ......
	    //检查mWindowMap是否有该窗口，如果有重复了，直接退出
            if (mWindowMap.containsKey(client.asBinder())) {
                Slog.w(TAG, "Window " + client + " is already added");
                return WindowManagerGlobal.ADD_DUPLICATE_ADD;
            }
	    //子窗口类型，父窗口类型必须存在且不能是子窗口类型，否则退出
            if (type >= FIRST_SUB_WINDOW && type <= LAST_SUB_WINDOW) {
                attachedWindow = windowForClientLocked(null, attrs.token, false);
                if (attachedWindow == null) {
                    Slog.w(TAG, "Attempted to add window with token that is not a window: "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_SUBWINDOW_TOKEN;
                }
                if (attachedWindow.mAttrs.type >= FIRST_SUB_WINDOW
                        && attachedWindow.mAttrs.type <= LAST_SUB_WINDOW) {
                    Slog.w(TAG, "Attempted to add window with token that is a sub-window: "
                            + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_SUBWINDOW_TOKEN;
                }
            }
			//私有窗口类型，显示设备不是私有类型，退出
            if (type == TYPE_PRIVATE_PRESENTATION && !displayContent.isPrivate()) {
                Slog.w(TAG, "Attempted to add private presentation window to a non-private display.  Aborting.");
                return WindowManagerGlobal.ADD_PERMISSION_DENIED;
            }
 
            boolean addToken = false;
            WindowToken token = mTokenMap.get(attrs.token);
			//如果mTokenMap中没有，但是窗口类型是应用窗口，输入法窗口，壁纸，Dream则退出
            if (token == null) {
                if (type >= FIRST_APPLICATION_WINDOW && type <= LAST_APPLICATION_WINDOW) {
                    Slog.w(TAG, "Attempted to add application window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (type == TYPE_INPUT_METHOD) {
                    Slog.w(TAG, "Attempted to add input method window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (type == TYPE_VOICE_INTERACTION) {
                    Slog.w(TAG, "Attempted to add voice interaction window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (type == TYPE_WALLPAPER) {
                    Slog.w(TAG, "Attempted to add wallpaper window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (type == TYPE_DREAM) {
                    Slog.w(TAG, "Attempted to add Dream window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (type == TYPE_ACCESSIBILITY_OVERLAY) {
                    Slog.w(TAG, "Attempted to add Accessibility overlay window with unknown token "
                            + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
	        //其他类型的窗口新建token
                token = new WindowToken(this, attrs.token, -1, false);
                addToken = true;
            } else if (type >= FIRST_APPLICATION_WINDOW && type <= LAST_APPLICATION_WINDOW) {
	       //应用类型窗口
                AppWindowToken atoken = token.appWindowToken;
                if (atoken == null) {//应用类型窗口必须有appWindowToken
                    Slog.w(TAG, "Attempted to add window with non-application token "
                          + token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_NOT_APP_TOKEN;
                } else if (atoken.removed) {
                    Slog.w(TAG, "Attempted to add window with exiting application token "
                          + token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_APP_EXITING;
                }
                if (type == TYPE_APPLICATION_STARTING && atoken.firstWindowDrawn) {
	           //还在启动中，不能添加窗口
                    // No need for this guy!
                    if (localLOGV) Slog.v(
                            TAG, "**** NO NEED TO START: " + attrs.getTitle());
                    return WindowManagerGlobal.ADD_STARTING_NOT_NEEDED;
                }
            } else if (type == TYPE_INPUT_METHOD) {//输入法
                if (token.windowType != TYPE_INPUT_METHOD) {
                    Slog.w(TAG, "Attempted to add input method window with bad token "
                            + attrs.token + ".  Aborting.");
                      return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (type == TYPE_VOICE_INTERACTION) {//
                if (token.windowType != TYPE_VOICE_INTERACTION) {
                    Slog.w(TAG, "Attempted to add voice interaction window with bad token "
                            + attrs.token + ".  Aborting.");
                      return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (type == TYPE_WALLPAPER) {//壁纸
                if (token.windowType != TYPE_WALLPAPER) {
                    Slog.w(TAG, "Attempted to add wallpaper window with bad token "
                            + attrs.token + ".  Aborting.");
                      return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (type == TYPE_DREAM) {//Dream
                if (token.windowType != TYPE_DREAM) {
                    Slog.w(TAG, "Attempted to add Dream window with bad token "
                            + attrs.token + ".  Aborting.");
                      return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (type == TYPE_ACCESSIBILITY_OVERLAY) {
                if (token.windowType != TYPE_ACCESSIBILITY_OVERLAY) {
                    Slog.w(TAG, "Attempted to add Accessibility overlay window with bad token "
                            + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            } else if (token.appWindowToken != null) {
                Slog.w(TAG, "Non-null appWindowToken for system window of type=" + type);
                // It is not valid to use an app token with other system types; we will
                // instead make a new token for it (as if null had been passed in for the token).
                attrs.token = null;
                token = new WindowToken(this, null, -1, false);
                addToken = true;
            }

```
这段代码主要是进行参数检查，大部分参数从客户进程传过来的。为了保证数据的一致性，重点检查窗口的WindowToken对象中的窗口类型和之前在WMS的WindowToken的windowType是否一致。这种检查主要针对应用窗口、输入法、壁纸、dream、TYPE_VOICE_INTERACTION、TYPE_ACCESSIBILITY_OVERLAY，因为这几种窗口的WindowToken对象在addWindow前已经创建好并加入WMS的mTokenMap中，这里需要检查是否一致。

一些特殊的窗口，入输入法窗口，必须由InputManagerService允许后才能创建。这些服务会预先调用WMS的addWindowToken方法插入WindowToken到WMS的mTokenMap中。如果应用不通过服务获取相应的WindowToken将无法创建这些特殊窗口，方便控制。

对于其他类型的窗口，addWindow方法会为它们创建WindowToken对象。

**(2)创建窗口对象**


```
            //创建WindowState对象
            WindowState win = new WindowState(this, session, client, token,
                    attachedWindow, appOp[0], seq, attrs, viewVisibility, displayContent);
            if (win.mDeathRecipient == null) {
                // Client has apparently died, so there is no reason to
                // continue.
                Slog.w(TAG, "Adding window client " + client.asBinder()
                        + " that is dead, aborting.");
                return WindowManagerGlobal.ADD_APP_EXITING;
            }
 
            if (win.getDisplayContent() == null) {
                Slog.w(TAG, "Adding window to Display that has been removed.");
                return WindowManagerGlobal.ADD_INVALID_DISPLAY;
            }
            mPolicy.adjustWindowParamsLw(win.mAttrs);
			//检查和设置查看是否能够让其他用户看见
            win.setShowToOwnerOnlyLocked(mPolicy.checkShowToOwnerOnly(attrs));
 
            res = mPolicy.prepareAddWindowLw(win, attrs);
            if (res != WindowManagerGlobal.ADD_OKAY) {
                return res;
            }
            //为按键创建和应用的通信通道
            if (outInputChannel != null && (attrs.inputFeatures
                    & WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL) == 0) {
                String name = win.makeInputChannelName();
                InputChannel[] inputChannels = InputChannel.openInputChannelPair(name);
                win.setInputChannel(inputChannels[0]);
                inputChannels[1].transferTo(outInputChannel);
 
                mInputManager.registerInputChannel(win.mInputChannel, win.mInputWindowHandle);
            }
 
            // From now on, no exceptions or errors allowed!
 
            res = WindowManagerGlobal.ADD_OKAY;
 
            origId = Binder.clearCallingIdentity();
 
            if (addToken) {//如果是新的Token，加入mTokenMap
                mTokenMap.put(attrs.token, token);
            }
            win.attach();
            mWindowMap.put(client.asBinder(), win);//加入WindowState对象列表中
            ......

```

这段代码主要是保存WindowState对象，放入mWindowMap中。

mPolicy的adjustWindowParamsLw方法检查窗口类型是否是TYPE_SYSTEM_OVERLAY或者TYPE_SERCURE_SYSTEM_OVERLAY，如果是将窗口标记上FLAG_NOT_FOCUSABLE FLAG_NOT_TOUCHABLE FLAG_WATCH_OUTSIDE_TOUCH.这些窗口不能获取焦点。


**(3)将窗口加入Display列表**


```
            boolean imMayMove = true;
 
            if (type == TYPE_INPUT_METHOD) {//如果窗口类是输入法窗口
                win.mGivenInsetsPending = true;
                mInputMethodWindow = win;
                addInputMethodWindowToListLocked(win);//插入输入法窗口到应用窗口上层
                imMayMove = false;
            } else if (type == TYPE_INPUT_METHOD_DIALOG) {//如果窗口是输入法对话框
                mInputMethodDialogs.add(win);
                addWindowToListInOrderLocked(win, true);//插入到正常位置
                moveInputMethodDialogsLocked(findDesiredInputMethodWindowIndexLocked(true));//调整对话框位置
                imMayMove = false;
            } else {
                addWindowToListInOrderLocked(win, true);
                if (type == TYPE_WALLPAPER) {
                    mLastWallpaperTimeoutTime = 0;
                    displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
                } else if ((attrs.flags&FLAG_SHOW_WALLPAPER) != 0) {
                    displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
                } else if (mWallpaperTarget != null
                        && mWallpaperTarget.mLayer >= win.mBaseLayer) {
                    // If there is currently a wallpaper being shown, and
                    // the base layer of the new window is below the current
                    // layer of the target window, then adjust the wallpaper.
                    // This is to avoid a new window being placed between the
                    // wallpaper and its target.
                    displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
                }
            }

```

DisplayContent类的mWindows列表按Z序保存了每个窗口，这段代码就是在根据窗口类型把窗口加入到DisplayContent合适位置。

addInputMethodWindowToListLocked方法作用就是一个输入法窗口放子啊需要显示输入法窗口的上面。

**addWindowToListInOrderLocked将一个窗口插入到窗口堆栈的当前位置**。

**(4)调整窗口的次序**


```
           winAnimator.mEnterAnimationPending = true;
            winAnimator.mEnteringAnimation = true;
 
            if (displayContent.isDefaultDisplay) {
                mPolicy.getInsetHintLw(win.mAttrs, mRotation, outContentInsets, outStableInsets,//计算窗口大小
                        outOutsets);
            } else {
                outContentInsets.setEmpty();
                outStableInsets.setEmpty();
            }
 
            if (mInTouchMode) {
                res |= WindowManagerGlobal.ADD_FLAG_IN_TOUCH_MODE;
            }
            if (win.mAppToken == null || !win.mAppToken.clientHidden) {
                res |= WindowManagerGlobal.ADD_FLAG_APP_VISIBLE;
            }
 
            mInputMonitor.setUpdateInputWindowsNeededLw();
 
            boolean focusChanged = false;
            if (win.canReceiveKeys()) {//如果窗口能接受输入，计算是否引起焦点变化
                focusChanged = updateFocusedWindowLocked(UPDATE_FOCUS_WILL_ASSIGN_LAYERS,
                        false /*updateInputWindows*/);
                if (focusChanged) {
                    imMayMove = false;
                }
            }
 
            if (imMayMove) {
                moveInputMethodWindowsIfNeededLocked(false);
            }
 
            assignLayersLocked(displayContent.getWindowList());
            // Don't do layout here, the window must call
            // relayout to be displayed, so we'll do it there.
 
            if (focusChanged) {
                mInputMonitor.setInputFocusLw(mCurrentFocus, false /*updateInputWindows*/);
            }
            mInputMonitor.updateInputWindowsLw(false /*force*/);
 
            if (localLOGV || DEBUG_ADD_REMOVE) Slog.v(TAG, "addWindow: New client "
                    + client.asBinder() + ": window=" + win + " Callers=" + Debug.getCallers(5));
 
            if (win.isVisibleOrAdding() && updateOrientationFromAppTokensLocked(false)) {
                reportNewConfig = true;//如果窗口的配置发生变化
            }
        }
 
        if (reportNewConfig) {
            sendNewConfiguration();//发生新的配置
        }
 
        Binder.restoreCallingIdentity(origId);
 
        return res;

```

后续还有确定窗口Z轴位置，窗口尺寸等~