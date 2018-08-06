# WindowManager add&update&remove过程

**参考：《Android开发艺术探索》 第八章**

**1. Window添加过程**<br>
**2. Window删除过程**<br>
**3. Window更新过程**


## 1. Window添加过程

Window的添加过程需要通过WindowManager的addView来实现，WindowManager是一个接口，真正实现是在WindowManagerImpl类中，在WindowManagerImpl中Window三大操作实现如下：


```
    @Override
    public void addView(View view, ViewGroup.LayoutParams params) {
        mGlobal.addView(view, params, mDisplay, mParentWindow);
    }

    @Override
    public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
        mGlobal.updateViewLayout(view, params);
    }

    @Override
    public void removeView(View view) {
        mGlobal.removeView(view, false);
    }

    @Override
    public void removeViewImmediate(View view) {
        mGlobal.removeView(view, true);
    }

```
可以发现，WindowManagerlmpl 并没有直接实现Window的三大操作，而是全部交给了WindowManagerGlobal 来处理。

**WindowManagerGlobal**以工厂的形式向外提供自己的实例，在WindowManagerGlobal中有如下一段代码:  
```
private finWindowManagerGlobalmGlobal = WindowManagerGlobal getInstance()。
```

WindowManagerlm这种工作模式是典型的**桥接模式**，将所有的操作全部委托给WindowManagerGlobal来实现。

**WindowManagerGlobal的addView方法主要分为如下几步**。



```
    public void addView(View view, ViewGroup.LayoutParams params,
            Display display, Window parentWindow) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("display must not be null");
        }
        if (!(params instanceof WindowManager.LayoutParams)) {
            throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
        }

        final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams)params;
        if (parentWindow != null) {
            parentWindow.adjustLayoutParamsForSubWindow(wparams);
        } else {
            // If there's no parent and we're running on L or above (or in the
            // system context), assume we want hardware acceleration.
            final Context context = view.getContext();
            if (context != null
                    && context.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.LOLLIPOP) {
                wparams.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            }
        }

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

            root = new ViewRootImpl(view.getContext(), display);

            view.setLayoutParams(wparams);

            mViews.add(view);
            mRoots.add(root);
            mParams.add(wparams);
        }

        // do this last because it fires off messages to start doing things
        try {
            root.setView(view, wparams, panelParentView);
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
1. **检查参数是否合法，如果是子Window还需要更新一些布局参数**
2. **创建ViewRootImpl并将View添加到列表中**

在WindowManagerGlobal中内部有如下几个列表比较重要：


```
    private final ArrayList<View> mViews = new ArrayList<View>();
    private final ArrayList<ViewRootImpl> mRoots = new ArrayList<ViewRootImpl>();
    private final ArrayList<WindowManager.LayoutParams> mParams =
            new ArrayList<WindowManager.LayoutParams>();
    private final ArraySet<View> mDyingViews = new ArraySet<View>();

```

在.上面的声明中:<br>
**mViews**存储的是所有Window所对应的View，<br>
**mRoots** 存储的是所有Window所对应的ViewRootImpl,<br> **mParams**存储的是所有Window所对应的布局参数，<br>
**mDyingViews**则存储了那些正在被删除的View对象,或者说是那些已经调用removeView方法但是删除操作还未完成的Window对象。

在addView中通过如下方式将Window的一系列对象添加到列表中:

```
 view.setLayoutParams(wparams);
 mViews.add(view);
 mRoots.add(root);
 mParams.add(wparams);
```

3. **通过ViewRootImpl来更新界面并完成Window的添加操作**

这个操作由ViewRootImpl的setView来完成。View的绘制过程是由ViewRootImpl来完成的，这里的setView内部会通过requestLayout来完成异步刷新请求。下面代码中，scheduleTraversals是实际View绘制的入口：


```
    @Override
    public void requestLayout() {
        if (!mHandlingLayoutInLayoutRequest) {
            checkThread();
            mLayoutRequested = true;
            scheduleTraversals();
        }
    }
```


接着会通过WindowSession最终来完成Window的添加过程。下面代码中
，mWindowSession的类型是IWindowSession,它是一个Binder对象，真正的实现类是Session,也就是Window的添加过程是一个IPC过程。

```
 try {
                    mOrigWindowType = mWindowAttributes.type;
                    mAttachInfo.mRecomputeGlobalAttributes = true;
                    collectViewAttributes();
                    res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
                            getHostVisibility(), mDisplay.getDisplayId(),
                            mAttachInfo.mContentInsets, mInputChannel);
                } catch (RemoteException e) {
                    mAdded = false;
                    mView = null;
                    mAttachInfo.mRootView = null;
                    mInputChannel = null;
                    mFallbackEventHandler.setView(null);
                    unscheduleTraversals();
                    setAccessibilityFocus(null, null);
                    throw new RuntimeException("Adding window failed", e);
                } finally {
                    if (restore) {
                        attrs.restore();
                    }
                }
```
在Session内部会通过WindowManagerService来实现Window的添加，代码如下：


```
@Override
 public int addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs,
         int viewVisibility, int displayId, Rect outContentInsets, Rect outStableInsets,
         Rect outOutsets, InputChannel outInputChannel) {
     return mService.addWindow(this, window, seq, attrs, viewVisibility, displayId,
             outContentInsets, outStableInsets, outOutsets, outInputChannel);
 }
```

addToDisplay方法中会调用了WMS的addWindow方法，并将自身也就是Session，作为参数传了进去，每个应用程序进程都会对应一个Session，WMS会用ArrayList来保存这些Session。

## 2. Window删除过程

Window的删除过程和添加过程一样，都是先通过WindowManagerImpl，进一步通过WindowManagerGlobal来实现。

其中，WindowManagerGlobal中的removeView如下：


```
    public void removeView(View view, boolean immediate) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }

        synchronized (mLock) {
            int index = findViewLocked(view, true);
            View curView = mRoots.get(index).getView();
            removeViewLocked(index, immediate);
            if (curView == view) {
                return;
            }

            throw new IllegalStateException("Calling with view " + view
                    + " but the ViewAncestor is attached to " + curView);
        }
    }
```
removeView中，首先通过findViewLocked来查找待删除的View的索引，这个查找过程就是建立的数组遍历，然后再调用removeViewLocked来做进一步删除，如下：


```
    private void removeViewLocked(int index, boolean immediate) {
        ViewRootImpl root = mRoots.get(index);
        View view = root.getView();

        if (view != null) {
            InputMethodManager imm = InputMethodManager.getInstance();
            if (imm != null) {
                imm.windowDismissed(mViews.get(index).getWindowToken());
            }
        }
        boolean deferred = root.die(immediate);
        if (view != null) {
            view.assignParent(null);
            if (deferred) {
                mDyingViews.add(view);
            }
        }
    }
```
**removeViewLocked是通过ViewRootmpl来完成删除操作的。在Windowanager中提供了两种删除接口removeView和removeViewImmediate,们分别表示异步删除和同步删除**，其中removeViewImmediate使用来需要特别注意，一般来说不需要使用此方法来删除Window以免发意外的错误。

这里主要说异步删除的情况，**具体的删除操作由ViewRootImpl的die方法来完成。在异步删除的情况下，die方法只是发了一个请求删除的消息后就立刻返回了，这个时候View并没有完成除操作，所以最后会将其添加到mDyingViews中，mDyingViews表示删除的View列表**。ViewRootlmpl 的die方法如下所示。


```
    boolean die(boolean immediate) {
        // Make sure we do execute immediately if we are in the middle of a traversal or the damage
        // done by dispatchDetachedFromWindow will cause havoc on return.
        if (immediate && !mIsInTraversal) {
            doDie();
            return false;
        }

        if (!mIsDrawing) {
            destroyHardwareRenderer();
        } else {
            Log.e(TAG, "Attempting to destroy the window while drawing!\n" +
                    "  window=" + this + ", title=" + mWindowAttributes.getTitle());
        }
        mHandler.sendEmptyMessage(MSG_DIE);
        return true;
    }
```
在die方法内部只是做了简单的判断，如果是异步删除，那么就发送-一个MSG_ DIE的消息，ViewRootImpl 中的Handler会处理此消息并调用doDie方法，如果是同步删除(立即删除)，那么就不发消息直接调用doDie方法，这就是这两种删除方式的区别。在doDie内部会调用dispatchDetachedFromWindow 方法，**真正删除View的逻辑在dispatchDetachedFromWindow方法的内部实现**。



```
    void dispatchDetachedFromWindow() {
        if (mView != null && mView.mAttachInfo != null) {
            mAttachInfo.mTreeObserver.dispatchOnWindowAttachedChange(false);
            mView.dispatchDetachedFromWindow();
        }

        mAccessibilityInteractionConnectionManager.ensureNoConnection();
        mAccessibilityManager.removeAccessibilityStateChangeListener(
                mAccessibilityInteractionConnectionManager);
        mAccessibilityManager.removeHighTextContrastStateChangeListener(
                mHighContrastTextManager);
        removeSendWindowContentChangedCallback();

        destroyHardwareRenderer();

        setAccessibilityFocus(null, null);

        mView.assignParent(null);
        mView = null;
        mAttachInfo.mRootView = null;

        mSurface.release();

        if (mInputQueueCallback != null && mInputQueue != null) {
            mInputQueueCallback.onInputQueueDestroyed(mInputQueue);
            mInputQueue.dispose();
            mInputQueueCallback = null;
            mInputQueue = null;
        }
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        try {
            mWindowSession.remove(mWindow);
        } catch (RemoteException e) {
        }

        // Dispose the input channel after removing the window so the Window Manager
        // doesn't interpret the input channel being closed as an abnormal termination.
        if (mInputChannel != null) {
            mInputChannel.dispose();
            mInputChannel = null;
        }

        mDisplayManager.unregisterDisplayListener(mDisplayListener);

        unscheduleTraversals();
    }
```




**dispatchDetachedFromWindow 方法主要做四件事:**
  
(1)**垃圾回收相关的工作**，比如清除数据和消息、移除回调。
  
(2)通过Session的remove方法删除Window: **mWindowSession.remove(mWindow),这同样是一一个IPC过程，最终会调用WindowManagerService的removeWindow方法**。
  
(3)调用**View的dispatchDetachedFromWindow方法,在内部会调用View的onDetachedFromWindow()以及onDetachedFromWindowInternal**(。 对于onDetachedFromWindow大家一定不陌生，当View从Window中移除时，这个方法就会被调用，可以在这个方法内部做一些资源回收的工作，比如终止动画、停止线程等。
  
(4)调用**WindowManagerGlobal的doRemoveView方法刷新数据，包括mRoots .mParams以及mDyingViews,  需要将当前Window所关联的这三类对象从列表中删除**。

## 3. Window更新过程

WindowManagerGloabl的updateViewLayout方法如下：

```
    public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (!(params instanceof WindowManager.LayoutParams)) {
            throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
        }

        final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams)params;

        view.setLayoutParams(wparams);

        synchronized (mLock) {
            int index = findViewLocked(view, true);
            ViewRootImpl root = mRoots.get(index);
            mParams.remove(index);
            mParams.add(index, wparams);
            root.setLayoutParams(wparams, false);
        }
    }
```

updateViewLayout方法做的事情就比较简单了：

首先它需要更新View的LayoutParams并替换掉老的LayoutParams,接着再**更新ViewRootImpl中的LayoutParams,这一步是通过ViewRootImpl的setLayoutParams方法来实现的**。


```
    void setLayoutParams(WindowManager.LayoutParams attrs, boolean newView) {
        synchronized (this) {
            final int oldInsetLeft = mWindowAttributes.surfaceInsets.left;
            final int oldInsetTop = mWindowAttributes.surfaceInsets.top;
            final int oldInsetRight = mWindowAttributes.surfaceInsets.right;
            final int oldInsetBottom = mWindowAttributes.surfaceInsets.bottom;
            final int oldSoftInputMode = mWindowAttributes.softInputMode;

            // Keep track of the actual window flags supplied by the client.
            mClientWindowLayoutFlags = attrs.flags;

            // Preserve compatible window flag if exists.
            final int compatibleWindowFlag = mWindowAttributes.privateFlags
                    & WindowManager.LayoutParams.PRIVATE_FLAG_COMPATIBLE_WINDOW;

            // Transfer over system UI visibility values as they carry current state.
            attrs.systemUiVisibility = mWindowAttributes.systemUiVisibility;
            attrs.subtreeSystemUiVisibility = mWindowAttributes.subtreeSystemUiVisibility;

            mWindowAttributesChangesFlag = mWindowAttributes.copyFrom(attrs);
            if ((mWindowAttributesChangesFlag
                    & WindowManager.LayoutParams.TRANSLUCENT_FLAGS_CHANGED) != 0) {
                // Recompute system ui visibility.
                mAttachInfo.mRecomputeGlobalAttributes = true;
            }
            if (mWindowAttributes.packageName == null) {
                mWindowAttributes.packageName = mBasePackageName;
            }
            mWindowAttributes.privateFlags |= compatibleWindowFlag;

            // Restore old surface insets.
            mWindowAttributes.surfaceInsets.set(
                    oldInsetLeft, oldInsetTop, oldInsetRight, oldInsetBottom);

            applyKeepScreenOnFlag(mWindowAttributes);

            if (newView) {
                mSoftInputMode = attrs.softInputMode;
                requestLayout();
            }

            // Don't lose the mode we last auto-computed.
            if ((attrs.softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                    == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED) {
                mWindowAttributes.softInputMode = (mWindowAttributes.softInputMode
                        & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                        | (oldSoftInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST);
            }

            mWindowAttributesChanged = true;
            scheduleTraversals();//View重新布局，包括测量、布局、重绘这三个过程
        }
    }
```

在ViewRootmpl中会通过**scheduleTraversals**方法来对View重新布局，包括测量、布局、重绘这三个过程。

除了View本身的重绘以外，ViewRootImpl还会通过WindowSession 来更新Window 的视图，这个过程最终是由WindowManagerService的relayoutWindow0来具体实现的，它同样是一个IPC过程。
