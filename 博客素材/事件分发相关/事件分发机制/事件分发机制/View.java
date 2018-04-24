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
