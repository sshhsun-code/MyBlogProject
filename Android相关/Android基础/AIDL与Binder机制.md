### AIDL文件生成Binder代码

在使用AIDL进行进程跨进程通信时，其本质是为我们生成了一个符合Binder通信格式的代码，便于我们进行通信。下面根据，IBase.aidl文件和生成的IBase.java进行分析：

IBase.aidl



```java
// IBase.aidl
package com.review.sunqi.iamss.androidreview.aidl2;

// Declare any non-default types here with import statements

import com.review.sunqi.iamss.androidreview.aidl2.UserInfo;
interface IBase {

   int add();
   String getUserInfo(in UserInfo userInfo);
   void getList(out String[] list);
   void setList(in String[] list);
   void getSList(in String[] list);
}

```

==上述文件是在AIDL使用中，我们本地定义好的aidl接口，而在实际使用中，系统为我们生成了`IBase.java`文件，使用真正的Binder机制进行通信。==

IBase.java

```java
/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: F:\\AndroidReview\\app\\src\\main\\aidl\\com\\review\\sunqi\\iamss\\androidreview\\aidl2\\IBase.aidl
 */
package com.review.sunqi.iamss.androidreview.aidl2;
public interface IBase extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.review.sunqi.iamss.androidreview.aidl2.IBase
{//用于标识当前Binder类名。
private static final java.lang.String DESCRIPTOR = "com.review.sunqi.iamss.androidreview.aidl2.IBase";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.review.sunqi.iamss.androidreview.aidl2.IBase interface,
 * generating a proxy if needed.
 */
public static com.review.sunqi.iamss.androidreview.aidl2.IBase asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.review.sunqi.iamss.androidreview.aidl2.IBase))) {
return ((com.review.sunqi.iamss.androidreview.aidl2.IBase)iin);
}
return new com.review.sunqi.iamss.androidreview.aidl2.IBase.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_add:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.add();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_getUserInfo:
{
data.enforceInterface(DESCRIPTOR);
com.review.sunqi.iamss.androidreview.aidl2.UserInfo _arg0;
if ((0!=data.readInt())) {
_arg0 = com.review.sunqi.iamss.androidreview.aidl2.UserInfo.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
java.lang.String _result = this.getUserInfo(_arg0);
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_getList:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String[] _arg0;
int _arg0_length = data.readInt();
if ((_arg0_length<0)) {
_arg0 = null;
}
else {
_arg0 = new java.lang.String[_arg0_length];
}
this.getList(_arg0);
reply.writeNoException();
reply.writeStringArray(_arg0);
return true;
}
case TRANSACTION_setList:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String[] _arg0;
_arg0 = data.createStringArray();
this.setList(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_getSList:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String[] _arg0;
_arg0 = data.createStringArray();
this.getSList(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.review.sunqi.iamss.androidreview.aidl2.IBase
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public int add() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_add, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public java.lang.String getUserInfo(com.review.sunqi.iamss.androidreview.aidl2.UserInfo userInfo) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((userInfo!=null)) {
_data.writeInt(1);
userInfo.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_getUserInfo, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public void getList(java.lang.String[] list) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((list==null)) {
_data.writeInt(-1);
}
else {
_data.writeInt(list.length);
}
mRemote.transact(Stub.TRANSACTION_getList, _data, _reply, 0);
_reply.readException();
_reply.readStringArray(list);
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void setList(java.lang.String[] list) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStringArray(list);
mRemote.transact(Stub.TRANSACTION_setList, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void getSList(java.lang.String[] list) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStringArray(list);
mRemote.transact(Stub.TRANSACTION_getSList, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_add = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_getUserInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_getList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_setList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_getSList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
}
public int add() throws android.os.RemoteException;
public java.lang.String getUserInfo(com.review.sunqi.iamss.androidreview.aidl2.UserInfo userInfo) throws android.os.RemoteException;
public void getList(java.lang.String[] list) throws android.os.RemoteException;
public void setList(java.lang.String[] list) throws android.os.RemoteException;
public void getSList(java.lang.String[] list) throws android.os.RemoteException;
}

```
接下来我们继续分析一下，上述系统生成的`IBase.java`文件。

- `DESCRIPTOR`**标识用于标记当前Binder类名表示**。
- ` asInterface(android.os.IBinder obj)`**用于将服务端的Binder对象转换成客户端所需要的AIDL接口类型的对象，这种转换过程是区分进程的，如果客户端和服务端位于同一进程，那么此方法返回的就是服务端的Stub对象本身，否则返回的是系统封装后的Stub.proxy对象**。
- `asBinder`**此方法用于返回当前Binder对象**。
- `onTransact`**这个方法运行在服务端中的Binder线程池中，当客户端发起跨进程请求时，远程请求会通过系统底层封装后交由此方法来处理。该方法的原型为public Boolean onTransact(it code , Parcle data,Pacel reply, int fags) 服务端通过code可以确定客户端所请求的目标方法是什么，接着从data中取出目标方法所需的参数(如果目标方法有参数的话)，然后执行目标方法。当目标方法执行完毕后，reply中写入返回值(如果目标方法有返回值的话)，onTransact方法的执行过程就是这样的。需要注意的是，如果此方法返回alse那么客户端的请求会大败，因此我们可以利用这个特性来做权限验证，毕竟我们也不希望随便一个进程都能远星周用我们的服务**。


### 从IBase.java看Binder通信过程:

根据上述生成的`IBase.java`文件，客户端以及服务端就可以进行通信了；下面分别贴上**客户端**和**服务端**的代码。分析整个通信过程在Java FrameWork层的通信架构。

如果通信过程中，我们使用了`IBase.java`定义的getList接口方法。接下里顺着这个方法，从客户端知道服务端。

#### 客户端

首先，客户端拿到服务端的Binder的代理对象mBaseAidl.
```java
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //连接后拿到 Binder，转换成 AIDL，在不同进程会返回其代理
            mBaseAidl = IBase.Stub.asInterface(service);
            Log.e("sunqi_log", "AIDLService onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBaseAidl = null;
            Log.e("sunqi_log", "AIDLService onServiceDisconnected");
        }
    };
```

由于客户端与服务端不在同一个进程，所以拿到的是一个代理对象`IBase.Stub.Proxy`，之后在一个点击事件中，调用这个接口。
```java
        findViewById(R.id.btn_get_list).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    mBaseAidl.getList(mStrs);//客户端调用IBase的接口方法
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
```
==从客户端代码，到`IBase.java`文件中，实际是调用到了`IBase.java`中的：==
```java
                @Override
            public void getList(java.lang.String[] list) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    if ((list == null)) {
                        _data.writeInt(-1);
                    } else {
                        _data.writeInt(list.length);
                    }
                    mRemote.transact(Stub.TRANSACTION_getList, _data, _reply, 0);
                    _reply.readException();
                    _reply.readStringArray(list);
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
```

我们发现，_data和_reply对象分别用于输入参数，输出结果。在_data中输入参数后，会调用`mRemote.transact(Stub.TRANSACTION_getList, _data, _reply, 0);`

#### 服务端

在上述过程执行完成后，`IBase.java`文件中的`mRemote`（即服务端）中的
`onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)`会调用；根据`code`参数，调用到：

```java
                case TRANSACTION_getList: {
                    data.enforceInterface(DESCRIPTOR);
                    java.lang.String[] _arg0;
                    int _arg0_length = data.readInt();
                    if ((_arg0_length < 0)) {
                        _arg0 = null;
                    } else {
                        _arg0 = new java.lang.String[_arg0_length];
                    }
                    this.getList(_arg0);//实际调用服务端的实现代码
                    reply.writeNoException();
                    reply.writeStringArray(_arg0);
                    return true;
                }
```
之后，这里的`this.getList(_arg0)`会实际调用，我们服务端的处理代码，并将处理结果，通过reply返回(即`reply.writeStringArray(_arg0)`).
==这个过程从IBase.java文件中到了服务端代码。==

我们现在看一下，服务端的真实处理：

```java
    private IBinder mBinder = new IBase.Stub() {
    
        `
        `
        `
        `
       
        @Override
        public void getList(String[] list) throws RemoteException {
            list[0] = "Server端赋值：" + info;
        }
        
        `
        `
        `
        
    };
```

至此，从客户端到服务端的一次通信过程终于完成，在整个通信过程中，Parcel 对象 _data和_reply分别作为输入参数和输出结果，传递整个过程。

整个过程可以用下图表示：

![image](http://o9m6aqy3r.bkt.clouddn.com/Binder%E5%B7%A5%E4%BD%9C%E6%9C%BA%E5%88%B6%28java%E5%B1%82%29.png)

>同时要说明的是：当客户端发起远程请求时，由于当前线程会被挂起直到服务器返回数据，所以，如果一个远程方法是很耗时的，==所以**不能在UI线程中发起远程请求**==；

>同时，服务端Binder的实现方法运行在Binder线程池中，所以，==**无论Binder方法是否耗时都应采用同步的方式去实现，因为其已经运行在线程池的一个线程中**==。