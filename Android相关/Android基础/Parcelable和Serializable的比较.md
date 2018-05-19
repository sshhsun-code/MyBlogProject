# Parcelable和Serializable的比较

**1#Serializable接口的实现与原理**

Serializable接口属于java原生的序列化接口，实现也很简单：

- 对象的序列化处理非常简单，只需对象实现了Serializable 接口即可（该接口仅是一个标记，没有方法）
- 序列化的对象包括基本数据类型，所有集合类以及其他许多东西，还有Class 对象
- 对象序列化不仅保存了对象的“全景图”，而且能追踪对象内包含的所有句柄并保存那些对象；接着又能对每个对象内包含的句柄进行追踪
- 使用transient关键字修饰的的变量，在序列化对象的过程中，该属性不会被序列化。

**序列化实例**
<br>bean对象先实现Serializable接口：

    public class User implements Serializable {

    private static final long serialVersionUID = -2083503801443301445L;

    private int age;
    private String name;

    public User(int age, String name) {
        this.age = age;
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "User{" +
                "age=" + age +
                ", name='" + name + '\'' +
                '}';
    }
	}

再分别使用OutputStream和InputStream进行序列化以及反序列化：

    private void saveSerialUserInfo() {
        User user = new User(26, "李四");
        try {
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(cacheFile));
            out.writeObject(user);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printSerialCacheInfo() {
        try {
            ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(cacheFile));
            User user1 = (User) inputStream.readObject();
            inputStream.close();
            Toast.makeText(SerializableTestDemo.this, "useInfo = " + user1, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

**Serializable序列化原理**

>**<font color = "red">Serializable有将对象保存本地的作用，具体思想就是将对象序列化之后，用file文件读写方式将序列化后的对象保存到外部存储，原理上还是用文件存储的方式。</font>**

objectOutputStream.writeObject(user)方法执行的详细如下：

	public ObjectOutputStream(OutputStream out) throws IOException {
    verifySubclass();
    bout = new BlockDataOutputStream(out);
    handles = new HandleTable(10, (float) 3.00);
    subs = new ReplaceTable(10, (float) 3.00);
    enableOverride = false;
    writeStreamHeader();
    bout.setBlockDataMode(true);
    if (extendedDebugInfo) {
        debugInfoStack = new DebugTraceInfoStack();
    } else {
        debugInfoStack = null;
    }
	}
在writeObject0方法中，以下贴出重要的代码：

	if (obj instanceof String) {
    writeString((String) obj, unshared);
	} else if (cl.isArray()) {
    writeArray(obj, desc, unshared);
	} else if (obj instanceof Enum) {
    writeEnum((Enum) obj, desc, unshared);
	} else if (obj instanceof Serializable) {
    writeOrdinaryObject(obj, desc, unshared);//Serializable接口对象调用writeOrdinaryObject
	} else {
    if (extendedDebugInfo) {
        throw new NotSerializableException(
            cl.getName() + "\n" + debugInfoStack.toString());
    } else {
        throw new NotSerializableException(cl.getName());
    }
	}
writeSerialData方法，主要执行方法：defaultWriteFields(obj, slotDesc)，在此方法中，是系统默认的写入对象的非transient部分：

	private void defaultWriteFields(Object obj, ObjectStreamClass desc) throws IOException {
    Class<?> cl = desc.forClass();
    if (cl != null && obj != null && !cl.isInstance(obj)) {
        throw new ClassCastException();
    }

    desc.checkDefaultSerialize();

    int primDataSize = desc.getPrimDataSize();
    if (primVals == null || primVals.length < primDataSize) {
        primVals = new byte[primDataSize];
    }
    desc.getPrimFieldValues(obj, primVals);
    bout.write(primVals, 0, primDataSize, false);

    ObjectStreamField[] fields = desc.getFields(false);
    Object[] objVals = new Object[desc.getNumObjFields()];
    int numPrimFields = fields.length - objVals.length;
    desc.getObjFieldValues(obj, objVals);
    for (int i = 0; i < objVals.length; i++) {
        if (extendedDebugInfo) {
            debugInfoStack.push(
                "field (class \"" + desc.getName() + "\", name: \"" +
                fields[numPrimFields + i].getName() + "\", type: \"" +
                fields[numPrimFields + i].getType() + "\")");
        }
        try {
            writeObject0(objVals[i],
                         fields[numPrimFields + i].isUnshared());
        } finally {
            if (extendedDebugInfo) {
                debugInfoStack.pop();
            }
        }
    }
	}

需要注意的是：如果一个实现Serializable接口的类，内部实现了：

	private void readObject(ObjectInputStream inputStream) throws ClassNotFoundException, IOException {
        //code
    }
    
    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        //code
    }
    
会被ObjectInputStream与ObjectOutputStream 对象的readObject，writeObject方法去调用，原理如下。ObjectStreamClass(final Class<?> cl) 构造方法：
	
	if (externalizable) {
    cons = getExternalizableConstructor(cl);
	} else {
    cons = getSerializableConstructor(cl);
    writeObjectMethod = getPrivateMethod(cl, "writeObject",
        new Class<?>[] { ObjectOutputStream.class },
        Void.TYPE);
    readObjectMethod = getPrivateMethod(cl, "readObject",
        new Class<?>[] { ObjectInputStream.class },
        Void.TYPE);
    readObjectNoDataMethod = getPrivateMethod(
        cl, "readObjectNoData", null, Void.TYPE);
    hasWriteObjectData = (writeObjectMethod != null);
	}
在writeSerialData的方法当中，有以下的代码：利用反射机制去执行方法
		
	slotDesc.invokeWriteObject(obj, this);

**2#Parceable接口的实现与原理**
>**<font color = "blue">Parcelable的设计初衷是因为Serializable效率过慢，为了在程序内不同组件间以及不同Android程序间(AIDL)高效的传输数据而设计，这些数据仅在内存中存在，Parcelable是通过IBinder通信的消息的载体**。</font>

**Parceable接口的实现：**
Parceable接口实现相对于Serializable比较复杂，需要自己实现writeToParcel，将对象中的每一个字段都进行序列保存。

	public class Student implements Parcelable {

    private int age;
    private String name;

    public Student(int age, String name) {
        this.age = age;
        this.name = name;
    }

    protected Student(Parcel in) {
        age = in.readInt();
        name = in.readString();
    }

    public static final Creator<Student> CREATOR = new Creator<Student>() {//反序列化过程
        @Override
        public Student createFromParcel(Parcel in) {
            return new Student(in);
        }

        @Override
        public Student[] newArray(int size) {
            return new Student[size];
        }
    };

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Student{" +
                "age=" + age +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {//对象序列化，将每一字段进行序列化
        parcel.writeInt(age);
        parcel.writeString(name);
    }
	}


**Parceable接口实现原理：**
<br>Framework中有parcel类，对每一种数据类型采用不同的方法写入：

    public final void writeValue(Object v) {
        if (v == null) {
            writeInt(VAL_NULL);
        } else if (v instanceof String) {
            writeInt(VAL_STRING);
            writeString((String) v);
        } else if (v instanceof Integer) {
            writeInt(VAL_INTEGER);
            writeInt((Integer) v);
        } else if (v instanceof Map) {
            writeInt(VAL_MAP);
            writeMap((Map) v);
        } else if (v instanceof Bundle) {
            // Must be before Parcelable
            writeInt(VAL_BUNDLE);
            writeBundle((Bundle) v);
        } else if (v instanceof PersistableBundle) {
         //.....
    }

![](http://o9m6aqy3r.bkt.clouddn.com/Parcel%E4%B8%AD%E7%9A%84%E6%9C%80%E7%BB%88%E8%B0%83%E7%94%A8.png)

序列化方法nativeWriteXXX()实现最终实现在“android_os_Parcel.cpp”中处理：

![](http://o9m6aqy3r.bkt.clouddn.com/android_os_Parcel%E5%86%85%E9%83%A8%E5%AE%9E%E7%8E%B0.png)

调用系统原生方法进行序列化处理，直接进行数据写入与内存拷贝。
>**<font color = "red">整个序列化过程在内存中进行**。

>**系统已经为我们系统了许多实现了Parceable接口的类。它们都是可以直接序列化的，比如Intent，Bundle，Bitmap等，同事List和Map也可以序列化，只要保证其内部的元素都是可序列化的。**</font>

**3#Serializable与Parceable的比较**

**Parcelable的性能比Serializable好，在内存开销方面较小，所以在内存间数据传输时推荐使用Parcelable，如Activity,Service间传输数据;****而Serializable可将数据持久化方便保存，所以在需要保存或网络传输数据时选择Serializable，因为android不同版本Parcelable可能不同，所以不推荐使用Parcelable进行数据持久化.**

