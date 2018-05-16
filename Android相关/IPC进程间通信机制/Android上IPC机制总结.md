# Android上Binder通信机制总结
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

## 3#Service注册服务 ##

## 4#客户端Client获取Service ##

## 5#Client Service通信过程？Binder线程池，Binder连接池 ##

## 6#Binder驱动的内存映射，mmap()分析 ##

## 7#Binder对象死亡通知机制 ##