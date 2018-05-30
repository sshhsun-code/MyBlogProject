>参考文章[：http://qiangbo.space/2017-01-15/AndroidAnatomy_Binder_Driver/](http://qiangbo.space/2017-01-15/AndroidAnatomy_Binder_Driver/)

## Binder驱动中主要结构：

1.与用户空间共用的，这些结构体在Binder通信协议中会用到。
包括：


结构体名称 | 说明
---|---
... | ...
**binder_write_read**| **存储一次读写操作的数据**
**binder_version**| **存储Binder版本号**
...| ...
**binder_transaction_data**| **存储一次事务的数据**
...| ...
**这其中，`binder_write_read`和`binder_transaction_data`这两个结构体最为重要，它们存储了IPC调用过程中的数据。**

2.Binder驱动中，还有一类结构体是仅仅Binder驱动内部实现过程中需要的，它们定义在binder.c中:


结构体名称 |	说明
---|---
**binder_node**	|**描述Binder实体节点，即：对应了一个Server**
**binder_ref**	|**描述对于Binder实体的引用**
**binder_buffer**	|**描述Binder通信过程中存储数据的Buffer**
**binder_proc**	|**描述使用Binder的进程**
**binder_thread**	|**描述使用Binder的线程**
binder_work	|描述通信过程中的一项任务
**binder_transaction**	|**描述一次事务的相关信息**
binder_deferred_state	|描述延迟任务
binder_ref_death	|描述Binder实体死亡的信息
binder_transaction_log	|debugfs日志
binder_transaction_log_entry	|debugfs日志条目

这些结构体互相之间都留有字段存储关联的结构。下面这幅图描述了这里说到的这些内容：

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/driver/binder_main_struct.png)



## Binder驱动中的协议

Binder协议可分为**控制协议**和**驱动协议**.

1.控制协议是进程通过ioctl“/dev/binder” 与Binder设备进行通讯的协议，该协议包含以下几种命令：


命令	|说明	|参数类型
---|---|---
**BINDER_WRITE_READ**	|**读写操作，最常用的命令。IPC过程就是通过这个命令进行数据传递**|**binder_write_read**
BINDER_SET_MAX_THREADS	|设置进程支持的最大线程数量	|size_t
BINDER_SET_CONTEXT_MGR	|设置自身为ServiceManager	|无
BINDER_THREAD_EXIT	|通知驱动Binder线程退出	|无
BINDER_VERSION	|获取Binder驱动的版本号	|binder_version
BINDER_SET_IDLE_PRIORITY	|暂未用到|	-
BINDER_SET_IDLE_TIMEOUT	|暂未用到	|-

2.Binder的驱动协议描述了对于Binder驱动的具体使用过程。驱动协议又可以分为两类：

- 一类是binder_driver_command_protocol，描述了==进程发送给Binder驱动的命令==
- 一类是binder_driver_return_protocol，描述了==Binder驱动发送给进程的命令==

**binder_driver_command_protocol命令**：

命令	|说明	|参数类型
---|---|---
BC_TRANSACTION	|Binder事务，即：Client对于Server的请求	|binder_transaction_data
BC_REPLY	|事务的应答，即：Server对于Client的回复	|binder_transaction_data
BC_FREE_BUFFER	|通知驱动释放Buffer	|binder_uintptr_t
BC_ACQUIRE	|强引用计数+1	|__u32
BC_RELEASE	|强引用计数-1	|__u32
BC_INCREFS	|弱引用计数+1	|__u32
BC_DECREFS	|弱引用计数-1	|__u32
BC_ACQUIRE_DONE	|BR_ACQUIRE的回复	|binder_ptr_cookie
BC_INCREFS_DONE	|BR_INCREFS的回复	|binder_ptr_cookie
BC_ENTER_LOOPER	|通知驱动主线程ready	|void
BC_REGISTER_LOOPER	|通知驱动子线程ready	|void
BC_EXIT_LOOPER	|通知驱动线程已经退出	|void
BC_REQUEST_DEATH_NOTIFICATION	|请求接收死亡通知	|binder_handle_cookie
BC_CLEAR_DEATH_NOTIFICATION	|去除接收死亡通知	|binder_handle_cookie
BC_DEAD_BINDER_DONE	|已经处理完死亡通知	|binder_uintptr_t
BC_ATTEMPT_ACQUIRE	|暂未实现	|-
BC_ACQUIRE_RESULT	|暂未实现	|-

**binder_driver_return_protocol共包含18个命令，分别是：**



返回类型	|说明	|参数类型
---|---|---
BR_OK	|操作完成	|void
BR_NOOP	|操作完成	|void
BR_ERROR	|发生错误	|__s32
BR_TRANSACTION	|通知进程收到一次Binder请求（Server端）	|binder_transaction_data
BR_REPLY	|通知进程收到Binder请求的回复（Client）	|binder_transaction_data
BR_TRANSACTION_COMPLETE	|驱动对于接受请求的确认回复	|void
BR_FAILED_REPLY	|告知发送方通信目标不存在	|void
BR_SPAWN_LOOPER	|通知Binder进程创建一个新的线程	|void
BR_ACQUIRE	|强引用计数+1请求	|binder_ptr_cookie
BR_RELEASE	|强引用计数-1请求	|binder_ptr_cookie
BR_INCREFS	|弱引用计数+1请求	|binder_ptr_cookie
BR_DECREFS	|若引用计数-1请求	|binder_ptr_cookie
BR_DEAD_BINDER	|发送死亡通知	|binder_uintptr_t
BR_CLEAR_DEATH_NOTIFICATION_DONE	|清理死亡通知完成	|binder_uintptr_t
BR_DEAD_REPLY	|告知发送方对方已经死亡	|void
BR_ACQUIRE_RESULT	|暂未实现	|-
BR_ATTEMPT_ACQUIRE	|暂未实现	|-
BR_FINISHED	|暂未实现	|-

根据上述的命令，我们以一次Binder请求过程详细看Binder协议是如何通信的：
![image](http://o9m6aqy3r.bkt.clouddn.com//binder/driver/binder_request_sequence.png)

## 打开Binder设备
任何进程在使用Binder之前，都需要先通过open("/dev/binder")打开Binder设备.
```CPP
static int binder_open(struct inode *nodp, struct file *filp)
{
	struct binder_proc *proc;

   // 创建进程对应的binder_proc对象
	proc = kzalloc(sizeof(*proc), GFP_KERNEL); 
	if (proc == NULL)
		return -ENOMEM;
	get_task_struct(current);
	proc->tsk = current;
	// 初始化binder_proc
	INIT_LIST_HEAD(&proc->todo);
	init_waitqueue_head(&proc->wait);
	proc->default_priority = task_nice(current);

  // 锁保护
	binder_lock(__func__);

	binder_stats_created(BINDER_STAT_PROC);
	// 添加到全局列表binder_procs中
	hlist_add_head(&proc->proc_node, &binder_procs);
	proc->pid = current->group_leader->pid;
	INIT_LIST_HEAD(&proc->delivered_death);
	filp->private_data = proc;

	binder_unlock(__func__);

	return 0;
}

```

1. 创建进程对应的binder_proc对象
2. 添加到全局列表binder_procs中
>在Binder驱动中，通过**binder_procs**记录了所有使用Binder的进程。每个初次打开Binder设备的进程都会被添加到这个列表中的。

## Binder mmap过程(binder_mmap)
使用Binder机制，数据只需要经历一次拷贝就可以了，其原理就在这个函数中。

binder_mmap这个函数中，会申请一块物理内存，然后在用户空间和内核空间同时对应到这块内存上。在这之后，当有Client要发送数据给Server的时候，只需一次，**将Client发送过来的数据拷贝到Server端的内核空间指定的内存地址即可，由于这个内存地址在服务端已经同时映射到用户空间，因此无需再做一次复制，Server即可直接访问**，整个过程如下图所示：

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/driver/mmap_and_transaction.png)
整个mmap过程大概完成以下工作：
1. Server在启动之后，调用对/dev/binder设备调用mmap
2. 内核中的binder_mmap函数进行对应的处理：申请一块物理内存，然后在用户空间和内核空间同时进行映射
3. Client通过BINDER_WRITE_READ命令发送请求，这个请求将先到驱动中，同时需要将数据从Client进程的用户空间拷贝到内核空间
4. 驱动通过BR_TRANSACTION通知Server有人发出请求，Server进行处理。由于这块内存也在用户空间进行了映射，因此Server进程的代码可以直接访问
 

- binder_mmap函数对应了mmap的系统调用的处理
- binder_update_page_range函数真正实现了内存分配和地址映射

## 内存的管理

在驱动中，会根据实际的使用情况进行内存的分配。有**内存的分配**，当然也需要**内存的释放**。这里我们就来看看Binder驱动中是如何进行内存的管理的。

**内存分配**

当一个Client想要对Server发出请求时，它首先将请求发送到Binder设备上，由Binder驱动根据请求的信息找到对应的目标节点，然后将请求数据传递过去。

进程通过ioctl系统调用来发出请求：`ioctl(mProcess->mDriverFD, BINDER_WRITE_READ, &bwr)`

>PS：这行代码来自于Framework层的IPCThreadState类。在后文中，我们将看到，**IPCThreadState类专门负责与驱动进行通信**。

BINDER_WRITE_READ对应了具体要做的操作码，这个操作码将由Binder驱动解析。bwr存储了请求数据，其类型是binder_write_read.

binder_write_read其实是一个相对外层的数据结构，其内部会包含一个binder_transaction_data结构的数据。binder_transaction_data包含了发出请求者的标识，请求的目标对象以及请求所需要的参数。它们的关系如下图所示：

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/driver/binder_write_read.png)

binder_ioctl函数对应了ioctl系统调用的处理。这个函数的逻辑比较简单，就是根据ioctl的命令来确定进一步处理的逻辑，具体如下:

- 如果命令是BINDER_WRITE_READ，并且
- 如果 bwr.write_size > 0，则调用binder_thread_write
- 如果 bwr.read_size > 0，则调用binder_thread_read
- 如果命令是BINDER_SET_MAX_THREADS，则设置进程的max_threads，即进程支持的最大线程数
- 如果命令是BINDER_SET_CONTEXT_MGR，则设置当前进程为ServiceManager，见下文
- 如果命令是BINDER_THREAD_EXIT，则调用binder_free_thread，释放binder_thread
- 如果命令是BINDER_VERSION，则返回当前的Binder版本号

当Client请求Server的时候，便会发送一个BINDER_WRITE_READ命令，同时框架会将将实际的数据包装好。此时，binder_transaction_data中的code将是BC_TRANSACTION，由此便会调用到binder_transaction方法，这个方法是对一次Binder事务的处理，这其中会调用`binder_alloc_buf`函数为此次事务申请一个缓存。这里提到到调用关系如下：

![image](http://o9m6aqy3r.bkt.clouddn.com//binder/driver/binder_alloc_buf.png)

在binder_proc（描述了使用Binder的进程）中，包含了几个字段用来管理进程在Binder IPC过程中缓存，如下：
```C
struct binder_proc {
	...
	struct list_head buffers; // 进程拥有的buffer列表
	struct rb_root free_buffers; // 空闲buffer列表
	struct rb_root allocated_buffers; // 已使用的buffer列表 
	size_t free_async_space; // 剩余的异步调用的空间
	
	size_t buffer_size; // 缓存的上限
  ...
};

```

**内存的释放**

BC_FREE_BUFFER命令是通知驱动进行内存的释放，binder_free_buf函数是真正实现的逻辑，这个函数与binder_alloc_buf是刚好对应的。在这个函数中，所做的事情包括：

- 重新计算进程的空闲缓存大小
- 通过binder_update_page_range释放内存
- 更新binder_proc的buffers，free_buffers，allocated_buffers字段
- 



## Binder中的“面向对象”

Binder驱动中，并不是真的将对象在进程间来回序列化，而是通过特定的标识来进行对象的传递。Binder驱动中，通过flat_binder_object来描述需要跨越进程传递的对象。其定义如下：

```c
struct flat_binder_object {
	__u32		type;
	__u32		flags;

	union {
		binder_uintptr_t	binder; /* local object */
		__u32			handle;	/* remote object */
	};
	binder_uintptr_t	cookie;
};
```

其中，type有如下5种类型。

```c
enum {
	BINDER_TYPE_BINDER	= B_PACK_CHARS('s', 'b', '*', B_TYPE_LARGE),
	BINDER_TYPE_WEAK_BINDER	= B_PACK_CHARS('w', 'b', '*', B_TYPE_LARGE),
	BINDER_TYPE_HANDLE	= B_PACK_CHARS('s', 'h', '*', B_TYPE_LARGE),
	BINDER_TYPE_WEAK_HANDLE	= B_PACK_CHARS('w', 'h', '*', B_TYPE_LARGE),
	BINDER_TYPE_FD		= B_PACK_CHARS('f', 'd', '*', B_TYPE_LARGE),
};
```

当对象传递到Binder驱动中的时候，**由驱动来进行翻译和解释，然后传递到接收的进程**。

例如当Server把Binder实体传递给Client时，在发送数据流中，flat_binder_object中的type是BINDER_TYPE_BINDER，同时binder字段指向Server进程用户空间地址。但这个地址对于Client进程是没有意义的（Linux中，每个进程的地址空间是互相隔离的），驱动必须对数据流中的flat_binder_object做相应的翻译：**将type该成BINDER_TYPE_HANDLE；为这个Binder在接收进程中创建位于内核中的引用并将引用号填入handle中。对于发生数据流中引用类型的Binder也要做同样转换。经过处理后接收进程从数据流中取得的Binder引用才是有效的，才可以将其填入数据包binder_transaction_data的target.handle域，向Binder实体发送请求**。

由于每个请求和请求的返回都会经历内核的翻译，因此这个过程从进程的角度来看是完全透明的。进程完全不用感知这个过程，就好像对象真的在进程间来回传递一样。

## 驱动层的线程管理

Binder机制的设计从最底层–驱动层，就考虑到了对于多线程的支持。具体内容如下：

- 使用Binder的进程在启动之后，通过BINDER_SET_MAX_THREADS告知驱动其支持的最大线程数量
- 驱动会对线程进行管理。在binder_proc结构中，这些字段记录了进程中线程的信息：max_threads，requested_threads，requested_threads_started，ready_threads
   **binder_thread结构对应了Binder进程中的线程**
- 驱动通过BR_SPAWN_LOOPER命令告知进程需要创建一个新的线程
- 进程通过BC_ENTER_LOOPER命令告知驱动其主线程已经ready
- 进程通过BC_REGISTER_LOOPER命令告知驱动其子线程（非主线程）已经ready
- 进程通过BC_EXIT_LOOPER命令告知驱动其线程将要退出
- 在线程退出之后，通过BINDER_THREAD_EXIT告知Binder驱动。驱动将对应的binder_thread对象销毁


## ServiceManager

Binder机制为ServiceManager预留了一个特殊的位置。这个位置是预先定好的，任何想要使用ServiceManager的进程只要通过这个特定的位置就可以访问到ServiceManager了（而不用再通过ServiceManager的接口）。

在Binder驱动中，有一个全局的变量：

`static struct binder_node *binder_context_mgr_node;

这个变量指向的就是ServiceManager。

当有进程通过ioctl并指定命令为BINDER_SET_CONTEXT_MGR的时候，驱动被认定这个进程是ServiceManager.binder_ioctl函数中对应的处理如下：

```CPP
case BINDER_SET_CONTEXT_MGR:
	if (binder_context_mgr_node != NULL) {
		pr_err("BINDER_SET_CONTEXT_MGR already set\n");
		ret = -EBUSY;
		goto err;
	}
	ret = security_binder_set_context_mgr(proc->tsk);
	if (ret < 0)
		goto err;
	if (uid_valid(binder_context_mgr_uid)) {
		if (!uid_eq(binder_context_mgr_uid, current->cred->euid)) {
			pr_err("BINDER_SET_CONTEXT_MGR bad uid %d != %d\n",
			       from_kuid(&init_user_ns, current->cred->euid),
			       from_kuid(&init_user_ns, binder_context_mgr_uid));   
			ret = -EPERM;
			goto err;
		}
	} else
		binder_context_mgr_uid = current->cred->euid;
	binder_context_mgr_node = binder_new_node(proc, 0, 0);
	if (binder_context_mgr_node == NULL) {
		ret = -ENOMEM;
		goto err;
	}
	binder_context_mgr_node->local_weak_refs++;
	binder_context_mgr_node->local_strong_refs++;
	binder_context_mgr_node->has_strong_ref = 1;
	binder_context_mgr_node->has_weak_ref = 1;
	break;
	
```

ServiceManager应当要先于所有Binder Server之前启动。在它启动完成并告知Binder驱动之后，驱动便设定好了这个特定的节点。

在这之后，当有其他模块想要使用ServerManager的时候，只要将请求指向ServiceManager所在的位置即可。

在Binder驱动中，通过handle = 0这个位置来访问ServiceManager。例如，binder_transaction中，判断如果target.handler为0，则认为这个请求是发送给ServiceManager的，相关代码如下：

```CPP
if (tr->target.handle) {
	struct binder_ref *ref;
	ref = binder_get_ref(proc, tr->target.handle, true);
	if (ref == NULL) {
		binder_user_error("%d:%d got transaction to invalid handle\n",
			proc->pid, thread->pid);
		return_error = BR_FAILED_REPLY;
		goto err_invalid_target_handle;
	}
	target_node = ref->node;
} else {
	target_node = binder_context_mgr_node;
	if (target_node == NULL) {
		return_error = BR_DEAD_REPLY;
		goto err_no_context_mgr_node;
	}
}
```

