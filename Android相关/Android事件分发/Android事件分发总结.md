# TouchEventDispatchDemo
# 通过源码和实际验证，了解Android事件分发机制及原理并总结 #


# 1.源码分析ACTION_DOWN事件的分发 #

Android中的事件分发在Activity,ViewGroup,View之间进行分发传递。

<strong>并且事件传递的顺序：Activity -> ViewGroup -> View</strong>

Android的事件分发机制中，touch事件从Activity是如何分发到其界面中的各个View/ViewGroup中的呢？
带着这个问题，我们先去源码中找一波答案。

**1.1首先是Activity中相关调用：**

   Activity.dispatchTouchEvent（）

    /**
     * 源码分析：Activity.dispatchTouchEvent（）
     */
    public boolean dispatchTouchEvent(MotionEvent ev) {

        // 一般事件列开始都是DOWN事件 = 按下事件，故此处基本是true
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {

            onUserInteraction();
            // ->>分析1

        }

        // ->>分析2
        if (getWindow().superDispatchTouchEvent(ev)) {

            return true;
            // 若getWindow().superDispatchTouchEvent(ev)的返回true
            // 则Activity.dispatchTouchEvent（）就返回true，则方法结束。即 ：该点击事件停止往下传递 & 事件传递过程结束
            // 否则：继续往下调用Activity.onTouchEvent

        }
        // ->>分析4
        return onTouchEvent(ev);
    }


    /**
     * 分析1：onUserInteraction()
     * 作用：实现屏保功能
     * 注：
     * a. 该方法为空方法
     * b. 当此activity在栈顶时，触屏点击按home，back，menu键等都会触发此方法
     */
    public void onUserInteraction() {

    }
    // 回到最初的调用原处

    /**
     * 分析2：getWindow().superDispatchTouchEvent(ev)
     * 说明：
     * a. getWindow() = 获取Window类的对象
     * b. Window类是抽象类，其唯一实现类 = PhoneWindow类；即此处的Window类对象 = PhoneWindow类对象
     * c. Window类的superDispatchTouchEvent() = 1个抽象方法，由子类PhoneWindow类实现
     */
    @Override
    public boolean superDispatchTouchEvent(MotionEvent event) {

        return mDecor.superDispatchTouchEvent(event);
        // mDecor = 顶层View（DecorView）的实例对象
        // ->> 分析3
    }

    /**
     * 分析3：mDecor.superDispatchTouchEvent(event)
     * 定义：属于顶层View（DecorView）
     * 说明：
     * a. DecorView类是PhoneWindow类的一个内部类
     * b. DecorView继承自FrameLayout，是所有界面的父类
     * c. FrameLayout是ViewGroup的子类，故DecorView的间接父类 = ViewGroup
     */
    public boolean superDispatchTouchEvent(MotionEvent event) {

        return super.dispatchTouchEvent(event);
        // 调用父类的方法 = ViewGroup的dispatchTouchEvent()
        // 即 将事件传递到ViewGroup去处理，详细请看ViewGroup的事件分发机制

    }
    // 回到最初的调用原处

    /**
     * 分析4：Activity.onTouchEvent（）
     * 定义：属于顶层View（DecorView）
     * 说明：
     * a. DecorView类是PhoneWindow类的一个内部类
     * b. DecorView继承自FrameLayout，是所有界面的父类
     * c. FrameLayout是ViewGroup的子类，故DecorView的间接父类 = ViewGroup
     */
    public boolean onTouchEvent(MotionEvent event) {

        // 当一个点击事件未被Activity下任何一个View接收 / 处理时
        // 应用场景：处理发生在Window边界外的触摸事件
        // ->> 分析5
        if (mWindow.shouldCloseOnTouch(this, event)) {
            finish();
            return true;
        }

        return false;
        // 即 只有在点击事件在Window边界外才会返回true，一般情况都返回false，分析完毕
    }

    /**
     * 分析5：mWindow.shouldCloseOnTouch(this, event)
     */
    public boolean shouldCloseOnTouch(Context context, MotionEvent event) {
        // 主要是对于处理边界外点击事件的判断：是否是DOWN事件，event的坐标是否在边界内等
        if (mCloseOnTouchOutside && event.getAction() == MotionEvent.ACTION_DOWN
                && isOutOfBounds(context, event) && peekDecorView() != null) {
            return true;
        }
        return false;
        // 返回true：说明事件在边界外，即 消费事件
        // 返回false：未消费（默认）
    }
    // 回到分析4调用原处`

通过代码可以发现，通过调用getWindow().dispatchTouchEvent()即将取得PhoneWindow实例，并调用其中的mDecoredView的dispatchTouEvent方法，而DecorView继承自FrameLayout，是所有界面的父类，FrameLayout又是ViewGroup的子类，故DecorView的间接父类 = ViewGroup。至此，Activity中touch事件，就传到了界面根布局的 ViewGroup中。即：

![](http://o9m6aqy3r.bkt.clouddn.com/%E4%BA%8B%E4%BB%B6%E5%88%86%E5%8F%91_Activity%E6%BA%90%E7%A0%81%E5%9B%BE%E8%A7%A3.png)


**1.2 ViewGroup中相关调用：**

Activity中的事件分发到布局根ViewGroup后，ViewGroup又是怎么将这个事件分发到其中的子View以及子ViewGroup中呢？

    /**
     * 源码分析：ViewGroup.dispatchTouchEvent（）
     */
    public boolean dispatchTouchEvent(MotionEvent ev) {

		... // 仅贴出关键代码

        // 重点分析1：ViewGroup每次事件分发时，都需调用onInterceptTouchEvent()询问是否拦截事件
        if (disallowIntercept || !onInterceptTouchEvent(ev)) {

            // 判断值1：disallowIntercept = 是否禁用事件拦截的功能(默认是false)，可通过调用requestDisallowInterceptTouchEvent（）修改
            // 判断值2： !onInterceptTouchEvent(ev) = 对onInterceptTouchEvent()返回值取反
            // a. 若在onInterceptTouchEvent()中返回false（即不拦截事件），就会让第二个值为true，从而进入到条件判断的内部
            // b. 若在onInterceptTouchEvent()中返回true（即拦截事件），就会让第二个值为false，从而跳出了这个条件判断
            // c. 关于onInterceptTouchEvent() ->>分析1

            ev.setAction(MotionEvent.ACTION_DOWN);
            final int scrolledXInt = (int) scrolledXFloat;
            final int scrolledYInt = (int) scrolledYFloat;
            final View[] children = mChildren;
            final int count = mChildrenCount;

            // 重点分析2
            // 通过for循环，遍历了当前ViewGroup下的所有子View
            for (int i = count - 1; i >= 0; i--) {
                final View child = children[i];
                if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE
                        || child.getAnimation() != null) {
                    child.getHitRect(frame);

                    // 判断当前遍历的View是不是正在点击的View，从而找到当前被点击的View
                    // 若是，则进入条件判断内部
                    if (frame.contains(scrolledXInt, scrolledYInt)) {
                        final float xc = scrolledXFloat - child.mLeft;
                        final float yc = scrolledYFloat - child.mTop;
                        ev.setLocation(xc, yc);
                        child.mPrivateFlags &= ~CANCEL_NEXT_UP_EVENT;

                        // 条件判断的内部调用了该View的dispatchTouchEvent()
                        // 即 实现了点击事件从ViewGroup到子View的传递（具体请看下面的View事件分发机制）
                        if (child.dispatchTouchEvent(ev)) {

                            mMotionTarget = child;
                            return true;
                            // 调用子View的dispatchTouchEvent后是有返回值的
                            // 若该控件可点击，那么点击时，dispatchTouchEvent的返回值必定是true，因此会导致条件判断成立
                            // 于是给ViewGroup的dispatchTouchEvent（）直接返回了true，即直接跳出
                            // 即把ViewGroup的点击事件拦截掉

                        }
                    }
                }
            }
        }


        boolean isUpOrCancel = (action == MotionEvent.ACTION_UP) ||
                (action == MotionEvent.ACTION_CANCEL);
        if (isUpOrCancel)

        {
            mGroupFlags &= ~FLAG_DISALLOW_INTERCEPT;
        }

        final View target = mMotionTarget;

        // 重点分析3
        // 若点击的是空白处（即无任何View接收事件） / 拦截事件（手动复写onInterceptTouchEvent（），从而让其返回true）
        if (target == null)

        {
            ev.setLocation(xf, yf);
            if ((mPrivateFlags & CANCEL_NEXT_UP_EVENT) != 0) {
                ev.setAction(MotionEvent.ACTION_CANCEL);
                mPrivateFlags &= ~CANCEL_NEXT_UP_EVENT;
            }

            return super.dispatchTouchEvent(ev);
            // 调用ViewGroup父类的dispatchTouchEvent()，即View.dispatchTouchEvent()
            // 因此会执行ViewGroup的onTouch() ->> onTouchEvent() ->> performClick（） ->> onClick()，即自己处理该事件，事件不会往下传递（具体请参考View事件的分发机制中的View.dispatchTouchEvent（））
            // 此处需与上面区别：子View的dispatchTouchEvent（）
        }

        ...

    }

    /**
     * 分析1：ViewGroup.onInterceptTouchEvent()
     * 作用：是否拦截事件
     * 说明：
     * a. 返回true = 拦截，即事件停止往下传递（需手动设置，即复写onInterceptTouchEvent（），从而让其返回true）
     * b. 返回false = 不拦截（默认）
     */
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        return false;

    }

在ViewGroup中每次dispatchTouchEvent时都会调用onInterceptTouchEvent来询问当前事件是否需要拦截，如果拦截或者其子View没有消费这个事件，就会走到ViewGroup的super.dispatchTouchEvent(ev);中，因此会执行ViewGroup的onTouch() ->> onTouchEvent() ->> performClick（） ->> onClick()即自己处理该事件，事件不会往下传递。即：

![](http://o9m6aqy3r.bkt.clouddn.com/%E4%BA%8B%E4%BB%B6%E5%88%86%E5%8F%91_ViewGroup%E6%BA%90%E7%A0%81%E5%9B%BE%E8%A7%A3.png)

**1.3  View中相关调用：**

ViewGroup的分发中，如果不被拦截(onInterceptTouchEvent 返回 true)，则会将touch事件进一步在每一个子View中dispatchTouchEvent。随即调用了View中的dispatchTouchEvent方法，那么View中又是如何让处理的呢？

    /**
     * 源码分析：View.dispatchTouchEvent（）
     */
    public boolean dispatchTouchEvent(MotionEvent event) {

        if (mOnTouchListener != null && (mViewFlags & ENABLED_MASK) == ENABLED &&
                mOnTouchListener.onTouch(this, event)) {
            return true;
        }
        return onTouchEvent(event);
    }
    // 说明：只有以下3个条件都为真，dispatchTouchEvent()才返回true；否则执行onTouchEvent()
    //     1. mOnTouchListener != null
    //     2. (mViewFlags & ENABLED_MASK) == ENABLED
    //     3. mOnTouchListener.onTouch(this, event)
    // 下面对这3个条件逐个分析
    
    /**
     * 条件1：mOnTouchListener != null
     * 说明：mOnTouchListener变量在View.setOnTouchListener（）方法里赋值
     */
    public void setOnTouchListener(OnTouchListener l) {
        mOnTouchListener = l;
        // 即只要我们给控件注册了Touch事件，mOnTouchListener就一定被赋值（不为空）

    }
    /**
     * 条件2：(mViewFlags & ENABLED_MASK) == ENABLED
     * 说明：
     *     a. 该条件是判断当前点击的控件是否enable
     *     b. 由于很多View默认enable，故该条件恒定为true
     */
    
    /**
     * 条件3：mOnTouchListener.onTouch(this, event)
     * 说明：即 回调控件注册Touch事件时的onTouch（）；需手动复写设置，具体如下（以按钮Button为例）
     */
    button.setOnTouchListener(new OnTouchListener() {
        @Override
        public boolean onTouch (View v, MotionEvent event){
            return false;
        }
    });
    // 若在onTouch（）返回true，就会让上述三个条件全部成立，从而使得View.dispatchTouchEvent（）直接返回true，事件分发结束
    // 若在onTouch（）返回false，就会使得上述三个条件不全部成立，从而使得View.dispatchTouchEvent（）中跳出If，执行onTouchEvent(event)

	
	/**
	  * 源码分析：View.onTouchEvent（）
	  */
    public boolean onTouchEvent(MotionEvent event) {
        final int viewFlags = mViewFlags;

        if ((viewFlags & ENABLED_MASK) == DISABLED) {

            return (((viewFlags & CLICKABLE) == CLICKABLE ||
                    (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE));
        }
        if (mTouchDelegate != null) {
            if (mTouchDelegate.onTouchEvent(event)) {
                return true;
            }
        }

        // 若该控件可点击，则进入switch判断中
        if (((viewFlags & CLICKABLE) == CLICKABLE ||
                (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE)) {

            switch (event.getAction()) {

                // a. 若当前的事件 = 抬起View（主要分析）
                case MotionEvent.ACTION_UP:
                    boolean prepressed = (mPrivateFlags & PREPRESSED) != 0;  

                            ...// 经过种种判断，此处省略

                    // 执行performClick() ->>分析1
                    performClick();
                    break;

                // b. 若当前的事件 = 按下View
                case MotionEvent.ACTION_DOWN:
                    if (mPendingCheckForTap == null) {
                        mPendingCheckForTap = new CheckForTap();
                    }
                    mPrivateFlags |= PREPRESSED;
                    mHasPerformedLongPress = false;
                    postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                    break;

                // c. 若当前的事件 = 结束事件（非人为原因）
                case MotionEvent.ACTION_CANCEL:
                    mPrivateFlags &= ~PRESSED;
                    refreshDrawableState();
                    removeTapCallback();
                    break;

                // d. 若当前的事件 = 滑动View
                case MotionEvent.ACTION_MOVE:
                    final int x = (int) event.getX();
                    final int y = (int) event.getY();

                    int slop = mTouchSlop;
                    if ((x < 0 - slop) || (x >= getWidth() + slop) ||
                            (y < 0 - slop) || (y >= getHeight() + slop)) {
                        // Outside button  
                        removeTapCallback();
                        if ((mPrivateFlags & PRESSED) != 0) {
                            // Remove any future long press/tap checks  
                            removeLongPressCallback();
                            // Need to switch from pressed to not pressed  
                            mPrivateFlags &= ~PRESSED;
                            refreshDrawableState();
                        }
                    }
                    break;
            }
            // 若该控件可点击，就一定返回true
            return true;
        }
        // 若该控件不可点击，就一定返回false
        return false;
    }

	/**
	  * 分析1：performClick（）
	  */  
    public boolean performClick() {  

        if (mOnClickListener != null) {  
            playSoundEffect(SoundEffectConstants.CLICK);  
            mOnClickListener.onClick(this);  
            return true;  
            // 只要我们通过setOnClickListener（）为控件View注册1个点击事件
            // 那么就会给mOnClickListener变量赋值（即不为空）
            // 则会往下回调onClick（） & performClick（）返回true
        }  
        return false;  
    }

我们发现，在每当控件被点击时：

![](http://o9m6aqy3r.bkt.clouddn.com/%E4%BA%8B%E4%BB%B6%E5%88%86%E5%8F%91_View%E6%BA%90%E7%A0%81%E5%9B%BE%E8%A7%A3.png)

><font color = "#f00"> <strong>注：执行优先级 onTouch（）>> onTouchEvent() >>  onClick（）</strong></font>

事件分发机制很重要的三个方法，点击事件的分发机制都是根据这三个方法共同完成的：**dispatchTouchEvent()、onInterceptTouchEvent()和onTouchEvent()。**

**dispatchTouchEvent()**：用来进行事件的分发，如果MotionEvent（点击事件）能够传递给该View，那么该方法一定会被调用。返回值由 本身的onTouchEvent() 和 子View的dispatchTouchEvent()的返回值 共同决定。
返回值为true，则表示该点击事件被本身或者子View消耗。返回值为false，则表示该ViewGroup没有子元素，或者子元素没有消耗该事件。

**onInterceptTouchEvent()**：在dispatchTouchEvent()中调用，用来判断是否拦截某个事件，如果当前View拦截了某个事件，那么在同一个事件序列中不会再访问该方法。

**onTouchEvent()**：在dispatchTouchEvent()中调用，返回结果表示是否消耗当前事件，如果不消耗（返回false），则在同一个事件序列中View不会再次接收到事件。

这三个方法的关系可以用以下伪代码表示：

    public boolean dispatchTouchEvent(MotionEvent ev) {
	boolean consume = false;
	if (onInterceptTouchEvent(ev)) {
		consume = onTouchEvent(ev);
	} else {
		consume = child.dispatchTouchEvent(ev);
	}
	
	return consume;
}


# 2.ACTION_DOWN事件分发图解与总结 #


**经过大概的整理和源码分析，我们大概可以得到一个这样的关系图：**

![](http://o9m6aqy3r.bkt.clouddn.com/Action_Down%E4%BA%8B%E4%BB%B6%E5%88%86%E5%8F%91%E5%9B%BE%E8%A7%A3.png)

**1.如果我们没有对控件里面的方法进行重写或更改返回值，而<font color = "#12f">直接用super调用父类的默认实现</font>，那么整个事件流向应该是从Activity---->ViewGroup--->View 从上往下调用dispatchTouchEvent方法，一直到叶子节点（View）的时候，再由View--->ViewGroup--->Activity从下往上调用onTouchEvent方法。**

**2、<font color = "#12f">dispatchTouchEvent 和 onTouchEvent 一旦return true,事件就停止传递了（到达终点）（没有谁能再收到这个事件）</font>。看下图中只要return true事件就没再继续传下去了，对于return true我们经常说事件被消费了，消费了的意思就是事件走到这里就是终点，不会往下传，没有谁能再收到这个事件了**

**3.dispatchTouchEvent 和 onTouchEvent <font color = "#12f">return false的时候事件都回传给父控件的onTouchEvent处理</font>。**

**4、dispatchTouchEvent、onTouchEvent、onInterceptTouchEvent
ViewGroup 和View的这些方法的默认实现就是会让整个事件安装U型完整走完，所以 return super.xxxxxx() 就会让事件依照U型的方向的完整走完整个事件流动路径），中间不做任何改动，不回溯、不终止，每个环节都走到**。

![](http://o9m6aqy3r.bkt.clouddn.com/%E5%AE%8C%E6%95%B4%E7%9A%84%E9%BB%98%E8%AE%A4%E5%A4%84%E7%90%86%E4%B8%8B%E7%9A%84%E4%BA%8B%E4%BB%B6%E5%88%86%E5%8F%91U%E5%BD%A2%E5%9B%BE.png)


# 3.关于ACTION_MOVE与ACTION_UP事件的分发 #


**3.1 假设在如图的界面中，黄色背景为MyGroupView，子View为MyView.**

![](http://o9m6aqy3r.bkt.clouddn.com/%E7%95%8C%E9%9D%A2.png)

**在MyGroupView中onTouchEvent()中return true；消费了这个ACTION_DOWN,那么接下来整个得到如下的流程：**

![](http://o9m6aqy3r.bkt.clouddn.com/ACTION_MOVE,ACTION_UP%E4%BA%8B%E4%BB%B6%E4%BC%A0%E9%80%92.jpg)

对于ACTION_MOVE、ACTION_UP总结：

**ACTION_DOWN事件在哪个控件消费了（return true）， 那么ACTION_MOVE和ACTION_UP就会从上往下（通过dispatchTouchEvent）做事件分发往下传，就只会传到这个控件，不会继续往下传，如果ACTION_DOWN事件是在dispatchTouchEvent消费，那么事件到此为止停止传递，如果ACTION_DOWN事件是在onTouchEvent消费的，那么会把ACTION_MOVE或ACTION_UP事件传给该控件的onTouchEvent处理并结束传递。**

**如果在某个控件的dispatchTouchEvent 返回true消费终结事件，那么收到ACTION_DOWN 的函数也能收到 ACTION_MOVE和ACTION_UP**

3.2 拦截后续事件(MOVE)的表现

在如下的界面中：

![](http://o9m6aqy3r.bkt.clouddn.com/%E6%8B%A6%E6%88%AA%E5%90%8E%E7%BB%AD%E4%BA%8B%E4%BB%B6%E7%95%8C%E9%9D%A2.png)

B 不拦截 down事件，但是拦截了move事件后。

若 ViewGroup 拦截了一个半路的事件（如MOVE），该事件将会被系统变成一个CANCEL事件 & 传递给之前处理该事件的子View；

该事件不会再传递给ViewGroup 的onTouchEvent()

只有再到来的事件才会传递到ViewGroup的onTouchEvent()

场景描述

**ViewGroup B 无拦截DOWN事件（还是View C来处理DOWN事件），但它拦截了接下来的MOVE事件**

>**即 DOWN事件传递到View C的onTouchEvent（），返回了true**

**实例讲解**

>**在后续到来的MOVE事件，ViewGroup B 的onInterceptTouchEvent（）返回true拦截该MOVE事件，但该事件并没有传递给ViewGroup B ；这个MOVE事件将会被系统变成一个CANCEL事件传递给View C的onTouchEvent（）
后续又来了一个MOVE事件，该MOVE事件才会直接传递给ViewGroup B 的onTouchEvent()
后续事件将直接传递给ViewGroup B 的onTouchEvent()处理，而不会再传递给ViewGroup B 的onInterceptTouchEvent（），因该方法一旦返回一次true，就再也不会被调用了。**

**View C再也不会收到该事件列产生的后续事件**

><font color = "#f00"> <strong>onInterceptTouchEvent（），因该方法一旦返回一次true，就再也不会被调用了</strong></font>

><font color = "#f00"> <strong>onInterceptTouchEvent（），因该方法一旦返回一次true，就再也不会被调用了</strong></font>

><font color = "#f00"> <strong>onInterceptTouchEvent（），因该方法一旦返回一次true，就再也不会被调用了</strong></font>



---
>参考：
><br><font color = "#1DACD6">**https://www.jianshu.com/p/38015afcdb58 <br>
>https://www.jianshu.com/p/e99b5e8bd67b**</font>