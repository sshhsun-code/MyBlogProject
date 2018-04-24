    /**
     * Դ�������Activity.dispatchTouchEvent����
     */
    public boolean dispatchTouchEvent(MotionEvent ev) {

        // һ���¼��п�ʼ����DOWN�¼� = �����¼����ʴ˴�������true
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {

            onUserInteraction();
            // ->>����1

        }

        // ->>����2
        if (getWindow().superDispatchTouchEvent(ev)) {

            return true;
            // ��getWindow().superDispatchTouchEvent(ev)�ķ���true
            // ��Activity.dispatchTouchEvent�����ͷ���true���򷽷��������� ���õ���¼�ֹͣ���´��� & �¼����ݹ��̽���
            // ���򣺼������µ���Activity.onTouchEvent

        }
        // ->>����4
        return onTouchEvent(ev);
    }


    /**
     * ����1��onUserInteraction()
     * ���ã�ʵ����������
     * ע��
     * a. �÷���Ϊ�շ���
     * b. ����activity��ջ��ʱ�����������home��back��menu���ȶ��ᴥ���˷���
     */
    public void onUserInteraction() {

    }
    // �ص�����ĵ���ԭ��

    /**
     * ����2��getWindow().superDispatchTouchEvent(ev)
     * ˵����
     * a. getWindow() = ��ȡWindow��Ķ���
     * b. Window���ǳ����࣬��Ψһʵ���� = PhoneWindow�ࣻ���˴���Window����� = PhoneWindow�����
     * c. Window���superDispatchTouchEvent() = 1�����󷽷���������PhoneWindow��ʵ��
     */
    @Override
    public boolean superDispatchTouchEvent(MotionEvent event) {

        return mDecor.superDispatchTouchEvent(event);
        // mDecor = ����View��DecorView����ʵ������
        // ->> ����3
    }

    /**
     * ����3��mDecor.superDispatchTouchEvent(event)
     * ���壺���ڶ���View��DecorView��
     * ˵����
     * a. DecorView����PhoneWindow���һ���ڲ���
     * b. DecorView�̳���FrameLayout�������н���ĸ���
     * c. FrameLayout��ViewGroup�����࣬��DecorView�ļ�Ӹ��� = ViewGroup
     */
    public boolean superDispatchTouchEvent(MotionEvent event) {

        return super.dispatchTouchEvent(event);
        // ���ø���ķ��� = ViewGroup��dispatchTouchEvent()
        // �� ���¼����ݵ�ViewGroupȥ������ϸ�뿴ViewGroup���¼��ַ�����

    }
    // �ص�����ĵ���ԭ��

    /**
     * ����4��Activity.onTouchEvent����
     * ���壺���ڶ���View��DecorView��
     * ˵����
     * a. DecorView����PhoneWindow���һ���ڲ���
     * b. DecorView�̳���FrameLayout�������н���ĸ���
     * c. FrameLayout��ViewGroup�����࣬��DecorView�ļ�Ӹ��� = ViewGroup
     */
    public boolean onTouchEvent(MotionEvent event) {

        // ��һ������¼�δ��Activity���κ�һ��View���� / ����ʱ
        // Ӧ�ó�������������Window�߽���Ĵ����¼�
        // ->> ����5
        if (mWindow.shouldCloseOnTouch(this, event)) {
            finish();
            return true;
        }

        return false;
        // �� ֻ���ڵ���¼���Window�߽���Ż᷵��true��һ�����������false���������
    }

    /**
     * ����5��mWindow.shouldCloseOnTouch(this, event)
     */
    public boolean shouldCloseOnTouch(Context context, MotionEvent event) {
        // ��Ҫ�Ƕ��ڴ���߽������¼����жϣ��Ƿ���DOWN�¼���event�������Ƿ��ڱ߽��ڵ�
        if (mCloseOnTouchOutside && event.getAction() == MotionEvent.ACTION_DOWN
                && isOutOfBounds(context, event) && peekDecorView() != null) {
            return true;
        }
        return false;
        // ����true��˵���¼��ڱ߽��⣬�� �����¼�
        // ����false��δ���ѣ�Ĭ�ϣ�
    }
    // �ص�����4����ԭ��