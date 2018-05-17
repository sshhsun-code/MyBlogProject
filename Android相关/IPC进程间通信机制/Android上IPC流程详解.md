# Android上Binder流程详解
1. Binder机制综述？Why Binder?
2. Binder 中ServiceManager 成为上下文管理者
3. Service端的注册过程
4. Client寻找匹配Service过程
5. Client Service通信过程？Binder线程池，Binder连接池
6. Binder驱动通信过程？内存映射 ，mmap
7. Binder对象的销毁

## 1#Binder机制 ##
Binder框架定义了四个角色：
**<br>Server**
**<br>Client**
**<br>ServiceManager**
**<br>Binder驱动**
<br>其中Server，Client，SMgr运行于**用户空间**，驱动运行于**内核空间**。这四个角色的关系和互联网类似：Server是服务器，Client是客户终端，SMgr是域名服务器（DNS），驱动是路由器。
![](http://o9m6aqy3r.bkt.clouddn.com/IPC-Binder.jpg)

**这四个角色之间的交互如下：**
**<br>0.启动Service Manager**：ServiceManager本身就是一个进程。在Binder 驱动中注册成为ServiceManager,其主要作用，就是帮助系统去维护众多的Service列表。
**<br>1.注册服务(addService)**：Server进程要先注册Service到ServiceManager。该过程：Server是客户端，ServiceManager是服务端。
**<br>2.获取服务(getService)**：Client进程使用某个Service前，须先向ServiceManager中获取相应的Service。该过程：Client是客户端，ServiceManager是服务端。
**<br>3.使用服务**：Client根据得到的Service信息建立与Service所在的Server进程通信的通路，然后就可以直接与Service交互。该过程：client是客户端，server是服务端。


ServiceManager本身工作相对简单，其功能：查询和注册服务。 对于Binder IPC通信过程中，其实更多的情形是BpBinder和BBinder之间的通信，比如ActivityManagerProxy和ActivityManagerService之间的通信等
**<br><font color>BpBinder(客户端)和BBinder(服务端)都是Android中Binder通信相关的代表，它们都从IBinder类中派生而来，关系图如下：**

![](http://o9m6aqy3r.bkt.clouddn.com/Ibinder_classes.jpg)

- **client端：BpBinder.transact()来发送事务请求**；


- **server端：BBinder.onTransact()会接收到相应事务**。

## 2#ServiceManager的启动与获取 ##
**ServiceManager是Binder IPC通信过程中的守护进程，本身也是一个Binder服务，但并没有采用libbinder中的多线程模型来与Binder驱动通信，而是自行编写了binder.c直接和Binder驱动来通信，并且只有一个循环binder_loop来进行读取和处理事务，这样的好处是简单而高效。**

<br>**2.1# 流程图**

![](http://o9m6aqy3r.bkt.clouddn.com/create_servicemanager.jpg)

**启动过程主要以下几个阶段：**<font color = "red">

1. 打开binder驱动：binder_open；
2. 注册成为binder服务的大管家：binder_become_context_manager；
3. 进入无限循环，处理client端发来的请求：binder_loop；</font>

说明：
<br>**2.2# binder_open:**

	struct binder_state *binder_open(size_t mapsize)
	{
    struct binder_state *bs;
    struct binder_version vers;

    bs = malloc(sizeof(*bs));
    if (!bs) {
        errno = ENOMEM;
        return NULL;
    }

    //通过系统调用陷入内核，打开Binder设备驱动
    bs->fd = open("/dev/binder", O_RDWR);
    if (bs->fd < 0) {
        goto fail_open; // 无法打开binder设备
    }

     //通过系统调用，ioctl获取binder版本信息
    if ((ioctl(bs->fd, BINDER_VERSION, &vers) == -1) ||
        (vers.protocol_version != BINDER_CURRENT_PROTOCOL_VERSION)) {
        goto fail_open; //内核空间与用户空间的binder不是同一版本
    }

    bs->mapsize = mapsize;
    //通过系统调用，mmap内存映射，mmap必须是page的整数倍
    bs->mapped = mmap(NULL, mapsize, PROT_READ, MAP_PRIVATE, bs->fd, 0);
    if (bs->mapped == MAP_FAILED) {
        goto fail_map; // binder设备内存无法映射
    }

    return bs;

	fail_map:
    close(bs->fd);
	fail_open:
    free(bs);
    return NULL;
	}
<br>
1.调用binder_open()来打开binder驱动，驱动文件为“/dev/binder”
<br>2. binder_open()的参数mapsize表示它希望把binder驱动文件的多少字节映射到本地空间。可以看到，Service Manager Service和普通进程所映射的binder大小并不相同。它把binder驱动文件的128K字节映射到内存空间
<br>3. 该函数会把“参数fd所指代的文件”中的一部分映射到进程空间去。这部分文件内容以offset为起始位置，以len为字节长度。其中，参数offset表明从文件起始处开始算起的偏移量。参数prot表明对这段映射空间的访问权限，可以是PROT_READ（可读）、PROT_WRITE （可写）、PROT_EXEC （可执行）、PROT_NONE（不可访问）。参数addr用于指出文件应被映射到进程空间的起始地址，一般指定为空指针，此时会由内核来决定起始地址。binder_open()的返回值类型为binder_state*，里面记录着刚刚打开的binder驱动文件句柄以及mmap()映射到的最终目标地址。
<br>4.  binder_open()的返回值类型为binder_state*，里面记录着刚刚打开的binder驱动文件句柄以及mmap()映射到的最终目标地址

    struct binder_state
	{
    int fd;
    void *mapped;
    unsigned mapsize;
	};

<br>**2.3# binder_become_context_manager:**

**binder_become_context_manager()的作用是让当前进程成为整个系统中唯一的上下文管理器，即service管理器；进程使用BINDER_SET_CONTEXT_MGR命令将自己注册成SMgr时Binder驱动会自动为它创建Binder实体。**

    int binder_become_context_manager(struct binder_state *bs) 
	{
    //通过ioctl，传递BINDER_SET_CONTEXT_MGR指令
    return ioctl(bs->fd, BINDER_SET_CONTEXT_MGR, 0);
	}

传递给ioctl()函数的**BINDER_SET_CONTEXT_MGR**会最终调用到**binder_ioctl_set_ctx_mgr():**
>生成的节点给binder_context_mgr_node赋值，成为Service管理节点

    static int binder_ioctl_set_ctx_mgr(struct file *filp)
	{
    int ret = 0;
    struct binder_proc *proc = filp->private_data;
    kuid_t curr_euid = current_euid();

    //保证只创建一次mgr_node对象
    if (binder_context_mgr_node != NULL) {
        ret = -EBUSY; 
        goto out;
    }

    if (uid_valid(binder_context_mgr_uid)) {
        ...
    } else {
        //设置当前线程euid作为Service Manager的uid
        binder_context_mgr_uid = curr_euid;
    }

    //创建ServiceManager的Binder节点
    binder_context_mgr_node = binder_new_node(proc, 0, 0);
    ...
    binder_context_mgr_node->local_weak_refs++;
    binder_context_mgr_node->local_strong_refs++;
    binder_context_mgr_node->has_strong_ref = 1;
    binder_context_mgr_node->has_weak_ref = 1;
	out:
    return ret;
	}

一般情况下，应用层的每个binder实体都会在binder驱动层对应一个binder_node节点，然而**binder_context_mgr_node**比较特殊，它没有对应的应用层binder实体。在整个系统里，它是如此特殊，以至于系统规定，任何应用都必须使用句柄0来跨进程地访问它。

<br>**2.4# binder_loop:**

    void binder_loop(struct binder_state *bs, binder_handler func) {
    int res;
    struct binder_write_read bwr;
    uint32_t readbuf[32];

    bwr.write_size = 0;
    bwr.write_consumed = 0;
    bwr.write_buffer = 0;

    readbuf[0] = BC_ENTER_LOOPER;
    //将>>>>>BC_ENTER_LOOPER<<<<<<<<<命令发送给binder驱动，让Service Manager进入循环
    binder_write(bs, readbuf, sizeof(uint32_t));

    for (;;) {
        bwr.read_size = sizeof(readbuf);
        bwr.read_consumed = 0;
        bwr.read_buffer = (uintptr_t) readbuf;

        res = ioctl(bs->fd, BINDER_WRITE_READ, &bwr); //*********进入循环，不断地binder读写过程***********
        if (res < 0) {
            break;
        }

        // ***********解析binder信息**********
        res = binder_parse(bs, 0, (uintptr_t) readbuf, bwr.read_consumed, func);
        if (res == 0) {
            break;
        }
        if (res < 0) {
            break;
        }
    }
	}

1.**BC_ENTER_LOOPER**
<br> binder_loop()中发出BC_ENTER_LOOPER命令的目的，是为了告诉binder驱动“本线程要进入循环状态了”。在binder驱动中，凡是用到跨进程通信机制的线程，都会对应一个binder_thread节点
<br>2.进入循环读写操作，由main()方法传递过来的参数func指向svcmgr_handler.func指向svcmgr_handler。故有请求到来，则调用svcmgr_handler函数中。
<br>3.binder_parse:
<br>binder_parse()负责解析从binder驱动读来的数据.

    int binder_parse(struct binder_state *bs, struct binder_io *bio,
                 uintptr_t ptr, size_t size, binder_handler func)
	{
    int r = 1;
    uintptr_t end = ptr + (uintptr_t) size;

    while (ptr < end) {
        uint32_t cmd = *(uint32_t *) ptr;
        ptr += sizeof(uint32_t);
        switch(cmd) {
        case BR_NOOP:  //无操作，退出循环
            break;
        case BR_TRANSACTION_COMPLETE:
            break;
        case BR_INCREFS:
        case BR_ACQUIRE:
        case BR_RELEASE:
        case BR_DECREFS:
            ptr += sizeof(struct binder_ptr_cookie);
            break;
        case BR_TRANSACTION: {
            struct binder_transaction_data *txn = (struct binder_transaction_data *) ptr;
            ...
            binder_dump_txn(txn);
            if (func) {
                unsigned rdata[256/4];
                struct binder_io msg; 
                struct binder_io reply;
                int res;
               
                bio_init(&reply, rdata, sizeof(rdata), 4);
                bio_init_from_txn(&msg, txn); //从txn解析出binder_io信息
                
                res = func(bs, txn, &msg, &reply);//处理数据传递后的信息！！！！回掉svcmgr_handler函数处理！！！
               
                binder_send_reply(bs, &reply, txn->data.ptr.buffer, res);
            }
            ptr += sizeof(*txn);
            break;
        }
        case BR_REPLY: {
            struct binder_transaction_data *txn = (struct binder_transaction_data *) ptr;
            ...
            binder_dump_txn(txn);
            if (bio) {
                bio_init_from_txn(bio, txn);
                bio = 0;
            }
            ptr += sizeof(*txn);
            r = 0;
            break;
        }
        case BR_DEAD_BINDER: {
            struct binder_death *death = (struct binder_death *)(uintptr_t) *(binder_uintptr_t *)ptr;
            ptr += sizeof(binder_uintptr_t);
            // binder死亡消息
            death->func(bs, death->ptr);
            break;
        }
        case BR_FAILED_REPLY:
            r = -1;
            break;
        case BR_DEAD_REPLY:
            r = -1;
            break;
        default:
            return -1;
        }
    }
    return r;
	}

**其中会回调svcmgr_handler函数，处理具体事件：**

    int svcmgr_handler(struct binder_state *bs,
                   struct binder_transaction_data *txn,
                   struct binder_io *msg,
                   struct binder_io *reply)
	{
    struct svcinfo *si;
    uint16_t *s;
    size_t len;
    uint32_t handle;
    uint32_t strict_policy;
    int allow_isolated;
    ...
    
    strict_policy = bio_get_uint32(msg);
    s = bio_get_string16(msg, &len);
    ...

    switch(txn->code) {
    case SVC_MGR_GET_SERVICE:
    case SVC_MGR_CHECK_SERVICE: 
        s = bio_get_string16(msg, &len); //服务名
        //根据名称查找相应服务
        handle = do_find_service(bs, s, len, txn->sender_euid, txn->sender_pid);//处理查找
       
        bio_put_ref(reply, handle);//结果返回
        return 0;

    case SVC_MGR_ADD_SERVICE: 
        s = bio_get_string16(msg, &len); //服务名
        handle = bio_get_ref(msg); //handle
        allow_isolated = bio_get_uint32(msg) ? 1 : 0;
         //注册指定服务
        if (do_add_service(bs, s, len, handle, txn->sender_euid,
            allow_isolated, txn->sender_pid))
            return -1;
        break;

    case SVC_MGR_LIST_SERVICES: {  
        uint32_t n = bio_get_uint32(msg);

        if (!svc_can_list(txn->sender_pid)) {
            return -1;
        }
        si = svclist;
        while ((n-- > 0) && si)
            si = si->next;
        if (si) {
            bio_put_string16(reply, si->name);
            return 0;
        }
        return -1;
    }
    default:
        return -1;
    }

    bio_put_uint32(reply, 0);
    return 0;}

该方法的功能：查询服务，注册服务，以及列举所有服务

>ps:每一个服务用**svcinfo**结构体来表示，该handle值是在注册服务的过程中，由服务所在进程那一端所确定的。这个链表被记录在ServiceManager中进行维护。注册及查询等操作时，均使用这个链表进行操作。

![](http://o9m6aqy3r.bkt.clouddn.com/Smgr%E4%B8%AD%E6%B3%A8%E5%86%8C%E7%9A%84%E6%9C%8D%E5%8A%A1%E7%BB%93%E6%9E%84.png)


<br>**2.5# do_find_service:**
<br>查询到目标服务，并返回该服务所对应的handle.

    uint32_t do_find_service(struct binder_state *bs, const uint16_t *s, size_t len, uid_t uid, pid_t spid)
	{
    //查询相应的服务
    struct svcinfo *si = find_svc(s, len);

    if (!si || !si->handle) {
        return 0;
    }

    if (!si->allow_isolated) {
        uid_t appid = uid % AID_USER;
        //检查该服务是否允许孤立于进程而单独存在
        if (appid >= AID_ISOLATED_START && appid <= AID_ISOLATED_END) {
            return 0;
        }
    }

    //服务是否满足查询条件
    if (!svc_can_find(s, len, spid)) {
        return 0;
    }
    return si->handle;
	}

>其中调用find_svc()从svclist服务列表中，根据服务名遍历查找是否已经注册。当服务已存在svclist，则返回相应的服务名，否则返回NULL。

    struct svcinfo *find_svc(const uint16_t *s16, size_t len)
	{
    struct svcinfo *si;

    for (si = svclist; si; si = si->next) {//svclist就是维护的已注册的服务链表
        //当名字完全一致，则返回查询到的结果
        if ((len == si->len) &&
            !memcmp(s16, si->name, len * sizeof(uint16_t))) {
            return si;
        }
    }
    return NULL;
	}
当找到服务的handle, 则调用bio_put_ref(reply, handle)，将handle封装到reply.

<br>**2.6# do_add_service:**

    int do_add_service(struct binder_state *bs,
                   const uint16_t *s, size_t len,
                   uint32_t handle, uid_t uid, int allow_isolated,
                   pid_t spid)
	{
    struct svcinfo *si;

    if (!handle || (len == 0) || (len > 127))
        return -1;

    //权限检查，检查selinux权限是否满足；
    if (!svc_can_register(s, len, spid)) {
        return -1;
    }

    //服务检索，根据服务名来查询匹配的服务；
    si = find_svc(s, len);
    if (si) {
        if (si->handle) {
            svcinfo_death(bs, si); //服务已注册时，释放服务，当查询到已存在同名的服务，则先清理该服务信息
        }
        si->handle = handle;
    } else {//将当前的服务加入到服务列表*********svclist***********
        si = malloc(sizeof(*si) + (len + 1) * sizeof(uint16_t));
        if (!si) {  //内存不足，无法分配足够内存
            return -1;
        }
        si->handle = handle;
        si->len = len;
        memcpy(si->name, s, (len + 1) * sizeof(uint16_t)); //内存拷贝服务信息
        si->name[len] = '\0';
        si->death.func = (void*) svcinfo_death;
        si->death.ptr = si;
        si->allow_isolated = allow_isolated;
        si->next = svclist; // svclist保存所有已注册的服务
        svclist = si;
    }

    //以BC_ACQUIRE命令，handle为目标的信息，通过ioctl发送给binder驱动
    binder_acquire(bs, handle);
    //以BC_REQUEST_DEATH_NOTIFICATION命令的信息，通过ioctl发送给binder驱动，主要用于清理内存等收尾工作。
    binder_link_to_death(bs, handle, &si->death);
    return 0;
	}

**注册服务的分以下3部分工作：**

- **svc_can_register：检查权限，检查selinux权限是否满足**；
- **find_svc：服务检索，根据服务名来查询匹配的服务**；
- **svcinfo_death：释放服务，当查询到已存在同名的服务，则先清理该服务信息，再将当前的服务加入到服务列表svclist**；

**ServiceManager启动流程：**
	
1. 打开binder驱动，并调用mmap()方法分配128k的内存映射空间：binder_open();
2. 通知binder驱动使其成为守护进程：binder_become_context_manager()；
3. 验证selinux权限，判断进程是否有权注册或查看指定服务；
4. 进入循环状态，等待Client端的请求：binder_loop()。
5. 注册服务的过程，根据服务名称，但同一个服务已注册，重新注册前会先移除之前的注册信息；
6. 死亡通知: 当binder所在进程死亡后,会调用binder_release方法,然后调用binder_node_release.这个过程便会发出死亡通知的回调.


**ServiceManager最核心的两个功能为查询和注册服务：**

- 注册服务：记录服务名和handle信息，保存到svclist列表；
- 查询服务：根据服务名查询相应的的handle信息。


## 3#Service注册服务 ##

## 4#客户端Client获取Service ##

## 5#Client Service通信过程？Binder线程池，Binder连接池 ##

## 6#Binder驱动的内存映射，mmap()分析 ##

## 7#Binder对象死亡通知机制 ##