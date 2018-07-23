# 理解Android进程创建流程

参考： [http://gityuan.com/2016/03/26/app-process-create/](http://gityuan.com/2016/03/26/app-process-create/)

1. 概述
2. system_server发起请求
3. Zygote创建进程
4. 新进程运行
5. 总结



## 1. 概述

**准备知识**

**进程：** 每个App在启动前必须先创建一个进程，该进程是由Zygote fork出来的，进程具有独立的资源空间，用于承载App上运行的各种Activity/Service等组件。进程对于上层应用来说是完全透明的，这也是google有意为之，让App程序都是运行在Android Runtime。大多数情况一个App就运行在一个进程中，除非在AndroidManifest.xml中配置Android:process属性，或通过native代码fork进程。

**线程：** 线程对应用开发者来说非常熟悉，比如每次new Thread().start()都会创建一个新的线程，该线程并没有自己独立的地址空间，而是与其所在进程之间资源共享。从Linux角度来说进程与线程都是一个task_struct结构体，除了是否共享资源外，并没有其他本质的区别。


**system_server进程和Zygote进程，下面简要这两个进程：**

- `system_server`进程：是用于管理整个Java framework层，包含ActivityManager，PowerManager等各种系统服务;
- `Zygote`进程：是Android系统的首个Java进程，**Zygote是所有Java进程的父进程，包括 system_server进程以及所有的App进程都是Zygote的子进程**，注意这里说的是子进程，而非子线程。


**进程创建图**

![image](http://o9m6aqy3r.bkt.clouddn.com//Application/start_app_process.jpg)

图解：

1. **App发起进程**：当从桌面启动应用，则发起进程便是Launcher所在进程；当从某App内启动远程进程，则发送进程便是该App所在进程。发起进程先通过binder发送消息给system_server进程;
2. **system_server进程**：调用Process.start()方法，通过socket向zygote进程发送创建新进程的请求；
3. **zygote进程**：在执行ZygoteInit.main()后便进入runSelectLoop()循环体内，当有客户端连接时便会执行ZygoteConnection.runOnce()方法，再经过层层调用后fork出新的应用进程；
4. **新进程**：执行handleChildProc方法，最后调用ActivityThread.main()方法。


## 2. system_server发起请求

Process.start


```
public static final ProcessStartResult start(final String processClass, final String niceName, int uid, int gid, int[] gids, int debugFlags, int mountExternal, int targetSdkVersion, String seInfo, String abi, String instructionSet, String appDataDir, String[] zygoteArgs) {
    try {
         
        return startViaZygote(processClass, niceName, uid, gid, gids,
                debugFlags, mountExternal, targetSdkVersion, seInfo,
                abi, instructionSet, appDataDir, zygoteArgs);
    } catch (ZygoteStartFailedEx ex) {
        throw new RuntimeException("");
    }
}
```

startViaZygote


```
private static ProcessStartResult startViaZygote(final String processClass, final String niceName, final int uid, final int gid, final int[] gids, int debugFlags, int mountExternal, int targetSdkVersion, String seInfo, String abi, String instructionSet, String appDataDir, String[] extraArgs) throws ZygoteStartFailedEx {
    synchronized(Process.class) {
        ArrayList<String> argsForZygote = new ArrayList<String>();

        argsForZygote.add("--runtime-args");
        argsForZygote.add("--setuid=" + uid);
        argsForZygote.add("--setgid=" + gid);
        argsForZygote.add("--target-sdk-version=" + targetSdkVersion);

        if (niceName != null) {
            argsForZygote.add("--nice-name=" + niceName);
        }
        if (appDataDir != null) {
            argsForZygote.add("--app-data-dir=" + appDataDir);
        }
        argsForZygote.add(processClass);

        if (extraArgs != null) {
            for (String arg : extraArgs) {
                argsForZygote.add(arg);
            }
        }
         
        return zygoteSendArgsAndGetResult(openZygoteSocketIfNeeded(abi), argsForZygote);
    }
}
```

该过程主要工作是生成argsForZygote数组，该数组保存了进程的uid、gid、groups、target-sdk、nice-name等一系列的参数。


zygoteSendArgsAndGetResult


```
private static ProcessStartResult zygoteSendArgsAndGetResult( ZygoteState zygoteState, ArrayList<String> args) throws ZygoteStartFailedEx {
    try {
        
        final BufferedWriter writer = zygoteState.writer;
        final DataInputStream inputStream = zygoteState.inputStream;

        writer.write(Integer.toString(args.size()));
        writer.newLine();

        int sz = args.size();
        for (int i = 0; i < sz; i++) {
            String arg = args.get(i);
            if (arg.indexOf('\n') >= 0) {
                throw new ZygoteStartFailedEx(
                        "embedded newlines not allowed");
            }
            writer.write(arg);
            writer.newLine();
        }

        writer.flush();

        ProcessStartResult result = new ProcessStartResult();
        //等待socket服务端（即zygote）返回新创建的进程pid;
        //对于等待时长问题，Google正在考虑此处是否应该有一个timeout，但目前是没有的。
        result.pid = inputStream.readInt();
        if (result.pid < 0) {
            throw new ZygoteStartFailedEx("fork() failed");
        }
        result.usingWrapper = inputStream.readBoolean();
        return result;
    } catch (IOException ex) {
        zygoteState.close();
        throw new ZygoteStartFailedEx(ex);
    }
}
```
**主要功能是通过socket通道向Zygote进程发送一个参数列表，然后进入阻塞等待状态，直到远端的socket服务端发送回来新创建的进程pid才返回**.


## 3.Zygote创建进程

简单来说就是Zygote进程是由由init进程而创建的，进程启动之后调用ZygoteInit.main()方法，经过创建socket管道，预加载资源后，便进程runSelectLoop()方法。

ZygoteInit.main


```
public static void main(String argv[]) {
    try {
        runSelectLoop(abiList); 
        ....
    } catch (MethodAndArgsCaller caller) {
        caller.run(); 
    } catch (RuntimeException ex) {
        closeServerSocket();
        throw ex;
    }
}
```
runSelectLoop


```
private static void runSelectLoop(String abiList) throws MethodAndArgsCaller {
    ArrayList<FileDescriptor> fds = new ArrayList<FileDescriptor>();
    ArrayList<ZygoteConnection> peers = new ArrayList<ZygoteConnection>();
    //sServerSocket是socket通信中的服务端，即zygote进程。保存到fds[0]
    fds.add(sServerSocket.getFileDescriptor());
    peers.add(null);

    while (true) {
        StructPollfd[] pollFds = new StructPollfd[fds.size()];
        for (int i = 0; i < pollFds.length; ++i) {
            pollFds[i] = new StructPollfd();
            pollFds[i].fd = fds.get(i);
            pollFds[i].events = (short) POLLIN;
        }
        try {
             //处理轮询状态，当pollFds有事件到来则往下执行，否则阻塞在这里
            Os.poll(pollFds, -1);
        } catch (ErrnoException ex) {
            ...
        }

        for (int i = pollFds.length - 1; i >= 0; --i) {
            //采用I/O多路复用机制，当接收到客户端发出连接请求 或者数据处理请求到来，则往下执行；
            // 否则进入continue，跳出本次循环。
            if ((pollFds[i].revents & POLLIN) == 0) {
                continue;
            }
            if (i == 0) {
                //即fds[0]，代表的是sServerSocket，则意味着有客户端连接请求；
                // 则创建ZygoteConnection对象,并添加到fds。
                ZygoteConnection newPeer = acceptCommandPeer(abiList);
                peers.add(newPeer);
                fds.add(newPeer.getFileDesciptor()); //添加到fds.
            } else {
                //i>0，则代表通过socket接收来自对端的数据，并执行相应操作
                boolean done = peers.get(i).runOnce();
                if (done) {
                    peers.remove(i);
                    fds.remove(i); //处理完则从fds中移除该文件描述符
                }
            }
        }
    }
}
```

该方法主要功能：
- 客户端通过openZygoteSocketIfNeeded()来跟zygote进程建立连接。zygote进程收到客户端连接请求后执行accept()；然后再创建ZygoteConnection对象,并添加到fds数组列表；
- 建立连接之后，可以跟客户端通信，进入runOnce()方法来接收客户端数据，并执行进程创建工作。


**runOnce**


```
boolean runOnce() throws ZygoteInit.MethodAndArgsCaller {

    String args[];
    Arguments parsedArgs = null;
    FileDescriptor[] descriptors;

    try {
        //读取socket客户端发送过来的参数列表
        args = readArgumentList();
        descriptors = mSocket.getAncillaryFileDescriptors();
    } catch (IOException ex) {
        closeSocket();
        return true;
    }

    PrintStream newStderr = null;
    if (descriptors != null && descriptors.length >= 3) {
        newStderr = new PrintStream(new FileOutputStream(descriptors[2]));
    }

    int pid = -1;
    FileDescriptor childPipeFd = null;
    FileDescriptor serverPipeFd = null;

    try {
        //将binder客户端传递过来的参数，解析成Arguments对象格式
        parsedArgs = new Arguments(args);
        ...

        int [] fdsToClose = { -1, -1 };
        FileDescriptor fd = mSocket.getFileDescriptor();
        if (fd != null) {
            fdsToClose[0] = fd.getInt$();
        }

        fd = ZygoteInit.getServerSocketFileDescriptor();
        if (fd != null) {
            fdsToClose[1] = fd.getInt$();
        }
        fd = null;
        
        pid = Zygote.forkAndSpecialize(parsedArgs.uid, parsedArgs.gid, parsedArgs.gids,
                parsedArgs.debugFlags, rlimits, parsedArgs.mountExternal, parsedArgs.seInfo,
                parsedArgs.niceName, fdsToClose, parsedArgs.instructionSet,
                parsedArgs.appDataDir);
    } catch (Exception e) {
        ...
    }

    try {
        if (pid == 0) {
            //子进程执行
            IoUtils.closeQuietly(serverPipeFd);
            serverPipeFd = null;
           
            handleChildProc(parsedArgs, descriptors, childPipeFd, newStderr);

            // 不应到达此处，子进程预期的是抛出异常ZygoteInit.MethodAndArgsCaller或者执行exec().
            return true;
        } else {
            //父进程执行
            IoUtils.closeQuietly(childPipeFd);
            childPipeFd = null;
            return handleParentProc(pid, descriptors, serverPipeFd, parsedArgs);
        }
    } finally {
        IoUtils.closeQuietly(childPipeFd);
        IoUtils.closeQuietly(serverPipeFd);
    }
}
```

fork()的主要工作是寻找空闲的进程号pid，然后从父进程拷贝进程信息，例如数据段和代码段，fork()后子进程要执行的代码等。 

Zygote进程是所有Android进程的母体，包括system_server和各个App进程。zygote利用fork()方法生成新进程，对于新进程A复用Zygote进程本身的资源，再加上新进程A相关的资源，构成新的应用进程A

ZygoteConnection.runOnce方法中，zygote进程执行完forkAndSpecialize()后，**新创建的App进程便进入handleChildProc()方法，下面的操作运行在App进程**。


**在新进程生成后，执行`handleChildProc`方法，对新进程进行初始化操作**。


## 4.新进程运行

运行在子进程中继续开始执行handleChildProc()方法

handleChildProc


```
private void handleChildProc(Arguments parsedArgs, FileDescriptor[] descriptors, FileDescriptor pipeFd, PrintStream newStderr) throws ZygoteInit.MethodAndArgsCaller {

    //关闭Zygote的socket两端的连接
    closeSocket();
    ZygoteInit.closeServerSocket();

    if (descriptors != null) {
        try {
            Os.dup2(descriptors[0], STDIN_FILENO);
            Os.dup2(descriptors[1], STDOUT_FILENO);
            Os.dup2(descriptors[2], STDERR_FILENO);
            for (FileDescriptor fd: descriptors) {
                IoUtils.closeQuietly(fd);
            }
            newStderr = System.err;
        } catch (ErrnoException ex) {
            Log.e(TAG, "Error reopening stdio", ex);
        }
    }

    if (parsedArgs.niceName != null) {
        //设置进程名
        Process.setArgV0(parsedArgs.niceName);
    }

    if (parsedArgs.invokeWith != null) {
        //据说这是用于检测进程内存泄露或溢出时场景而设计，后续还需要进一步分析。
        WrapperInit.execApplication(parsedArgs.invokeWith,
                parsedArgs.niceName, parsedArgs.targetSdkVersion,
                VMRuntime.getCurrentInstructionSet(),
                pipeFd, parsedArgs.remainingArgs);
    } else {
        //执行目标类的main()方法 
        RuntimeInit.zygoteInit(parsedArgs.targetSdkVersion,
                parsedArgs.remainingArgs, null);
    }
}
```

zygoteInit


```
public static final void zygoteInit(int targetSdkVersion, String[] argv, ClassLoader classLoader) throws ZygoteInit.MethodAndArgsCaller {

    redirectLogStreams(); //重定向log输出

    commonInit(); // 通用的一些初始化
    nativeZygoteInit(); // zygote初始化 
    applicationInit(targetSdkVersion, argv, classLoader); // 应用初始化
}
```

**zygote初始化**

nativeZygoteInit


```
static void com_android_internal_os_RuntimeInit_nativeZygoteInit(JNIEnv* env, jobject clazz) {
    //此处的gCurRuntime为AppRuntime，是在AndroidRuntime.cpp中定义的
    gCurRuntime->onZygoteInit();
}
```
即执行：


```
virtual void onZygoteInit() {
    sp<ProcessState> proc = ProcessState::self();
    proc->startThreadPool(); //启动新binder线程
}
```

- ProcessState::self():主要工作是调用open()打开/dev/binder驱动设备，再利用mmap()映射内核的地址空间，将Binder驱动的fd赋值ProcessState对象中的变量mDriverFD，用于交互操作。startThreadPool()是创建一个新的binder线程，不断进行talkWithDriver().
- startThreadPool(): 启动Binder线程池

**应用初始化**

applicationInit


```
private static void applicationInit(int targetSdkVersion, String[] argv, ClassLoader classLoader) throws ZygoteInit.MethodAndArgsCaller {
    //true代表应用程序退出时不调用AppRuntime.onExit()，否则会在退出前调用
    nativeSetExitWithoutCleanup(true);

    //设置虚拟机的内存利用率参数值为0.75
    VMRuntime.getRuntime().setTargetHeapUtilization(0.75f);
    VMRuntime.getRuntime().setTargetSdkVersion(targetSdkVersion);

    final Arguments args;
    try {
        args = new Arguments(argv); //解析参数
    } catch (IllegalArgumentException ex) {
        return;
    }

    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);

    //调用startClass的static方法 main() 
    invokeStaticMain(args.startClass, args.startArgs, classLoader);
}
```

**此处args.startClass为”android.app.ActivityThread”**

invokeStaticMain


```
private static void invokeStaticMain(String className, String[] argv, ClassLoader classLoader) throws ZygoteInit.MethodAndArgsCaller {
    Class<?> cl = Class.forName(className, true, classLoader);

    Method m = cl.getMethod("main", new Class[] { String[].class });

    int modifiers = m.getModifiers();
    ...

    //通过抛出异常，回到ZygoteInit.main()。这样做好处是能清空栈帧，提高栈帧利用率。
    throw new ZygoteInit.MethodAndArgsCaller(m, argv);
}
```

invokeStaticMain()方法中抛出的异常MethodAndArgsCaller caller，该方法的参数m是指main()方法, argv是指ActivityThread. **根据前面的ZygoteInit.main中可知**，下一步进入caller.run()方法，也就是MethodAndArgsCaller.run()

MethodAndArgsCaller

```
public static class MethodAndArgsCaller extends Exception implements Runnable {

    public void run() {
        try {
            //根据传递过来的参数，此处反射调用ActivityThread.main()方法
            mMethod.invoke(null, new Object[] { mArgs });
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(ex);
        }
    }
}
```

**到此，总算是进入到了ActivityThread类的main()方法。**

ActivityThread.main


```
public static void main(String[] args) {
    ...
    Environment.initForCurrentUser();
    ...
    Process.setArgV0("<pre-initialized>");
    //创建主线程looper
    Looper.prepareMainLooper();

    ActivityThread thread = new ActivityThread();
    //attach到系统进程
    thread.attach(false);

    if (sMainThreadHandler == null) {
        sMainThreadHandler = thread.getHandler();
    }

    //主线程进入循环状态
    Looper.loop();

    throw new RuntimeException("Main thread loop unexpectedly exited");
}
```

## 5.总结



Process.start()方法是阻塞操作，等待直到进程创建完成并返回相应的新进程pid，才完成该方法。

当App第一次启动时或者启动远程Service，即AndroidManifest.xml文件中定义了process:remote属性时，都需要创建进程。比如当用户点击桌面的某个App图标，桌面本身是一个app（即Launcher App），那么Launcher所在进程便是这次创建新进程的发起进程，该通过binder发送消息给system_server进程，该进程承载着整个java framework的核心服务。system_server进程从Process.start开始，执行创建进程，流程图（以进程的视角）如下：

![image](http://o9m6aqy3r.bkt.clouddn.com//Application/process-create.jpg)

**system_server进**程通过socket IPC通道向**zygote进程**通信，zygote在fork出新进程后由于fork调用一次，返回两次，**即在zygote进程中调用一次，在zygote进程和子进程中各返回一次，从而能进入子进程来执行代码**。该调用流程图的过程：

1. **system_server进程**：通过Process.start()方法发起创建新进程请求，会先收集各种新进程uid、gid、nice-name等相关的参数，然后通过socket通道发送给zygote进程；
2. **zygote进程**：接收到system_server进程发送过来的参数后封装成Arguments对象，图中绿色框forkAndSpecialize()方法是进程创建过程中最为核心的一个环节，其具体工作是依次执行下面的3个方法：
>- preFork()：先停止Zygote的4个Daemon子线程（java堆内存整理线程、对线下引用队列线程、析构线程以及监控线程）的运行以及初始化gc堆；

>- nativeForkAndSpecialize()：调用linux的fork()出新进程，创建Java堆处理的线程池，重置gc性能数据，设置进程的信号处理函数，启动JDWP线程；

>- postForkCommon()：在启动之前被暂停的4个Daemon子线程**。**

3. **新进程**：进入handleChildProc()方法，设置进程名，打开binder驱动，启动新的binder线程；然后设置art虚拟机参数，再反射调用目标类的main()方法，即Activity.main()方法。


再之后的流程，如果是startActivity则将要进入Activity的onCreate/onStart/onResume等生命周期；如果是startService则将要进入Service的onCreate等生命周期。

system_server进程等待zygote返回进程创建完成(ZygoteConnection.handleParentProc), **一旦Zygote.forkAndSpecialize()方法执行完成, 那么分道扬镳, zygote告知system_server进程进程已创建, 而子进程继续执行后续的handleChildProc操作**.


RuntimeInit.java的方法nativeZygoteInit()会调用到onZygoteInit()，这个过程中有startThreadPool()创建Binder线程池。也就是说**每个进程无论是否包含任何activity等组件，一定至少会包含一个Binder线程**