# Activity与Fragment生命周期

## 1.Fragment生命周期：

![image](http://o9m6aqy3r.bkt.clouddn.com//Activity/frag_lifecycle.png)

## 2.Activity与Fragment生命周期对比

![image](http://o9m6aqy3r.bkt.clouddn.com//Activity/activity&fragment_lifecycle1.png)

#### Fragment生命周期方法含义：

`public void onAttach(Context context)`

- onAttach方法会在Fragment于窗口关联后立刻调用。从该方法开始，就可以通过Fragment.getActivity方法获取与Fragment关联的窗口对象，但因为Fragment的控件未初始化，所以不能够操作控件。

`public void onCreate(Bundle savedInstanceState)`

- 在调用完onAttach执行完之后立刻调用onCreate方法，可以在Bundle对象中获取一些在Activity中传过来的数据。通常会在该方法中读取保存的状态，获取或初始化一些数据。在该方法中不要进行耗时操作，不然窗口不会显示。

`public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState)`

- 该方法是Fragment很重要的一个生命周期方法，因为会在该方法中创建在Fragment显示的View，其中inflater是用来装载布局文件的，container是<fragment>标签的父标签对应对象，savedInstanceState参数可以获取Fragment保存的状态，如果未保存那么就为null。

`public void onViewCreated(View view,Bundle savedInstanceState)`

- Android在创建完Fragment中的View对象之后，会立刻回调该方法。其种view参数就是onCreateView中返回的view，而bundle对象用于一般用途。

`public void onActivityCreated(Bundle savedInstanceState)`

- 在Activity的onCreate方法执行完之后，Android系统会立刻调用该方法，表示窗口已经初始化完成，从这一个时候开始，就可以在Fragment中使用getActivity().findViewById(Id);来操控Activity中的view了。

`public void onStart()`

- 这个没啥可讲的，但有一个细节需要知道，当系统调用该方法的时候，fragment已经显示在ui上，但还不能进行互动，因为onResume方法还没执行完。

`public void onResume()`

- 该方法为fragment从创建到显示Android系统调用的最后一个生命周期方法，调用完该方法时候，fragment就可以与用户互动了。

`public void onPause()`

- fragment由活跃状态变成非活跃状态执行的第一个回调方法，通常可以在这个方法中保存一些需要临时暂停的工作。如保存音乐播放进度，然后在onResume中恢复音乐播放进度。

`public void onStop()`

- 当onStop返回的时候，fragment将从屏幕上消失。

`public void onDestoryView()`

- 该方法的调用意味着在 onCreateView 中创建的视图都将被移除。

`public void onDestroy()`

- Android在Fragment不再使用时会调用该方法，要注意的是这时Fragment还和Activity藕断丝连！并且可以获得Fragment对象，但无法对获得的Fragment进行任何操作（呵呵呵~我已经不听你的了）。

`public void onDetach()`

- 为Fragment生命周期中的最后一个方法，当该方法执行完后，Fragment与Activity不再有关联(分手！我们分手！！(╯‵□′)╯︵┻━┻)。

#### Fragment比Activity多了几个额外的生命周期回调方法：

- onAttach(Activity):当Fragment和Activity发生关联时使用
- onCreateView(LayoutInflater, ViewGroup, Bundle):创建该Fragment的视图
- onActivityCreate(Bundle):当Activity的onCreate方法返回时调用
- onDestoryView():与onCreateView相对应，当该Fragment的视图被移除时调用
- onDetach():与onAttach相对应，当Fragment与Activity关联被取消时调用

>**除了onCreateView，其他的所有方法如果重写，必须调用父类对于该方法的实现**

## 3.Activity与Fragment超详细对比图

![image](http://o9m6aqy3r.bkt.clouddn.com//Activity/complete_android_fragment_lifecycle.png)


