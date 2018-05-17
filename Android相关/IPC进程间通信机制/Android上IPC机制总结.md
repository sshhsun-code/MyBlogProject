# Android上Binder通信机制总结

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




## ServiceManager启动流程： ##
	
1. 打开binder驱动，并调用mmap()方法分配128k的内存映射空间：binder_open();
2. 通知binder驱动使其成为守护进程：binder_become_context_manager()；
3. 验证selinux权限，判断进程是否有权注册或查看指定服务；
4. 进入循环状态，等待Client端的请求：binder_loop()。
5. 注册服务的过程，根据服务名称，但同一个服务已注册，重新注册前会先移除之前的注册信息；
6. 死亡通知: 当binder所在进程死亡后,会调用binder_release方法,然后调用binder_node_release.这个过程便会发出死亡通知的回调.


**ServiceManager最核心的两个功能为查询和注册服务：**

- 注册服务：记录服务名和handle信息，保存到svclist列表；
- 查询服务：根据服务名查询相应的的handle信息。
