# ContentProvider工作原理（一）
1. 初始化（启动与发布过程）
2. 工作过程（Binder通信链接到远程实现类）
3. 数据共享（查询结果+匿名共享内存）
4. 数据超大问题（Binder mmap内核缓存大小限制）


## Ⅰ.初始化（启动与发布过程）
（一）主动启动过程（Provider进程尚未启动）：
>安装应用程序的时候，并不会把相应的Content Provider加载到内存中来，系统采取的是懒加载的机制，等到
**第一次要使用这个Content Provider的时候，系统才会把它加载到内存中来，下次再要使用这个Content Provider的时候，就可以直接返回了**.

![image](http://o9m6aqy3r.bkt.clouddn.com//contentprovider/content_provider_start1.png)

1. 应用启动，创建并启动Content Provider所在进程
2. 在Android系统中，每一个应用程序进程都加载了一个ActivityThread实例，进程启动时候会调用ActivityThread的main函数。
3. ActivityThread实例通过ApplicationThread调用ActivityManagerService的attach，将当前进程和ActivityManagerService关联起来。在这个ActivityThread实例里面，有一个成员变量mAppThread，它是一个Binder对象，类型为ApplicationThread，实现了IApplicationThread接口，它是专门用来和ActivityManagerService服务进行通信的。
4. ActivityManagerService的attachApplication方法会进行此进程的关联操作，并通过ApplicationThread将Provider信息给ActivityThread
5. ApplicationThread调用installProvider来在本地安装每一个Content Proivder的信息，并且为每一个Content Provider创建一个ContentProviderHolder对象来保存相关的信息。ContentProviderHolder对象是一个Binder对象，是用来把Content Provider的信息传递给ActivityManagerService服务的
6. 当这些Content Provider都处理好了以后，ApplicationThread还要调用ActivityManagerService服务的publishContentProviders函数来通知ActivityManagerService服务，这个进程中所要加载的Content Provider，都已经准备完毕了


**更为详细的启动过程：**

![image](http://o9m6aqy3r.bkt.clouddn.com//contentProvider/contentprovider_start_2.png)

>PS:另外从图中可以发现**ContentProvider 的 onCreate 要先于 Application 的 onCreate 执行**

（二）被动启动过程：

**Content Provider被其他进程使用时，被动启动**
即从`getContentResolver()`开始的过程。

![image](http://o9m6aqy3r.bkt.clouddn.com//contentProvider/contentProvider_passive_start_1.png)

1. 某个进程需要访问其他进程提供的Content Provider时，需要先通过Context获取Content Resolver，使用如下方法：
context.getContentResolver();
而应用程序上下文Context是由ContextImpl类来实现的，ContextImpl类的init函数是在应用程序启动的时候调用的，生成的Content Resolver是ApplicationContentResolver类型的对象。
2. 拿到ContentResolver后，会调用到ContentResolver.acqireProvider来获取需要访问的Content Provider
3. ContentResolver验证参数URI的scheme是否正确，即是否是以content://开头，然后取出它的authority部分，最后获取Content Provider
4. 这里最终会调用ActivityThread.acquireProvider获取Content Provider
5. 此时Content Provider可能尚未加载，所以ActivityThread这里会有一个检查逻辑，在这里这个函数首先会通过getExistingProvider函数来检查本地是否已经存在这个要获取的Content Provider接口，如果存在，就直接返回了。本地已经存在的Context Provider接口保存在ActivityThread类的mProviderMap成员变量中，以Content Provider对应的URI的authority为键值保存。
6. 如果ActivityThread本地未找到此Content Provider，就会调用ActivityManagerService服务的getContentProvider接口来获取一个ContentProviderHolder对象，这个对象就包含了我们所要获取的Provider接口。
7. 在ActivityManagerService中，有两个成员变量是用来保存系统中的Content Provider信息的，一个是mProvidersByName，另外一个是mProvidersByClass，前者是以Content Provider的authoriry值为键值来保存的，后者是以Content Provider的类名为键值来保存的。一个Content Provider可以有多个authority，而只有一个类来和它对应，因此，这里要用两个Map来保存，这里为了方便根据不同条件来快速查找而设计的
8. 如果ActivityManagerService里没有此Content Provider缓存信息，这里会根据当前Content Provider是否是开启了多进程模式，如果是多进程模式，并且调用方UID和Content Provider声明的UID相同，则此处会创建一个ContentProviderHolder返回，通知调用方在本进程实例化一个
9. 一般情况下都不开多进程模式，所以按照单进程模式来，图中的第5步就是去启动Content Provider进程。所以ActivityManagerService通过AppGlobals.getPackageManager函数来获得PackageManagerService服务接口，然后分别通过它的resolveContentProvider和getApplicationInfo函数来分别获取Provider应用程序的相关信息，这些信息都是在安装应用程序的过程中保存下来的。调用startProcessLocked函数来启动Content Provider声明所在的进程来加载这个Content Provider对应的类，启动过程同前文介绍的主动启动APP过程。
10. 因为我们需要获取的Content Provider是在新的进程中加载的，而ActivityManagerService的getContentProviderImpl这个函数是在系统进程中执行的，它必须要等到要获取的Content Provider是在新的进程中加载完成后才能返回，这样就涉及到进程同步的问题了。这里使用的同步方法是不断地去检查变量provider域是否被设置了
11. 当要获取的Content Provider在新的进程加载完成之后，它会通过Binder进程间通信机制调用到系统进程中，把这个provider域设置为已经加载好的Content Provider接口，这时候，函数getContentProviderImpl就可以返回了
12. 返回给调用者ActivityThread之后，ActivityThread还会调用installProvider函数来把这个接口保存在本地中，以便下次要使用这个Content Provider接口时，直接就可以通过getExistingProvider函数获取了。同样是执行installProvider函数，与APP主动启动时候加载Provider不同，这里传进来的参数provider是不为null的，因此，它不需要执行在本地加载Content Provider的工作，只需要把从ActivityMangerService中获得的Content Provider接口保存在成员变量mProviderMap中就可以了



## Ⅱ.工作过程（Binder通信链接到远程实现类）


>**其他app或者进程想要操作ContentProvider，则需要先获取其相应的ContentResolver，再利用ContentResolver类来完成对数据的增删改查操作.**

其中ContentProvider的相关继承关系如图：

![image](http://o9m6aqy3r.bkt.clouddn.com//contentProvider/content_provider.jpg)

- CPP与CPN是一对Binder通信的C/S两端;
- ACR(ApplicationContentResolver)继承于ContentResolver, 位于ContextImpl的内部类. ACR的实现往往是通过调用其成员变量mMainThread(数据类型为ActivityThread)来完成;

>##### 整个ContentProvider其实就通过Binder机制，拿到ContentProvider的实现端CPN的接口，即CPP。并进行通信，执行各个具体方法。

**实现过程中的重要成员变量**

类名	|成员变量	|含义
---|---|---
AMS	|CONTENT_PROVIDER_PUBLISH_TIMEOUT	|默认值为10s
AMS	|mProviderMap	|记录所有contentProvider
AMS	|mLaunchingProviders	|记录存在客户端等待publish的ContentProviderRecord
PR	|pubProviders	|该进程创建的ContentProviderRecord
PR	|conProviders	|该进程使用的ContentProviderConnection
AT	|mLocalProviders	|记录所有本地的ContentProvider，以IBinder以key
AT	|mLocalProvidersByName	|记录所有本地的ContentProvider，以组件名为key
AT	|mProviderMap	|记录该进程的contentProvider
AT	|mProviderRefCountMap	|记录所有对其他进程中的ContentProvider的引用计数

- PR:ProcessRecord, AT: ActivityThread
- `CONTENT_PROVIDER_PUBLISH_TIMEOUT`(10s): provider所在进程发布其ContentProvider的超时时长为10s，超过10s则会系统所杀。
- `mLaunchingProviders`：记录的每一项是一个ContentProviderRecord对象, 所有的存在client等待其发布完成的contentProvider列表，一旦发布完成则相应的contentProvider便会从该列表移除；
- `mProviderMap`： AMS和AT都有一个同名的成员变量, AMS的数据类型为ProviderMap,而AT则是以ProviderKey为key的ArrayMap类型.
- `mLocalProviders`和`mLocalProvidersByName`：都是用于记录所有本地的ContentProvider,不同的只是key.

**query方法整个完整的过程如下：**

![image](http://o9m6aqy3r.bkt.clouddn.com//contentProvider/get_content_provider.jpg)


>**整个工作过程--->以ContentProvider的查询过程为例展开了对Provider的整个使用过程的源码分析.先获取provider,然后安装provider信息,最后便是真正的查询操作.**

**(一) 场景一（进程不存在）**

Provider进程不存在: 当provider进程不存在时,先创建进程并publish相关的provider:

![image](http://o9m6aqy3r.bkt.clouddn.com//contentProvider/content_provider_ipc.jpg)

图解:

1. client进程：通过binder(调用AMS.getContentProviderImpl)向system_server进程请求相应的provider；
2. system进程：如果目标provider所对应的进程尚未启动，system_server会调用startProcessLocked来启动provider进程； 当进程启动完成，此时cpr.provider ==null，则system_server便会进入wait()状态，等待目标provider发布；
3. provider进程：进程启动后执行完attch到system_server，紧接着执行bindApplication；在这个过程会installProvider以及 publishContentProviders；再binder call到system_server进程；
4. system进程：再回到system_server，发布provider信息，并且通过notify机制，唤醒前面处于wait状态的binder线程；并将 getContentProvider的结果返回给client进程；
5. client进程：接着执行installProvider操作，安装provider的(包含对象记录,引用计数维护等工作)；


**另外，关于`CONTENT_PROVIDER_PUBLISH_TIMEOUT`超时机制所统计的时机区间是指在`startProcessLocked`之后会调用`AMS.attachApplicationLocked`为起点，一直到`AMS.publishContentProviders`的过程**

---

**(二) 场景二（进程已存在）**

provider未发布: 请求provider时,provider进程存在但provide的记录对象cpr ==null,这时的流程如下:

![image](http://o9m6aqy3r.bkt.clouddn.com//contentProvider/content_provider_ipc2.jpg)

- Client进程在获取provider的过程,发现cpr为空,则调用scheduleInstallProvider来向provider所在进程发出一个oneway的binder请求,并进入wait()状态.
- provider进程安装完provider信息,则notifyAll()处于等待状态的进程/线程;


**如果provider在`publish`完成之后, 这时再次请求该provider,那就便没有的最右侧的这个过程,直接在`AMS.getContentProviderImpl`之后便进入`AT.installProvider`的过程,而不会再次进入wait()过程.**

最后, 关于provider分为`stable provider`和`unstable provider`, 在于引用计数 的不同，**一句话来说就是`stable provider`建立的是强连接, 客户端进程的与provider进程是存在依赖关系, 即provider进程死亡则会导致客户端进程被杀.**

**(三) CPP调用到CPN端。**

**经过上述操作客户端拿到服务端`ContentProviderProxy`对象以后，在`ContentProviderProxy`中执行`query`时，代码如下：**

```java

public Cursor query(String callingPkg, Uri url, String[] projection, String selection, String[] selectionArgs, String sortOrder, ICancellationSignal cancellationSignal) throws RemoteException {
    //实例化BulkCursorToCursorAdaptor对象
    BulkCursorToCursorAdaptor adaptor = new BulkCursorToCursorAdaptor();
    Parcel data = Parcel.obtain();
    Parcel reply = Parcel.obtain();
    try {
        data.writeInterfaceToken(IContentProvider.descriptor);
        data.writeString(callingPkg);
        url.writeToParcel(data, 0);
        int length = 0;
        if (projection != null) {
            length = projection.length;
        }
        data.writeInt(length);
        for (int i = 0; i < length; i++) {
            data.writeString(projection[i]);
        }
        data.writeString(selection);
        if (selectionArgs != null) {
            length = selectionArgs.length;
        } else {
            length = 0;
        }
        data.writeInt(length);
        for (int i = 0; i < length; i++) {
            data.writeString(selectionArgs[i]);
        }
        data.writeString(sortOrder);
        data.writeStrongBinder(adaptor.getObserver().asBinder());
        data.writeStrongBinder(cancellationSignal != null ? cancellationSignal.asBinder() : null);
        mRemote.transact(IContentProvider.QUERY_TRANSACTION, data, reply, 0);

        DatabaseUtils.readExceptionFromParcel(reply);
        if (reply.readInt() != 0) {
            BulkCursorDescriptor d = BulkCursorDescriptor.CREATOR.createFromParcel(reply);
            adaptor.initialize(d);
        } else {
            adaptor.close();
            adaptor = null;
        }
        return adaptor;
    } catch (RemoteException ex) {
        adaptor.close();
        throw ex;
    } catch (RuntimeException ex) {
        adaptor.close();
        throw ex;
    } finally {
        data.recycle();
        reply.recycle();
    }
}
```

**上述代理对象的transact会调用到服务端CPN.onTransact：**

```java
public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
    switch (code) {
        case QUERY_TRANSACTION:{
            data.enforceInterface(IContentProvider.descriptor);
            String callingPkg = data.readString();
            Uri url = Uri.CREATOR.createFromParcel(data);

            int num = data.readInt();
            String[] projection = null;
            if (num > 0) {
                projection = new String[num];
                for (int i = 0; i < num; i++) {
                    projection[i] = data.readString();
                }
            }

            String selection = data.readString();
            num = data.readInt();
            String[] selectionArgs = null;
            if (num > 0) {
                selectionArgs = new String[num];
                for (int i = 0; i < num; i++) {
                    selectionArgs[i] = data.readString();
                }
            }

            String sortOrder = data.readString();
            IContentObserver observer = IContentObserver.Stub.asInterface(
                    data.readStrongBinder());
            ICancellationSignal cancellationSignal = ICancellationSignal.Stub.asInterface(
                    data.readStrongBinder());
            //***************************************执行查询操作***************************************
            Cursor cursor = query(callingPkg, url, projection, selection, selectionArgs,
                    sortOrder, cancellationSignal);
            if (cursor != null) {
                CursorToBulkCursorAdaptor adaptor = null;
                try {
                    //创建CursorToBulkCursorAdaptor对象
                    adaptor = new CursorToBulkCursorAdaptor(cursor, observer,
                            getProviderName());
                    cursor = null;

                    BulkCursorDescriptor d = adaptor.getBulkCursorDescriptor();
                    adaptor = null;

                    reply.writeNoException();
                    reply.writeInt(1);
                    d.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                } finally {
                    if (adaptor != null) {
                        adaptor.close();
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            } else {
                reply.writeNoException();
                reply.writeInt(0);
            }
            return true;
        }
        ...
    }
}

```
而query方法最终调用的是ContentProvider中的mTransport对象中的方法：`class Transport extends ContentProviderNative`这个Transport对象执行具体的操作逻辑。

```java

public Cursor query(String callingPkg, Uri uri, String[] projection,
        String selection, String[] selectionArgs, String sortOrder,
        ICancellationSignal cancellationSignal) {
    validateIncomingUri(uri);
    uri = getUriWithoutUserId(uri);
    if (enforceReadPermission(callingPkg, uri, null) != AppOpsManager.MODE_ALLOWED) {
        if (projection != null) {
            return new MatrixCursor(projection, 0);
        }

        Cursor cursor = ContentProvider.this.query(uri, projection, selection,
                selectionArgs, sortOrder, CancellationSignal.fromTransport(
                        cancellationSignal));
        if (cursor == null) {
            return null;
        }
        return new MatrixCursor(cursor.getColumnNames(), 0);
    }
    final String original = setCallingPackage(callingPkg);
    try {
        // ***************这个query就是我们实现自己的Provider时，复写的查询方法。回调目标ContentProvider所定义的query方法
        return ContentProvider.this.query(
                uri, projection, selection, selectionArgs, sortOrder,
                CancellationSignal.fromTransport(cancellationSignal));
    } finally {
        setCallingPackage(original);
    }
}
```

**ContentProviderProxy是ContentProviderNative的内部类。这个ContentProviderNative类内部结构类似于AIDL文件自动生成的java类。ContentProviderProxy是ContentProviderNative中`asInterface`的返回值。而服务端的调用，会最终调用到ContentProvider类的Transport对象，执行具体逻辑。我们实现自己的Provider时，就是复写了Transport对象的内部执行逻辑，比如`query`,`insert`,`getType`等。**

## III. 数据共享（查询结果+匿名共享内存）

> Binder进程间通信机制虽然打通了应用程序之间共享数据的通道，但是还有一个问题需要解决，那就是数据要以什么来作来媒介来传输。我们知道，应用程序采用Binder进程间通信机制进行通信时，要传输的数据都是采用函数参数的形式进行的，对于一般的进程间调来来说，这是没有问题的，然而，对于应用程序之间的共享数据来说，它们的数据量可能是非常大的，如果还是简单的用函数参数的形式来传递，效率就会比较低下。

Content Provider在进行数据传递时，包括跨进程通信时，使用了`SQLiteCursor`对象，即SQLite数据库游标对象，此对象包含了一个**成员变量mWindow，它的类型为CursorWindow，这个成员变量是通过SQLiteCursor的setWindow成员函数来设置的。最重要的是CursorWindow对象内部包含一块匿名共享内存，它实际上存储了匿名共享内存文件描述符，占用很少内存空间**；并且在跨进程通信过程中，Binder驱动程序能自动确保两个进程中的匿名共享内存文件描述符指向同一块匿名内存。这样在跨进程传输中，结果数据并不需要跨进程传输，而是**在不同进程中通过传输的匿名共享内存文件描述符来操作同一块匿名内存，这样来实现不同进程访问相同数据的目的，所以节省了跨进程传输大量数据的开销，也大幅提升了效率**。

**第三方应用程序这一侧，当它需要访问Content Provider中的数据时，它会在本进程中创建一个CursorWindow对象，它在内部创建了一块匿名共享内存，同时，它实现了Parcel接口，因此它可以在进程间传输。接下来第三方应用程序把这个CursorWindow对象（连同它内部的匿名共享内存文件描述符）通过Binder进程间调用传输到Content Provider这一侧。这个匿名共享内存文件描述符传输到Binder驱动程序的时候，Binder驱动程序就会在目标进程（即Content Provider所在的进程）中创建另一个匿名共享文件描述符，指向前面已经创建好的匿名共享内存，因此，就实现了在两个进程中共享同一块匿名内存.**

![image](http://o9m6aqy3r.bkt.clouddn.com//contentProvider/SqliteCursor.png)



此外，**SQLiteCursor采用懒加载模式加载数据**，最初只是预编译一个查询计划，需要执行的时候才去真正执行。这个SQLiteCursor对象的内部还有一个SQLite数据库查询对象mQuery，类型为SQLiteQuery，它继承自SQLiteProgram类。SQLiteProgram类代表一个数据库存查询计划，它的成员变量mCompiledSql包含了一个已经编译好的SQL查询语句，SQLiteCursor对象就是利用这个编译好的SQL查询语句来获得数据的，但是**它并不是马上就去获取数据的，而是采用懒加载策略，等到需要时才去获取。当真正执行的时候，例如调用了SQLiteCursor对象的getCount、moveToFirst等成员函数时，SQLiteCursor对象通过调用成员变量mQuery的fillWindow成员函数来把从SQLite数据库中查询得到的数据保存其父类匿名共享内存mWindow中去。这是一种数据懒加载机制，需要的时候才去加载，这样就提高了数据传输过程中的效率**.

需要注意的是**Content Provider的call函数，这个函数比较特别，使用Bundle进行数据传递**。如前所述，一般Content Provider交互都是通过Cursor传递数据的，比如使用query函数从Content Provider中获得数据，会将数据通过匿名共享内存来返回给调用者。**当要传输的数据量比较大的时候，使用匿名共享内存来传输数据是比较好的，这样可以减少数据的拷贝，提高传输效率，节省内存占用。但是，当要传输的数据量小时，使用匿名共享内存来作为媒介就有点杀鸡用牛刀的味道，因为匿名共享内存并不是免费的午餐，系统创建和匿名共享内存也是有开销的。因此，Content Provider提供了call函数来让第三方应用程序来获取一些自定义数据，这些数据一般都比较小**，例如，只是传输一个整数，这样就可以用较小的代价来达到相同的数据传输的目的。


## IV. 数据超大问题（Binder mmap内核缓存大小限制）

>**对于跨进程传输，安卓系统都有一个数据包的最大1M限制，而且是各个跨进程通信共同复用的，所以当单个跨进程数据传输过大的时候，很容易出现这个异常。**

ContentProvider底层数据通信是采用了Binder，而关于Binder的文档也提到了.

`The Binder transaction buffer has a limited fixed size, currently 1Mb, which is shared by all transactions in progress for the process.`

**ContentProvider仅仅是对返回数据使用了虚拟共享内存，但是接口调用参数依然是需要跨进程传输的，比如要批量插入很多数据，那么就会出现一个插入数据的数组，如果这个太大了，那么这个操作一定会出现数据超大异常,会导致Binder报TransactionTooLargeException.**

**参数数据太大，超过了跨进程阈值1M的限制，会导致Content Provider所在进程根本收不到接口调用请求。解决办法还是不要使得接口参数太大，特别是批量操作接口，对应的参数数据不能太大，可以将批量操作拆成多个小的批量操作**。

**另外需要注意的call接口在跨进程中并没有使用虚拟共享内存，而是和普通AIDL一样使用了Binder框架，所以这个接口的使用一样存在普通AIDL的数据超大问题**。