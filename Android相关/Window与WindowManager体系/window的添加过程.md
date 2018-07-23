# Window的添加过程


**1. 概述&Window的属性**

**2. WindowManager添加Window的过程**

**3. WindowManagerService添加Window的过程**

参考：<br>
[http://liuwangshu.cn/framework/wm/2-window-property.html](http://liuwangshu.cn/framework/wm/2-window-property.html)

[http://liuwangshu.cn/framework/wm/3-add-window.html](http://liuwangshu.cn/framework/wm/3-add-window.html)

[https://blog.csdn.net/itachi85/article/details/78357437](https://blog.csdn.net/itachi85/article/details/78357437)

## 1. 概述&Window的属性

### 概述

WMS是Window的最终管理者，Window好比是员工，WMS是老板，为了方便老板管理员工则需要定义一些“协议”，这些“协议”就是Window的属性，被定义在WindowManager的内部类LayoutParams中，了解Window的属性能够更好的理解WMS的内部原理。

Window的属性有很多种，**与应用开发最密切的有三种**，它们分别是：

**Type(Window的类型)**

**Flag(Window的标志)**

**SoftInputMode（软键盘相关模式）**

下面分别介绍这三种Window的属性。

#### Window的属性

##### （1）Type(Window的类型)

Window的类型和显示次序

Window的类型有很多种，比如应用程序窗口、系统错误窗口、输入法窗口、PopupWindow、Toast、Dialog等等。总来来说分为三大类分别是：Application Window（应用程序窗口）、Sub Windwow（子窗口）、System Window（系统窗口），每个大类又包含了很多种类型，它们都定义在WindowManager的静态内部类LayoutParams中，接下来我们分别对这三大类进行讲解。

**应用程序窗口**

Activity就是一个典型的应用程序窗口，应用程序窗口包含的类型如下所示。


```
public static final int FIRST_APPLICATION_WINDOW = 1;//1
public static final int TYPE_BASE_APPLICATION   = 1;//窗口的基础值，其他的窗口值要大于这个值
public static final int TYPE_APPLICATION        = 2;//普通的应用程序窗口类型
public static final int TYPE_APPLICATION_STARTING = 3;//应用程序启动窗口类型，用于系统在应用程序窗口启动前显示的窗口。
public static final int TYPE_DRAWN_APPLICATION = 4;
public static final int LAST_APPLICATION_WINDOW = 99;//2
```

应用程序窗口共包含了以上几种Type值，其中注释1处的Type表示应用程序窗口类型初始值，注释2处的Type表示应用程序窗口类型结束值，也就是说**应用程序窗口的Type值范围为1到99**。

**子窗口**

子窗口，顾名思义，它不能独立的存在，需要附着在其他窗口才可以，PopupWindow就属于子窗口。子窗口的类型定义如下所示：


```
public static final int FIRST_SUB_WINDOW = 1000;//子窗口类型初始值
public static final int TYPE_APPLICATION_PANEL = FIRST_SUB_WINDOW;
public static final int TYPE_APPLICATION_MEDIA = FIRST_SUB_WINDOW + 1;
public static final int TYPE_APPLICATION_SUB_PANEL = FIRST_SUB_WINDOW + 2;
public static final int TYPE_APPLICATION_ATTACHED_DIALOG = FIRST_SUB_WINDOW + 3;
public static final int TYPE_APPLICATION_MEDIA_OVERLAY  = FIRST_SUB_WINDOW + 4; 
public static final int TYPE_APPLICATION_ABOVE_SUB_PANEL = FIRST_SUB_WINDOW + 5;
public static final int LAST_SUB_WINDOW = 1999;//子窗口类型结束值
```
**子窗口的Type值范围为1000到1999**。


**系统窗口**

Toast、输入法窗口、系统音量条窗口、系统错误窗口都属于系统窗口。系统窗口的类型定义如下所示：

```
public static final int FIRST_SYSTEM_WINDOW     = 2000;//系统窗口类型初始值
public static final int TYPE_STATUS_BAR         = FIRST_SYSTEM_WINDOW;//系统状态栏窗口
public static final int TYPE_SEARCH_BAR         = FIRST_SYSTEM_WINDOW+1;//搜索条窗口
public static final int TYPE_PHONE              = FIRST_SYSTEM_WINDOW+2;//通话窗口
public static final int TYPE_SYSTEM_ALERT       = FIRST_SYSTEM_WINDOW+3;//系统ALERT窗口
public static final int TYPE_KEYGUARD           = FIRST_SYSTEM_WINDOW+4;//锁屏窗口
public static final int TYPE_TOAST              = FIRST_SYSTEM_WINDOW+5;//TOAST窗口
...

public static final int LAST_SYSTEM_WINDOW      = 2999;/
```

系统窗口的类型值有接近40个，这里只列出了一小部分， 系统窗口的Type值范围为2000到2999。

**窗口显示次序**

当一个进程向WMS申请一个窗口时，WMS会为窗口确定显示次序。为了方便窗口显示次序的管理，手机屏幕可以虚拟的用X、Y、Z轴来表示，其中Z轴垂直于屏幕，从屏幕内指向屏幕外，这样确定窗口显示次序也就是确定窗口在Z轴上的次序，这个次序称为Z-Oder。Type值是Z-Oder排序的依据，我们知道应用程序窗口的Type值范围为1到99，子窗口1000到1999 ，系统窗口 2000到2999，，一般情况下，Type值越大则Z-Oder排序越靠前，就越靠近用户。当然窗口显示次序的逻辑不会这么简单，情况会比较多，举个常见的情况：当多个窗口的Type值都是TYPE_APPLICATION，这时WMS会结合各种情况给出最终的Z-Oder。


##### （2）Flag(Window的标志)

**Window的标志**

Window的标志也就是Flag，用于控制Window的显示，同样被定义在WindowManager的内部类LayoutParams中，一共有20多个，这里我们给出几个比较常用：

Flag	|描述
---|---
FLAG_ALLOW_LOCK_WHILE_SCREEN_ON	|只要窗口可见，就允许在开启状态的屏幕上锁屏
FLAG_NOT_FOCUSABLE	|窗口不能获得输入焦点，设置该标志的同时，FLAG_NOT_TOUCH_MODAL也会被设置
FLAG_NOT_TOUCHABLE	|窗口不接收任何触摸事件
FLAG_NOT_TOUCH_MODAL	|在该窗口区域外的触摸事件传递给其他的Window,而自己只会处理窗口区域内的触摸事件
FLAG_KEEP_SCREEN_ON	|只要窗口可见，屏幕就会一直亮着
FLAG_LAYOUT_NO_LIMITS	|允许窗口超过屏幕之外
FLAG_FULLSCREEN	|隐藏所有的屏幕装饰窗口，比如在游戏、播放器中的全屏显示
FLAG_SHOW_WHEN_LOCKED	|窗口可以在锁屏的窗口之上显示
FLAG_IGNORE_CHEEK_PRESSES	|当用户的脸贴近屏幕时（比如打电话），不会去响应此事件
FLAG_TURN_SCREEN_ON	|窗口显示时将屏幕点亮

##### （3）SoftInputMode（软键盘相关模式）

**软键盘相关模式**

窗口和窗口的叠加是非常常见的场景，但如果其中的窗口是软键盘窗口，可能就会出现一些问题，比如典型的用户登录界面，默认的情况弹出的软键盘窗口可能会盖住输入框下方的按钮，这样用户体验会非常糟糕。
为了使得软键盘窗口能够按照期望来显示，WindowManager的静态内部类LayoutParams中定义了软键盘相关模式，这里给出常用的几个：

SoftInputMode	|描述
---|---
SOFT_INPUT_STATE_UNSPECIFIED	|没有指定状态,系统会选择一个合适的状态或依赖于主题的设置
SOFT_INPUT_STATE_UNCHANGED	|不会改变软键盘状态
SOFT_INPUT_STATE_HIDDEN	|当用户进入该窗口时，软键盘默认隐藏
SOFT_INPUT_STATE_ALWAYS_HIDDEN	|当窗口获取焦点时，软键盘总是被隐藏
SOFT_INPUT_ADJUST_RESIZE	|当软键盘弹出时，窗口会调整大小
SOFT_INPUT_ADJUST_PAN	|当软键盘弹出时，窗口不需要调整大小，要确保输入焦点是可见的


从上面给出的SoftInputMode ，可以发现，它们与AndroidManifest中Activity的属性android:windowSoftInputMode是对应的。因此，除了在AndroidMainfest中为Activity设置android:windowSoftInputMode以外还可以在Java代码中为Window设置SoftInputMode：
```
getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

```

## 2. WindowManager添加Window的过程

WindowManager对Window进行管理，说到管理那就离不开对Window的添加、更新和删除的操作，在这里我们把它们统称为Window的操作。对于Window的操作，最终都是交由WMS来进行处理。**窗口的操作分为两大部分，一部分是WindowManager处理部分，另一部分是WMS处理部分**。我们知道Window分为三大类，分别是：

- Application Window（应用程序窗口）
- Sub Windwow（子窗口）
- System Window（系统窗口）

**对于不同类型的窗口添加过程会有所不同，但是对于WMS处理部分，添加的过程基本上是一样的， WMS对于这三大类的窗口基本是“一视同仁”的。**


![image](http://o9m6aqy3r.bkt.clouddn.com//Window/WindowManager1.png)

#### (1)系统窗口的添加过程

三大类窗口的添加过程会有所不同，这里以系统窗口StatusBar为例，StatusBar是SystemUI的重要组成部分，具体就是指系统状态栏，用于显示时间、电量和信号等信息。我们来查看StatusBar的实现类PhoneStatusBar的addStatusBarWindow方法，这个方法负责为StatusBar添加Window，如下所示。


```
 private void addStatusBarWindow() {
    makeStatusBarView();//1
    mStatusBarWindowManager = new StatusBarWindowManager(mContext);
    mRemoteInputController = new RemoteInputController(mStatusBarWindowManager,
            mHeadsUpManager);
    mStatusBarWindowManager.add(mStatusBarWindow, getStatusBarHeight());//2
}
```

- 注释1处用于构建StatusBar的视图。
- 在注释2处调用了StatusBarWindowManager的add方法，并将StatusBar的视图（StatusBarWindowView）和StatusBar的传进去。


```
public void add(View statusBarView, int barHeight) {
    mLp = new WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            barHeight,
            WindowManager.LayoutParams.TYPE_STATUS_BAR,//1
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT);
    mLp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
    mLp.gravity = Gravity.TOP;
    mLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
    mLp.setTitle("StatusBar");
    mLp.packageName = mContext.getPackageName();
    mStatusBarView = statusBarView;
    mBarHeight = barHeight;
    mWindowManager.addView(mStatusBarView, mLp);//2
    mLpChanged = new WindowManager.LayoutParams();
    mLpChanged.copyFrom(mLp);
}
```
首先通过创建LayoutParams来配置StatusBar视图的属性，包括Width、Height、Type、 Flag、Gravity、SoftInputMode等

关键在**注释1处**，设置了TYPE_STATUS_BAR，表示StatusBar视图的窗口类型是状态栏。在**注释2处**调用了WindowManager的addView方法，addView方法定义在WindowManager的父类接口ViewManager中，而实现addView方法的则是WindowManagerImpl中，如下所示。


```
@Override
public void addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
    applyDefaultToken(params);
    mGlobal.addView(view, params, mContext.getDisplay(), mParentWindow);
}
```
在WindowManagerImpl的addView方法中，接着会调用WindowManagerGlobal的addView方法：


```
public void addView(View view, ViewGroup.LayoutParams params,
          Display display, Window parentWindow) {
    ...//参数检查
      final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams) params;
      if (parentWindow != null) {
          parentWindow.adjustLayoutParamsForSubWindow(wparams);//1
      } else {
      ...
      }

      ViewRootImpl root;
      View panelParentView = null;
       ...
          root = new ViewRootImpl(view.getContext(), display);//2
          view.setLayoutParams(wparams);
          mViews.add(view);
          mRoots.add(root);//3
          mParams.add(wparams);
      }
      try {
          root.setView(view, wparams, panelParentView);//4
      } catch (RuntimeException e) {
         ...
      }
  }
```

- 注释1处，如果当前窗口要作为子窗口，就会根据父窗口对子窗口的WindowManager.LayoutParams类型的wparams对象进行相应调整
- 注释2处创建了ViewRootImp并赋值给root
- 注释3处将root存入到ArrayList<ViewRootImpl>类型的mRoots中，除了mRoots，mViews和mParams也是ArrayList类型的，分别用于存储窗口的view对象和WindowManager.LayoutParams类型的wparams对象
- 注释4处调用了ViewRootImpl的setView方法。
> ViewRootImpl身负了很多职责：
>- View树的根并管理View树
>- 触发View的测量、布局和绘制
>- 输入事件的中转站
>- 管理Surface
>- 负责与WMS进行进程间通信



```
public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
       synchronized (this) {
          ...
               try {
                   mOrigWindowType = mWindowAttributes.type;
                   mAttachInfo.mRecomputeGlobalAttributes = true;
                   collectViewAttributes();
                   res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
                           getHostVisibility(), mDisplay.getDisplayId(),
                           mAttachInfo.mContentInsets, mAttachInfo.mStableInsets,
                           mAttachInfo.mOutsets, mInputChannel);
               } 
               ...
   }
```
setView方法中,主要就是调用了mWindowSession的addToDisplay方法。**mWindowSession是IWindowSession类型的，它是一个Binder对象，用于进行进程间通信**，IWindowSession是Client端的代理，**它的Server端的实现为Session，此前包含ViewRootImpl在内的代码逻辑都是运行在本地进程的，而Session的addToDisplay方法则运行在WMS所在的进程**。


```
@Override
 public int addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs,
         int viewVisibility, int displayId, Rect outContentInsets, Rect outStableInsets,
         Rect outOutsets, InputChannel outInputChannel) {
     return mService.addWindow(this, window, seq, attrs, viewVisibility, displayId,
             outContentInsets, outStableInsets, outOutsets, outInputChannel);
 }
```
addToDisplay方法中会调用了WMS的addWindow方法，并将自身也就是Session，作为参数传了进去，**每个应用程序进程都会对应一个Session，WMS会用ArrayList来保存这些Session**。这样剩下的工作就交给WMS来处理，在**WMS中会为这个添加的窗口分配Surface，并确定窗口显示次序，可见负责显示界面的是画布Surface，而不是窗口本身。WMS会将它所管理的Surface交由SurfaceFlinger处理，SurfaceFlinger会将这些Surface混合并绘制到屏幕上**。
窗口添加的WMS处理部分会在后续介绍WMS的系列文章进行讲解，系统窗口的添加过程的时序图如下所示。

- 每个应用程序进程都会对应一个Session
- WMS中会为这个添加的窗口分配Surface，并确定窗口显示次序
- WMS会将它所管理的Surface交由SurfaceFlinger处理，SurfaceFlinger会将这些Surface混合并绘制到屏幕上

![image](http://o9m6aqy3r.bkt.clouddn.com//Window/WindowManager%E6%B7%BB%E5%8A%A0%E7%AA%97%E5%8F%A3%E8%BF%87%E7%A8%8B.png)


#### (2)Activity的添加过程

无论是哪种窗口，它的的添加过程在WMS处理部分中基本是类似的，只不过会在权限和窗口显示次序等方面会有些不同。但是在WindowManager处理部分会有所不同。

这里以最典型的应用程序窗口Activity为例，Activity在启动过程中，如果Activity所在的进程不存在则会创建新的进程，创建新的进程之后就会运行代表主线程的实例ActivityThread，ActivityThread管理着当前应用程序进程的线程。当界面要与用户进行交互时，会调用ActivityThread的handleResumeActivity方法，如下所示。


```
 final void handleResumeActivity(IBinder token,
            boolean clearHide, boolean isForward, boolean reallyResume, int seq, String reason) {
       ...   
         r = performResumeActivity(token, clearHide, reason);//1           
  ...
 if (r.window == null && !a.mFinished && willBeVisible) {
                r.window = r.activity.getWindow();
                View decor = r.window.getDecorView();
                decor.setVisibility(View.INVISIBLE);
                ViewManager wm = a.getWindowManager();//2
                WindowManager.LayoutParams l = r.window.getAttributes();
                a.mDecor = decor;
                l.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
                l.softInputMode |= forwardBit;
                if (r.mPreserveWindow) {
                    a.mWindowAdded = true;
                    r.mPreserveWindow = false;
                    ViewRootImpl impl = decor.getViewRootImpl();
                    if (impl != null) {
                        impl.notifyChildRebuilt();
                    }
                }
                if (a.mVisibleFromClient && !a.mWindowAdded) {
                    a.mWindowAdded = true;
                    wm.addView(decor, l);//3
                }
...                
}
```

- 注释1处的performResumeActivity方法最终会调用Activity的onResume方法
- 在注释2处得到ViewManager类型的wm对象
- 注释3处调用了wm的addView方法，而addView方法的实现则是在WindowManagerImpl中，此后的过程在上面的系统窗口的添加过程已经讲过，唯一需要注意的是addView的第一个参数是DecorView。

#### (3)扩展
ViewManager不只定义了addView方法用来添加窗口，还定义了updateViewLayout和removeView方法用来更新和删除窗口，如下所示。


```
package android.view;
public interface ViewManager
{
    public void addView(View view, ViewGroup.LayoutParams params);
    public void updateViewLayout(View view, ViewGroup.LayoutParams params);
    public void removeView(View view);
}
```
其定义的updateViewLayout和removeView方法的处理流程和addView方法是类似的，都是要经过WindowManagerGlobal处理，最后通过Session与WMS进行跨进程通信，将更新和删除窗口的工作交由WMS来处理。


## 3. WindowManagerService添加Window的过程

#### (1)WMS的重要成员

WMS的重要成员是指WMS中的重要的成员变量，如下所示:


```
    final WindowManagerPolicy mPolicy;
    final IActivityManager mActivityManager;
    final ActivityManagerInternal mAmInternal;
    final AppOpsManager mAppOps;
    final DisplaySettings mDisplaySettings;
    ...
    final ArraySet<Session> mSessions = new ArraySet<>();
    final WindowHashMap mWindowMap = new WindowHashMap();
    final ArrayList<AppWindowToken> mFinishedStarting = new ArrayList<>();
    final ArrayList<AppWindowToken> mFinishedEarlyAnim = new ArrayList<>();
    final ArrayList<AppWindowToken> mWindowReplacementTimeouts = new ArrayList<>();
    final ArrayList<WindowState> mResizingWindows = new ArrayList<>();
    final ArrayList<WindowState> mPendingRemove = new ArrayList<>();
    WindowState[] mPendingRemoveTmp = new WindowState[20];
    final ArrayList<WindowState> mDestroySurface = new ArrayList<>();
    final ArrayList<WindowState> mDestroyPreservedSurface = new ArrayList<>();
    ...
    final H mH = new H();
    ...
    final WindowAnimator mAnimator;
    ...
     final InputManagerService mInputManager
```
其中：

**mPolicy：WindowManagerPolicy** 

**WindowManagerPolicy（WMP）类型的变量**。WindowManagerPolicy是窗口管理策略的接口类，用来定义一个窗口策略所要遵循的通用规范，并提供了WindowManager所有的特定的UI行为。它的具体实现类为PhoneWindowManager，这个实现类在WMS创建时被创建。WMP允许定制窗口层级和特殊窗口类型以及关键的调度和布局。

**mSessions：ArraySet**

ArraySet类型的变量，**元素类型为Session**。它主要用于进程间通信，其他的应用程序进程想要和WMS进程进行通信就需要经过Session，并且每个应用程序进程都会对应一个Session，WMS保存这些Session用来记录所有向WMS提出窗口管理服务的客户端。

**mWindowMap：WindowHashMap**

**WindowHashMap类型的变量**，WindowHashMap继承了HashMap，它限制了HashMap的key值的类型为IBinder，value值的类型为WindowState。WindowState用于保存窗口的信息，在WMS中它用来描述一个窗口。综上得出结论，mWindowMap就是用来保存WMS中各种窗口的集合。

**mFinishedStarting：ArrayList**

ArrayList类型的变量，**元素类型为AppWindowToken**，它是WindowToken的子类。要想理解mFinishedStarting的含义，需要先了解WindowToken是什么。WindowToken主要有两个作用：

- 可以理解为窗口令牌，当应用程序想要向WMS申请新创建一个窗口，则需要向WMS出示有效的WindowToken。AppWindowToken作为WindowToken的子类，主要用来描述应用程序的WindowToken结构， 
应用程序中每个Activity都对应一个AppWindowToken。
- WindowToken会将相同组件（比如Acitivity）的窗口（WindowState）集合在一起，方便管理。
**mFinishedStarting就是用于存储已经完成启动的应用程序窗口（比如Acitivity）的AppWindowToken的列表**。 

**mResizingWindows：ArrayList**

**ArrayList类型的变量，元素类型为WindowState**。 
mResizingWindows是用来存储正在调整大小的窗口的列表。与mResizingWindows类似的还有mPendingRemove、mDestroySurface和mDestroyPreservedSurface等等。其中mPendingRemove是在内存耗尽时设置的，里面存有需要强制删除的窗口。mDestroySurface里面存有需要被Destroy的Surface。mDestroyPreservedSurface里面存有窗口需要保存的等待销毁的Surface，为什么窗口要保存这些Surface？这是因为当窗口经历Surface变化时，窗口需要一直保持旧Surface，直到新Surface的第一帧绘制完成。

**mAnimator：WindowAnimator**

WindowAnimator类型的变量，用于管理窗口的动画以及特效动画。

**mH：H**
H类型的变量，系统的Handler类，用于将任务加入到主线程的消息队列中，这样代码逻辑就会在主线程中执行。

**mInputManager：InputManagerService**

InputManagerService类型的变量，输入系统的管理者。InputManagerService（IMS）会对触摸事件进行处理，它会寻找一个最合适的窗口来处理触摸反馈信息，WMS是窗口的管理者，因此，WMS“理所应当”的成为了输入系统的中转站，WMS包含了IMS的引用不足为怪。

#### (2)Window的添加过程（WMS部分）

无论是系统窗口还是Activity，它们的Window的添加过程都会调用WMS的addWindow方法：

**part 1**


```
 public int addWindow(Session session, IWindow client, int seq,
            WindowManager.LayoutParams attrs, int viewVisibility, int displayId,
            Rect outContentInsets, Rect outStableInsets, Rect outOutsets,
            InputChannel outInputChannel) {

        int[] appOp = new int[1];
        int res = mPolicy.checkAddPermission(attrs, appOp);//1
        if (res != WindowManagerGlobal.ADD_OKAY) {
            return res;
        }
        ...
        synchronized(mWindowMap) {
            if (!mDisplayReady) {
                throw new IllegalStateException("Display has not been initialialized");
            }
            final DisplayContent displayContent = mRoot.getDisplayContentOrCreate(displayId);//2
            if (displayContent == null) {
                Slog.w(TAG_WM, "Attempted to add window to a display that does not exist: "
                        + displayId + ".  Aborting.");
                return WindowManagerGlobal.ADD_INVALID_DISPLAY;
            }
            ...
            if (type >= FIRST_SUB_WINDOW && type <= LAST_SUB_WINDOW) {//3
                parentWindow = windowForClientLocked(null, attrs.token, false);//4
                if (parentWindow == null) {
                    Slog.w(TAG_WM, "Attempted to add window with token that is not a window: "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_SUBWINDOW_TOKEN;
                }
                if (parentWindow.mAttrs.type >= FIRST_SUB_WINDOW
                        && parentWindow.mAttrs.type <= LAST_SUB_WINDOW) {
                    Slog.w(TAG_WM, "Attempted to add window with token that is a sub-window: "
                            + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_SUBWINDOW_TOKEN;
                }
            }
           ...
}
...
}
```

- WMS的addWindow返回的是addWindow的各种状态，比如添加Window成功，无效的display等等，这些状态被定义在WindowManagerGlobal中。
- 注释1处根据Window的属性，调用WMP的checkAddPermission方法来检查权限，具体的实现在PhoneWindowManager的checkAddPermission方法中，如果没有权限则不会执行后续的代码逻辑。
- 注释2处通过displayId来获得窗口要添加到哪个DisplayContent上，如果没有找到DisplayContent，则返回WindowManagerGlobal.ADD_INVALID_DISPLAY这一状态，其中DisplayContent用来描述一块屏幕。
- 注释3处，type代表一个窗口的类型，它的数值介于FIRST_SUB_WINDOW和LAST_SUB_WINDOW之间（1000~1999），这个数值定义在WindowManager中，说明这个窗口是一个子窗口.
- 注释4处，attrs.token是IBinder类型的对象，windowForClientLocked方法内部会根据attrs.token作为key值从mWindowMap中得到该子窗口的父窗口。接着对父窗口进行判断，如果父窗口为null或者type的取值范围不正确则会返回错误的状态。


**part 2**


```
   ...
            AppWindowToken atoken = null;
            final boolean hasParent = parentWindow != null;
            WindowToken token = displayContent.getWindowToken(
                    hasParent ? parentWindow.mAttrs.token : attrs.token);//1
            final int rootType = hasParent ? parentWindow.mAttrs.type : type;//2
            boolean addToastWindowRequiresToken = false;

            if (token == null) {
                if (rootType >= FIRST_APPLICATION_WINDOW && rootType <= LAST_APPLICATION_WINDOW) {
                    Slog.w(TAG_WM, "Attempted to add application window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (rootType == TYPE_INPUT_METHOD) {
                    Slog.w(TAG_WM, "Attempted to add input method window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (rootType == TYPE_VOICE_INTERACTION) {
                    Slog.w(TAG_WM, "Attempted to add voice interaction window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                if (rootType == TYPE_WALLPAPER) {
                    Slog.w(TAG_WM, "Attempted to add wallpaper window with unknown token "
                          + attrs.token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
                ...
                if (type == TYPE_TOAST) {
                    // Apps targeting SDK above N MR1 cannot arbitrary add toast windows.
                    if (doesAddToastWindowRequireToken(attrs.packageName, callingUid,
                            parentWindow)) {
                        Slog.w(TAG_WM, "Attempted to add a toast window with unknown token "
                                + attrs.token + ".  Aborting.");
                        return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                    }
                }
                final IBinder binder = attrs.token != null ? attrs.token : client.asBinder();
                token = new WindowToken(this, binder, type, false, displayContent,
                        session.mCanAddInternalSystemWindow);//3
            } else if (rootType >= FIRST_APPLICATION_WINDOW && rootType <= LAST_APPLICATION_WINDOW) {//4
                atoken = token.asAppWindowToken();//5
                if (atoken == null) {
                    Slog.w(TAG_WM, "Attempted to add window with non-application token "
                          + token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_NOT_APP_TOKEN;
                } else if (atoken.removed) {
                    Slog.w(TAG_WM, "Attempted to add window with exiting application token "
                          + token + ".  Aborting.");
                    return WindowManagerGlobal.ADD_APP_EXITING;
                }
            } else if (rootType == TYPE_INPUT_METHOD) {
                if (token.windowType != TYPE_INPUT_METHOD) {
                    Slog.w(TAG_WM, "Attempted to add input method window with bad token "
                            + attrs.token + ".  Aborting.");
                      return WindowManagerGlobal.ADD_BAD_APP_TOKEN;
                }
            }
      ...      

```
- 注释1处通过displayContent的getWindowToken方法来得到WindowToken
- 注释2处，如果有父窗口就将父窗口的type值赋值给rootType，如果没有将当前窗口的type值赋值给rootType。接下来如果WindowToken为null，则根据rootType或者type的值进行区分判断，如果rootType值等于TYPE_INPUT_METHOD、TYPE_WALLPAPER等值时，则返回状态值WindowManagerGlobal.ADD_BAD_APP_TOKEN，说明rootType值等于TYPE_INPUT_METHOD、TYPE_WALLPAPER等值时是不允许WindowToken为null的。
- 注释3处隐式创建WindowToken，这说明当我们添加窗口时是可以不向WMS提供WindowToken的，前提是rootType和type的值不为前面条件判断筛选的值。WindowToken隐式和显式的创建肯定是要加以区分的，注释3处的第4个参数为false就代表这个WindowToken是隐式创建的,接下来的代码逻辑就是WindowToken不为null的情况，根据rootType和type的值进行判断.
- 注释4处判断如果窗口为应用程序窗口，在注释5处会将WindowToken转换为专门针对应用程序窗口的AppWindowToken，然后根据AppWindowToken的值进行后续的判断。


**part 3**


```
   ...
  final WindowState win = new WindowState(this, session, client, token, parentWindow,
                    appOp[0], seq, attrs, viewVisibility, session.mUid,
                    session.mCanAddInternalSystemWindow);//1
            if (win.mDeathRecipient == null) {//2
                // Client has apparently died, so there is no reason to
                // continue.
                Slog.w(TAG_WM, "Adding window client " + client.asBinder()
                        + " that is dead, aborting.");
                return WindowManagerGlobal.ADD_APP_EXITING;
            }

            if (win.getDisplayContent() == null) {//3
                Slog.w(TAG_WM, "Adding window to Display that has been removed.");
                return WindowManagerGlobal.ADD_INVALID_DISPLAY;
            }

            mPolicy.adjustWindowParamsLw(win.mAttrs);//4
            win.setShowToOwnerOnlyLocked(mPolicy.checkShowToOwnerOnly(attrs));
            res = mPolicy.prepareAddWindowLw(win, attrs);//5
            ...
            win.attach();
            mWindowMap.put(client.asBinder(), win);//6
            if (win.mAppOp != AppOpsManager.OP_NONE) {
                int startOpResult = mAppOps.startOpNoThrow(win.mAppOp, win.getOwningUid(),
                        win.getOwningPackage());
                if ((startOpResult != AppOpsManager.MODE_ALLOWED) &&
                        (startOpResult != AppOpsManager.MODE_DEFAULT)) {
                    win.setAppOpVisibilityLw(false);
                }
            }

            final AppWindowToken aToken = token.asAppWindowToken();
            if (type == TYPE_APPLICATION_STARTING && aToken != null) {
                aToken.startingWindow = win;
                if (DEBUG_STARTING_WINDOW) Slog.v (TAG_WM, "addWindow: " + aToken
                        + " startingWindow=" + win);
            }

            boolean imMayMove = true;
            win.mToken.addWindow(win);//7
             if (type == TYPE_INPUT_METHOD) {
                win.mGivenInsetsPending = true;
                setInputMethodWindowLocked(win);
                imMayMove = false;
            } else if (type == TYPE_INPUT_METHOD_DIALOG) {
                displayContent.computeImeTarget(true /* updateImeTarget */);
                imMayMove = false;
            } else {
                if (type == TYPE_WALLPAPER) {
                    displayContent.mWallpaperController.clearLastWallpaperTimeoutTime();
                    displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
                } else if ((attrs.flags&FLAG_SHOW_WALLPAPER) != 0) {
                    displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
                } else if (displayContent.mWallpaperController.isBelowWallpaperTarget(win)) {
                    displayContent.pendingLayoutChanges |= FINISH_LAYOUT_REDO_WALLPAPER;
                }
            }
         ...
```

- 注释1处创建了WindowState，它存有窗口的所有的状态信息，在WMS中它代表一个窗口。从WindowState传入的参数，可以发现WindowState中包含了WMS、Session、WindowToken、父类的WindowState、LayoutParams等信息
- 注释2和3处分别判断请求添加窗口的客户端是否已经死亡、窗口的DisplayContent是否为null，如果是则不会再执行下面的代码逻辑
- 注释4处调用了WMP的adjustWindowParamsLw方法，该方法的实现在PhoneWindowManager中，会根据窗口的type对窗口的LayoutParams的一些成员变量进行修改
- 注释5处调用WMP的prepareAddWindowLw方法，用于准备将窗口添加到系统中。 
- 注释6处将WindowState添加到mWindowMap中
- 注释7处将WindowState添加到该WindowState对应的WindowToken中(实际是保存在WindowToken的父类WindowContainer中)，这样WindowToken就包含了相同组件的WindowState.

#### (3)总结

WMS中`addWindow`方法分了3个部分来进行讲解，主要就是做了下面4件事： 

1. 对所要添加的窗口进行检查，如果窗口不满足一些条件，就不会再执行下面的代码逻辑。 
2. WindowToken相关的处理，比如有的窗口类型需要提供WindowToken，没有提供的话就不会执行下面的代码逻辑，有的窗口类型则需要由WMS隐式创建WindowToken。 
3. WindowState的创建和相关处理，将WindowToken和WindowState相关联。 
4. 创建和配置DisplayContent，完成窗口添加到系统前的准备工作。

