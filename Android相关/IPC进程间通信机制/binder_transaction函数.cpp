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