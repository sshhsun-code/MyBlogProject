# 宿主Activity与Fragment间几种数据交互方式梳理

#### 0x01 在Fragment中获取宿主Activity对象


重写Fragment的`onAttach()`方法，把参数`activity`强转为宿主Activity即可，如下：

```java
@Override
public void onAttach(Activity activity) {
    super.onAttach(activity);
    //通过强转成宿主activity，就可以获取到宿主Activity对象
    titles = ((MainActivity) activity).getTitles();
}
```

<!--more-->


#### 0x02 在宿主Activity中获取Fragment对象

很简单。一般各个Fragment都是在宿主Activity中进行创建的，所以在创建ragment对象时，直接将其保存为全局变量即可。


#### 0x03 宿主Activity传值给子Fragment

最常见的做法Android API已经封装好：就是使用Bundle参数来传递。在宿主Activity中调用`Fragment#setArguements(Bundle)`传入 数据，然后在Fragment`onCreate()/onCreatView()`方法中通过`getArgments()`方法获取到携带数据的`Bundle`对象，在使用`Bundle#getString()`根据对应的key拿到我们传递过来的数据。如下：

```java
// 宿主Activity/FragmentAdapter中
Bundle bundle = new Bundle();
bundle.putString(Constant.INTENT_ID, productId);
Fragment fragment = new YourFragment();
fragment.setArguments(bundle);

// Activity或FragmentAdapter
@Override
public void onCreate() {
    super.onCreate();
    if (isAdded()) { //判断此时Fragment已经依附于Activity
        String productId = getArguments().getString(Constant.INTENT_ID);
    }
}
```

第二种方式是在宿主Activity中定义传值方法。把要传递的数据传递到Fragment中。由上可知在`Fragment#onAttach()`方法中可获取到宿主Activity的对象mActivvity，再调用`mActivity#getData()`获取数据。如下：

```java
// 宿主activity中的getTitles()方法
public String getTitles() {
    return "hello";
}

// Fragment中的onAttach方法
@Override
public void onAttach(Activity activity) {
    super.onAttach(activity);
    mActivity = activity;
}

@Override
public Xxx OnCteate(Bundle savedInstance) {
    super.onCreate();
    String titles = ((MainActivity) activity).getTitles();
    // TODO: suing titles
}
```

第三种方式：扩展一下Fragment！类似大多数单例的写法，将其构造方法方法私有化，通过调用静态的`getInstance(Args args)`函数获取其对象，然后，在调用此函数是通过参数Args传递数据。**但是一般来说getInstance()函数用来获取单例对象，不建议这样写！一段设计良好的断码应该是每一个函数都职责单一。所以慎用此方法！**如下：

```java
// 子Fragment中
private static MyFragment sInstance;
private Args mArgs;
public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) { //... }
public static void getInstance(Args args) {
    if (sInstance == null) {
        sInstance = new MyFragment();
    }
    mArgs = args;
    // TODO: using args
    return sInstance;
}

// 宿主Activity中
Args args = new Args(); // put data here
MyFragment fragment = MyFragment.getInstance(args);
```

第四种方式：进一步扩展一下方式一和方式三，我们可以继续在子Fragment的`onCreateView()`函数中作文章。如下：

```java
// 子Fragment中
private static MyFragment sInstance;
private Args mArgs;

public static void getInstance(Args args) {
    if (sInstance == null) {
        sInstance = new MyFragment();
    }
    Bundle bundle = new Bundle();
    bundle.putExtras("DATA", args);
    sInstance.setArguments(bundle);
    return sInstance;
}

public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle     savedInstanceState) { 
    //... 
    Bundle bundle = getArguments();
    Args data = bundle.getExtras("DATA");
}
```

#### 0x04 Fragment传值给宿主Activity 

使用接口回调传递数据。其实不仅仅局限在Fragment与其宿主Activity间的数据交互，通过接口回调传递数据适用于几乎所用的场景。**甚至说，接口回调是Java特意为Java类间数据交互而设计。**

1.  在fragment中定义一个内部回调接口，再让宿主`activity`实现该接口，这样`fragment`即可通过宿主Activity对象回调该接口方法将数据传给activity。其实接口回调的原理都一样，以前的博客有讲到，接口回调是不同对象间数据交互的通用方法。
2.  宿主`activity`实现完了接口怎么将`this`传给刚才的`fragment`呢？当fragment添加到activity中时，会调用fragment的方法`onAttach()`，这个方法中适合检查activity是否实现了`OnArticleSelectedListener`接口，检查方法就是对传入的`activity` 参数进行强制类型转换，然后通过此对象回调我们在fragment中定义的接口方法。
3.  当一个fragment从activity中剥离的时候，就会调用`onDetach()`方法，这个时候要把传递进来的activity对象释放掉，不然会影响activity的销毁，产生不必要的错误（一般指内存泄露）。注意看`onAttach()`方法中的代码，在赋值之前要做一个判断，看看Activity中有没有实现了这个接口，用到了`instanceof` 操作符。如果没有实现接口，我们就抛出异常。

```java
// 在宿主activity中，创建Fragment
public class MainActivity extends Activity implements MenuFragment.ProcessListener {

    private TextView textView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        textView = (TextView) findViewById(R.id.content_text);
    }

    // 实现接口 实现回调
    @Override
    public void process(String str) {
        if (str != null) {
            textView.setText(str);
        }
    }
}

// 定义了所有activity必须实现的接口方法
public interface ProcessListener {
    void process(String str);
}

// Fragment中
public class MenuFragment extends Fragment {

    private ProcessListener mListterner;
    private Activity mActivity;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.mActivity = activity;

        if(activity instanceof ProcessListener) {
            mListterner = (MainActivity) activity; 
        } else{
            throw new IllegalArgumentException("...");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu, container, false);
        View btn = view.findViewById(R.id.tv_button);
        if (btn != null || btn_m != null) {
            btn.setOnClickListener(this);
        }
        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tv_button:
                listterner.process("我是电视剧");
                break;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        //把传递进来的activity对象释放掉
        mListterner = null;
        mActivity = null;
    }
}
```

#### 0x05 Fragment与Fragment间数据交互

在Activity中加载Fragment的时候，有时候要使用多个Fragment切换、并传值到另外一个Fragment，也就是说两个Fragment之间进行参数的传递有两个方法。很自然的。第一种方式通过Bundle对象来传递。如下：

```java
// 在宿主activity中
ft.hide(getActivity().getSupportFragmentManager().findFragmentByTag(""));
    DemoFragment demoFragment = new DemoFragment();  
    Bundle bundle = new Bundle();  
    bundle.putString("key", "方法二");  
    demoFragment.setArguments(bundle);  
    ft.add(R.id.fragmentRoot, demoFragment, SEARCHPROJECT);  
    ft.commit();

// Fragment中
String string = getArguments().getString("key");  
```

基于这些子Fragment有一个共同的宿主Activity，我们可以考虑通过这个共享的宿主Activity来进行数据交互。具体实现是：在Activity里面添加一个字段、来临时保存要一些值。在Activity中定义一个字段、然后添加set和get方法。如下：

```java
// 在宿主activity中
public class DemoActivity {

    private String mTitle;

    public String getmTitle() {
        return mTitle;
    }

    public void setmTitle(String title) {
        this.mTitle = title;
    }

}

// Fragment中，调用方法、需要注意的是在设值的时候要进行强转一下
((DemoActivity)getActivity()).getmTitle();
```

方式三。扩展思考一下，我们是否可以通过接口回调来实现呢？当然可以！我们考虑将接口定义为一个单独的类，或者直接定义在宿主Activity中，各个子Fragment分别实现接口方法。

由于宿主Activity持有各个Fragment对象，故可以将每个Fragment对象保存为全局变量。然后，为每个全局的Fragment对象生成Getter方法，以供其余子Fragment调用，来获取其所需数据的Fragment的对象。进而，通过此对象回调接口方法，实现数据交互。如下。

```java
// 宿主Activity中
public MainActivity extends FragmentActivity {
    // 定义接口
	public interface GlobalDataInterface {
    	DataType1 method1();
    	DataType2 method2();
	}
    
    public FragmentOne mOne;
    public FragmentTwo mTwo;
  
    public FragmentOne getFragment1() {
        return mOne;
    }
  
    public FragmentTwo getFragment2() {
        return mTwo;
    }
  
    public onCreate(Args args) {
        mOne = new FragmentOne();
        mTwo = new FragmentTwo();
    }
}

// FragmentOne实现接口方法
public FragmentOne extends Fragment implements GlobalDataInterface {
    private MainActivity mActivity; //宿主对象
    // ...
    @Override
    public DataType1 method1() {
        // TODO: inner logic or business.
    }
    @Override
    public DataType2 method2() {
        // TODO: nothing nedd to do.
    }
    // ...
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    	mActivity = (MainActivity) activity;
    }
    
    @Override
    public void onCreateView(Args args) {
        // 回调FragmentTwo实现的接口方法，获取其数据
        DataType2 data = mActivity.getFragment2().method2();
        // to do your buysiness based on data.
    }
}

// FragmentTwo实现接口方法
public FragmentTwo extends Fragment implements GlobalDataInterface {
    private MainActivity mActivity; //宿主对象
    @Override
    public DataType1 method1() {
        // TODO: nothing nedd to do.
    }
    // ...
    @Override
    public DataType2 method2() {
        // TODO: inner logic or business.
    } 
    // ...
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    	mActivity = (MainActivity) activity;
    }
    
    @Override
    public void onCreateView(Args args) {
        // 回调FragmentOne实现的接口方法，获取其数据
        DataType1 data = mActivity.getFragment1().method1();
        // to do your buysiness based on data.
    }
}
```



>   参考文献



[Android进阶之Fragment与Activity之间的数据交互](http://blog.csdn.net/chenliguan/article/details/53906934) ，感谢原作者提供思路。



