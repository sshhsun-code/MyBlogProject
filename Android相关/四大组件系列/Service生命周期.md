
# Service生命周期 #




**服务生命周期（从创建到销毁）可以遵循两条不同的路径：**

- **启动服务<br>该服务在其他组件调用 `startService()` 时创建，然后无限期运行，且必须通过调用 `stopSelf()` 来自行停止运行。此外，其他组件也可以通过调用 `stopService()` 来停止服务。服务停止后，系统会将其销毁。**

- **绑定服务<br>该服务在另一个组件（客户端）调用 `bindService()` 时创建。然后，客户端通过 IBinder 接口与服务进行通信。客户端可以通过调用 `unbindService()` 关闭连接。多个客户端可以绑定到相同服务，而且当所有绑定全部取消后，系统即会销毁该服务。 （服务不必自行停止运行。）**


**这两条路径并非完全独立。也就是说，您可以绑定到已经使用 `startService()` 启动的服务。例如，可以通过使用 Intent（标识要播放的音乐）调用 `startService()` 来启动后台音乐服务。随后，可能在用户需要稍加控制播放器或获取有关当前播放歌曲的信息时，Activity 可以通过调用 `bindService()` 绑定到服务。在这种情况下，除非所有客户端均取消绑定，否则 `stopService()` 或 `stopSelf()` 不会实际停止服务。**


---

![](http://o9m6aqy3r.bkt.clouddn.com/service_lifecycle.png)

**在实际使用中有以下几种使用场景：**

![](http://o9m6aqy3r.bkt.clouddn.com/%E5%90%AF%E5%8A%A8%E5%90%8E%E5%86%8D%E7%BB%91%E5%AE%9A.png)