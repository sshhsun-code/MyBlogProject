
# Service生命周期 #




**服务生命周期（从创建到销毁）可以遵循两条不同的路径：**

- **启动服务<br>该服务在其他组件调用 `startService()` 时创建，然后无限期运行，且必须通过调用 `stopSelf()` 来自行停止运行。此外，其他组件也可以通过调用 `stopService()` 来停止服务。服务停止后，系统会将其销毁。**

- **绑定服务<br>该服务在另一个组件（客户端）调用 `bindService()` 时创建。然后，客户端通过 IBinder 接口与服务进行通信。客户端可以通过调用 `unbindService()` 关闭连接。多个客户端可以绑定到相同服务，而且当所有绑定全部取消后，系统即会销毁该服务。 （服务不必自行停止运行。）**


**这两条路径并非完全独立。也就是说，您可以绑定到已经使用 `startService()` 启动的服务。例如，可以通过使用 Intent（标识要播放的音乐）调用 `startService()` 来启动后台音乐服务。随后，可能在用户需要稍加控制播放器或获取有关当前播放歌曲的信息时，Activity 可以通过调用 `bindService()` 绑定到服务。在这种情况下，除非所有客户端均取消绑定，否则 `stopService()` 或 `stopSelf()` 不会实际停止服务。**


---

![](http://o9m6aqy3r.bkt.clouddn.com/service_lifecycle.png)

**在实际使用中有以下几种使用场景：**

![](http://o9m6aqy3r.bkt.clouddn.com/%E5%90%AF%E5%8A%A8%E5%90%8E%E5%86%8D%E7%BB%91%E5%AE%9A.png)

---

>**在Service每一次的开启关闭过程中，只有onStart可被多次调用(通过多次startService调用)，其他onCreate，onBind，onUnbind，onDestory在一个生命周期中只能被调用一次。**


1. **通过startservice开启的服务.一旦服务开启, 这个服务和开启他的调用者之间就没有任何的关系了. 
调用者不可以访问 service里面的方法. 调用者如果被系统回收了或者调用了ondestroy方法, service还会继续存在** 
2. **通过bindService开启的服务,服务开启之后,调用者和服务之间 还存在着联系 , 
一旦调用者挂掉了.service也会跟着挂掉 .**
3. **混合使用的场景下，只有当所有的调用者释放掉一个service的bind引用(即unbindService)，这个时候再用stopService(或者先stopService再释放所有的bind引用)，这个service才会结束生命周期。**


---
## 管理绑定服务的生命周期

![image](http://o9m6aqy3r.bkt.clouddn.com//service/service_binding_tree_lifecycle.png)

当服务与所有客户端之间的绑定全部取消时，Android 系统便会销毁服务（除非还使用 `onStartCommand()` 启动了该服务）。因此，如果您的服务是纯粹的绑定服务，则无需对其生命周期进行管理 — Android 系统会根据它是否绑定到任何客户端代您管理。

不过，如果您选择实现 `onStartCommand()` 回调方法，则您必须显式停止服务，因为系统现在已将服务视为已启动。在此情况下，服务将一直运行到其通过 `stopSelf()` 自行停止，或其他组件调用 `stopService()` 为止，无论其是否绑定到任何客户端。

此外，如果您的服务已启动并接受绑定，则当系统调用您的 `onUnbind()` 方法时，如果您想在客户端下一次绑定到服务时接收 `onRebind()` 调用，则可选择返回 `true`。`onRebind()` 返回空值，但客户端仍在其 `onServiceConnected()` 回调中接收 `IBinder`。