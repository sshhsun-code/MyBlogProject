>参考文章[：http://qiangbo.space/2017-02-12/AndroidAnatomy_Binder_CPP/](http://qiangbo.space/2017-02-12/AndroidAnatomy_Binder_CPP/)


## Binder Framework Java层到C++层的衔接关系

![image](http://o9m6aqy3r.bkt.clouddn.com//Binder/Binder_JNI.png)


## C++层的主要结构

Binder Framework 的C++部分称之为libbinder

libbinder中，将实现分为Proxy和Native两端。Proxy对应了上文提到的Client端，是服务对外提供的接口。而Native是服务实现的一端，对应了上文提到的Server端。类名中带有小写字母p的（例如BpInterface），就是指Proxy端。类名带有小写字母n的（例如BnInterface），就是指Native端。

Proxy代表了调用方，通常与服务的实现不在同一个进程，因此下文中，我们也称Proxy端为“远程”端。Native端是服务实现的自身，因此下文中，我们也称Native端为”本地“端。

这里，我们对libbinder中的主要类做一个简要说明，了解一下它们的关系，然后再详细的讲解：


类名|	说明
---|---
BpRefBase	|RefBase的子类，提供remote方法获取远程Binder
IInterface	|Binder服务接口的基类，Binder服务通常需要同时提供本地接口和远程接口
BpInterface	|远程接口的基类，远程接口是供客户端调用的接口集
BnInterface	|本地接口的基类，本地接口是需要服务中真正实现的接口集
IBiner	|Binder对象的基类，BBinder和BpBinder都是这个类的子类
BpBinder	|远程Binder，这个类提供transact方法来发送请求，BpXXX实现中会用到
BBinder	|本地Binder，服务实现方的基类，提供了onTransact接口来接收请求
ProcessState	|代表了使用Binder的进程
IPCThreadState	|代表了使用Binder的线程，这个类中封装了与Binder驱动通信的逻辑
Parcel	|在Binder上传递的数据的包装器

下图描述了这些类之间的关系：
- 服务的接口使用I字母作为前缀
- 远程接口使用Bp作为前缀
- 本地接口使用Bn作为前缀
 



![image](http://o9m6aqy3r.bkt.clouddn.com//Binder/binder_middleware.png)

其中`IBinder`这个类描述了所有在Binder上传递的对象，它既是Binder本地对象BBinder的父类，也是Binder远程对象BpBinder的父类。这个类中的主要方法说明如下：


方法名	|说明
---|---
localBinder	|获取本地Binder对象
remoteBinder	|获取远程Binder对象
transact	|进行一次Binder操作
queryLocalInterface	|尝试获取本地Binder，如何失败返回NULL
getInterfaceDescriptor	|获取Binder的服务接口描述，其实就是Binder服务的唯一标识
isBinderAlive	|查询Binder服务是否还活着
pingBinder	|发送PING_TRANSACTION给Binder服务

`BpBinder`**的实例代表了远程Binder，这个类的对象将被客户端调用**。其中handle方法会返回指向Binder服务实现者的句柄，这个类最重要就是提供了transact方法，这个方法会将远程调用的参数封装好发送的Binder驱动。

由于每个Binder服务通常都会提供多个服务接口，而这个方法中的uint32_t code参数就是用来对服务接口进行编号区分的。**Binder服务的每个接口都需要指定一个唯一的code，这个code要在Proxy和Native端配对好**。当客户端将请求发送到服务端的时候，服务端根据这个code（onTransact方法中）来区分调用哪个接口方法。

`BBinder`**的实例代表了本地Binder，它描述了服务的提供方**，所有Binder服务的实现者都要继承这个类（的子类），在继承类中，最重要的就是实现onTransact方法，因为这个方法是所有请求的入口。因此，这个方法是和BpBinder中的transact方法对应的，这个方法同样也有一个uint32_t code参数，在这个方法的实现中，由服务提供者通过code对请求的接口进行区分，然后调用具体实现服务的方法。

每个Binder服务都是为了某个功能而实现的，因此其本身会定义一套接口集（通常是C++的一个类）来描述自己提供的所有功能。而Binder服务既有自身实现服务的类，也要有给客户端进程调用的类。为了便于开发，这两中类里面的服务接口应当是一致的，例如：假设服务实现方提供了一个接口为add(int a, int b)的服务方法，那么其远程接口中也应当有一个add(int a, int b)方法。因此为了实现方便，本地实现类和远程接口类需要有一个公共的描述服务接口的基类（即上图中的IXXXService）来继承。而这个基类通常是IInterface的子类。

![image](http://o9m6aqy3r.bkt.clouddn.com//Binder/BpBinder_BBinder.png)

## Binder的初始化

任何使用Binder机制的进程都必须要对/dev/binder设备进行open以及mmap之后才能使用，这部分逻辑是所有使用Binder机制进程共同的。对于这种共同逻辑的封装便是Framework层的职责之一。libbinder中，ProcessState类封装了这个逻辑

```C
ProcessState::ProcessState()
    : mDriverFD(open_driver())//初始化mDriverFD的时候调用了open_driver方法打开binder设备
    , mVMStart(MAP_FAILED)
    , mThreadCountLock(PTHREAD_MUTEX_INITIALIZER)
    , mThreadCountDecrement(PTHREAD_COND_INITIALIZER)
    , mExecutingThreadsCount(0)
    , mMaxThreads(DEFAULT_MAX_BINDER_THREADS)
    , mStarvationStartTimeMs(0)
    , mManagesContexts(false)
    , mBinderContextCheckFunc(NULL)
    , mBinderContextUserData(NULL)
    , mThreadPoolStarted(false)
    , mThreadPoolSeq(1)
{
    if (mDriverFD >= 0) {//通过mmap进行内存映射
        mVMStart = mmap(0, BINDER_VM_SIZE, PROT_READ, MAP_PRIVATE | MAP_NORESERVE, mDriverFD, 0);
        if (mVMStart == MAP_FAILED) {
            // *sigh*
            ALOGE("Using /dev/binder failed: unable to mmap transaction memory.\n");
            close(mDriverFD);
            mDriverFD = -1;
        }
    }

    LOG_ALWAYS_FATAL_IF(mDriverFD < 0, "Binder driver could not be opened.  Terminating.");
}
```

初始化mDriverFD的时候调用了open_driver方法，`open_driver`的函数实现如下所示。在这个函数中完成了三个工作：
- 首先通过open系统调用打开了dev/binder设备
- 然后通过ioctl获取Binder实现的版本号，并检查是否匹配
- 最后通过ioctl设置进程支持的最大线程数量

`ProcessState`是一个Singleton（单例）类型的类，在一个进程中，只会存在一个实例。通过ProcessState::self()接口获取这个实例。一旦获取这个实例，便会执行其构造函数，由此完成了对于Binder设备的初始化工作。

**关于Binder传递数据的大小限制**

由于Binder的数据需要跨进程传递，并且还需要在内核上开辟空间，因此允许在Binder上传递的数据并不是无无限大的。mmap中指定的大小便是对数据传递的大小限制：

```C
#define BINDER_VM_SIZE ((1*1024*1024) - (4096 *2)) // 1M - 8k

mVMStart = mmap(0, BINDER_VM_SIZE, PROT_READ, MAP_PRIVATE | MAP_NORESERVE, mDriverFD, 0);

```

进行mmap的时候，指定了最大size为BINDER_VM_SIZE，即 1M - 8k的大小。 因此我们在开发过程中，**一次Binder调用的数据总和不能超过这个大小**.否则会报`TransactionTooLargeException`异常。

最底层原因是binder驱动的`binder_transaction`函数中会调用`binder_alloc_buf`在**通信目标进程内核缓存区中分配一段合适的内存.**
如果**超过mmap时分配的大小就会报错**。

## 与驱动的通信

上文提到**ProcessState是一个单例类，一个进程只有一个实例**。而**负责与Binder驱动通信的IPCThreadState**也是一个单例类。**但这个类不是一个进程只有一个实例，而是一个线程有一个实例。**

IPCThreadState负责了与驱动通信的细节处理。这个类中的关键几个方法说明如下：


方法	|说明
---|---
transact	|公开接口。供Proxy发送数据到驱动，并读取返回结果
sendReply	|供Server端写回请求的返回结果
waitForResponse	|发送请求后等待响应结果
talkWithDriver	|通过ioctl BINDER_WRITE_READ来与驱动通信
writeTransactionData	|写入一次事务的数据
executeCommand	|处理binder_driver_return_protocol协议命令
freeBuffer	|通过BC_FREE_BUFFER命令释放Buffer

`BpBinder::transact`方法在发送请求的时候，其实就是直接调用了IPCThreadState对应的方法来发送请求到Binder驱动的，相关代码如下

```C
status_t BpBinder::transact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    if (mAlive) {
        status_t status = IPCThreadState::self()->transact(
            mHandle, code, data, reply, flags);
        if (status == DEAD_OBJECT) mAlive = 0;
        return status;
    }

    return DEAD_OBJECT;
}
```


**`IPCThreadState::transact`方法主要逻辑如下:**

```C
status_t IPCThreadState::transact(int32_t handle,
                                  uint32_t code, const Parcel& data,
                                  Parcel* reply, uint32_t flags)
{
    status_t err = data.errorCheck();

    flags |= TF_ACCEPT_FDS;

    if (err == NO_ERROR) {
        err = writeTransactionData(BC_TRANSACTION, flags, handle, code, data, NULL);
    }

    if (err != NO_ERROR) {
        if (reply) reply->setError(err);
        return (mLastError = err);
    }

    if ((flags & TF_ONE_WAY) == 0) {
        if (reply) {
            err = waitForResponse(reply);
        } else {
            Parcel fakeReply;
            err = waitForResponse(&fakeReply);
        }
    } else {
        err = waitForResponse(NULL, NULL);
    }

    return err;
}
```
首先通过`writeTransactionData`写入数据，然后通过`waitForResponse`等待返回结果。**`TF_ONE_WAY`表示此次请求是单向的，即：不用真正等待结果即可返回**.

而`writeTransactionData`方法其实就是在组装`binder_transaction_data`数据：

```C
status_t IPCThreadState::writeTransactionData(int32_t cmd, uint32_t binderFlags,
    int32_t handle, uint32_t code, const Parcel& data, status_t* statusBuffer)
{
    binder_transaction_data tr;

    tr.target.ptr = 0; /* Don't pass uninitialized stack data to a remote process */
    tr.target.handle = handle;//设置要通信的进程的handler.在transaction中找对应的(binder_node)targer_node.
    tr.code = code;
    tr.flags = binderFlags;
    tr.cookie = 0;
    tr.sender_pid = 0;
    tr.sender_euid = 0;

    const status_t err = data.errorCheck();
    if (err == NO_ERROR) {
        tr.data_size = data.ipcDataSize();
        tr.data.ptr.buffer = data.ipcData();
        tr.offsets_size = data.ipcObjectsCount()*sizeof(binder_size_t);
        tr.data.ptr.offsets = data.ipcObjects();
    } else if (statusBuffer) {
        tr.flags |= TF_STATUS_CODE;
        *statusBuffer = err;
        tr.data_size = sizeof(status_t);
        tr.data.ptr.buffer = reinterpret_cast<uintptr_t>(statusBuffer);
        tr.offsets_size = 0;
        tr.data.ptr.offsets = 0;
    } else {
        return (mLastError = err);
    }

    mOut.writeInt32(cmd);
    mOut.write(&tr, sizeof(tr));

    return NO_ERROR;
}
```
![image](http://o9m6aqy3r.bkt.clouddn.com//binder/driver/binder_write_read.png)


**数据包装器：Parcel**

Parcel就像一个包装器，调用者可以以任意顺序往里面放入需要的数据，所有写入的数据就像是被打成一个整体的包，然后可以直接在Binde上传输.

对于基本类型，开发者可以直接调用接口写入和读出。而对于非基本类型，需要由开发者将其拆分成基本类型然后写入到Parcel中（读出的时候也是一样）。 Parcel会将所有写入的数据进行打包，Parcel本身可以作为一个整体在进程间传递。接收方在收到Parcel之后，只要按写入同样的顺序读出即可。

Parcel类除了可以传递基本数据类型，还可以传递Binder对象：

```C
status_t Parcel::writeStrongBinder(const sp<IBinder>& val)
{
    return flatten_binder(ProcessState::self(), val, this);
}
```
这个方法写入的是sp<IBinder>类型的对象，而IBinder既可能是本地Binder，也可能是远程Binder，这样我们就不可以不用关心具体细节直接进行Binder对象的传递

**Binder驱动并不是真的将对象在进程间序列化传递，而是由Binder驱动完成了对于Binder对象指针的解释和翻译，使调用者看起来就像在进程间传递对象一样。**  

## Framework层的线程管理

`ProcessState::setThreadPoolMaxThreadCount` 方法中，会通过`BINDER_SET_MAX_THREADS`命令设置进程支持的最大线程数量：

```C
#define DEFAULT_MAX_BINDER_THREADS 15

status_t ProcessState::setThreadPoolMaxThreadCount(size_t maxThreads) {
    status_t result = NO_ERROR;
    if (ioctl(mDriverFD, BINDER_SET_MAX_THREADS, &maxThreads) != -1) {
        mMaxThreads = maxThreads;
    } else {
        result = -errno;
        ALOGE("Binder ioctl to set max threads failed: %s", strerror(-result));
    }
    return result;
}
```

驱动在运行过程中，会根据需要，并在没有超过上限的情况下，通过`BR_SPAWN_LOOPER`命令通知进程创建线程 

IPCThreadState在收到`BR_SPAWN_LOOPER`请求之后，便会调用`ProcessState::spawnPooledThread`来创建线程：

```C
status_t IPCThreadState::executeCommand(int32_t cmd)
{
    ...
    case BR_SPAWN_LOOPER:
        mProcess->spawnPooledThread(false);
        break;
    ...
}

---------------------------------------------------------


void ProcessState::spawnPooledThread(bool isMain)
{
    if (mThreadPoolStarted) {
        String8 name = makeBinderThreadName();
        ALOGV("Spawning new pooled thread, name=%s\n", name.string());
        sp<Thread> t = new PoolThread(isMain);
        t->run(name.string());
    }
}

```

线程在run之后，会调用threadLoop将自身添加的线程池中：

```C
virtual bool threadLoop()
{
   IPCThreadState::self()->joinThreadPool(mIsMain);
   return false;
}

```
而IPCThreadState::joinThreadPool方法中，会根据当前线程是否是主线程发送`BC_ENTER_LOOPER`或者`BC_REGISTER_LOOPER`命令告知驱动线程已经创建完毕。整个调用流程如下图所示：

![image](http://o9m6aqy3r.bkt.clouddn.com//Binder/create_thread_sequence.png)



## C++ Binder服务举例

以一个具体的Binder服务例子来结合上文的知识进行讲解。

下面以PowerManager为例，来看看C++的Binder服务是如何实现的

![image](http://o9m6aqy3r.bkt.clouddn.com//Binder/Binder_PowerManager.png)

**`IPowerManager`定义了PowerManager所有对外提供的功能接口，其子类都继承了这些接口。**

- BpPowerManager是提供给客户端调用的远程接口
- BnPowerManager中只有一个onTransact方法，该方法根据请求的code来对接每个请求，并直接调用PowerManager中对应的方法
- PowerManager是服务真正的实现

### 本地实现：Native端

服务的本地实现主要就是实现BnPowerManager和PowerManager两个类，PowerManager是BnPowerManager的子类，因此在BnPowerManager中调用自身的virtual方法其实都是在子类PowerManager类中实现的。

BnPowerManager类要做的就是复写onTransact方法，这个方法的职责是：根据请求的code区分具体调用的是那个接口，然后按顺序从Parcel中读出打包好的参数，接着调用留待子类实现的虚函数。需要注意的是：**这里从Parcel读出参数的顺序需要和BpPowerManager中写入的顺序完全一致，否则读出的数据将是无效的。**

电源服务包含了好几个接口。虽然每个接口的实现逻辑各不一样，但从Binder框架的角度来看，它们的实现结构是一样。而这里我们并不关心电源服务的实现细节，因此我们取其中一个方法看其实现方式即可。

```C
status_t BnPowerManager::onTransact(uint32_t code,
                                    const Parcel& data,
                                    Parcel* reply,
                                    uint32_t flags) {
  switch (code) {
  ...
      case IPowerManager::REBOOT: {
      CHECK_INTERFACE(IPowerManager, data, reply);
      bool confirm = data.readInt32();
      String16 reason = data.readString16();
      bool wait = data.readInt32();
      return reboot(confirm, reason, wait);
    }
  ...
  }
}
```
代码中我们看到了实现中是如何根据code区分接口，并通过Parcel读出调用参数，然后调用具体服务方的。

而PowerManager这个类才真正是服务实现的本体，reboot方法真正实现了重启的逻辑：

```C
status_t PowerManager::reboot(bool confirm, const String16& reason, bool wait) {
  const std::string reason_str(String8(reason).string());
  if (!(reason_str.empty() || reason_str == kRebootReasonRecovery)) {
    LOG(WARNING) << "Ignoring reboot request with invalid reason \""
                 << reason_str << "\"";
    return BAD_VALUE;
  }

  LOG(INFO) << "Rebooting with reason \"" << reason_str << "\"";
  if (!property_setter_->SetProperty(ANDROID_RB_PROPERTY,
                                     kRebootPrefix + reason_str)) {
    return UNKNOWN_ERROR;
  }
  return OK;
}

```

这样结构的设计，将框架相关的逻辑（BnPowerManager中的实现）和业务本身的逻辑（PowerManager中的实现）彻底分离开了，保证每一个类都非常的“干净”，这一点是很值得我们在做软件设计时学习的

### 服务的发布

服务实现完成之后，并不是立即就能让别人使用的。上文中，我们就说到过：所有在Binder上发布的服务必须要注册到ServiceManager中才能被其他模块获取和使用。而在BinderService类中，提供了publishAndJoinThreadPool方法来简化服务的发布，其代码如下：
```C
static void publishAndJoinThreadPool(bool allowIsolated = false) {
   publish(allowIsolated);
   joinThreadPool();
}

static status_t publish(bool allowIsolated = false) {
   sp<IServiceManager> sm(defaultServiceManager());
   return sm->addService(
           String16(SERVICE::getServiceName()),
           new SERVICE(), allowIsolated);
}

...

static void joinThreadPool() {
   sp<ProcessState> ps(ProcessState::self());
   ps->startThreadPool();
   ps->giveThreadPoolName();
   IPCThreadState::self()->joinThreadPool();
}

```

由此可见，**Binder服务的发布其实有三个步骤**：
1. 通过`IServiceManager::addService在ServiceManager`中进行服务的注册
2. 通过`ProcessState::startThreadPool`启动线程池
3. 通过`IPCThreadState::joinThreadPool`将主线程加入的Binder中


### 远程接口：Proxy端

Proxy类是供客户端使用的。BpPowerManager需要实现IPowerManager中的所有接口。

我们还是以上文提到的reboot接口为例，来看看BpPowerManager::reboot方法是如何实现的：

```C
virtual status_t reboot(bool confirm, const String16& reason, bool wait)
{
   Parcel data, reply;
   data.writeInterfaceToken(IPowerManager::getInterfaceDescriptor());
   data.writeInt32(confirm);
   data.writeString16(reason);
   data.writeInt32(wait);
   return remote()->transact(REBOOT, data, &reply, 0);
}
```

通过Parcel写入调用参数进行打包，然后调用remote()->transact将请求发送出去。

所有其他BpXXX中所有的方法，实现都是和这个方法一样的套路。就是：通过Parcel打包数据，通过remote()->transact发送数据。而这里的remote返回的其实就是BpBinder对象，由此经由IPCThreadState将数据发送到了驱动层。

![image](http://o9m6aqy3r.bkt.clouddn.com//Binder/BpBinder_BBinder.png)

这里的REBOOT就是请求的code，而这个code是在IPowerManager中定义好的，这样子类可以直接使用，并保证是一致的：
```C
enum {
   ACQUIRE_WAKE_LOCK            = IBinder::FIRST_CALL_TRANSACTION,
   ACQUIRE_WAKE_LOCK_UID        = IBinder::FIRST_CALL_TRANSACTION + 1,
   RELEASE_WAKE_LOCK            = IBinder::FIRST_CALL_TRANSACTION + 2,
   UPDATE_WAKE_LOCK_UIDS        = IBinder::FIRST_CALL_TRANSACTION + 3,
   POWER_HINT                   = IBinder::FIRST_CALL_TRANSACTION + 4,
   UPDATE_WAKE_LOCK_SOURCE      = IBinder::FIRST_CALL_TRANSACTION + 5,
   IS_WAKE_LOCK_LEVEL_SUPPORTED = IBinder::FIRST_CALL_TRANSACTION + 6,
   USER_ACTIVITY                = IBinder::FIRST_CALL_TRANSACTION + 7,
   WAKE_UP                      = IBinder::FIRST_CALL_TRANSACTION + 8,
   GO_TO_SLEEP                  = IBinder::FIRST_CALL_TRANSACTION + 9,
   NAP                          = IBinder::FIRST_CALL_TRANSACTION + 10,
   IS_INTERACTIVE               = IBinder::FIRST_CALL_TRANSACTION + 11,
   IS_POWER_SAVE_MODE           = IBinder::FIRST_CALL_TRANSACTION + 12,
   SET_POWER_SAVE_MODE          = IBinder::FIRST_CALL_TRANSACTION + 13,
   REBOOT                       = IBinder::FIRST_CALL_TRANSACTION + 14,
   SHUTDOWN                     = IBinder::FIRST_CALL_TRANSACTION + 15,
   CRASH                        = IBinder::FIRST_CALL_TRANSACTION + 16,
};
```

### 服务的获取

- 先尝试通过queryLocalInterface看看能够获得本地Binder，如果是在服务所在进程调用，自然能获取本地Binder，否则将返回NULL
- 如果获取不到本地Binder，则创建并返回一个远程Binder。
 
**由此保证了：我们在进程内部的调用，是直接通过方法调用的形式。而不在同一个进程的时候，才通过Binder进行跨进程的调用。**


## C++层的ServiceManager

**ServiceManager是整个Binder IPC的控制中心和交通枢纽。这里我们就来看一下这个模块的具体实现.**

ServiceManager是一个独立的可执行文件，在设备中的进程名称是/system/bin/servicemanager，这个也是其可执行文件的路径。

ServiceManager实现源码的位于这个路径：`frameworks/native/cmds/servicemanager/`
其main函数主要实现如下：

```C
int main()
{
    struct binder_state *bs;

    bs = binder_open(128*1024);//分配128K的内核缓冲区
    if (!bs) {
        ALOGE("failed to open binder driver\n");
        return -1;
    }

    if (binder_become_context_manager(bs)) {//自己注册成为context_manager
        ALOGE("cannot become context manager (%s)\n", strerror(errno));
        return -1;
    }
    ...

    binder_loop(bs, svcmgr_handler);

    return 0;
}
```

这段代码很简单，主要做了三件事情：

1. `binder_open(128*1024)` 是打开Binder，并指定缓存大小为128k，由于ServiceManager提供的接口很简单（下文会讲到），因此并不需要普通进程那么多（1M - 8K）的缓存
2. `binder_become_context_manager(bs)` 使自己成为Context Manager。这里的Context Manager是Binder驱动里面的名称，等同于ServiceManager。`binder_become_context_manager`的方法实现只有一行代码：`ioctl(bs->fd, BINDER_SET_CONTEXT_MGR, 0)`
3. `binder_loop(bs, svcmgr_handler)` 是在Looper上循环，等待其他模块请求服务

service_manager.c中的实现与普通Binder服务的实现有些不一样：**++并没有通过继承接口类来实现，而是通过几个c语言的函数来完成了实现++**。这个文件中的主要方法如下：


方法名称	|方法说明
---|---
main	|可执行文件入口函数，刚刚已经做过说明
svcmgr_handler	|请求的入口函数，类似于普通Binder服务的onTransact
do_add_service	|注册一个Binder服务
do_find_service	|通过名称查找一个已经注册的Binder服务

ServiceManager中，通过`svcinfo`结构体来描述已经注册的Binder服务：

```C
struct svcinfo
{
    struct svcinfo *next;
    uint32_t handle;
    struct binder_death death;
    int allow_isolated;
    size_t len;
    uint16_t name[0];
};
```
`next`是一个指针，指向下一个服务，通过这个指针将所有服务串成了链表。`handle`是指向Binder服务的句柄，这个句柄是由Binder驱动翻译，指向了Binder服务的实体（参见驱动中：Binder中的“面向对象”），name是服务的名称。

`ServiceManager`的实现逻辑并不复杂，这个模块就好像在整个系统上提供了一个全局的HashMap而已：通过服务名称进行服务注册，然后再通过服务名称来查找。而**真正复杂的逻辑其实都是在Binder驱动中实现了**

ServiceManager提供的接口只有四个，这四个接口说明如下

接口名称	|接口说明
---|---
addService	|向ServiceManager中注册一个新的Service
getService	|查询Service。如果服务不存在，将阻塞数秒
checkService	|查询Service，但是不会阻塞
listServices	|列出所有的服务

在libbinder中，提供了一个defaultServiceManager方法来获取ServiceManager的Proxy，并且这个方法不需要传入参数。原因我们在驱动篇中也已经讲过了：Binder的实现中，为ServiceManager留了一个特殊的位置，不需要像普通服务那样通过标识去查找。defaultServiceManager代码如下：

```C
sp<IServiceManager> defaultServiceManager()
{
    if (gDefaultServiceManager != NULL) return gDefaultServiceManager;

    {
        AutoMutex _l(gDefaultServiceManagerLock);
        while (gDefaultServiceManager == NULL) {
            gDefaultServiceManager = interface_cast<IServiceManager>(
                ProcessState::self()->getContextObject(NULL));
            if (gDefaultServiceManager == NULL)
                sleep(1);
        }
    }

    return gDefaultServiceManager;
}
```