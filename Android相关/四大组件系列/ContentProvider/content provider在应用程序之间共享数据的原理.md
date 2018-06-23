# Content Provider在应用程序之间共享数据的原理

- Android系统匿名共享内存简介

- ContentProvider如何使用匿名共享内存

参考 [https://blog.csdn.net/fyfcauc/article/details/50765372](https://blog.csdn.net/fyfcauc/article/details/50765372)

参考 [https://blog.csdn.net/luoshengyang/article/details/6666491](https://blog.csdn.net/luoshengyang/article/details/6666491)

参考 [https://blog.csdn.net/luoshengyang/article/details/6967204](https://blog.csdn.net/luoshengyang/article/details/6967204)

参考 [http://www.10tiao.com/html/223/201608/2651232011/1.html](http://www.10tiao.com/html/223/201608/2651232011/1.html)

## Android 匿名共享内存简介

>Ashmem(Anonymous Shared Memory 匿名共享内存)，是在 Android 的内存管理中提供的一种机制。它基于mmap系统调用，不同的进程可以将同一段物理内存空间映射到各自的虚拟空间，从而实现共享。


它以驱动程序的形式实现在内核空间。它有两个特点:
1. 是能够辅助内存管理系统来有效地管理不再使用的内存块;
2. 是它通过Binder进程间通信机制来实现进程间的内存共享。

Ashmem的两个特点就是共享和高效。

共享是指可以在不同进程间共享信息;

高效则是因为不同进程都是直接进行的内存操作，相对于其他的进程间通信方式来讲，这种方式会更快一些。

#### (1)匿名共享内存使用实例

Android系统的匿名共享内存系统的主体是以驱动程序的形式实现在内核空间的，同时，在系统运行时库和应用程序框架层提供了访问接口，其中，在系统运行时库提供了C/C ++ 调用接口，而在应用程序框架层提供了Java调用接口。Android开发中通常只需要调用Java接口，而实际上，应用程序框架层的Java调用接口是通过JNI方法来调用系统运行时库的C/C++调用接口，最后进入到内核空间的Ashmem驱动程序去的

 在Android中，主要提供了**MemoryFile**这个类来供应用使用匿名共享内存。在Android应用程序框架层，提供了一个MemoryFile接口来封装了匿名共享内存文件的创建和使用，通过JNI调用底层C++方法。

比如：

 在Activiy端，具体的实现代码如下：
 
 ```
 byte[] contentBytes = new byte[100];  
MemoryFile mf = new MemoryFile(“memfile”, contentBytes.length);  
mf.writeBytes(contentBytes, 0, 0, contentBytes.length);  
Method method = MemoryFile.class.getDeclaredMethod("getFileDescriptor");  
FileDescriptor fd = (FileDescriptor) method.invoke(mf);  
pfd = ParcelFileDescriptor.dup(fd);  
 ```
 
 在Service端，通过AIDL拿到文件描述符后，通过正常的文件读取方式，就可以读取到数据。
 
 ```
 fis = new FileInputStream(pfd .getFileDescriptor());  
fis.read(new byte[100]);  
 ```

**将文件描述符通过Binder机制传输，在目标端通过这个描述符，就可以拿到同一个文件并进行处理。实现进程间的数据共享**。通过这样的方式，可以避免Binder对传递的数据过大的限制，又可以解决跨进程传递数据的效率问题。

#### (2)Binder机制传输文件描述符

>我们知道，在Linux系统中，文件描述符其实就是一个整数。每一个进程在内核空间都有一个打开文件的数组，这个文件描述符的整数值就是用来索引这个数组的，而且，这个文件描述符只是在本进程内有效，也就是说，在不同的进程中，相同的文件描述符的值，代表的可能是不同的打开文件。

**在进程间传输文件描述符时，不能简要地把一个文件描述符从一个进程传给另外一个进程，中间必须做一过转换，使得这个文件描述在目标进程中是有效的，并且它和源进程的文件描述符所对应的打开文件是一致的，这样才能保证共享。**

结合前面的Binder进程间通信机制知识，我们通过下面这个序列图来总结这个实例中的匿名共享内存文件的文件描述符在进程间传输的过程：

![image](http://o9m6aqy3r.bkt.clouddn.com//contentProvider/binder_trans_fd.gif)

用来传输的Binder对象的数据结构struct flat_binder_object，它定义在kernel/common/drivers/staging/android/binder.h 文件中：

```
/* 
 * This is the flattened representation of a Binder object for transfer 
 * between processes.  The 'offsets' supplied as part of a binder transaction 
 * contains offsets into the data where these structures occur.  The Binder 
 * driver takes care of re-writing the structure type and data as it moves 
 * between processes. 
 */  
struct flat_binder_object {  
    /* 8 bytes for large_flat_header. */  
    unsigned long       type;  
    unsigned long       flags;  
  
    /* 8 bytes of data. */  
    union {  
        void        *binder;    /* local object */  
        signed long handle;     /* remote object */  
    };  
  
    /* extra data associated with local object */  
    void            *cookie;  
}; 
```
其中type域是一个枚举类型：
```
enum {  
    BINDER_TYPE_BINDER  = B_PACK_CHARS('s', 'b', '*', B_TYPE_LARGE),  
    BINDER_TYPE_WEAK_BINDER = B_PACK_CHARS('w', 'b', '*', B_TYPE_LARGE),  
    BINDER_TYPE_HANDLE  = B_PACK_CHARS('s', 'h', '*', B_TYPE_LARGE),  
    BINDER_TYPE_WEAK_HANDLE = B_PACK_CHARS('w', 'h', '*', B_TYPE_LARGE),  
    BINDER_TYPE_FD      = B_PACK_CHARS('f', 'd', '*', B_TYPE_LARGE),  
}; 
```

相关处理逻辑实现在binder_transact函数,其中，对于BINDER_TYPE_FD类型处理如下：

```
static void binder_transaction(struct binder_proc*proc,
                      structbinder_thread *thread,
                      structbinder_transaction_data *tr, int reply)
   ......
   fp = (struct flat_binder_object *)(t->buffer->data + *offp);  //1.获得Binder对象，并保存在本地变量fp中：
   ......
   
   switch(fp->type) {
    caseBINDER_TYPE_FD: {
            int target_fd;
            struct file *file;
            if (reply) {
             ......
            file = fget(fp->handle);//2. 文件描述符的值就保存在fp->handle中，通过fget函数取回这个文件描述符所对应的打开文件结构：
            ......
            target_fd = task_get_unused_fd_flags(target_proc, O_CLOEXEC);//3.接着在目标进程中获得一个空闲的文件描述符：
            ......
            task_fd_install(target_proc, target_fd, file);//4.把这个文件描述符和这个打开文件结构关联起来
            fp->handle = target_fd;//5.  由于这个Binder对象最终是要返回给目标进程的，所以还要修改fp->handle的值，它原来表示的是在源进程中的文件描述符，现在要改成目标进程的文件描述符：
     }break;
   ......//其他处理
}
```

首先是获得Binder对象，并保存在本地变量fp中：
```
fp = (struct flat_binder_object *)(t->buffer->data + *offp);  
```
文件描述符的值就保存在fp->handle中，通过fget函数取回这个文件描述符所对应的打开文件结构：
```
file = fget(fp->handle);  
```
这里的file是一个struct file指针，它表示一个打开文件结构。注间，在Linux系统中，打开文件结构struct file是可以在进程间共享的，它与文件描述符不一样。
       接着在目标进程中获得一个空闲的文件描述符：

```
target_fd = task_get_unused_fd_flags(target_proc, O_CLOEXEC); 
```
现在，在目标进程中，打开文件结构有了，文件描述符也有了，接下来就可以把这个文件描述符和这个打开文件结构关联起来就可以了：
```
task_fd_install(target_proc, target_fd, file);  
```

由于这个Binder对象最终是要返回给目标进程的，所以还要修改fp->handle的值，它原来表示的是在源进程中的文件描述符，现在要改成目标进程的文件描述符：
```
fp->handle = target_fd;  
```
这样，对文件描述符类型的Binder对象的处理就完成了。目标进程拿到这个文件描述符后，就可以和源进程一起共享打开文件了。

**这样binder驱动就悄无声息的帮我们在内核中在目标进程中新建了文件描述符，并将原进程的file结构与之挂钩，就像在目标进程中打开了原进程中的该文件一样，只不过返回给目标进程上层的描述符是新的target_fd**


**`task_fd_install(struct binder_proc *proc, unsigned int fd, struct file *file)`函数将file指向的file结构体和fd关联起来，会在proc指定的进程中生效**


## ContentProvider如何使用匿名共享内存

Binder进程间通信机制虽然打通了应用程序之间共享数据的通道，但是还有一个问题需要解决，那就是数据要以什么来作来媒介来传输。我们知道，应用程序采用Binder进程间通信机制进行通信时，要传输的数据都是采用函数参数的形式进行的，对于一般的进程间调来来说，这是没有问题的，然而，对于应用程序之间的共享数据来说，它们的数据量可能是非常大的，如果还是简单的用函数参数的形式来传递，效率就会比较低下。

在应用程序进程之间以匿名共享内存的方式来传输数据效率是非常高的，因为它们之间只需要传递一个文件描述符就可以了。因此，Content Provider组件在不同应用程序之间传输数据正是基于匿名共享内存机制来实现的。

Content Provider在进行数据传递时，包括跨进程通信时，使用了SQLiteCursor对象，即SQLite数据库游标对象。此对象包含了一个成员变量mWindow，它的类型为CursorWindow，这个成员变量是通过SQLiteCursor的setWindow成员函数来设置的。

**最重要的是CursorWindow对象内部包含一块匿名共享内存，它实际上存储了匿名共享内存文件描述符，占用很少内存空间；并且在跨进程通信过程中，Binder驱动程序能自动确保两个进程中的匿名共享内存文件描述符指向同一块匿名内存**。

SQLiteCursor在共享数据的传输过程中发挥着重要的作用，因此，我们先来它和其它相关的类的关系图：

![image](http://o9m6aqy3r.bkt.clouddn.com//contentProvider/SqliteCursor.png)

 在Content Provider这一侧，利用在Binder驱动程序为它创建好的这个匿名共享内存文件描述符，在本进程中创建了一个CursorWindow对象。
####  关于共享内存填充数据
 现在，Content Provider开始要从本地中从数据库中查询第三方应用程序想要获取的数据了。Content Provider首先会创建一个SQLiteCursor对象，即SQLite数据库游标对象，它继承了AbstractWindowedCursor类，后者又继承了AbstractCursor类，而AbstractCursor类又实现了CrossProcessCursor和Cursor接口。其中，最重要的是在AbstractWindowedCursor类中，有一个成员变量mWindow，它的类型为CursorWindow，这个成员变量是通过AbstractWindowedCursor的子类SQLiteCursor的setWindow成员函数来设置的。这个SQLiteCursor对象设置好了父类AbstractWindowedCursor类的mWindow成员变量之后，它就具有传输数据的能力了，因为这个mWindow对象内部包含一块匿名共享内存。
 
 此外，这个SQLiteCursor对象的内部有两个成员变量，一个是SQLite数据库对象mDatabase，另外一个是SQLite数据库查询对象mQuery。SQLite数据库查询对象mQuery的类型为SQLiteQuery，它继承了SQLiteProgram类，后者又继承了SQLiteClosable类。SQLiteProgram类代表一个数据库存查询计划，它的成员变量mCompiledSql包含了一个已经编译好的SQL查询语句，SQLiteCursor对象就是利用这个编译好的SQL查询语句来获得数据的，但是它并不是马上就去获取数据的，而是等到需要时才去获取。
 
**SQLiteCursor采用懒加载模式加载数据**，最初只是预编译一个查询计划，**需要执行的时候才去真正执行**。这个SQLiteCursor对象的内部还有一个SQLite数据库查询对象mQuery，类型为SQLiteQuery，它继承自SQLiteProgram类。**SQLiteProgram类代表一个数据库存查询计划，它的成员变量mCompiledSql包含了一个已经编译好的SQL查询语句，SQLiteCursor对象就是利用这个编译好的SQL查询语句来获得数据的，但是它并不是马上就去获取数据的，而是采用懒加载策略，等到需要时才去获取。**当真正执行的时候，例如调用了SQLiteCursor对象的getCount、moveToFirst等成员函数时，**SQLiteCursor对象通过调用成员变量mQuery的fillWindow成员函数来把从SQLite数据库中查询得到的数据保存其父类匿名共享内存mWindow中去。这是一种数据懒加载机制，需要的时候才去加载**，这样就提高了数据传输过程中的效率。

#### 关于SQLiteCursor传输问题
Content Provider向第三方应用程序返回的数据实际上是一个SQLiteCursor对象，那么，这个SQLiteCursor对象是如何传输到第三方应用程序的呢？因为它本身并不是一个Binder对象，我们需要对它进行适配一下。首先，Content Provider会根据这个SQLiteCursor对象来创建一个CursorToBulkCursorAdaptor适配器对象，这个适配器对象是一个Binder对象，因此，它可以在进程间传输，同时，它实现了IBulkCursor接口。Content Provider接着就通过Binder进程间通信机制把这个CursorToBulkCursorAdaptor对象返回给第三方应用程序，第三方应用程序得到了这个CursorToBulkCursorAdaptor之后，再在本地创建一个BulkCursorToCursorAdaptor对象，这个BulkCursorToCursorAdaptor对象的继承结构和SQLiteCursor对象是一样的，不过，它没有设置父类AbstractWindowedCursor的mWindow成员变量，因此，它只可以通过它内部的CursorToBulkCursorAdaptor对象引用来访问匿名共享内存中的数据，即通过访问Content Provider这一侧的SQLiteCursor对象来访问共享数据。

