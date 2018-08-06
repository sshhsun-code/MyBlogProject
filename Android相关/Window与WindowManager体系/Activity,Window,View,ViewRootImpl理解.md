# Activity,Window,View,ViewRootImpl理解

参考 [https://www.jianshu.com/p/c223b993b1ec](https://www.jianshu.com/p/c223b993b1ec)

**1. 提出问题**

**2. 问题探究**

**3. 问题总结**

## 1. 提出问题

(1) 在Activity里调用<br>

```
WindowManager.LayoutParams wl = new WindowManager.LayoutParams(); 
getWindowManager().addView(mView,wl)
```

和<br>

```
LayoutParams wmParams =...
addContentView(mView,wmParams);//activity里的方法
```
 
**这两种方式背后的实现是怎样的,有什么区别?**

(2)**Dialog和PopupWindow的区别在哪里?为什么Dialog传入application的Context会报错?**

(3)**ViewRootImpl是什么,一个Activity有多少个ViewRootImpl对象?**

(4)**该怎样理解Window?**

## 2. 问题探究

**Window的创建过程**

在ActivityThread.performLaunchActivity中,创建Activity的实例,接着会调用Activity.attach()来初始化一些内容,而Window对象就是在attach里进行创建初始化赋值的。


```
1. Activity.attach


final void attach（...） { 
    ...    
    mWindow = new PhoneWindow(this);    
    mWindow.setWindowManager((WindowManager)context.getSystemService(Context.WINDOW_SERVICE), 
        mToken, mComponent.flattenToString(),
        (info.flags & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0);   
    if (mParent != null) {            
        mWindow.setContainer(mParent.getWindow());        
    }
    mWindowManager = mWindow.getWindowManager();    
    ...
}




2.Window.setWindowManager



public void setWindowManager(WindowManager wm, IBinder appToken, String appName,boolean hardwareAccelerated) { 
    ...    
    mWindowManager = ((WindowManagerImpl)wm).createLocalWindowManager(this);
    ...
}
```
可看出Activity里新建一个PhoneWindow对象。在Android中,Window是个抽象的概念,Android中Window的具体实现类是PhoneWindow,Activity和Dialog中的Window对象都是PhoneWindow。

同时得到一个WindowManager对象,WindowManager是一个抽象类,这个WindowManager的具体实现实在WindowManagerImpl中,对比Context和ContextImpl。

**每个Activity会有一个WindowManager对象,这个mWindowManager就是和WindowManagerService(WMS)进行通信,也是WMS识别View具体属于那个Activity的关键,创建时传入IBinder 类型的mToken**.


```
mWindow.setWindowManager(...,mToken, ...,...)
```
这个Activity的mToken,这个mToken是一个IBinder,WMS就是通过这个IBinder来管理Activity里的View。

接着在onCreate的setContentView中,


```
Activity.setContentView() 
public void setContentView(@LayoutRes int layoutResID) {
    getWindow().setContentView(layoutResID);        
    initWindowDecorActionBar();    
}

PhoneWindow.setContentView() 
public void setContentView(int layoutResID) {
    ...    
    installDecor(); 
    ... 
} 

PhoneWindow.installDecor 
private void installDecor() {    
    //根据不同的Theme,创建不同的DecorView,DecorView是一个FrameLayout 
}

```
这时只是创建了PhoneWindow,和DecorView,但目前二者也没有任何关系,产生利息的时刻是在ActivityThread.performResumeActivity中,再调用r.activity.performResume()，**调用r.activity.makeVisible,将DecorView添加到当前的Window上**。


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

WindowManager的addView的**具体实现在WindowManagerImpl中,而WindowManagerImpl的addView又会调用WindowManagerGlobal.addView**。


```
WindowManagerGlobal.addView
public void addView(View view, ViewGroup.LayoutParams params,  Display display, Window parentWindow) {
    ...
    ViewRootImpl root = new ViewRootImpl(view.getContext(), display);        
    view.setLayoutParams(wparams);    
    mViews.add(view);    
    mRoots.add(root);    
    mParams.add(wparams);        
    root.setView(view, wparams, panelParentView);
    ...
}

```
这个过程创建一个ViewRootImpl,并将之前创建的DecoView作为参数传入,以后DecoView的事件都由ViewRootImpl来管理了,比如DecoView上添加View,删除View。ViewRootImpl实现了ViewParent这个接口,这个接口最常见的一个方法是requestLayout().

ViewRootImpl是个ViewParent，在DecoView添加的View时,就会将View中的ViewParent设为DecoView所在的ViewRootImpl,View的ViewParent相同时,理解为这些View在一个View链上。所以**每当调用View的requestLayout(）时,其实是调用到ViewRootImpl，ViewRootImpl会控制整个事件的流程。可以看出一个ViewRootImpl对添加到DecoView的所有View进行事件管理**。


![image](http://o9m6aqy3r.bkt.clouddn.com//Window/ViewRootImpl&DecorView&View.png)

其中，**mView和DecoView是同级关系,由不同ViewRootImpl控制,不在同一个View链上,之间没有联系**。这个类似于PopupWindow。

## 3. 问题总结

#### 问题1：

在Activity里调用<br>

```
WindowManager.LayoutParams wl = new WindowManager.LayoutParams(); 
getWindowManager().addView(mView,wl)
```

和<br>

```
LayoutParams wmParams =...
addContentView(mView,wmParams);//activity里的方法
```
 
**这两种方式背后的实现是怎样的,有什么区别?**

#### 回答：

现在来看在Activity通过 `getWindowManager().addView(mView,wl)`和 `addContentView(mView,wmParams)`的区别。

第一种情况会调用到WindowManagerGlobal.addView,这时会创建一个新的ViewRootImpl,和原来的DecoView不在一条View链上,所以它们之间的任何一个调用requestLayout(）不会影响到另一个。

而addContentView(mView,wmParams)是直接将mView添加到DecoView中,会使ViewRootImpl链下的所有View重绘。

---

#### 问题2：

Dialog和PopupWindow的区别在哪里?为什么Dialog传入application的Context会报错?

#### 回答：

**Dialog在创建时会新建一个PhoneWindow**,同时也会使用DecoView作为这个PhoneWindow的根View,相当于走了一遍Activity里创建PhoneWindow和DecoView的流程,而调用Dialog的show方法时,类似于ActivityThread.performResumeActivity,将DecoView添加到Window,**同时创建管理DecoView链的RootViewImpl来管理DecoView**。

**PopupWindow就和第一个问题中 getWindowManager().addView(mView,wl)类似了,只是创建一条新的View链和ViewRootImpl,并没有创建新的Window**。

Dialog对话框和PopupWindow对话框最**主要的区别就是Dialog窗口内部拥有一个PhoneWindow对象来处理了输入事件，而PopupWindow窗口内部没有PhoneWindow对象来理输入事件。这也就导致了Dialog能响应“Back”返回键对话框消失和点击对话框之外的地方对话框消失而PopupWindow不能的原因**

Dialog的参数不能传递非Activity的Context,如Application 和 Service,这是因为Dialog通过传入的Context来得到context里的mWindowManager(也就是WindowManagerImpl)与mToken,这是为了表明Dialog所属的Activity,在Window.addView时,需要这个mToken(IBinder对象),而Application 和 Service传入的情况下Token是null。

#### 问题3：
ViewRootImpl是什么,一个Activity有多少个ViewRootImpl对象?
#### 回答：

上面的分析可看出,ViewRootImpl是实际管理Window中所以View的类,**每个Activity中ViewRootImpl数量取决于调用mWindowManager.addView的调用次数**。

#### 问题4：
该怎样理解Window?
#### 回答：

Activity提供和WMS通信的Token(IBinder对象）,**DecoView结合ViewRootImpl来管理同一View链（有相同的ParentView的View,ViewRootImpl也就是ParentView）的所以View的事件,绘制等**。

那Window的意义在哪?**虽然Window也就是PhoneWindow没有具体做什么,但Window把Activity从View的一些创建,管理以及和ViewRootImpl的交互中脱离出来,让Activity与View尽量解耦,**要不然这些工作都要放在Activity中午处理,Activity的任务就会变得更杂更重。为什么不能用一个ViewGroup比如DecoView来管理所有的View呢,因为**一个Activity可能有不止一条View链,总要有一个进行管理的地方。View的意义就是将Activity和View的繁琐工作中脱离出来**。
