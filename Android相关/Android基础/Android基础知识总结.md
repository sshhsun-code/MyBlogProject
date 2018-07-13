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

**2.Android里跨进程传递数据的几种方案**

- Binder
- Socket/LocalSocket
- 共享内存

**3.Parcelable和Serializable区别**

`Parcelable`的性能比`Serializable`好，在内存开销方面较小，所以在内存间数据传输时推荐使用Parcelable，如activity间传输数据，而Serializable可将数据持久化方便保存，所以在需要保存或网络传输数据时选择Serializable，因为android不同版本Parcelable可能不同，所以不推荐使用Parcelable进行数据持久化。

Serializable序列化不保存静态变量，可以使用Transient关键字对部分字段不进行序列化，也可以覆盖writeObject、readObject方法以实现序列化过程自定义。

**4.SharedPreference的commit与apply区别**
- apply没有返回值而commit返回boolean表明修改是否提交成功
- apply是将修改数据原子提交到内存，而后异步真正提交到硬件磁盘；而commit是同步的提交到硬件磁盘，因此，在多个并发的提交commit的时候，他们会等待正在处理的commit保存到磁盘后在操作，从而降低了效率。而apply只是原子的提交到内存，后面有调用apply的函数的将会直接覆盖前面的内存数据，这样从一定程度上提高了很多效率。
- apply方法不会提示任何失败的提示

SharedPreferencesImpl.EditorImpl.java#commit&apply

```java
public boolean commit() {
    MemoryCommitResult mcr = commitToMemory();
    SharedPreferencesImpl.this.enqueueDiskWrite(mcr, null /* sync write on this thread okay */);
    try {
        mcr.writtenToDiskLatch.await();
    } catch (InterruptedException e) {
        return false;
    }
    notifyListeners(mcr);
    return mcr.writeToDiskResult;
}

public void apply() {
    final MemoryCommitResult mcr = commitToMemory();
    final Runnable awaitCommit = new Runnable() {
        public void run() {
            try {
                mcr.writtenToDiskLatch.await();
            } catch (InterruptedException ignored) {
            }
        }
    };
    QueuedWork.add(awaitCommit);
    Runnable postWriteRunnable = new Runnable() {
        public void run() {
            awaitCommit.run();
            QueuedWork.remove(awaitCommit);
        }
    };
    SharedPreferencesImpl.this.enqueueDiskWrite(mcr, postWriteRunnable);
    // Okay to notify the listeners before it's hit disk
    // because the listeners should always get the same
    // SharedPreferences instance back, which has the
    // changes reflected in memory.
    notifyListeners(mcr);
}
```
这两个方法都是首先修改内存中缓存的mMap的值，然后将数据写到磁盘中。它们的主要区别是commit会等待写入磁盘后再返回，而apply则在调用写磁盘操作后就直接返回了，**但是这时候可能磁盘中数据还没有被修改**。

apply和commit都调用了`enqueueDiskWrite()`方法。以下为其具体实现代码。writeToDiskRunnable中调用`writeToFile`写文件。如果参数中的`postWriteRunable`为null，则该Runnable会被同步执行，而如果不为null，则会将该Runnable放入线程池中异步执行。在这里也验证了之前提到的commit和apply的区别

```java
private void enqueueDiskWrite(final MemoryCommitResult mcr,final Runnable postWriteRunnable) {
    final Runnable writeToDiskRunnable = new Runnable() {
        public void run() {
            synchronized (mWritingToDiskLock) {
                writeToFile(mcr);
            }
            synchronized (SharedPreferencesImpl.this) {
                mDiskWritesInFlight--;
            }
            if (postWriteRunnable != null) {
                postWriteRunnable.run();
            }
        }
    };
    final boolean isFromSyncCommit = (postWriteRunnable == null);
    // Typical #commit() path with fewer allocations, doing a write on
    // the current thread.
    if (isFromSyncCommit) {
        boolean wasEmpty = false;
        synchronized (SharedPreferencesImpl.this) {
            wasEmpty = mDiskWritesInFlight == 1;
        }
        if (wasEmpty) {
            writeToDiskRunnable.run();
            return;
        }
QueuedWork.singleThreadExecutor().execute(writeToDiskRunnable);
}
```

**5.Android系统启动过程 & App启动过程**

**Android手机启动过程**：

![image](http://o9m6aqy3r.bkt.clouddn.com//Application/android-booting.jpg)

当我们开机时，首先是启动Linux内核，在Linux内核中首先启动的是init进程，这个进程会去读取配置文件system\core\rootdir\init.rc配置文件，这个文件中配置了Android系统中第一个进程Zygote进程。

启动Zygote进程 --> 创建AppRuntime（Android运行环境） --> 启动虚拟机 --> 在虚拟机中注册JNI方法 --> 初始化进程通信使用的Socket（用于接收AMS的请求） --> 启动系统服务进程 --> 初始化时区、键盘布局等通用信息 --> 启动Binder线程池 --> 初始化系统服务（包括PMS，AMS等等） --> 启动Launcher

**App启动过程**

![image](http://o9m6aqy3r.bkt.clouddn.com//Activity/start_activity_process.jpg)

启动流程：
1. 点击桌面App图标，Launcher进程采用Binder IPC向system_server进程发起startActivity请求；

2. system_server进程接收到请求后，向zygote进程发送创建进程的请求；

3. Zygote进程fork出新的子进程，即App进程；

4. App进程，通过Binder IPC向sytem_server进程发起attachApplication请求；

5. system_server进程在收到请求后，进行一系列准备工作后，再通过binder IPC向App进程发送scheduleLaunchActivity请求；

6. App进程的binder线程（ApplicationThread）在收到请求后，通过handler向主线程发送LAUNCH_ACTIVITY消息；

7. 主线程在收到Message后，通过发射机制创建目标Activity，并回调Activity.onCreate()等方法。

**到此，App便正式启动，开始进入Activity生命周期，执行完onCreate/onStart/onResume方法，UI渲染结束后便可以看到App的主界面**。 

