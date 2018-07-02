
# Android Binder通信架构
>参考 [http://gityuan.com/2016/09/04/binder-start-service/](http://gityuan.com/2016/09/04/binder-start-service/)

1. Binder架构及IPC原理
2. 通信过程
3. Binder driver
4. 回到用户空间
5. Reply流程
6. 总结

## 1. Binder架构及IPC原理

##### Binder架构采用分层架构设计, 每一层都有其不同的功能:

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/binder_ipc_arch.jpg)

- **Java应用层**: 对于上层应用通过调用AMP.startService, 完全可以不用关心底层,经过层层调用,最终必然会调用到AMS.startService.
- **Java IPC层**: Binder通信是采用C/S架构, Android系统的基础架构便已设计好Binder在Java framework层的Binder客户类BinderProxy和服务类Binder;
- **Native IPC层**: 对于Native层,如果需要直接使用Binder(比如media相关), 则可以直接使用BpBinder和BBinder(当然这里还有JavaBBinder)即可, 对于上一层Java IPC的通信也是基于这个层面.
- **Kernel物理层**: 这里是Binder Driver, 前面3层都跑在用户空间,对于用户空间的内存资源是不共享的,每个Android的进程只能运行在自己进程所拥有的虚拟地址空间, 而内核空间却是可共享的. 真正通信的核心环节还是在Binder Driver.


##### Binder IPC原理

Binder通信采用C/S架构，从组件视角来说，包含Client、Server、ServiceManager以及binder驱动，其中ServiceManager用于管理系统中的各种服务

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/ams_ipc.jpg)

图中Client/Server/ServiceManage之间的相互通信都是基于Binder机制。既然基于Binder机制通信，那么同样也是C/S架构，则图中的3大步骤都有相应的Client端与Server端。

- **注册服务**：首先AMS注册到ServiceManager。该过程：AMS所在进程(system_server)是客户端，ServiceManager是服务端。
- **获取服务**：Client进程使用AMS前，须先向ServiceManager中获取AMS的代理类AMP。该过程：AMP所在进程(app process)是客户端，ServiceManager是服务端。
- **使用服务**： app进程根据得到的代理类AMP,便可以直接与AMS所在进程交互。该过程：AMP所在进程(app process)是客户端，AMS所在进程(system_server)是服务端

Client,Server,Service Manager之间交互都是虚线表示，是由于它们彼此之间不是直接交互的，而是都通过与Binder Driver进行交互的，从而实现IPC通信方式。其中Binder驱动位于内核空间，Client,Server,Service Manager位于用户空间。Binder驱动和Service Manager可以看做是Android平台的基础架构，而Client和Server是Android的应用层


## 2. 通信过程
以`startService`为例详解Binder通信过程。

![](http://o9m6aqy3r.bkt.clouddn.com//binder/start_server_binder.jpg)

AMP和AMN都是实现了IActivityManager接口,AMS继承于AMN. 其中AMP作为Binder的客户端,运行在各个app所在进程, AMN(或AMS)运行在系统进程system_server.

先用一张图总结整个过程，之后再总结各个部分：

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/%E5%AE%8C%E6%95%B4%E9%80%9A%E4%BF%A1%E8%BF%87%E7%A8%8B.png)

 **1. AMP.startService**
 
 [-> ActivityManagerNative.java ::ActivityManagerProxy]
 
 
```
public ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, String callingPackage, int userId) throws RemoteException {
    //获取或创建Parcel对象
    Parcel data = Parcel.obtain();
    Parcel reply = Parcel.obtain();
    data.writeInterfaceToken(IActivityManager.descriptor);
    data.writeStrongBinder(caller != null ? caller.asBinder() : null);
    service.writeToParcel(data, 0);
    //写入Parcel数据 
    data.writeString(resolvedType);
    data.writeString(callingPackage);
    data.writeInt(userId);

    //通过Binder传递数据
    mRemote.transact(START_SERVICE_TRANSACTION, data, reply, 0);
    //读取应答消息的异常情况
    reply.readException();
    //根据reply数据来创建ComponentName对象
    ComponentName res = ComponentName.readFromParcel(reply);
   
    data.recycle();
    reply.recycle();
    return res;
}
```
**2. mRemote.transact**

**(1) mRemote是什么？**

mRemote的出生,要出先说说ActivityManagerProxy对象(简称AMP)创建说起, <br>AMP是通过ActivityManagerNative.getDefault()来获取的.

AMN.getDefault

```
static public IActivityManager getDefault() 
    
    return gDefault.get();
}
```
gDefault的数据类型为Singleton<IActivityManager>, 这是一个单例模式, 接下来看看Singleto.get()的过程


```
public abstract class Singleton<IActivityManager> {
    public final IActivityManager get() {
        synchronized (this) {
            if (mInstance == null) {
                //首次调用create()来获取AMP对象
                mInstance = create();
            }
            return mInstance;
        }
    }
}
```
首次调用时需要创建,创建完之后保持到mInstance对象,之后可直接使用.

**gDefault.create**
```
private static final Singleton<IActivityManager> gDefault = new Singleton<IActivityManager>() {
    protected IActivityManager create() {
        //获取名为"activity"的服务
        IBinder b = ServiceManager.getService("activity");
        //创建AMP对象
        IActivityManager am = asInterface(b);
        return am;
    }
};
```

**AMN.asInterface**


```
public abstract class ActivityManagerNative extends Binder implements IActivityManager {
    static public IActivityManager asInterface(IBinder obj) {
        if (obj == null) {
            return null;
        }
        //此处obj = BinderProxy, descriptor = "android.app.IActivityManager"; 
        IActivityManager in = (IActivityManager)obj.queryLocalInterface(descriptor);
        if (in != null) { //此处为null
            return in;
        }
        
        return new ActivityManagerProxy(obj);
    }
    ...
}
```
此时obj为BinderProxy对象, 记录着远程进程system_server中AMS服务的binder线程的handle.

**queryLocalInterface**


```
public class Binder implements IBinder {
    //对于Binder对象的调用,则返回值不为空
    public IInterface queryLocalInterface(String descriptor) {
        //mDescriptor的初始化在attachInterface()过程中赋值
        if (mDescriptor.equals(descriptor)) {
            return mOwner;
        }
        return null;
    }
}

final class BinderProxy implements IBinder {
    //BinderProxy对象的调用, 则返回值为空
    public IInterface queryLocalInterface(String descriptor) {
        return null;
    }
}
```
对于Binder IPC的过程中, 同一个进程的调用则会是asInterface()方法返回的便是本地的Binder对象;对于不同进程的调用则会是远程代理对象BinderProxy.

**创建AMP**


```
class ActivityManagerProxy implements IActivityManager {
    public ActivityManagerProxy(IBinder remote)  {
        mRemote = remote;
    }
}
```



**(2) mRemote.transact**

android_os_BinderProxy_transact


```
static jboolean android_os_BinderProxy_transact(JNIEnv* env, jobject obj,
    jint code, jobject dataObj, jobject replyObj, jint flags)
{
    ...
    //将java Parcel转为c++ Parcel
    Parcel* data = parcelForJavaObject(env, dataObj);
    Parcel* reply = parcelForJavaObject(env, replyObj);

    //gBinderProxyOffsets.mObject中保存的是new BpBinder(handle)对象
    IBinder* target = (IBinder*) env->GetLongField(obj, gBinderProxyOffsets.mObject);
    ...

    //此处便是BpBinder::transact()
    status_t err = target->transact(code, *data, reply, flags);
    ...

    //最后根据transact执行具体情况，抛出相应的Exception
    signalExceptionForError(env, obj, err, true , data->dataSize());
    return JNI_FALSE;
}
```

`gBinderProxyOffsets.mObject`中保存的是BpBinder对象, 这是开机时Zygote调用`AndroidRuntime::startReg`方法来完成jni方法的注册.

**BpBinder.transact**


```
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

**IPCThreadState::self()采用单例模式，保证每个线程只有一个实例对象。**

**IPC.transact**


```
status_t IPCThreadState::transact(int32_t handle,
                                  uint32_t code, const Parcel& data,
                                  Parcel* reply, uint32_t flags)
{
    status_t err = data.errorCheck(); //数据错误检查
    flags |= TF_ACCEPT_FDS;
    ....
    if (err == NO_ERROR) {
         // 传输数据 
        err = writeTransactionData(BC_TRANSACTION, flags, handle, code, data, NULL);
    }

    if (err != NO_ERROR) {
        if (reply) reply->setError(err);
        return (mLastError = err);
    }

    // 默认情况下,都是采用非oneway的方式, 也就是需要等待服务端的返回结果
    if ((flags & TF_ONE_WAY) == 0) {
        if (reply) {
            //reply对象不为空 
            err = waitForResponse(reply);
        }else {
            Parcel fakeReply;
            err = waitForResponse(&fakeReply);
        }
    } else {
        err = waitForResponse(NULL, NULL);
    }
    return err;
}
```

`transact`主要过程:
- 先执行`writeTransactionData()`已向Parcel数据类型的mOut写入数据，此时mIn还没有数据
- 然后执行`waitForResponse()`方法，循环执行，直到收到应答消息. 调用`talkWithDriver()`跟驱动交互，收到应答消息，便会写入mIn, 则根据收到的不同响应吗，执行相应的操作。


此处调用waitForResponse根据是否有设置`TF_ONE_WAY`的标记:

- 当已设置oneway时, 则调用waitForResponse(NULL, NULL);
- 当未设置oneway时, 则调用waitForResponse(reply) 或 waitForResponse(&fakeReply)

**IPC.writeTransactionData**


```
status_t IPCThreadState::writeTransactionData(int32_t cmd, uint32_t binderFlags,
    int32_t handle, uint32_t code, const Parcel& data, status_t* statusBuffer)
{
    binder_transaction_data tr;

    tr.target.ptr = 0;
    tr.target.handle = handle; // handle指向AMS
    tr.code = code;            // START_SERVICE_TRANSACTION
    tr.flags = binderFlags;    // 0
    tr.cookie = 0;
    tr.sender_pid = 0;
    tr.sender_euid = 0;

    const status_t err = data.errorCheck();
    if (err == NO_ERROR) {
        // data为startService相关信息
        tr.data_size = data.ipcDataSize();   // mDataSize
        tr.data.ptr.buffer = data.ipcData(); // mData指针
        tr.offsets_size = data.ipcObjectsCount()*sizeof(binder_size_t); //mObjectsSize
        tr.data.ptr.offsets = data.ipcObjects(); //mObjects指针
    }
    ...
    mOut.writeInt32(cmd);         //cmd = BC_TRANSACTION
    mOut.write(&tr, sizeof(tr));  //写入binder_transaction_data数据
    return NO_ERROR;
}
```

将数据写入mOut


**IPC.waitForResponse**


```
status_t IPCThreadState::waitForResponse(Parcel *reply, status_t *acquireResult)
{
    int32_t cmd;
    int32_t err;

    while (1) {
        if ((err=talkWithDriver()) < NO_ERROR) break; 
        err = mIn.errorCheck();
        if (err < NO_ERROR) break; //当存在error则退出循环

         //每当跟Driver交互一次，若mIn收到数据则往下执行一次BR命令
        if (mIn.dataAvail() == 0) continue;

        cmd = mIn.readInt32();

        switch (cmd) {
        case BR_TRANSACTION_COMPLETE:
            //只有当不需要reply, 也就是oneway时 才会跳出循环,否则还需要等待.
            if (!reply && !acquireResult) goto finish; break;

        case BR_DEAD_REPLY:
            err = DEAD_OBJECT;         goto finish;
        case BR_FAILED_REPLY:
            err = FAILED_TRANSACTION;  goto finish;
        case BR_REPLY: ...             goto finish;

        default:
            err = executeCommand(cmd); 
            if (err != NO_ERROR) goto finish;
            break;
        }
    }

finish:
    if (err != NO_ERROR) {
        if (reply) reply->setError(err); //将发送的错误代码返回给最初的调用者
    }
    return err;
}
```

在这个过程中, 收到以下任一BR_命令，处理后便会退出waitForResponse()的状态:

- BR_TRANSACTION_COMPLETE: binder驱动收到BC_TRANSACTION事件后的应答消息; 对于oneway transaction,当收到该消息,则完成了本次Binder通信;
- BR_DEAD_REPLY: 回复失败，往往是线程或节点为空. 则结束本次通信Binder;
- BR_FAILED_REPLY:回复失败，往往是transaction出错导致. 则结束本次通信Binder;
- BR_REPLY: Binder驱动向Client端发送回应消息; 对于非oneway transaction时,当收到该消息,则完整地完成本次Binder通信;
- 其他指令处理见 `IPC.executeCommand`

**IPC.talkWithDriver**


```
//mOut有数据，mIn还没有数据。doReceive默认值为true
status_t IPCThreadState::talkWithDriver(bool doReceive)
{
    binder_write_read bwr;

    const bool needRead = mIn.dataPosition() >= mIn.dataSize();
    const size_t outAvail = (!doReceive || needRead) ? mOut.dataSize() : 0;

    bwr.write_size = outAvail;
    bwr.write_buffer = (uintptr_t)mOut.data();

    if (doReceive && needRead) {
        //接收数据缓冲区信息的填充。当收到驱动的数据，则写入mIn
        bwr.read_size = mIn.dataCapacity();
        bwr.read_buffer = (uintptr_t)mIn.data();
    } else {
        bwr.read_size = 0;
        bwr.read_buffer = 0;
    }

    // 当同时没有输入和输出数据则直接返回
    if ((bwr.write_size == 0) && (bwr.read_size == 0)) return NO_ERROR;

    bwr.write_consumed = 0;
    bwr.read_consumed = 0;
    status_t err;
    do {
        //ioctl执行binder读写操作，经过syscall，进入Binder驱动。调用Binder_ioctl
        if (ioctl(mProcess->mDriverFD, BINDER_WRITE_READ, &bwr) >= 0)
            err = NO_ERROR;
        else
            err = -errno;
        ...
    } while (err == -EINTR);

    if (err >= NO_ERROR) {
        if (bwr.write_consumed > 0) {
            if (bwr.write_consumed < mOut.dataSize())
                mOut.remove(0, bwr.write_consumed);
            else
                mOut.setDataSize(0);
        }
        if (bwr.read_consumed > 0) {
            mIn.setDataSize(bwr.read_consumed);
            mIn.setDataPosition(0);
        }
        return NO_ERROR;
    }
    return err;
}
```
`binder_write_read` 结构体用来与Binder设备交换数据的结构, 通过ioctl与mDriverFD通信，是**真正与Binder驱动进行数据读写交互的过程**。

**IPC.executeCommand**

```
status_t IPCThreadState::executeCommand(int32_t cmd)
{
    BBinder* obj;
    RefBase::weakref_type* refs;
    status_t result = NO_ERROR;

    switch ((uint32_t)cmd) {
    case BR_ERROR: ...
    case BR_OK: ...
    case BR_ACQUIRE: ...
    case BR_RELEASE: ...
    case BR_INCREFS: ...
    case BR_TRANSACTION: ... //Binder驱动向Server端发送消息
    case BR_DEAD_BINDER: ...
    case BR_CLEAR_DEATH_NOTIFICATION_DONE: ...
    case BR_NOOP: ...
    case BR_SPAWN_LOOPER: ... //创建新binder线程
    default: ...
    }
}
```

## 3. Binder driver

**3.1 binder_ioctl**

**由上述过程传递过来的参数为 cmd= `BINDER_WRITE_READ`**
```
static long binder_ioctl(struct file *filp, unsigned int cmd, unsigned long arg)
{
    int ret;
    struct binder_proc *proc = filp->private_data;
    struct binder_thread *thread;

    //当binder_stop_on_user_error>=2时，则该线程加入等待队列并进入休眠状态. 该值默认为0
    ret = wait_event_interruptible(binder_user_error_wait, binder_stop_on_user_error < 2);
    ...
    binder_lock(__func__);
    //查找或创建binder_thread结构体
    thread = binder_get_thread(proc);
    ...
    switch (cmd) {
        case BINDER_WRITE_READ:
            
            ret = binder_ioctl_write_read(filp, cmd, arg, thread);
            break;
        ...
    }
    ret = 0;

err:
    if (thread)
        thread->looper &= ~BINDER_LOOPER_STATE_NEED_RETURN;
    binder_unlock(__func__);
    wait_event_interruptible(binder_user_error_wait, binder_stop_on_user_error < 2);
    return ret;
}
```

- 根据传递过来的文件句柄指针获取相应的binder_proc结构体, 再从中查找binder_thread,如果当前线程已经加入到proc的线程队列则直接返回， 如果不存在则创建binder_thread，并将当前线程添加到当前的proc.

**3.2 binder_ioctl_write_read**


```
static int binder_ioctl_write_read(struct file *filp,
                unsigned int cmd, unsigned long arg,
                struct binder_thread *thread)
{
    int ret = 0;
    struct binder_proc *proc = filp->private_data;
    unsigned int size = _IOC_SIZE(cmd);
    void __user *ubuf = (void __user *)arg;
    struct binder_write_read bwr;
    if (size != sizeof(struct binder_write_read)) {
        ret = -EINVAL;
        goto out;
    }
    //将用户空间bwr结构体拷贝到内核空间
    if (copy_from_user(&bwr, ubuf, sizeof(bwr))) {
        ret = -EFAULT;
        goto out;
    }

    if (bwr.write_size > 0) {
        //将数据放入目标进程
        ret = binder_thread_write(proc, thread,
                      bwr.write_buffer,
                      bwr.write_size,
                      &bwr.write_consumed);
        //当执行失败，则直接将内核bwr结构体写回用户空间，并跳出该方法
        if (ret < 0) {
            bwr.read_consumed = 0;
            if (copy_to_user_preempt_disabled(ubuf, &bwr, sizeof(bwr)))
                ret = -EFAULT;
            goto out;
        }
    }
    if (bwr.read_size > 0) {
        //读取自己队列的数据
        ret = binder_thread_read(proc, thread, bwr.read_buffer,
             bwr.read_size,
             &bwr.read_consumed,
             filp->f_flags & O_NONBLOCK);
        //当进程的todo队列有数据,则唤醒在该队列等待的进程
        if (!list_empty(&proc->todo))
            wake_up_interruptible(&proc->wait);
        //当执行失败，则直接将内核bwr结构体写回用户空间，并跳出该方法
        if (ret < 0) {
            if (copy_to_user_preempt_disabled(ubuf, &bwr, sizeof(bwr)))
                ret = -EFAULT;
            goto out;
        }
    }

    if (copy_to_user(ubuf, &bwr, sizeof(bwr))) {
        ret = -EFAULT;
        goto out;
    }
out:
    return ret;
}   
```

此时arg是一个binder_write_read结构体，mOut数据保存在write_buffer，所以write_size>0，但此时read_size=0。首先,将用户空间bwr结构体拷贝到内核空间,然后执行binder_thread_write()操作

**3.3 binder_thread_write**


```
static int binder_thread_write(struct binder_proc *proc,
            struct binder_thread *thread,
            binder_uintptr_t binder_buffer, size_t size,
            binder_size_t *consumed)
{
    uint32_t cmd;
    void __user *buffer = (void __user *)(uintptr_t)binder_buffer;
    void __user *ptr = buffer + *consumed;
    void __user *end = buffer + size;
    while (ptr < end && thread->return_error == BR_OK) {
        //拷贝用户空间的cmd命令，此时为BC_TRANSACTION
        if (get_user(cmd, (uint32_t __user *)ptr)) -EFAULT;
        ptr += sizeof(uint32_t);
        switch (cmd) {
        case BC_TRANSACTION:
        case BC_REPLY: {
            struct binder_transaction_data tr;
            //拷贝用户空间的binder_transaction_data
            if (copy_from_user(&tr, ptr, sizeof(tr)))   return -EFAULT;
            ptr += sizeof(tr);
          
            binder_transaction(proc, thread, &tr, cmd == BC_REPLY);
            break;
        }
        ...
    }
    *consumed = ptr - buffer;
  }
  return 0;
}
```
不断从binder_buffer所指向的地址获取cmd, 当只有`BC_TRANSACTION`或者`BC_REPLY`时, 则调用binder_transaction()来处理事务.

**3.4 binder_transaction**


```
static void binder_transaction(struct binder_proc *proc,
               struct binder_thread *thread,
               struct binder_transaction_data *tr, int reply){
     struct binder_transaction *t;
     struct binder_work *tcomplete;
     binder_size_t *offp, *off_end;
     binder_size_t off_min;
     struct binder_proc *target_proc;
     struct binder_thread *target_thread = NULL;
     struct binder_node *target_node = NULL;
     struct list_head *target_list;
     wait_queue_head_t *target_wait;
     struct binder_transaction *in_reply_to = NULL;

    if (reply) {
        ...
    }else {
        if (tr->target.handle) {
            struct binder_ref *ref;
            // 由handle 找到相应 binder_ref, 由binder_ref 找到相应 binder_node
            ref = binder_get_ref(proc, tr->target.handle);
            target_node = ref->node;
        } else {
            target_node = binder_context_mgr_node;
        }
        // 由binder_node 找到相应 binder_proc
        target_proc = target_node->proc;
    }


    if (target_thread) {
        e->to_thread = target_thread->pid;
        target_list = &target_thread->todo;
        target_wait = &target_thread->wait;
    } else {
        //首次执行target_thread为空
        target_list = &target_proc->todo;
        target_wait = &target_proc->wait;
    }

    t = kzalloc(sizeof(*t), GFP_KERNEL);
    tcomplete = kzalloc(sizeof(*tcomplete), GFP_KERNEL);

    //非oneway的通信方式，把当前thread保存到transaction的from字段
    if (!reply && !(tr->flags & TF_ONE_WAY))
        t->from = thread;
    else
        t->from = NULL;

    t->sender_euid = task_euid(proc->tsk);
    t->to_proc = target_proc; //此次通信目标进程为system_server
    t->to_thread = target_thread;
    t->code = tr->code;  //此次通信code = START_SERVICE_TRANSACTION
    t->flags = tr->flags;  // 此次通信flags = 0
    t->priority = task_nice(current);

    //从目标进程target_proc中分配内存空间【3.4.1】
    t->buffer = binder_alloc_buf(target_proc, tr->data_size,
        tr->offsets_size, !reply && (t->flags & TF_ONE_WAY));

    t->buffer->allow_user_free = 0;
    t->buffer->transaction = t;
    t->buffer->target_node = target_node;

    if (target_node)
        binder_inc_node(target_node, 1, 0, NULL); //引用计数加1
    //binder对象的偏移量
    offp = (binder_size_t *)(t->buffer->data + ALIGN(tr->data_size, sizeof(void *)));

    //分别拷贝用户空间的binder_transaction_data中ptr.buffer和ptr.offsets到目标进程的binder_buffer
    copy_from_user(t->buffer->data,
        (const void __user *)(uintptr_t)tr->data.ptr.buffer, tr->data_size);
    copy_from_user(offp,
        (const void __user *)(uintptr_t)tr->data.ptr.offsets, tr->offsets_size);

    off_end = (void *)offp + tr->offsets_size;

    for (; offp < off_end; offp++) {
        struct flat_binder_object *fp;
        fp = (struct flat_binder_object *)(t->buffer->data + *offp);
        off_min = *offp + sizeof(struct flat_binder_object);
        switch (fp->type) {
        ...
        case BINDER_TYPE_HANDLE:
        case BINDER_TYPE_WEAK_HANDLE: {
            //处理引用计数情况
            struct binder_ref *ref = binder_get_ref(proc, fp->handle);
            if (ref->node->proc == target_proc) {
                if (fp->type == BINDER_TYPE_HANDLE)
                    fp->type = BINDER_TYPE_BINDER;
                else
                    fp->type = BINDER_TYPE_WEAK_BINDER;
                fp->binder = ref->node->ptr;
                fp->cookie = ref->node->cookie;
                binder_inc_node(ref->node, fp->type == BINDER_TYPE_BINDER, 0, NULL);
            } else {    
                struct binder_ref *new_ref;
                new_ref = binder_get_ref_for_node(target_proc, ref->node);
                fp->handle = new_ref->desc;
                binder_inc_ref(new_ref, fp->type == BINDER_TYPE_HANDLE, NULL);
            }
        } break;
        ...

        default:
            return_error = BR_FAILED_REPLY;
            goto err_bad_object_type;
        }
    }

    if (reply) {
        //BC_REPLY的过程
        binder_pop_transaction(target_thread, in_reply_to);
    } else if (!(t->flags & TF_ONE_WAY)) {
        //BC_TRANSACTION 且 非oneway,则设置事务栈信息
        t->need_reply = 1;
        t->from_parent = thread->transaction_stack;
        thread->transaction_stack = t;
    } else {
        //BC_TRANSACTION 且 oneway,则加入异步todo队列
        if (target_node->has_async_transaction) {
            target_list = &target_node->async_todo;
            target_wait = NULL;
        } else
            target_node->has_async_transaction = 1;
    }

    //将BINDER_WORK_TRANSACTION添加到目标队列,即target_proc->todo
    t->work.type = BINDER_WORK_TRANSACTION;
    list_add_tail(&t->work.entry, target_list);

    //将BINDER_WORK_TRANSACTION_COMPLETE添加到当前线程队列，即thread->todo
    tcomplete->type = BINDER_WORK_TRANSACTION_COMPLETE;
    list_add_tail(&tcomplete->entry, &thread->todo);

    //唤醒等待队列，本次通信的目标队列为target_proc->wait
    if (target_wait)
        wake_up_interruptible(target_wait);
    return;
}

```
主要功能:

1. 查询目标进程的过程： handle -> binder_ref -> binder_node -> binder_proc
2. 将`BINDER_WORK_TRANSACTION`添加到目标队列target_list:
- call事务， 则目标队列target_list=target_proc->todo;
- reply事务，则目标队列target_list=target_thread->todo;
- async事务，则目标队列target_list=target_node->async_todo.
3. 数据拷贝
- 将用户空间binder_transaction_data中ptr.buffer和ptr.offsets拷贝到目标进程的binder_buffer->data；
- **这就是只拷贝一次的真理所在**
4. 设置事务栈信息
- BC_TRANSACTION且非oneway, 则将当前事务添加到thread->transaction_stack；
5. 事务分发过程：
- 将`BINDER_WORK_TRANSACTION`添加到目标队列(此时为target_proc->todo队列);
- 将`BINDER_WORK_TRANSACTION_COMPLETE`添加到当前线程thread->todo队列;
6. 唤醒目标进程target_proc开始执行事务

该方法中proc/thread是指当前发起方的进程信息，而binder_proc是指目标接收端进程。 此时当前线程thread的todo队列已经有事务, 接下来便会进入`binder_thread_read`来处理相关的事务.

**3.5 binder_thread_read**


```
binder_thread_read（）{
    //当已使用字节数为0时，将BR_NOOP响应码放入指针ptr
    if (*consumed == 0) {
            if (put_user(BR_NOOP, (uint32_t __user *)ptr))
                return -EFAULT;
            ptr += sizeof(uint32_t);
        }

retry:
    //binder_transaction()已设置transaction_stack不为空，则wait_for_proc_work为false.
    wait_for_proc_work = thread->transaction_stack == NULL &&
            list_empty(&thread->todo);

    thread->looper |= BINDER_LOOPER_STATE_WAITING;
    if (wait_for_proc_work)
      proc->ready_threads++; //进程中空闲binder线程加1

    //只有当前线程todo队列为空，并且transaction_stack也为空，才会开始处于当前进程的事务
    if (wait_for_proc_work) {
        if (non_block) {
            ...
        } else
            //当进程todo队列没有数据,则进入休眠等待状态
            ret = wait_event_freezable_exclusive(proc->wait, binder_has_proc_work(proc, thread));
    } else {
        if (non_block) {
            ...
        } else
            //当线程todo队列有数据则执行往下执行；当线程todo队列没有数据，则进入休眠等待状态
            ret = wait_event_freezable(thread->wait, binder_has_thread_work(thread));
    }

    if (wait_for_proc_work)
      proc->ready_threads--; //退出等待状态, 则进程中空闲binder线程减1
    thread->looper &= ~BINDER_LOOPER_STATE_WAITING;
    ...

    while (1) {

        uint32_t cmd;
        struct binder_transaction_data tr;
        struct binder_work *w;
        struct binder_transaction *t = NULL;
        //先从线程todo队列获取事务数据
        if (!list_empty(&thread->todo)) {
            w = list_first_entry(&thread->todo, struct binder_work, entry);
        // 线程todo队列没有数据, 则从进程todo对获取事务数据
        } else if (!list_empty(&proc->todo) && wait_for_proc_work) {
            w = list_first_entry(&proc->todo, struct binder_work, entry);
        } else {
            //没有数据,则返回retry
            if (ptr - buffer == 4 &&
                !(thread->looper & BINDER_LOOPER_STATE_NEED_RETURN))
                goto retry;
            break;
        }

        switch (w->type) {
            case BINDER_WORK_TRANSACTION:
                //获取transaction数据
                t = container_of(w, struct binder_transaction, work);
                break;

            case BINDER_WORK_TRANSACTION_COMPLETE:
                cmd = BR_TRANSACTION_COMPLETE;
                //将BR_TRANSACTION_COMPLETE写入*ptr，并跳出循环。
                put_user(cmd, (uint32_t __user *)ptr)；
                list_del(&w->entry);
                kfree(w);
                break;

            case BINDER_WORK_NODE: ...    break;
            case BINDER_WORK_DEAD_BINDER:
            case BINDER_WORK_DEAD_BINDER_AND_CLEAR:
            case BINDER_WORK_CLEAR_DEATH_NOTIFICATION: ...   break;
        }

        //只有BINDER_WORK_TRANSACTION命令才能继续往下执行
        if (!t)
            continue;

        if (t->buffer->target_node) {
            //获取目标node
            struct binder_node *target_node = t->buffer->target_node;
            tr.target.ptr = target_node->ptr;
            tr.cookie =  target_node->cookie;
            t->saved_priority = task_nice(current);
            ...
            cmd = BR_TRANSACTION;  //设置命令为BR_TRANSACTION
        } else {
            tr.target.ptr = NULL;
            tr.cookie = NULL;
            cmd = BR_REPLY; //设置命令为BR_REPLY
        }
        tr.code = t->code;
        tr.flags = t->flags;
        tr.sender_euid = t->sender_euid;

        if (t->from) {
            struct task_struct *sender = t->from->proc->tsk;
            //当非oneway的情况下,将调用者进程的pid保存到sender_pid
            tr.sender_pid = task_tgid_nr_ns(sender,
                            current->nsproxy->pid_ns);
        } else {
            //当oneway的的情况下,则该值为0
            tr.sender_pid = 0;
        }

        tr.data_size = t->buffer->data_size;
        tr.offsets_size = t->buffer->offsets_size;
        tr.data.ptr.buffer = (void *)t->buffer->data + proc->user_buffer_offset;
        tr.data.ptr.offsets = tr.data.ptr.buffer +
                    ALIGN(t->buffer->data_size, sizeof(void *));

        //将cmd和数据写回用户空间
        if (put_user(cmd, (uint32_t __user *)ptr))
            return -EFAULT;
        ptr += sizeof(uint32_t);
        if (copy_to_user(ptr, &tr, sizeof(tr)))
            return -EFAULT;
        ptr += sizeof(tr);

        list_del(&t->work.entry);
        t->buffer->allow_user_free = 1;
        if (cmd == BR_TRANSACTION && !(t->flags & TF_ONE_WAY)) {
            t->to_parent = thread->transaction_stack;
            t->to_thread = thread;
            thread->transaction_stack = t;
        } else {
            t->buffer->transaction = NULL;
            kfree(t); //通信完成,则运行释放
        }
        break;
    }
done:
    *consumed = ptr - buffer;
    //当满足请求线程加已准备线程数等于0，已启动线程数小于最大线程数(15)，
    //且looper状态为已注册或已进入时创建新的线程。
    if (proc->requested_threads + proc->ready_threads == 0 &&
        proc->requested_threads_started < proc->max_threads &&
        (thread->looper & (BINDER_LOOPER_STATE_REGISTERED |
         BINDER_LOOPER_STATE_ENTERED))) {
        proc->requested_threads++;
        // 生成BR_SPAWN_LOOPER命令，用于创建新的线程
        put_user(BR_SPAWN_LOOPER, (uint32_t __user *)buffer)；
    }
    return 0;
}
```

此处wait_for_proc_work是指当前线程todo队列为空，并且transaction_stack也为空,该值为true.



**3.6 下一步何去何从**

1. 执行完binder_thread_write方法后, 通过binder_transaction()首先写入BINDER_WORK_TRANSACTION_COMPLETE写入当前线程.
2. 这时bwr.read_size > 0, 回到binder_ioctl_write_read方法, 便开始执行binder_thread_read();
3. 在binder_thread_read()方法, 将获取cmd=BR_TRANSACTION_COMPLETE, 再将cmd和数据写回用户空间;
4. 一次Binder_ioctl完成,接着回调用户空间方法talkWithDriver(),刚才的数据以写入mIn.
5. 这时mIn有可读数据, 回到IPC.waitForResponse()方法,完成BR_TRANSACTION_COMPLETE过程. 如果本次transaction采用非oneway方式, 这次Binder通信便完成, 否则还是要等待Binder服务端的返回。

对于startService过程, 采用的便是非oneway方式,那么发起者进程还会继续停留在waitForResponse()方法,继续talkWithDriver()，然后休眠在binder_thread_read()的wait_event_freezable()过程，等待当前线程的todo队列有数据的到来，即等待收到BR_REPLY消息.


由于在前面binder_transaction()除了向自己所在线程写入了BINDER_WORK_TRANSACTION_COMPLETE, 还向目标进程(此处为system_server)写入了BINDER_WORK_TRANSACTION命令，那么接下来进入`system_server`进程的工作。

## 4. 回到用户空间

`system_server`的binder线程是如何运转的，那么就需要从Binder线程的创建开始说起， Binder线程的创建有两种方式：

- ProcessState::self()->startThreadPool();
- IPCThreadState::self()->joinThreadPool();

**startThreadPool()过程会创建新Binder线程，再经过层层调用也会进入joinThreadPool()方法。 system_server的binder线程从IPC.joinThreadPool –> IPC.getAndExecuteCommand() -> IPC.talkWithDriver() ,但talkWithDriver收到事务之后, 便进入IPC.executeCommand()方法。**

接下来从joinThreadPool说起：

**4.1 IPC.joinThreadPool**


```
void IPCThreadState::joinThreadPool(bool isMain)
{
    mOut.writeInt32(isMain ? BC_ENTER_LOOPER : BC_REGISTER_LOOPER);
    set_sched_policy(mMyThreadId, SP_FOREGROUND);

    status_t result;
    do {
        processPendingDerefs(); //处理对象引用
        result = getAndExecuteCommand();//获取并执行命令

        if (result < NO_ERROR && result != TIMED_OUT && result != -ECONNREFUSED && result != -EBADF) {
            ALOGE("getAndExecuteCommand(fd=%d) returned unexpected error %d, aborting",
                  mProcess->mDriverFD, result);
            abort();
        }

        //对于binder非主线程不再使用，则退出
        if(result == TIMED_OUT && !isMain) {
            break;
        }
    } while (result != -ECONNREFUSED && result != -EBADF);

    mOut.writeInt32(BC_EXIT_LOOPER);
    talkWithDriver(false);
}
```

**4.2 IPC.getAndExecuteCommand**


```
status_t IPCThreadState::getAndExecuteCommand()
{
    status_t result;
    int32_t cmd;

    result = talkWithDriver(); //该Binder Driver进行交互
    if (result >= NO_ERROR) {
        size_t IN = mIn.dataAvail();
        if (IN < sizeof(int32_t)) return result;
        cmd = mIn.readInt32(); //读取命令

        pthread_mutex_lock(&mProcess->mThreadCountLock);
        mProcess->mExecutingThreadsCount++;
        pthread_mutex_unlock(&mProcess->mThreadCountLock);

        result = executeCommand(cmd);

        pthread_mutex_lock(&mProcess->mThreadCountLock);
        mProcess->mExecutingThreadsCount--;
        pthread_cond_broadcast(&mProcess->mThreadCountDecrement);
        pthread_mutex_unlock(&mProcess->mThreadCountLock);

        set_sched_policy(mMyThreadId, SP_FOREGROUND);
    }
    return result;
}
```

**此时system_server的binder线程空闲便是停留在binder_thread_read()方法来处理进程/线程新的事务。 由`binder_transaction`可知收到的是BINDER_WORK_TRANSACTION命令, 再经过inder_thread_read()后生成命令cmd=BR_TRANSACTION.再将cmd和数据写回用户空间。**

**4.3 IPC.executeCommand**


```
status_t IPCThreadState::executeCommand(int32_t cmd)
{
    BBinder* obj;
    RefBase::weakref_type* refs;
    status_t result = NO_ERROR;

    switch ((uint32_t)cmd) {
        case BR_TRANSACTION:
        {
            binder_transaction_data tr;
            result = mIn.read(&tr, sizeof(tr)); //读取mIn数据
            if (result != NO_ERROR) break;

            Parcel buffer;
            //当buffer对象回收时，则会调用freeBuffer来回收内存
            buffer.ipcSetDataReference(
                reinterpret_cast<const uint8_t*>(tr.data.ptr.buffer),
                tr.data_size,
                reinterpret_cast<const binder_size_t*>(tr.data.ptr.offsets),
                tr.offsets_size/sizeof(binder_size_t), freeBuffer, this);

            const pid_t origPid = mCallingPid;
            const uid_t origUid = mCallingUid;
            const int32_t origStrictModePolicy = mStrictModePolicy;
            const int32_t origTransactionBinderFlags = mLastTransactionBinderFlags;

            //设置调用者的pid和uid
            mCallingPid = tr.sender_pid;
            mCallingUid = tr.sender_euid;
            mLastTransactionBinderFlags = tr.flags;

            int curPrio = getpriority(PRIO_PROCESS, mMyThreadId);
            if (gDisableBackgroundScheduling) {
                ... //不进入此分支
            } else {
                if (curPrio >= ANDROID_PRIORITY_BACKGROUND) {
                    set_sched_policy(mMyThreadId, SP_BACKGROUND);
                }
            }

            Parcel reply;
            status_t error;
            if (tr.target.ptr) {
                //尝试通过弱引用获取强引用
                if (reinterpret_cast<RefBase::weakref_type*>(
                        tr.target.ptr)->attemptIncStrong(this)) {

                    // tr.cookie里存放的是BBinder子类JavaBBinder
                    error = reinterpret_cast<BBinder*>(tr.cookie)->transact(tr.code, buffer,
                            &reply, tr.flags);
                    reinterpret_cast<BBinder*>(tr.cookie)->decStrong(this);
                } else {
                    error = UNKNOWN_TRANSACTION;
                }

            } else {
                error = the_context_object->transact(tr.code, buffer, &reply, tr.flags);
            }

            if ((tr.flags & TF_ONE_WAY) == 0) {
                if (error < NO_ERROR) reply.setError(error);
                //对于非oneway, 需要reply通信过程,则向Binder驱动发送BC_REPLY命令
                sendReply(reply, 0);
            }
            //恢复pid和uid信息
            mCallingPid = origPid;
            mCallingUid = origUid;
            ...
        }
        break;

        case ...

        default:
            result = UNKNOWN_ERROR;
            break;
    }

    if (result != NO_ERROR) {
        mLastError = result;
    }
    return result;
}
```
- 对于oneway的场景, 执行完本次transact()则全部结束.
- 对于非oneway, 需要reply的通信过程,则向Binder驱动发送BC_REPLY命令

在出库command时，会调用 BBinder的`transact`进行处理：


```
            if (tr.target.ptr) {
                //尝试通过弱引用获取强引用
                if (reinterpret_cast<RefBase::weakref_type*>(
                        tr.target.ptr)->attemptIncStrong(this)) {

                    // tr.cookie里存放的是BBinder子类JavaBBinder 
                    error = reinterpret_cast<BBinder*>(tr.cookie)->transact(tr.code, buffer,
                            &reply, tr.flags);//调用BBinder的transact进行处理；
                    reinterpret_cast<BBinder*>(tr.cookie)->decStrong(this);
                } else {
                    error = UNKNOWN_TRANSACTION;
                }

            } else {
                error = the_context_object->transact(tr.code, buffer, &reply, tr.flags);
            }
```

**4.4 BBinder.transact**


```
status_t BBinder::transact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    data.setDataPosition(0);

    status_t err = NO_ERROR;
    switch (code) {
        case PING_TRANSACTION:
            reply->writeInt32(pingBinder());
            break;
        default:
            err = onTransact(code, data, reply, flags); 
            break;
    }

    if (reply != NULL) {
        reply->setDataPosition(0);
    }

    return err;
}
```
到这里我们就渐渐清楚了，这里调用的就是AIDL生产的代码中的防范。之后调用onTransact，BBinder准备处理客户端调用的具体请求。

**4.5 JavaBBinder.onTransact**


```
virtual status_t onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags = 0)
{
    JNIEnv* env = javavm_to_jnienv(mVM);

    IPCThreadState* thread_state = IPCThreadState::self();

    //调用Binder.execTransact
    jboolean res = env->CallBooleanMethod(mObject, gBinderOffsets.mExecTransact,
        code, reinterpret_cast<jlong>(&data), reinterpret_cast<jlong>(reply), flags);

    jthrowable excep = env->ExceptionOccurred();
    if (excep) {
        res = JNI_FALSE;
        //发生异常, 则清理JNI本地引用
        env->DeleteLocalRef(excep);
    }
    ...
    return res != JNI_FALSE ? NO_ERROR : UNKNOWN_TRANSACTION;
}
```

此处斗转星移, 从C++代码回到了Java代码. 进入`AMN.execTransact`, 由于AMN继续于Binder对象, 接下来进入`Binder.execTransact`

**4.6 Binder.execTransact**


```
private boolean execTransact(int code, long dataObj, long replyObj, int flags) {
    Parcel data = Parcel.obtain(dataObj);
    Parcel reply = Parcel.obtain(replyObj);

    boolean res;
    try {
        // 调用子类AMN.onTransact方法
        res = onTransact(code, data, reply, flags);
    } catch (RemoteException e) {
        if ((flags & FLAG_ONEWAY) != 0) {
            ...
        } else {
            //非oneway的方式,则会将异常写回reply
            reply.setDataPosition(0);
            reply.writeException(e);
        }
        res = true;
    } catch (RuntimeException e) {
        if ((flags & FLAG_ONEWAY) != 0) {
            ...
        } else {
            reply.setDataPosition(0);
            reply.writeException(e);
        }
        res = true;
    } catch (OutOfMemoryError e) {
        RuntimeException re = new RuntimeException("Out of memory", e);
        reply.setDataPosition(0);
        reply.writeException(re);
        res = true;
    }
    reply.recycle();
    data.recycle();
    return res;
}
```

**4.7 AMN.onTransact**


```
public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
    switch (code) {
    ...
     case START_SERVICE_TRANSACTION: {
        data.enforceInterface(IActivityManager.descriptor);
        IBinder b = data.readStrongBinder();
        //生成ApplicationThreadNative的代理对象，即ApplicationThreadProxy对象
        IApplicationThread app = ApplicationThreadNative.asInterface(b);
        Intent service = Intent.CREATOR.createFromParcel(data);
        String resolvedType = data.readString();
        String callingPackage = data.readString();
        int userId = data.readInt();
        //调用ActivityManagerService的startService()方法
        ComponentName cn = startService(app, service, resolvedType, callingPackage, userId);
        reply.writeNoException();
        ComponentName.writeToParcel(cn, reply);
        return true;
    }
}
```

**4.8 AMS.startService**


```
public ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, String callingPackage, int userId) throws TransactionTooLargeException {

    synchronized(this) {
        ...
        ComponentName res = mServices.startServiceLocked(caller, service,
                resolvedType, callingPid, callingUid, callingPackage, userId);
        Binder.restoreCallingIdentity(origId);
        return res;
    }
}
```

历经千山万水, 总算是进入了AMS.startService. 当system_server收到BR_TRANSACTION的过程后，通信并没有完全结束，还需将服务启动完成的回应消息 告诉给发起端进程


## 5. Reply流程
**前面IPC.waitForResponse()过程，对于非oneway的方式，还仍在一直等待system_server这边的响应呢，只有收到BR_REPLY，或者BR_DEAD_REPLY，或者BR_FAILED_REPLY，再或许其他BR_命令执行出错的情况下，该waitForResponse()才会退出。**

**BR_REPLY命令是如何来的呢？ `IPC.executeCommand()`过程处理完BR_TRANSACTION命令的同时，还会通过sendReply()向Binder Driver发送BC_REPLY消息，接下来从该方法说起**

**5.1 IPC.sendReply**


```
status_t IPCThreadState::sendReply(const Parcel& reply, uint32_t flags)
{
    status_t err;
    status_t statusBuffer;
   
    err = writeTransactionData(BC_REPLY, flags, -1, 0, reply, &statusBuffer);
    if (err < NO_ERROR) return err;
    
    return waitForResponse(NULL, NULL);
}
```

先将数据写入mOut；再进waitForResponse，等待应答，此时同理也是等待BR_TRANSACTION_COMPLETE。 同理经过IPC.talkWithDriver -> binder_ioctl -> binder_ioctl_write_read -> binder_thread_write， 再就是进入binder_transaction方法。


**5.2 BC_REPLY**


```
// reply =true
static void binder_transaction(struct binder_proc *proc,
             struct binder_thread *thread,
             struct binder_transaction_data *tr, int reply)
{
  ...
  if (reply) {
    in_reply_to = thread->transaction_stack; //接收端的事务栈
    ...
    thread->transaction_stack = in_reply_to->to_parent;
    target_thread = in_reply_to->from; //发起端的线程

        //发起端线程不能为空
    if (target_thread == NULL) {
      return_error = BR_DEAD_REPLY;
      goto err_dead_binder;
    }

        //发起端线程的事务栈 要等于 接收端的事务栈
    if (target_thread->transaction_stack != in_reply_to) {
      return_error = BR_FAILED_REPLY;
      in_reply_to = NULL;
      target_thread = NULL;
      goto err_dead_binder;
    }
    target_proc = target_thread->proc; //发起端的进程
  } else {
    ...
  }

  if (target_thread) {
      //发起端的线程
      target_list = &target_thread->todo;
      target_wait = &target_thread->wait;
    } else {
      ...
    }

    t = kzalloc(sizeof(*t), GFP_KERNEL);
    tcomplete = kzalloc(sizeof(*tcomplete), GFP_KERNEL);
    ...

    if (!reply && !(tr->flags & TF_ONE_WAY))
      t->from = thread;
    else
      t->from = NULL; //进入该分支
    t->sender_euid = task_euid(proc->tsk);
    t->to_proc = target_proc;
    t->to_thread = target_thread;
    t->code = tr->code;
    t->flags = tr->flags;
    t->priority = task_nice(current);

    // 发起端进程分配buffer
    t->buffer = binder_alloc_buf(target_proc, tr->data_size,
      tr->offsets_size, !reply && (t->flags & TF_ONE_WAY));
    ...
    t->buffer->allow_user_free = 0;
    t->buffer->transaction = t;
    t->buffer->target_node = target_node;
    if (target_node)
      binder_inc_node(target_node, 1, 0, NULL);

    //分别拷贝用户空间的binder_transaction_data中ptr.buffer和ptr.offsets到内核
    copy_from_user(t->buffer->data,
       (const void __user *)(uintptr_t)tr->data.ptr.buffer, tr->data_size);
    copy_from_user(offp,
       (const void __user *)(uintptr_t)tr->data.ptr.offsets, tr->offsets_size);
    ...

    if (reply) {
      binder_pop_transaction(target_thread, in_reply_to);
    } else if (!(t->flags & TF_ONE_WAY)) {
      ...
    } else {
      ...
    }

    //将BINDER_WORK_TRANSACTION添加到目标队列，本次通信的目标队列为target_thread->todo
    t->work.type = BINDER_WORK_TRANSACTION;
    list_add_tail(&t->work.entry, target_list);

    //将BINDER_WORK_TRANSACTION_COMPLETE添加到当前线程的todo队列
    tcomplete->type = BINDER_WORK_TRANSACTION_COMPLETE;
    list_add_tail(&tcomplete->entry, &thread->todo);

    //唤醒等待队列，本次通信的目标队列为target_thread->wait
    if (target_wait)
        wake_up_interruptible(target_wait);
    return;
```

binder_transaction -> binder_thread_read -> IPC.waitForResponse，收到BR_REPLY来回收buffer.

**5.3 BR_REPLY**


```
status_t IPCThreadState::waitForResponse(Parcel *reply, status_t *acquireResult)
{
    int32_t cmd;
    int32_t err;

    while (1) {
        if ((err=talkWithDriver()) < NO_ERROR) break; 
        if (mIn.dataAvail() == 0) continue;
        ...
        cmd = mIn.readInt32();
        switch (cmd) {
          ...
          case BR_REPLY:
           {
               binder_transaction_data tr;
               err = mIn.read(&tr, sizeof(tr));
               if (err != NO_ERROR) goto finish;

               if (reply) {
                   ...
               } else {
                   // 释放buffer
                   freeBuffer(NULL,
                       reinterpret_cast<const uint8_t*>(tr.data.ptr.buffer),
                       tr.data_size,
                       reinterpret_cast<const binder_size_t*>(tr.data.ptr.offsets),
                       tr.offsets_size/sizeof(binder_size_t), this);
                   continue;
               }
           }
           goto finish;
        default:
            err = executeCommand(cmd);
            ...
            break;
        }
    }
    ...
}
```

**5.4 binder_thread_read**


```
binder_thread_read（）{
    ...
    while (1) {

        uint32_t cmd;
        struct binder_transaction_data tr;
        struct binder_work *w;
        struct binder_transaction *t = NULL;

        //从线程todo队列获取事务数据
        if (!list_empty(&thread->todo)) {
            w = list_first_entry(&thread->todo, struct binder_work, entry);
        } else if (!list_empty(&proc->todo) && wait_for_proc_work) {
            ...
        } else {
            ...
        }

        switch (w->type) {
            case BINDER_WORK_TRANSACTION:
                //获取transaction数据
                t = container_of(w, struct binder_transaction, work);
                break;

            ...
        }

        ...
        if (t->buffer->target_node) {
            //获取目标node
            struct binder_node *target_node = t->buffer->target_node;
            tr.target.ptr = target_node->ptr;
            tr.cookie =  target_node->cookie;
            t->saved_priority = task_nice(current);
            ...
            cmd = BR_TRANSACTION;  //设置命令为BR_TRANSACTION
        } else {
            tr.target.ptr = NULL;
            tr.cookie = NULL;
            cmd = BR_REPLY; //设置命令为BR_REPLY
        }

        tr.code = t->code;
        tr.flags = t->flags;
        tr.sender_euid = t->sender_euid;

        ...
        //将cmd和数据写回用户空间
        if (put_user(cmd, (uint32_t __user *)ptr)) return -EFAULT;
        ptr += sizeof(uint32_t);
        if (copy_to_user(ptr, &tr, sizeof(tr)))  return -EFAULT;
        ptr += sizeof(tr);

        list_del(&t->work.entry);
        t->buffer->allow_user_free = 1;
        if (cmd == BR_TRANSACTION && !(t->flags & TF_ONE_WAY)) {
            t->to_parent = thread->transaction_stack;
            t->to_thread = thread;
            thread->transaction_stack = t;
        } else {
            t->buffer->transaction = NULL;
            kfree(t); //通信完成,则运行释放
        }
        break;
    }

    ...
    return 0;
}
```

## 6. 总结

本文详细地介绍如何从AMP.startService是如何通过Binder一步步调用进入到system_server进程的AMS.startService. 整个过程涉及Java framework, native, kernel driver各个层面知识. 仅仅一个Binder IPC调用, 就花费了如此大篇幅来讲解, 可见系统之庞大. 整个过程的调用流程:

**6.1 通信流程**

从通信流程角度来看整个过程:

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/binder_ipc_process.jpg)

1. 发起端线程向Binder Driver发起binder ioctl请求后, 便采用环不断talkWithDriver,此时该线程处于阻塞状态, 直到收到如下BR_XXX命令才会结束该过程.

- BR_TRANSACTION_COMPLETE: oneway模式下,收到该命令则退出
- BR_REPLY: 非oneway模式下,收到该命令才退出;
- BR_DEAD_REPLY: 目标进程/线程/binder实体为空, 以及释放正在等待reply的binder thread或者binder buffer;
- BR_FAILED_REPLY: 情况较多,比如非法handle, 错误事务栈, security, 内存不足, buffer不足, 数据拷贝失败, 节点创建失败, 各种不匹配等问题
- BR_ACQUIRE_RESULT: 目前未使用的协议;

2. 左图中waitForResponse收到BR_TRANSACTION_COMPLETE,则直接退出循环, 则没有机会执行executeCommand()方法, 故将其颜色画为灰色. 除以上5种BR_XXX命令, 当收到其他BR命令,则都会执行executeCommand过程.

3. 目标Binder线程创建后, 便进入joinThreadPool()方法, 采用循环不断地循环执行getAndExecuteCommand()方法, 当bwr的读写buffer都没有数据时,则阻塞在binder_thread_read的wait_event过程. 另外,正常情况下binder线程一旦创建则不会退出.

**6.2 通信协议**

从通信协议的角度来看这个过程:

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/binder_transaction.jpg)

1. Binder客户端或者服务端向Binder Driver发送的命令都是以BC_开头,例如本文的BC_TRANSACTION和BC_REPLY, 所有Binder Driver向Binder客户端或者服务端发送的命令则都是以BR_开头, 例如本文中的BR_TRANSACTION和BR_REPLY.
2. 只有当BC_TRANSACTION或者BC_REPLY时, 才调用binder_transaction()来处理事务. 并且都会回应调用者一个BINDER_WORK_TRANSACTION_COMPLETE事务, 经过binder_thread_read()会转变成BR_TRANSACTION_COMPLETE.
3. startService过程便是一个非oneway的过程, 那么oneway的通信过程如下所述.

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/binder_transaction_oneway.jpg)

**6.3 数据流**

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/binder_transaction_data.jpg)

整个传输过程中的数据组装拆包过程：

**客户端：**

AMP.startService：组装flat_binder_object对象等组成的Parcel data；

IPC.writeTransactionData：组装BC_TRANSACTION和binder_transaction_data结构体，写入mOut;

IPC.talkWithDriver: 组装BINDER_WRITE_READ和binder_write_read结构体，通过ioctl传输到驱动层。

**进入驱动后：**

binder_thread_write: 处理binder_write_read.write_buffer数据;

binder_transaction: 处理write_buffer.binder_transaction_data数据；
- 创建binder_transaction结构体，记录事务通信的线程来源以及事务链条等相关信息；
- 分配binder_buffer结构体，拷贝当前线程binder_transaction_data的data数据到binder_buffer->data；

binder_thread_read: 处理binder_transaction结构体数据;

- 组装cmd=BR_TRANSACTION和binder_transaction_data结构体，写入binder_write_read.read_buffer数据


**服务端：**

IPC.executeCommand：处理BR_TRANSACTION命令, 将binder_transaction_data数据解析成BBinder.transact()所需的参数；

AMN.onTransact: 层层回调，进入该方法，反序列化数据后，调用startService()方法