# AccessibilityDemo
学习总结辅助功能的第一步
---
**问题解决：关于直接调用辅助功能Service的getRootInActiveWindow（）为空，延时2s后调用就不为空的问题 ？**

1.问题重现
---
在辅助功能学习demo中， 在开启辅助功能权限后，手动打开一个AccessibilityActionActivity,在这个Activity的onResume中，根据节点文案（<font color = "#ff0000">"点击按钮"</font>）进行节点查找并**点击**，同时，延时2s后，再根据这个控件的ID（<font color = "#ff0000"><strong>R.id.btn_click</strong></font>）去查找进行**点击**。结果发现按照文案查找时，没有找到这个节点，但是延时2s后，根据ID去查找控件时，就能找到这个节点并执行点击。
<br/>经过断点发现，<font color = "#ff0000">**问题实质表现为，OnResume中，直接调用AccessibilityService.getRootInActiveWindow()为空。而延时2s后，再调用AccessibilityService.getRootInActiveWindow()就不为空，并正常使用。**</font>**所以，这是为什么？**

![](http://o9m6aqy3r.bkt.clouddn.com/%E8%BE%85%E5%8A%A9%E5%8A%9F%E8%83%BD%E6%A8%A1%E6%8B%9F%E7%82%B9%E5%87%BBActivity.png)

2.问题研究
---
我们都知道，通过辅助功能，可以以根节点遍历整个布局，并根据ID或者文案进行控件查找。
但是这个根节点的获取。一般是通过AccessibilityService.getRootInActiveWindow()来获取的，但是为什么会出现为空的现象呢？一开始我以为是跟Activity的生命周期有关，因为要获得这个根节点，应该是界面完全加载完成后才能获取，于是在Activity中的onWindowFocusChanged回调中，再次调用了 getRootInActiveWindow，结果发现还是为空，同样需要延时后，才不为空。于是我将，AccessibilityService中的onAccessibilityEvent，以及Activity中的调用联合打印log。发现结果如下：


![](http://o9m6aqy3r.bkt.clouddn.com/%E8%BE%85%E5%8A%A9%E5%8A%9F%E8%83%BD%E7%9B%B8%E5%85%B3%E6%89%93%E5%8D%B01.png)

	
整个页面的真正的根节点为 **info@80007558**,而真正这个对象的生成是在eventType: 32,即AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED回调事件后生产，在这之前的调用取得根节点时，要么为空，要么取到的对象是错的。那么**AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED**到底是什么事件呢？
**<br>Google文档中这样描述：TYPE_WINDOW_STATE_CHANGED**
>**Window state changed - represents the event of a change to a section of the user interface that is visually distinct. Should be sent from either the root view of a window or from a view that is marked as a pane setAccessibilityPaneTitle(CharSequence)**


同时将TYPE_WINDOW_CONTENT_CHANGED和TYPE_WINDOW_STATE_CHANGED做了对比解释：
![](http://o9m6aqy3r.bkt.clouddn.com/%E8%BE%85%E5%8A%A9%E5%8A%9F%E8%83%BD%E4%BA%8B%E4%BB%B6%E5%9B%9E%E8%B0%83%E5%AF%B9%E6%AF%94.png)

>**也就是说，TYPE_WINDOW_CONTENT_CHANGED是在该页面树状结构上子树进行改动时，页面内容发生变化时，发出的回调，页面不发生。而TYPE_WINDOW_STATE_CHANGED事件是整个界面变化即跳转时，整个页面树结构发生变化时调用，具体比如页面跳转/切换/关闭时调用。并且这个事件是由页面的根节点rootInfo发出的。所以，只有当TYPE_WINDOW_STATE_CHANGED事件调用时，整个页面的根节点才可以得到，不为空。**


3.结论
--
**辅助功能中根节点的获取，是通过AccessibilityService.getRootInActiveWindow()得到的，而且必须是在TYPE_WINDOW_STATE_CHANGED回调后，才能获取到。和Activity等生命周期没有关系。实际使用中，辅助功能的流程事件一般是以onAccessibilityEvent来作为驱动的，根据eventType做不同的响应。同时，事件event中的getPackageName可以区分不同应用。所以，以Package为区分条件也可以减少，event回调使用错的情况。**

