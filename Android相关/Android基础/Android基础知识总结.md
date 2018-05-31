# Android基础知识总结

**1.Activity A 打开新的Activity B 时，B的onResume和A的onPause哪个先执行？**

![image](http://o9m6aqy3r.bkt.clouddn.com//Activity/resumeTopActivityInnerLocker.png)

上述代码看出，**++在新的Activity启动前，栈顶的Activity需要先onPause后，新的Activity才会启动++**。

另外经过试验也可以得出结论：

新建两个 Activity：MainActivity 和 Main2Activity，并在两个 Activity 的生命周期方法中打印 log。从 MainActivity 跳转到 Main2Activity，查看 log 日志

打开 MainActivity：

```Java
07-07 14:42:46.366 15625-15625/com.dp.activitylifecycledemo D/MainActivity: onCreate
07-07 14:42:46.374 15625-15625/com.dp.activitylifecycledemo D/MainActivity: onStart
07-07 14:42:46.379 15625-15625/com.dp.activitylifecycledemo D/MainActivity: onResume
```

然后跳转：

```Java
07-07 14:44:36.107 15625-15625/com.dp.activitylifecycledemo D/MainActivity: onPause
07-07 14:44:36.120 15625-15625/com.dp.activitylifecycledemo D/Main2Activity: onCreate
07-07 14:44:36.128 15625-15625/com.dp.activitylifecycledemo D/Main2Activity: onStart
07-07 14:44:36.130 15625-15625/com.dp.activitylifecycledemo D/Main2Activity: onResume
07-07 14:44:36.523 15625-15625/com.dp.activitylifecycledemo D/MainActivity: onStop
```
可以看出，跳转动作发出后，先走 MainActivity#onPause()，然后依次走 Main2Activity 的 onCreate()、onStart()、onResume()，最后走 MainActivity#onStop()。

按返回键，返回到 MainActivity：

```java
07-07 14:47:27.965 15625-15625/com.dp.activitylifecycledemo D/Main2Activity: onPause
07-07 14:47:28.014 15625-15625/com.dp.activitylifecycledemo D/MainActivity: onRestart
07-07 14:47:28.015 15625-15625/com.dp.activitylifecycledemo D/MainActivity: onStart
07-07 14:47:28.015 15625-15625/com.dp.activitylifecycledemo D/MainActivity: onResume
07-07 14:47:28.334 15625-15625/com.dp.activitylifecycledemo D/Main2Activity: onStop
07-07 14:47:28.334 15625-15625/com.dp.activitylifecycledemo D/Main2Activity: onDestroy
```
先执行 Main2Activity#onPause()，然后执行 MainActivity 的 onRestart()、onStart()、onResume()，再执行 Main2Activity 的 onStop、onDestroy()