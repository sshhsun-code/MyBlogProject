# onAttachedToWindow和onDetachedFromWindow调用时机源码解析

>**参考**

[https://www.jianshu.com/p/e7b6fa788ae6](https://www.jianshu.com/p/e7b6fa788ae6)

[https://blog.csdn.net/hty1053240123/article/details/76507265](https://blog.csdn.net/hty1053240123/article/details/76507265)

[http://yuanfentiank789.github.io/2017/06/23/viewtree/](http://yuanfentiank789.github.io/2017/06/23/viewtree/)

>**章节**

1. View ViewRootImpl中的AttachInfo 传递
2. onAttachedToWindow调用时机源码解析
3. onDetachedFromWindow调用时机源码解析
4. 总结和扩展


## 1. View ViewRootImpl中的AttachInfo 传递

**View与ViewRootImpl的绑定**

通过view.getViewRootImpl可以获取到ViewRootImpl。
```
public ViewRootImpl getViewRootImpl() {
        if (mAttachInfo != null) {
            return mAttachInfo.mViewRootImpl;
        }
        return null;
    }

```
而这个AttachInfo则是View里面一个静态内部类，它的构造方法：


```
AttachInfo(IWindowSession session, IWindow window, Display display,
                ViewRootImpl viewRootImpl, Handler handler, Callbacks effectPlayer) {
            mSession = session;
            mWindow = window;
            mWindowToken = window.asBinder();
            mDisplay = display;
            mViewRootImpl = viewRootImpl;
            mHandler = handler;
            mRootCallbacks = effectPlayer;
        }

```
而在ViewRootImpl的构造函数中:

```
public ViewRootImpl(Context context, Display display) {
        mContext = context;
        mWindowSession = WindowManagerGlobal.getWindowSession();
        ...
        mAttachInfo = new View.AttachInfo(mWindowSession, mWindow, display, this, mHandler, this);
        ...
    }
```
ViewRootImpl中持有了mAttachInfo实例。

那么，每一个view的mAttachInfo是怎么获取到的呢？

**当一个View附着到它的父Window中时，这个View能获取到一组View和父Window之间的信息，就存储在AttachInfo当中。**

**AttachInfo是在View第一次attach到Window时，ViewRoot传给自己的子View的，然后沿着视图树，AttachInfo会一直传递到每一个View。**

**ViewGroup.java**
```
@Override
void dispatchAttachedToWindow(AttachInfo info, int visibility) {
      ...
    final int count = mChildrenCount;
    final View[] children = mChildren;
    for (int i = 0; i < count; i++) {
        final View child = children[i];
        child.dispatchAttachedToWindow(info,
                combineVisibility(visibility, child.getVisibility()));
    }
    ...
}
```

**View.java**
```
void dispatchAttachedToWindow(AttachInfo info, int visibility) {
    //System.out.println("Attached! " + this);
    mAttachInfo = info;
    ...
}
```
**另外，当新的View加入到ViewGroup中时，也会将AttachInfo传入**。

`ViewGroup.java`
```
 private void addViewInner(View child, int index, LayoutParams params, boolean preventRequestLayout) {
    ...
    AttachInfo ai = mAttachInfo;
    if (ai != null && (mGroupFlags & FLAG_PREVENT_DISPATCH_ATTACHED_TO_WINDOW) == 0) {
        boolean lastKeepOn = ai.mKeepScreenOn;
        ai.mKeepScreenOn = false;
        child.dispatchAttachedToWindow(mAttachInfo, (mViewFlags&VISIBILITY_MASK));
        if (ai.mKeepScreenOn) {
            needGlobalAttributesUpdate(true);
        }
        ai.mKeepScreenOn = lastKeepOn;
    }
    ...

}
```

## 2. onAttachedToWindow调用时机源码解析

在ActivityThread.handleResumeActivity的过程中，会将Act的DecorView添加到WindowManager中.当在ActivityThread.handleResumeActivity()方法中调用WindowManager.addView()方法时，最终是调去了:


```
WindowManagerImpl.addView() -->
WindowManagerGlobal.addView()
```

最终调用到的代码：


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

        final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams) params;
        if (parentWindow != null) {
            parentWindow.adjustLayoutParamsForSubWindow(wparams);
        } else {
            // If there's no parent, then hardware acceleration for this view is
            // set from the application's hardware acceleration setting.
            final Context context = view.getContext();
            if (context != null
                    && (context.getApplicationInfo().flags
                            & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0) {
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
            // 这行代码是本文重点关注的！！！
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

其中有一句`root.setView(view, wparams, panelParentView);`正是这行代码将调用流程转移到了`ViewRootImpl.setView()`此方法内部最终会触发`ViewRootImpl.performTraversals()`方法.

这个方法就是我们熟悉的View从无到有要经历的3个阶段(measure, layout, draw)，不过这个方法内部和我们这里讨论的内容相关的是其1364行代码：`host.dispatchAttachedToWindow(mAttachInfo, 0);`

这里的host就是Act的**DecorView**(FrameLayout的子类)，我们可以看到是**通过这样的dispatch方法将这个调用沿着View tree分发了下去，我们分别看下ViewGroup和View中这个方法的实现，如下**：


```
// ViewGroup中的实现：
void dispatchAttachedToWindow(AttachInfo info, int visibility) {
        mGroupFlags |= FLAG_PREVENT_DISPATCH_ATTACHED_TO_WINDOW;
        // 先调用自己的
        super.dispatchAttachedToWindow(info, visibility);
        mGroupFlags &= ~FLAG_PREVENT_DISPATCH_ATTACHED_TO_WINDOW;

        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) {
            final View child = children[i];
           // 递归调用每个child的dispatchAttachedToWindow方法
           // 典型的深度优先遍历
            child.dispatchAttachedToWindow(info,
                    combineVisibility(visibility, child.getVisibility()));
        }
        final int transientCount = mTransientIndices == null ? 0 : mTransientIndices.size();
        for (int i = 0; i < transientCount; ++i) {
            View view = mTransientViews.get(i);
            view.dispatchAttachedToWindow(info,
                    combineVisibility(visibility, view.getVisibility()));
        }
    }

```


```
// View中的实现：
void dispatchAttachedToWindow(AttachInfo info, int visibility) {
        //System.out.println("Attached! " + this);
        mAttachInfo = info;
        if (mOverlay != null) {
            mOverlay.getOverlayView().dispatchAttachedToWindow(info, visibility);
        }
        mWindowAttachCount++;
        // We will need to evaluate the drawable state at least once.
        mPrivateFlags |= PFLAG_DRAWABLE_STATE_DIRTY;
        if (mFloatingTreeObserver != null) {
            info.mTreeObserver.merge(mFloatingTreeObserver);
            mFloatingTreeObserver = null;
        }
        if ((mPrivateFlags&PFLAG_SCROLL_CONTAINER) != 0) {
            mAttachInfo.mScrollContainers.add(this);
            mPrivateFlags |= PFLAG_SCROLL_CONTAINER_ADDED;
        }
        performCollectViewAttributes(mAttachInfo, visibility);
        onAttachedToWindow();

        ListenerInfo li = mListenerInfo;
        final CopyOnWriteArrayList<OnAttachStateChangeListener> listeners =
                li != null ? li.mOnAttachStateChangeListeners : null;
        if (listeners != null && listeners.size() > 0) {
            // NOTE: because of the use of CopyOnWriteArrayList, we *must* use an iterator to
            // perform the dispatching. The iterator is a safe guard against listeners that
            // could mutate the list by calling the various add/remove methods. This prevents
            // the array from being modified while we iterate it.
            for (OnAttachStateChangeListener listener : listeners) {
                listener.onViewAttachedToWindow(this);
            }
        }

        int vis = info.mWindowVisibility;
        if (vis != GONE) {
            onWindowVisibilityChanged(vis);
        }

        // Send onVisibilityChanged directly instead of dispatchVisibilityChanged.
        // As all views in the subtree will already receive dispatchAttachedToWindow
        // traversing the subtree again here is not desired.
        onVisibilityChanged(this, visibility);

        if ((mPrivateFlags&PFLAG_DRAWABLE_STATE_DIRTY) != 0) {
            // If nobody has evaluated the drawable state yet, then do it now.
            refreshDrawableState();
        }
        needGlobalAttributesUpdate(false);
    }
```

**从源码我们可以清晰地看到ViewGroup先是调用自己的onAttachedToWindow()方法，再调用其每个child的onAttachedToWindow()方法，这样此方法就在整个view树中遍布开了，注意到visibility并不会对这个方法产生影响。**

## 3. onDetachedFromWindow调用时机源码解析

和attched对应的，detached的发生是从act的销毁开始的，具体的代码调用流程如下：


```
ActivityThread.handleDestroyActivity() -->
WindowManager.removeViewImmediate() -->
WindowManagerGlobal.removeViewLocked()方法 —>
ViewRootImpl.die() --> doDie() -->
ViewRootImpl.dispatchDetachedFromWindow()
```
最终会调用到View层次结构的dispatchDetachedFromWindow方法去，对应的代码如下：

```
// ViewGroup的：
@Override
    void dispatchDetachedFromWindow() {
        // If we still have a touch target, we are still in the process of
        // dispatching motion events to a child; we need to get rid of that
        // child to avoid dispatching events to it after the window is torn
        // down. To make sure we keep the child in a consistent state, we
        // first send it an ACTION_CANCEL motion event.
        cancelAndClearTouchTargets(null);

        // Similarly, set ACTION_EXIT to all hover targets and clear them.
        exitHoverTargets();

        // In case view is detached while transition is running
        mLayoutCalledWhileSuppressed = false;

        // Tear down our drag tracking
        mDragNotifiedChildren = null;
        if (mCurrentDrag != null) {
            mCurrentDrag.recycle();
            mCurrentDrag = null;
        }

        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) {
            // 先调用child的方法
            children[i].dispatchDetachedFromWindow();
        }
        clearDisappearingChildren();
        final int transientCount = mTransientViews == null ? 0 : mTransientIndices.size();
        for (int i = 0; i < transientCount; ++i) {
            View view = mTransientViews.get(i);
            view.dispatchDetachedFromWindow();
        }
       // 最后才是自己的
        super.dispatchDetachedFromWindow();
    }
```


```
// View的：
void dispatchDetachedFromWindow() {
        AttachInfo info = mAttachInfo;
        if (info != null) {
            int vis = info.mWindowVisibility;
            if (vis != GONE) {
                onWindowVisibilityChanged(GONE);
            }
        }
        // 调用回调
        onDetachedFromWindow();
        onDetachedFromWindowInternal();

        InputMethodManager imm = InputMethodManager.peekInstance();
        if (imm != null) {
            imm.onViewDetachedFromWindow(this);
        }

        ListenerInfo li = mListenerInfo;
        final CopyOnWriteArrayList<OnAttachStateChangeListener> listeners =
                li != null ? li.mOnAttachStateChangeListeners : null;
        if (listeners != null && listeners.size() > 0) {
            // NOTE: because of the use of CopyOnWriteArrayList, we *must* use an iterator to
            // perform the dispatching. The iterator is a safe guard against listeners that
            // could mutate the list by calling the various add/remove methods. This prevents
            // the array from being modified while we iterate it.
            for (OnAttachStateChangeListener listener : listeners) {
                listener.onViewDetachedFromWindow(this);
            }
        }

        if ((mPrivateFlags & PFLAG_SCROLL_CONTAINER_ADDED) != 0) {
            mAttachInfo.mScrollContainers.remove(this);
            mPrivateFlags &= ~PFLAG_SCROLL_CONTAINER_ADDED;
        }

        mAttachInfo = null;
        if (mOverlay != null) {
            mOverlay.getOverlayView().dispatchDetachedFromWindow();
        }
    }
```
**至此，onDetachedFromWindow()就在整个view树上传播开了。**

## 4. 总结和扩展

**总结**


- **onAttachedToWindow是先调用自己，然后调用子View的onAttachedToWindow;onDetachedFromWindow是先调用子View的，然后再调用自己的**.
- onAttachedToWindow调用顺序：
>ActivityThread.handleResumeActivity-><br>
>WindowManagerImpl.addView-><br>
>WindowManagerGlobal.addView-><br>
>ViewRootImpl.performTraversals-><br>
>ViewGroup.dispatchAttachedToWindow-><br>
>View.dispatchAttachedToWindow->onAttachedToWindow
- onDetachedFromWindow调用顺序：
>ActivityThread.handleDestroyActivity-><br>
>WindowManagerImpl.removeViewImmediate-><br>
>WindowManagerGlobal.removeView-><br>
>ViewRootImpl.die-><br>
>ViewRootImpl.doDie-><br>
>ViewRootImpl.dispatchDetachedFromWindow-><br>
>ViewGroup.dispatchDetachedFromWindow-><br>
>View.dispatchDetachedFromWindow->onDetachedToWindow
- onAttachedToWindow和onDetachedFromWindow的调用与visibility无关。

**扩展**

fragment下的view的attach/detach时机。

fragment的case里面最终会分别触发ViewGroup的addViewInner 和removeViewInternal这2个方法，它们内部又会调到dispatchAttachedToWindow/dispatchDetachedFromWindow。

fragments的大部分状态都和activitie很相似，但fragment有一些新的状态。

- onAttached() —— 当fragment和activity关联之后，调用这个方法
- onCreateView() —— 创建fragment中的视图的时候，调用这个方法。
- onActivityCreated() —— 当activity的onCreate()方法被返回之后，调用这个方法。
- onDestroyView() —— 当fragment中的视图被移除的时候，调用这个方法。
- onDetach() —— 当fragment和activity分离的时候，调用这个方法。