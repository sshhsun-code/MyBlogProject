
# Android性能优化 #
![性能优化](http://o9m6aqy3r.bkt.clouddn.com/%E6%80%A7%E8%83%BD%E4%BC%98%E5%8C%96.jpg)

**1.HTTP用GZIP压缩，设置连接超时时间和响应超时时间 HTTP请求按照业务需求，分为是否可以缓存和不可缓存，那么在无网络的环境中，仍然通过缓存的HTTPRESPONSE浏览部分数据，实现离线阅读。**

**2、listview 性能优化
复用convertView 在getItemView中，判断convertView是否为空，如果不为空，可复用。如果couvertview中的view需要添加listerner，代码一定要在if(convertView==null){}之外。异步加载图片 item中如果包含有webimage，那么最好异步加载快速滑动时不显示图片 当快速滑动列表时（SCROLL_STATE_FLING），item中的图片或获取需要消耗资源的view，可以不显示出来；而处于其他两种状态（SCROLL_STATE_IDLE 和SCROLL_STATE_TOUCH_SCROLL），则将那些view显示出来**

**3、使用线程池，分为核心线程池和普通线程池，下载图片等耗时任务放置在普通线程池，避免耗时任务阻塞线程池后，导致所有异步任务都必须等待**

**4、异步任务，分为核心任务和普通任务，只有核心任务中出现的系统级错误才会报错，异步任务的ui操作需要判断原activity是否处于激活状态**

**5、尽量避免static成员变量引用资源耗费过多的实例,比如Context**


**6、使用WeakReference代替强引用，弱引用可以让您保持对对象的引用，同时允许GC在必要时释放对象，回收内存。对于那些创建便宜但耗费大量内存的对象，即希望保持该对象，又要在应用程序需要时使用，同时希望GC必要时回收时，可以考虑使用弱引用。**

**7、超级大胖子Bitmap 及时的销毁(Activity的onDestroy时，将bitmap回收) 设置一定的采样率 巧妙的运用软引用 drawable对应resid的资源，bitmap对应其他资源8.保证Cursor 占用的内存被及时的释放掉，而不是等待GC来处理。并且 Android明显是倾向于编程者手动的将Cursor close掉**

**8、线程也是造成内存泄露的一个重要的源头。线程产生内存泄露的主要原因在于线程生命周期的不可控**

**9、如果ImageView的图片是来自网络，进行异步加载**

**10、应用开发中自定义View的时候，交互部分，千万不要写成线程不断刷新界面显示，而是根据TouchListener事件主动触发界面的更新**