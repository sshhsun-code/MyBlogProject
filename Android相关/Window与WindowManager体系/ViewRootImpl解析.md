# ViewRootImpl解析

参考：

[https://blog.csdn.net/feiduclear_up/article/details/46772477](https://blog.csdn.net/feiduclear_up/article/details/46772477)

[https://www.jianshu.com/p/9da7bfe18374](https://www.jianshu.com/p/9da7bfe18374)

1. ViewRootImpl
2. View通过ViewRootImpl来绘制
3. ViewRootImpl, View与WindowManager联系
4. ViewRootImpl向DecorView分发事件
5. 补充扩展


## 1. ViewRootImpl

ViewRoot类在Android2.2之后就被ViewRootImpl替换了，对应于ViewRootImpl.java，他是链接WindowManager和DecorView的纽带，另外View的绘制也是通过ViewRootImpl来完成的。

```
/**
 * The top of a view hierarchy, implementing the needed protocol between View
 * and the WindowManager.  This is for the most part an internal implementation
 * detail of {@link WindowManagerGlobal}.
 *
 * {@hide}
 */
```

**ViewRootImpl是一个视图层次结构的顶部，它实现了View与WindowManager之间所需要的协议，作为WindowManagerGlobal中大部分的内部实现**。


**在WindowManagerGlobal中实现方法中，都可以见到ViewRootImpl**，也就说WindowManagerGlobal方法最后还是调用到了ViewRootImpl。addView,removeView,update调用顺序：


```
WindowManagerImpl -> WindowManagerGlobal -> ViewRootImpl
```

比如说，addView最终调用到了viewRootImpl的setView方法：


```
public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
                ...
                // Schedule the first layout -before- adding to the window  
                // manager, to make sure we do the relayout before receiving  
                // any other events from the system.
                requestLayout();
                ...
                try {
                ...
                    res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
                            getHostVisibility(), mDisplay.getDisplayId(),
                            mAttachInfo.mContentInsets, mAttachInfo.mStableInsets,
                            mAttachInfo.mOutsets, mInputChannel);
                } 
    }

```
在setView方法中，首先会调用到requestLayout（），表示**添加Window之前先完成第一次layout布局过程，以确保在收到任何系统事件后面重新布局**。requestLayout最终会调用performTraversals方法来完成View的绘制。

接着会通过WindowSession最终来完成Window的添加过程。在下面的代码中mWindowSession类型是IWindowSession，它是一个Binder对象，真正的实现类是Session，也就是说这其实是一次IPC过程，远程调用了Session中的addToDisPlay方法。


```
@Override
    public int addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs,
            int viewVisibility, int displayId, Rect outContentInsets, Rect outStableInsets,
            Rect outOutsets, InputChannel outInputChannel) {
        return mService.addWindow(this, window, seq, attrs, viewVisibility, displayId,
                outContentInsets, outStableInsets, outOutsets, outInputChannel);
    }
```
这里的mService就是WindowManagerService，也就是说**Window的添加请求，最终是通过WindowManagerService来添加的**。

## 2. View通过ViewRootImpl来绘制

![image](http://o9m6aqy3r.bkt.clouddn.com//Window/ViewRootImpl%E7%BB%98%E5%88%B6.png)

之前提到了，**ViewRootImpl调用到requestLayout()来完成View的绘制操作**


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

View绘制，先判断当前线程


```
void checkThread() {
        if (mThread != Thread.currentThread()) {
            throw new CalledFromWrongThreadException(
                    "Only the original thread that created a view hierarchy can touch its views.");
        }
    }

```

如果不是当前线程则抛出异常，这个异常是不是感觉很熟悉啊。没错，当你在子线程更新UI没使用handler的话就会抛出这个异常


```
android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.

```
抛出地方就是这里，一般在子线程操作UI都会调用到view.invalidate，而View的重绘会触发ViewRootImpl的requestLayout，就会去判断当前线程。

接着看，判断完线程后，接着调用scheduleTraversals（）


```
    void scheduleTraversals() {
        if (!mTraversalScheduled) {
            mTraversalScheduled = true;
            mTraversalBarrier = mHandler.getLooper().postSyncBarrier();
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_TRAVERSAL, mTraversalRunnable, null);
            if (!mUnbufferedInputDispatch) {
                scheduleConsumeBatchedInput();
            }
            notifyRendererOfFramePending();
        }
    }

..............

final class TraversalRunnable implements Runnable {
        @Override
        public void run() {
            doTraversal();
        }
    }
final TraversalRunnable mTraversalRunnable = new TraversalRunnable();

...............

 void doTraversal() {
        if (mTraversalScheduled) {
            mTraversalScheduled = false;
            mHandler.getLooper().removeSyncBarrier(mTraversalBarrier);

            try {
                performTraversals();
            } finally {
                Trace.traceEnd(Trace.TRACE_TAG_VIEW);
            }
        }
    }

............

```

分析上述代码发现：

**scheduleTraversals中会通过handler去异步调用mTraversalRunnable接口，接着，最后真正调用绘制的是performTraversals（）方法**。

**`performTraversals`方法**

```
private void performTraversals() {
        // cache mView since it is used so much below...
        //我们在Step3知道，mView就是DecorView根布局
        final View host = mView;
        //在Step3 成员变量mAdded赋值为true，因此条件不成立
        if (host == null || !mAdded)
            return;
        //是否正在遍历
        mIsInTraversal = true;
        //是否马上绘制View
        mWillDrawSoon = true;

        .............
        //顶层视图DecorView所需要窗口的宽度和高度
        int desiredWindowWidth;
        int desiredWindowHeight;

        .....................
        //在构造方法中mFirst已经设置为true，表示是否是第一次绘制DecorView
        if (mFirst) {
            mFullRedrawNeeded = true;
            mLayoutRequested = true;
            //如果窗口的类型是有状态栏的，那么顶层视图DecorView所需要窗口的宽度和高度就是除了状态栏
            if (lp.type == WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL
                    || lp.type == WindowManager.LayoutParams.TYPE_INPUT_METHOD) {
                // NOTE -- system code, won't try to do compat mode.
                Point size = new Point();
                mDisplay.getRealSize(size);
                desiredWindowWidth = size.x;
                desiredWindowHeight = size.y;
            } else {//否则顶层视图DecorView所需要窗口的宽度和高度就是整个屏幕的宽高
                DisplayMetrics packageMetrics =
                    mView.getContext().getResources().getDisplayMetrics();
                desiredWindowWidth = packageMetrics.widthPixels;
                desiredWindowHeight = packageMetrics.heightPixels;
            }
    }
............
//获得view宽高的测量规格，mWidth和mHeight表示窗口的宽高，lp.widthhe和lp.height表示DecorView根布局宽和高
 int childWidthMeasureSpec = getRootMeasureSpec(mWidth, lp.width);
 int childHeightMeasureSpec = getRootMeasureSpec(mHeight, lp.height);

  // Ask host how big it wants to be
  //执行测量操作
  performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);

........................
//执行布局操作
 performLayout(lp, desiredWindowWidth, desiredWindowHeight);

.......................
//执行绘制操作
performDraw();

}
```

该方法主要流程就体现了View绘制渲染的三个主要步骤，分别是测量，布局，绘制三个阶段才能将一个View绘制出来，所以View的绘制是ViewRootImpl完成的，另外当手动调用invalidate，postInvalidate，requestInvalidate也会最终调用performTraversals，来重新绘制View。

这里先给出Android系统View的绘制流程：依次执行View类里面的如下三个方法：

1. **measure(int ,int) :测量View的大小**
2. **layout(int ,int ,int ,int) ：设置子View的位置**
3. **draw(Canvas) ：绘制View内容到Canvas画布上**


![image](http://o9m6aqy3r.bkt.clouddn.com//Window/View%E7%BB%98%E5%88%B6.png)

## 3. ViewRootImpl, View与WindowManager联系

View和WindowManager之间是怎么通过ViewRootImpl联系的呢？

**(1)View与WindowManager之间是怎么建立联系的 ?**

WindowManager所提供的功能很简单，常用的只有三个方法，即添加View，更新View和删除View，当然还有其它功能哈，比如改变Window的位置，WindowManager操作Window的过程更像是在操作Window中的View，这三个方法定义在ViewManager中，而WindowManager继承了ViewManager。

Window是一个抽象的概念，**每一个Window都对应着一个View和一个ViewRootImpl，Window又通过ViewRootImpl与View建立联系，因此Window并不是实际存在的，他是以View的形式存在的**。这点从WindowManager的定义也可以看出，它提供的三个接口方法addView，updateView，removeView都是针对View的，这说明View才是Window的实体。

**在实际使用中无法直接访问Window，对Window的访问必须通过WindowManager。而对Window(View)的访问(添加，更新，删除)都是通过ViewRootImpl实现的**.

**(2) View与ViewRootImpl的绑定 ?**

View和ViewRootImpl是怎么绑定在一起的呢？通过view.getViewRootImpl可以获取到ViewRootImpl。


```
public ViewRootImpl getViewRootImpl() {
        if (mAttachInfo != null) {
            return mAttachInfo.mViewRootImpl;
        }
        return null;
    }
```

而这**个AttachInfo则是View里面一个静态内部类**，它的构造方法:


```
    /**
     * Creates a new set of attachment information with the specified
     * events handler and thread.
     *
     * @param handler the events handler the view must use
     */
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

可以看到viewRootImpl在它的构造方法里赋值了

而这个方法肯定是在ViewRootImpl创建时创建的，即在ViewRootImpl的创建是在调用WindowManagerGlobal.addView的时候


```
  root = new ViewRootImpl(view.getContext(), display);
```

而构造方法中


```
public ViewRootImpl(Context context, Display display) {
        mContext = context;
        mWindowSession = WindowManagerGlobal.getWindowSession();
        ...
        mAttachInfo = new View.AttachInfo(mWindowSession, mWindow, display, this, mHandler, this);
        ...
    }

```

**AttachInfo则是View里面一个静态内部类**，所以，可以看到View与ViewRootImpl绑定一起了。
之后就可以通过view.getViewRootImpl获取到，而在Window里面也可以获取到ViewRootImpl，因为Window里面有DecorView（这里说的Window都是讲它的实现类PhoneWindo）

另外，**一个View会对应一个ViewRootImpl吗**？我们做个测试，在一个布局中打印两个不同控件的ViewRootImpl的内存地址：


```
  Log.e(TAG, "getViewRootImpl: textView： " + tv.getViewRootImpl() );
  Log.e(TAG, "getViewRootImpl: button： " + btn.getViewRootImpl() );
```

而结果：


```
  E/ MainActivity: getViewRootImpl: textView:  android. view. ViewRoot Imp1@1d7c8bb4
  E/ MainActivity: getViewRootImpl: button:  android. view. ViewRoot Impl@1d7c8bb4

```
可以看到，都是同一个对象，共用一个ViewRootImpl。

>**小结**
>- 之所以说ViewRoot是View和WindowManager的桥梁，是因为在真正操控绘制View的是ViewRootImpl，View通过WindowManager来转接调用ViewRootImpl
>- ViewRootImpl绘制View的时候会先检查当前线程是否是主线程，是才能继续绘制下去
>- 在ViewRootImpl未初始化创建的时候是可以进行子线程更新UI的，而它创建是在activity.handleResumeActivity方法调用，即DecorView被添加到WindowManager的时候

## 4. ViewRootImpl向DecorView分发事件

>**ViewRootImpl的功能可不只是绘制，它还有事件分发的功能**

这里的事件不仅仅包括MotionEvent，还有KeyEvent。我们知道View的时间分发顺序为
`Activity——>Window——>View`，那么Activity的事件来源在哪里呢？这是个需要思考的问题，答案和ViewRootImpl有关。

首先，事件的根本来源来自于Native层的嵌入式硬件，然后会经过InputEventReceiver接受事件，然后交给ViewRootImpl，将事件传递给DecorView，DecorView再交给PhoneWindow，PhoneWindow再交给Activity。这样看来，整个体系的事件分发顺序为：

![image](http://o9m6aqy3r.bkt.clouddn.com//Window/ViewRootImpl_dispatchKeyEvent.png)

>**过程详解：**

**(1) ViewRootImpl的dispatchInputEvent方法**

```
    public void dispatchInputEvent(InputEvent event, InputEventReceiver receiver) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = event;
        args.arg2 = receiver;
        Message msg = mHandler.obtainMessage(MSG_DISPATCH_INPUT_EVENT, args);
        msg.setAsynchronous(true);
        mHandler.sendMessage(msg);
    }

```
InputEvent输入事件，它有2个子类：KeyEvent和MotionEvent，其中KeyEvent表示键盘事件，而MotionEvent表示点击事件，这里InputEventReceiver译为输入事件接收者，顾名思义，就是用于接收输入事件，然后交给ViewRootImpl的dispatchInputEvent方法去分发处理。

可以看到mHandler将逻辑切换到UI线程，代码如下。


```
   final ViewRootHandler mHandler = new ViewRootHandler();
   
   final class ViewRootHandler extends Handler {
       
   }
```


```
            case MSG_DISPATCH_INPUT_EVENT: {
                SomeArgs args = (SomeArgs)msg.obj;
                InputEvent event = (InputEvent)args.arg1;
                InputEventReceiver receiver = (InputEventReceiver)args.arg2;
                enqueueInputEvent(event, receiver, 0, true);
                args.recycle();
```

在mHandler的UI线程中，最终调用了**enqueueInputEvent**方法，该方法就是将输入事件打包，利用InputEvent，InputEventReceiver构造对象QueueInputEvent，然后加入到待处理的事件队列中，代码如下：


```

    void enqueueInputEvent(InputEvent event,
            InputEventReceiver receiver, int flags, boolean processImmediately) {
        adjustInputEventForCompatibility(event);
        QueuedInputEvent q = obtainQueuedInputEvent(event, receiver, flags);

        // Always enqueue the input event in order, regardless of its time stamp.
        // We do this because the application or the IME may inject key events
        // in response to touch events and we want to ensure that the injected keys
        // are processed in the order they were received and we cannot trust that
        // the time stamp of injected events are monotonic.
        QueuedInputEvent last = mPendingInputEventTail;
        if (last == null) {
            mPendingInputEventHead = q;
            mPendingInputEventTail = q;
        } else {
            last.mNext = q;
            mPendingInputEventTail = q;
        }
        mPendingInputEventCount += 1;
        Trace.traceCounter(Trace.TRACE_TAG_INPUT, mPendingInputEventQueueLengthCounterName,
                mPendingInputEventCount);

        if (processImmediately) {
            doProcessInputEvents();
        } else {
            scheduleProcessInputEvents();
        }
    }
```

enqueueInputEvent方法又会调用doProcessInputEvents方法或者scheduleProcessInputEvents方法，这其实是同步或者异步处理消息队列的，同步或者异步根据传入的标志位processImmediately来判断。

scheduleProcessInputEvents方法只是利用mHandler向UI线程发送了一个message，代码如下：


```
    private void scheduleProcessInputEvents() {
        if (!mProcessInputEventsScheduled) {
            mProcessInputEventsScheduled = true;
            Message msg = mHandler.obtainMessage(MSG_PROCESS_INPUT_EVENTS);
            msg.setAsynchronous(true);
            mHandler.sendMessage(msg);
        }
    }

```

UI 线程Handler中处理如下：


```
        case MSG_PROCESS_INPUT_EVENTS:
                mProcessInputEventsScheduled = false;
                doProcessInputEvents();
                brea</span>

```

所以，使是调用了scheduleProcessInputEvents方法，最终还是会调用doProcessInputEvents方法，只是同步与异步的区别。

**doProcessInputEvents**的代码如下：


```
    void doProcessInputEvents() {
        // Deliver all pending input events in the queue.
        while (mPendingInputEventHead != null) {
            QueuedInputEvent q = mPendingInputEventHead;
            mPendingInputEventHead = q.mNext;
            if (mPendingInputEventHead == null) {
                mPendingInputEventTail = null;
            }
            q.mNext = null;
 
            mPendingInputEventCount -= 1;
            Trace.traceCounter(Trace.TRACE_TAG_INPUT, mPendingInputEventQueueLengthCounterName,
                    mPendingInputEventCount);
 
            deliverInputEvent(q);
        }
 
        // We are done processing all input events that we can process right now
        // so we can clear the pending flag immediately.
        if (mProcessInputEventsScheduled) {
            mProcessInputEventsScheduled = false;
            mHandler.removeMessages(MSG_PROCESS_INPUT_EVENTS);
        }
    }

```

代码中的注释已经写得很清楚了，就是逐个分发输入事件队列中的事件，分发一个便从队列中删除，其实就是单链表的操作，**分发过程用过deliverInputEvent方法，当队列中的输入事件都已经处理完，就立即清除标志位**。

deliverInputEvent方法如下：


```
    private void deliverInputEvent(QueuedInputEvent q) {
        Trace.asyncTraceBegin(Trace.TRACE_TAG_VIEW, "deliverInputEvent",
                q.mEvent.getSequenceNumber());
        if (mInputEventConsistencyVerifier != null) {
            mInputEventConsistencyVerifier.onInputEvent(q.mEvent, 0);
        }
 
        InputStage stage;
        if (q.shouldSendToSynthesizer()) {
            stage = mSyntheticInputStage;
        } else {
            stage = q.shouldSkipIme() ? mFirstPostImeInputStage : mFirstInputStage;
        }
 
        if (stage != null) {
            stage.deliver(q);
        } else {
            finishInputEvent(q);
        }
    }

```

在ViewRootImpl中，有一系列类似于InputStage（输入事件舞台）的概念，他是一个抽象类，它的deliver方法会处理一个输入事件。处理完成之后会调用finishInputEvent方法。


它有很多子类，对应具体的InputStage，每种InputStage可以处理一定的事件类型，比如AsyncInputStage、SyntheticInputStage、NativePostImeInputStage、ViewPreImeInputStage、ViewPostImeInputStage等，它的子类实现了InputStage的一些抽象方法，比如onProcess、onDeliverToNext、processKeyEvent、processPointerEvent、processTrackballEvent、processGenericMotionEvent，从这些方法大概可以看出意思，在不同的情况下，onProcess、onDeliverToNext方法就会被回调。


当一个InputEvent到来时，ViewRootImpl会寻找合适它的InputStage来处理。对于点击事件来说，ViewPostImeInputStage可以处理它，ViewPostImeInputStage中，ViewPostImeInputStage类中的onProcess方法如下。当onProcess被回调时，processKeyEvent、processPointerEvent、processTrackballEvent、processGenericMotionEvent至少有一个方法就会被调用，这些方法都是属于**ViewPostImeInputStage**的。


```
    /**
     * Delivers post-ime input events to the view hierarchy.
     */
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

        @Override
        protected void onDeliverToNext(QueuedInputEvent q) {
            if (mUnbufferedInputDispatch
                    && q.mEvent instanceof MotionEvent
                    && ((MotionEvent)q.mEvent).isTouchEvent()
                    && isTerminalInputEvent(q.mEvent)) {
                mUnbufferedInputDispatch = false;
                scheduleConsumeBatchedInput();
            }
            super.onDeliverToNext(q);
        }

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

            if (shouldDropInputEvent(q)) {
                return FINISH_NOT_HANDLED;
            }

            // If the Control modifier is held, try to interpret the key as a shortcut.
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && event.isCtrlPressed()
                    && event.getRepeatCount() == 0
                    && !KeyEvent.isModifierKey(event.getKeyCode())) {
                if (mView.dispatchKeyShortcutEvent(event)) {
                    return FINISH_HANDLED;
                }
                if (shouldDropInputEvent(q)) {
                    return FINISH_NOT_HANDLED;
                }
            }

            // Apply the fallback event policy.
            if (mFallbackEventHandler.dispatchKeyEvent(event)) {
                return FINISH_HANDLED;
            }
            if (shouldDropInputEvent(q)) {
                return FINISH_NOT_HANDLED;
            }

            // Handle automatic focus changes.
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int direction = 0;
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (event.hasNoModifiers()) {
                            direction = View.FOCUS_LEFT;
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (event.hasNoModifiers()) {
                            direction = View.FOCUS_RIGHT;
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (event.hasNoModifiers()) {
                            direction = View.FOCUS_UP;
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (event.hasNoModifiers()) {
                            direction = View.FOCUS_DOWN;
                        }
                        break;
                    case KeyEvent.KEYCODE_TAB:
                        if (event.hasNoModifiers()) {
                            direction = View.FOCUS_FORWARD;
                        } else if (event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
                            direction = View.FOCUS_BACKWARD;
                        }
                        break;
                }
                if (direction != 0) {
                    View focused = mView.findFocus();
                    if (focused != null) {
                        View v = focused.focusSearch(direction);
                        if (v != null && v != focused) {
                            // do the math the get the interesting rect
                            // of previous focused into the coord system of
                            // newly focused view
                            focused.getFocusedRect(mTempRect);
                            if (mView instanceof ViewGroup) {
                                ((ViewGroup) mView).offsetDescendantRectToMyCoords(
                                        focused, mTempRect);
                                ((ViewGroup) mView).offsetRectIntoDescendantCoords(
                                        v, mTempRect);
                            }
                            if (v.requestFocus(direction, mTempRect)) {
                                playSoundEffect(SoundEffectConstants
                                        .getContantForFocusDirection(direction));
                                return FINISH_HANDLED;
                            }
                        }

                        // Give the focused view a last chance to handle the dpad key.
                        if (mView.dispatchUnhandledMove(focused, direction)) {
                            return FINISH_HANDLED;
                        }
                    } else {
                        // find the best view to give focus to in this non-touch-mode with no-focus
                        View v = focusSearch(null, direction);
                        if (v != null && v.requestFocus(direction)) {
                            return FINISH_HANDLED;
                        }
                    }
                }
            }
            return FORWARD;
        }

        private int processPointerEvent(QueuedInputEvent q) {
            final MotionEvent event = (MotionEvent)q.mEvent;

            mAttachInfo.mUnbufferedDispatchRequested = false;
            boolean handled = mView.dispatchPointerEvent(event);
            if (mAttachInfo.mUnbufferedDispatchRequested && !mUnbufferedInputDispatch) {
                mUnbufferedInputDispatch = true;
                if (mConsumeBatchedInputScheduled) {
                    scheduleConsumeBatchedInputImmediately();
                }
            }
            return handled ? FINISH_HANDLED : FORWARD;
        }

        private int processTrackballEvent(QueuedInputEvent q) {
            final MotionEvent event = (MotionEvent)q.mEvent;

            if (mView.dispatchTrackballEvent(event)) {
                return FINISH_HANDLED;
            }
            return FORWARD;
        }

        private int processGenericMotionEvent(QueuedInputEvent q) {
            final MotionEvent event = (MotionEvent)q.mEvent;

            // Deliver the event to the view.
            if (mView.dispatchGenericMotionEvent(event)) {
                return FINISH_HANDLED;
            }
            return FORWARD;
        }
    }
```

在processKeyEvent、processPointerEvent、processTrackballEvent、processGenericMotionEvent方法中都有一句很关键的一句代码;


```
mView.dispatchKeyEvent(event)//按键事件
```


```
mView.dispatchPointerEvent(event)
```


```
mView.dispatchTrackballEvent(event)
```


```
mView.dispatchGenericMotionEvent(event)
```

补充！！！其中mView对象是在ViewRootImpl的setView方法中：

```
 public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
        synchronized (this) {
            if (mView == null) {
                mView = view;
		.............................
 
 
		}
	}
}

```

可以看出，mView的实例化是在setView方法中完成，而我们知道ViewRootImpl的setView方法中传入的view参数是DecorView，因为ViewRootImpl通过setView方法将DecorView添加到PhoneWindow的。


**所以这里的mView其实就是DecorView。**

这样一来，可以知道ViewPostImeInputStage将事件分发到了View，而这里的mView又是DecorView，也就是多态的原理，如果DecorView没有上述的mView.的几个方法，就会调用View的方法，如果DecorView实现了就会调用DecorView的方法。

下面再看DecorView的dispatchTouchEvent方法。


```
@Override
public boolean dispatchTouchEvent(MotionEvent ev) {
    final Callback cb = getCallback();
    return cb != null && !isDestroyed() && mFeatureId < 0 ? cb.dispatchTouchEvent(ev)
            : super.dispatchTouchEvent(ev);
}
```

可以看出DecorView最终会调用cb.dispatchTouchEvent方法，那么问题问题又来了，这个Callback是什么，其实这个Callback就是当前的Activity。

首先Activity继承了Window.Callback


```
public class Activity extends ContextThemeWrapper
        implements LayoutInflater.Factory2,
        Window.Callback, KeyEvent.Callback,
        OnCreateContextMenuListener, ComponentCallbacks2,
        Window.OnWindowDismissedCallback {
```

然后Window.Callback的代码如下：


```
public interface Callback {
    /**
     * Called when action mode is first created. The menu supplied will be used to
     * generate action buttons for the action mode.
     *
     * @param mode ActionMode being created
     * @param menu Menu used to populate action buttons
     * @return true if the action mode should be created, false if entering this
     *              mode should be aborted.
     */
    public boolean onCreateActionMode(ActionMode mode, Menu menu);

    /**
     * Called to refresh an action mode's action menu whenever it is invalidated.
     *
     * @param mode ActionMode being prepared
     * @param menu Menu used to populate action buttons
     * @return true if the menu or action mode was updated, false otherwise.
     */
    public boolean onPrepareActionMode(ActionMode mode, Menu menu);

    /**
     * Called to report a user click on an action button.
     *
     * @param mode The current ActionMode
     * @param item The item that was clicked
     * @return true if this callback handled the event, false if the standard MenuItem
     *          invocation should continue.
     */
    public boolean onActionItemClicked(ActionMode mode, MenuItem item);

    /**
     * Called when an action mode is about to be exited and destroyed.
     *
     * @param mode The current ActionMode being destroyed
     */
    public void onDestroyActionMode(ActionMode mode);
}
```
Activity必须要实现Window.Callback当中的方法，基本都是事件传递相关的，其中就是dispatchTouchEvent方法，在Activity的 attach方法中有如下一段代码：


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

```


**PhoneWindow通过设置setCallback将Callback设置为this也就是Activity**。

**至此点击事件已经到了Activity，之后的事件分发就属于我们经常讨论的分发机制了**。



## 5. 补充扩展

**(1) onCreate方法中调用view.getMeasureHeight() = 0？**

我们知道activity.handleResumeActivity最后调用到的是activity的onResume方法，但是按上面所说在onResume方法中调用就可以得到了吗，答案肯定是否定的，因为ViewRootImpl绘制View并非是同步的，而是异步（Handler）。

难道就没有得监听了吗？相信大家以前获取使用的大多是：


```
view.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
    @Override
    public void onGlobalLayout() {
    // TODO Auto-generated method stub
             
    }
});
```

没错，的确是这个，为什么呢，因为在viewRootImpl的performTraversals的绘制最后，调用了
```
{
        if (triggerGlobalLayoutListener) {
            mAttachInfo.mRecomputeGlobalAttributes = false;
            mAttachInfo.mTreeObserver.dispatchOnGlobalLayout();
        }
        ...
        performDraw();
}
```
dispatchOnGlobalLayout会触发OnGlobalLayoutListener的onGlobalLayout()函数回调
但此时View并还没有绘制显示出来，只是先调用了measure和layout，但也可以得到它的宽高了。

**(2) ViewRootImpl在调用requestLayout准备绘制View的时候会先判断线程**


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
真的这样吗？
先看Activity下这段代码：


```
@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv = (TextView) findViewById(R.id.tv);
        new Thread(new Runnable() {
            @Override
            public void run() {
                tv.setText("Hohohong Test");
            }
        }).start();
    }
```
在onCreate里面的子线程去更新UI的，那么会报错吗？

测试后你就会知道不会报错，如果你放置个Button点击再去调用的话则会弹出报错。

为什么会这样？<br>
答案就是跟ViewRootImpl的初始化有关，因为**在onCreate的时候此时View还没被绘制出来，ViewRootImpl还未创建出来，它的创建是在activity.handleResumeActivity的调用到windowManager.addView(decorView)时候，如前面说的ViewRootImpl才被创建起来**

```
public void addView(View view, ViewGroup.LayoutParams params,
            Display display, Window parentWindow) {
        ...
        ViewRootImpl root;
        ...
           
        root = new ViewRootImpl(view.getContext(), display);
        view.setLayoutParams(wparams);
        mViews.add(view);
        //ViewRootImpl保存在一个集合List中
        mRoots.add(root);
        mParams.add(wparams);
        //ViewRootImpl开始绘制view
        root.setView(view, wparams, panelParentView);
        ...
    }

```

**此时创建完才会去判断线程!!!**

**所以：在ViewRootImpl未初始化创建的时候是可以进行子线程更新UI的，而它创建是在activity.handleResumeActivity方法调用，即DecorView被添加到WindowManager的时候。**