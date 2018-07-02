# Android Binder 线程池管理

1. Binder线程创建
2. Binder线程使用管理
3. 关于Binder线程的一些问题
4. 总结

## Binder线程创建

Binder线程创建与其所在进程的创建中产生，Java层进程的创建都是通过`Process.start()`方法，向Zygote进程发出创建进程的socket消息，Zygote收到消息后会调用`Zygote.forkAndSpecialize()`来fork出新进程，在新进程中会调用到RuntimeInit.nativeZygoteInit方法，该方法经过jni映射，最终会调用到app_main.cpp中的 `onZygoteInit`

onZygoteInit:

```
virtual void onZygoteInit() {
    //获取ProcessState对象
    sp<ProcessState> proc = ProcessState::self();
    //启动新binder线程 
    proc->startThreadPool();
}
```

- ProcessState::self()是单例模式，主要工作是调用open()打开/dev/binder驱动设备，再利用mmap()映射内核的地址空间，将Binder驱动的fd赋值ProcessState对象中的变量mDriverFD，用于交互操作。
- startThreadPool()是创建一个新的binder线程，不断进行talkWithDriver()。


startThreadPool:


```
void ProcessState::startThreadPool()
{
    AutoMutex _l(mLock);    //多线程同步
    if (!mThreadPoolStarted) {
        mThreadPoolStarted = true;
        spawnPooledThread(true);  //创建Binder主线程
    }
}
```

- 本次创建的是binder主线程(isMain=true). 其余binder线程池中的线程都是由Binder驱动来控制创建的。

spawnPooledThread:


```
void ProcessState::spawnPooledThread(bool isMain)
{
    if (mThreadPoolStarted) {
        //获取Binder线程名
        String8 name = makeBinderThreadName();
        //此处isMain=true
        sp<Thread> t = new PoolThread(isMain);
        t->run(name.string());
    }
}
```
- 每个进程中的binder编码是从1开始，依次递增; 只有通过spawnPooledThread方法来创建的线程才符合这个格式，对于直接将当前线程通过joinThreadPool加入线程池的线程名则不符合这个命名规则。 另外,目前Android N中Binder命令已改为Binder:<pid>_x格式, 则对于分析问题很有帮忙,通过binder名称的pid字段可以快速定位该binder线程所属的进程p.

PoolThread.run:


```
class PoolThread : public Thread
{
public:
    PoolThread(bool isMain)
        : mIsMain(isMain)
    {
    }

protected:
    virtual bool threadLoop()  {
        IPCThreadState::self()->joinThreadPool(mIsMain); //加入Binder线程池，处理Binder请求
        return false;
    }
    const bool mIsMain;
};
```

- 该PoolThread继承Thread类。t->run()方法最终调用 PoolThread的threadLoop()方法。

joinThreadPool:

```
void IPCThreadState::joinThreadPool(bool isMain)
{
    //创建Binder线程
    mOut.writeInt32(isMain ? BC_ENTER_LOOPER : BC_REGISTER_LOOPER);
    set_sched_policy(mMyThreadId, SP_FOREGROUND); //设置前台调度策略

    status_t result;
    do {
        processPendingDerefs(); 
        result = getAndExecuteCommand(); //处理指令，处理Binder请求

        if (result < NO_ERROR && result != TIMED_OUT
                && result != -ECONNREFUSED && result != -EBADF) {
            abort();
        }

        if(result == TIMED_OUT && !isMain) {
            break; ////非主线程出现timeout则线程退出
        }
    } while (result != -ECONNREFUSED && result != -EBADF);//循环阻塞

    mOut.writeInt32(BC_EXIT_LOOPER);  // 线程退出循环
    talkWithDriver(false); //false代表bwr数据的read_buffer为空
}
```

- 对于isMain=true的情况下， command为BC_ENTER_LOOPER，代表的是Binder主线程，不会退出的线程；
- 对于isMain=false的情况下，command为BC_REGISTER_LOOPER，表示是由binder驱动创建的线程。


getAndExecuteCommand：


```
status_t IPCThreadState::getAndExecuteCommand()
{
    status_t result;
    int32_t cmd;

    result = talkWithDriver(); //与binder驱动进行交互
    if (result >= NO_ERROR) {
        size_t IN = mIn.dataAvail();
        if (IN < sizeof(int32_t)) return result;
        cmd = mIn.readInt32();

        pthread_mutex_lock(&mProcess->mThreadCountLock);
        mProcess->mExecutingThreadsCount++;
        pthread_mutex_unlock(&mProcess->mThreadCountLock);

        result = executeCommand(cmd); //执行Binder响应码

        pthread_mutex_lock(&mProcess->mThreadCountLock);
        mProcess->mExecutingThreadsCount--;
        pthread_cond_broadcast(&mProcess->mThreadCountDecrement);
        pthread_mutex_unlock(&mProcess->mThreadCountLock);

        set_sched_policy(mMyThreadId, SP_FOREGROUND);
    }
    return result;
}

```

talkWithDriver 处理与驱动的交互，得到驱动的指令


```
//mOut有数据，mIn还没有数据。doReceive默认值为true
status_t IPCThreadState::talkWithDriver(bool doReceive)
{
    binder_write_read bwr;
    ...
    // 当同时没有输入和输出数据则直接返回
    if ((bwr.write_size == 0) && (bwr.read_size == 0)) return NO_ERROR;
    ...

    do {
        //ioctl执行binder读写操作，经过syscall，进入Binder驱动。调用Binder_ioctl
        if (ioctl(mProcess->mDriverFD, BINDER_WRITE_READ, &bwr) >= 0)
            err = NO_ERROR;
        ...
    } while (err == -EINTR);
    ...
    return err;
}
```
- `talkWithDriver`: 其实质还是去掉用ioctl(mProcess->mDriverFD, BINDER_WRITE_READ, &bwr) >= 0)去不断的监听Binder字符设备，获取到Client传输的数据后，再通过executeCommand去执行相应的请求

executeCommand 处理Binder字节码，执行具体指令

```
status_t IPCThreadState::executeCommand(int32_t cmd)
{
    status_t result = NO_ERROR;
    switch ((uint32_t)cmd) {
      ...
      case BR_SPAWN_LOOPER:
          //创建新的binder线程 
          mProcess->spawnPooledThread(false);
          break;
      ...
    }
    return result;
}
```

executeCommand一定是从Bindr驱动返回的BR命令，这里是BR_SPAWN_LOOPER，什么时候，Binder驱动会向进程发送BR_SPAWN_LOOPER呢？**全局搜索之后，发现只有一个地方`binder_thread_read`**

**什么时候需要新建Binder线程呢？很简单，不够用的时候.同时注意spawnPooledThread(false)，这里启动的都是普通Binder线程。**


`binder_thread_read`:


```
binder_thread_read（）{
  ...
 retry:
    //当前线程todo队列为空且transaction栈为空，则代表该线程是空闲的 ，看看是不是自己被复用了
    wait_for_proc_work = thread->transaction_stack == NULL &&
        list_empty(&thread->todo);
 ...//可用线程个数+1
    if (wait_for_proc_work)
        proc->ready_threads++; 
    binder_unlock(__func__);
    if (wait_for_proc_work) {
        ...
            //当进程todo队列没有数据,则进入休眠等待状态
            ret = wait_event_freezable_exclusive(proc->wait, binder_has_proc_work(proc, thread));
    } else {
        if (non_block) {
            ...
        } else
            //当线程todo队列没有数据，则进入休眠等待状态
            ret = wait_event_freezable(thread->wait, binder_has_thread_work(thread));
    }    
    binder_lock(__func__);
    //被唤醒可用线程个数-1
    if (wait_for_proc_work)
        proc->ready_threads--; 
    thread->looper &= ~BINDER_LOOPER_STATE_WAITING;
    ...
    while (1) {
        uint32_t cmd;
        struct binder_transaction_data tr;
        struct binder_work *w;
        struct binder_transaction *t = NULL;

        //先考虑从线程todo队列获取事务数据
        if (!list_empty(&thread->todo)) {
            w = list_first_entry(&thread->todo, struct binder_work, entry);
        //线程todo队列没有数据, 则从进程todo对获取事务数据
        } else if (!list_empty(&proc->todo) && wait_for_proc_work) {
            w = list_first_entry(&proc->todo, struct binder_work, entry);
        } else {
        }
         ..
        if (t->buffer->target_node) {
            cmd = BR_TRANSACTION;  //设置命令为BR_TRANSACTION
        } else {
            cmd = BR_REPLY; //设置命令为BR_REPLY
        }
        .. 
done:
    *consumed = ptr - buffer;
    //创建线程的条件
    if (proc->requested_threads + proc->ready_threads == 0 &&
        proc->requested_threads_started < proc->max_threads &&
        (thread->looper & (BINDER_LOOPER_STATE_REGISTERED |
         BINDER_LOOPER_STATE_ENTERED))) {
         //需要新建的数目线程数+1
        proc->requested_threads++;
        // 生成BR_SPAWN_LOOPER命令，用于创建新的线程
        put_user(BR_SPAWN_LOOPER, (uint32_t __user *)buffer)；
    }
    return 0;
}

```
其中创建新Binder线程的条件为：
```
if (proc->requested_threads + proc->ready_threads == 0 &&
            proc->requested_threads_started < proc->max_threads &&
            (thread->looper & (BINDER_LOOPER_STATE_REGISTERED |
             BINDER_LOOPER_STATE_ENTERED)))
```
即：
- proc->requested_threads + proc->ready_threads == 0 ：如果目前还没申请新建Binder线程，并且proc->ready_threads空闲Binder线程也是0，就**需要新建一个Binder线程，其实就是为了保证有至少有一个空闲的线程**。

- proc->requested_threads_started < proc->max_threads：目前启动的普通Binder线程数requested_threads_started还没达到上限（默认APP进程是15）

- thread->looper & (BINDER_LOOPER_STATE_REGISTERED | BINDER_LOOPER_STATE_ENTERED) 当前线程已接收到BC_ENTER_LOOPER或者BC_REGISTER_LOOPER命令，即当前处于BINDER_LOOPER_STATE_REGISTERED或者BINDER_LOOPER_STATE_ENTERED状态。


## Binder线程使用管理

**驱动层的线程管理**

Binder本身是C/S架构。由Server提供服务，被Client使用。既然是C/S架构，就可能存在多个Client会同时访问Server的情况。 在这种情况下，如果Server只有一个线程处理响应，就会导致客户端的请求可能需要排队而导致响应过慢的现象发生。解决这个问题的方法就是引入多线程。

Binder机制的设计从最底层–驱动层，就考虑到了对于多线程的支持。具体内容如下：

- 使用Binder的进程在启动之后，通过BINDER_SET_MAX_THREADS告知驱动其支持的最大线程数量
驱动会对线程进行管理。在binder_proc结构中，这些字段记录了进程中线程的信息：max_threads，requested_threads，requested_threads_started，ready_threads
- binder_thread结构对应了Binder进程中的线程
- 驱动通过BR_SPAWN_LOOPER命令告知进程需要创建一个新的线程
- 进程通过BC_ENTER_LOOPER命令告知驱动其主线程已经ready
- 进程通过BC_REGISTER_LOOPER命令告知驱动其子线程（非主线程）已经ready
- 进程通过BC_EXIT_LOOPER命令告知驱动其线程将要退出
- 在线程退出之后，通过BINDER_THREAD_EXIT告知Binder驱动。驱动将对应的binder_thread对象销毁

## 关于Binder线程的一些问题

**1. Android APP有多少Binder线程，是固定的么？**

Android APP进程在Zygote fork之初就为它新建了一个Binder主线程，使得APP端也可以作为Binder的服务端，这个时候Binder线程的数量就只有一个，假设我们的APP自身实现了很多的Binder服务，一个线程够用的吗？这里不妨想想一下SystemServer进程，SystemServer拥有很多系统服务，一个线程应该是不够用的，如果看过SystemServer代码可能会发现，对于Android4.3的源码，其实一开始为该服务开启了两个Binder线程。还有个分析Binder常用的服务，media服务，也是在一开始的时候开启了两个线程。

以SystemServer为例，SystemServer的开始加载的线程：通过

`ProcessState::self()->startThreadPool()`新加了一个Binder线程，然后通过
`IPCThreadState::self()->joinThreadPool()`;将当前线程变成Binder线程，注意这里是针对Android4.3的源码，android6.0的这里略有不同。


```
    ...
    ALOGI("System server: entering thread pool.\n");
    ProcessState::self()->startThreadPool();
    IPCThreadState::self()->joinThreadPool();
    ALOGI("System server: exiting thread pool.\n");
    return NO_ERROR;
```
同理：MediaServer也是开启了两个Binder线程：

```
int main(int argc, char** argv)
{      ...
        ProcessState::self()->startThreadPool();
        IPCThreadState::self()->joinThreadPool();
 }  
```

Android APP上层应用的进程一般是开启一个Binder线程，而对于SystemServer或者media服务等使用频率高，服务复杂的进程，一般都是开启两个或者更多。来看问题，**Binder线程的数目是固定的吗？答案是否定的**，驱动会根据目标进程中是否存在足够多的Binder线程来告诉进程是不是要新建Binder线程。**什么时候需要新建Binder线程呢？很简单，不够用的时候**。

proc->max_threads是多少呢？不同的进程其实设置的是不一样的，看普通的APP进程，在ProcessState::self()新建ProcessState单利对象的时候会调用ioctl(fd, BINDER_SET_MAX_THREADS, &maxThreads);设置上限,可以看到默认设置的上限是15。

采用动态新建Binder线程的意义有两点，

第一：如果没有Client请求服务，就保持线程数不变，减少资源浪费，需要的时候再分配新线程。

第二：有请求的情况下，保证至少有一个空闲线程是给Client端，以提高Server端响应速度。
## 总结

Binder设计架构中，只有第一个Binder主线程(也就是Binder_1线程)是由应用程序主动创建，Binder线程池的普通线程都是由Binder驱动根据IPC通信需求创建，Binder线程的创建流程图：

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/binder_thread_create.jpg)

- 每次由Zygote `fork`出新进程的过程中，伴随着创建binder线程池，调用`spawnPooledThread`来创建binder主线程。

- 当线程执行`binder_thread_read`的过程中，发现当前没有空闲线程，没有请求创建线程，且没有达到上限，则创建新的binder线程。

**Binder系统中可分为3类`binder线程`：**

- Binder主线程：进程创建过程会调用startThreadPool()过程中再进入spawnPooledThread(true)，来创建Binder主线程。编号从1开始，也就是意味着binder主线程名为binder_1，并且主线程是不会退出的。**这个线程不会受到Binder驱动的控制来创建，销毁。**

- Binder普通线程：是由Binder Driver来根据是否有空闲的binder线程来决定是否创建binder线程，回调spawnPooledThread(false) ，isMain=false，该线程名格式为binder_x。**这个线程会受到Binder驱动的控制来创建，销毁。**

- Binder其他线程：其他线程是指并没有调用spawnPooledThread方法，而是直接调用`IPC.joinThreadPool()`，将当前线程直接加入binder线程队列。**这个线程不会受到Binder驱动的控制来创建，销毁**   例如： mediaserver和servicemanager的主线程都是binder线程，但system_server的主线程并非binder线程。

#### 判断一个线程是否是binder线程，就要看其是否调用了`IPC.joinThreadPool()`将自己加入到binder线程池来处理binder请求。

#### 采用动态新建Binder线程的意义有两点：

1. **如果没有Client请求服务，就保持线程数不变，减少资源浪费，需要的时候再分配新线程。**

2. **有请求的情况下，保证至少有一个空闲线程是给Client端，以提高Server端响应速度。**