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
    // 回到分析4调用原处