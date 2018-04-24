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
    // 回到调用原处
