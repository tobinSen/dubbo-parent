## dubbo总结
####一.dubbo的spi
#####jdk的spi
    1.在META-INF\service 下面添加全类名文件
    2.通过ServiceLoader进行加载
    注意：缺点是单文件的实例类很多时候，如果某一个类没有则报错信息不完整
    
#####dubbo spi
    1.在META-INF\dubbo 下面添加全类名文件
    2.通过ExtendLoader进行加载
    3.文件内容进行描述key=xxxx.serviceImpl
    4.dubbo的SPI机制增加了对IOC、AOP的支持，一个扩展点可以直接通过setter注入到其他扩展点
    注意：某一个类不存在的时候，会报错key的类不加载
####SPI注解
    @SPI 【扩展=备胎】（面向扩展编码 = default也是扩展）
    1.在某个接口上加上@SPI注解后，表明该接口为可扩展接口。
      我用协议扩展接口Protocol来举例子，如果使用者在<dubbo:protocol />、<dubbo:service />、<dubbo:reference />
      都没有指定protocol属性的话，那么就会默认DubboProtocol就是接口Protocol，因为在Protocol上有@SPI("dubbo")注解。
      而这个protocol属性值或者默认值会被当作该接口的实现类中的一个key，
      dubbo会去META-INF\dubbo\internal\com.alibaba.dubbo.rpc.Protocol文件中找该key对应的value
    @Adaptive
    1.添加到实现类上【优先级高于注解方法】
        表明该类是一个适配器类，然后可以动态的获取@SPI注解标记的接口的实现类
       步骤：
         1.interface上标记@SPI("默认key")
         2.impl实现类上标记@Adaptive
         3.在META-INF\dubbo\internal中添加key=xxxx.impl实现类
         
    2.添加到接口方法中
        ExtendLoader会去为该注解生产一个适配器类
       步骤：
         1.interface上标记@SPI("默认key")
         2.method上标记@Adaptive("默认参数（url中）key")[key=value]方法中参数必须要有一个URL对象用来参数来传递注解中的value值
            url中的参数设定
                1.@Adaptive中无参，则默认为类名的驼峰命名点的形式
                    example:com.alibaba.dubbo.xxx.YyyInvokerWrapper-->yyy.invoker.wrapper
                2.@Adaptive中有参，则参数名为key,value值为文件中的dubbo | adaptive
         3.在META-INF\dubbo\internal中添加value=xxxx.impl实现类
    总结：
        注解class ：shuqi.dubbotest.spi.adaptive.ThriftAdaptiveExt2
        注解method: shuqi.dubbotest.spi.adaptive.AdaptiveExt2$Adpative
    @Activate
        用于自动加载时实现类
        1.在impl实现类中添加会进行自动加载装配
        2.通过group value(参数名) order等参数进行判断什么时候进行加载和加载的顺序
####Wrapper（包装实现类）
    Wrapper类也实现了扩展接口，但是Wrapper类的用途是ExtensionLoader 返回扩展点时，包装在真正的扩展点实现外，这实现了扩展点自动包装的特性。
    通俗点说，就是一个接口有很多的实现类，这些实现类会有一些公共的逻辑，如果在每个实现类写一遍这个公共逻辑，那么代码就会重复，所以增加了这个Wrapper类来包装，把公共逻辑写到Wrapper类中，有点类似AOP切面编程思想
####ExtendLoader核心方法
    1.getExtensionLoader：一个扩展接口对应一个扩张加载器
    2.getActivateExtension：获取所有标记@Activate的实例类，过滤不加载的filter，并自动注入实例类中的值
        a.group的值合法判断，因为group可选"provider"或"consumer"。
        b.判断该配置是否被移除。
        c.如果有自定义配置，并且需要放在自动激活扩展实现对象加载前，那么需要先存放自定义配置
    3.getExtension:获得通过扩展名获得扩展对象(dubbo-->yyy.so.xxxImpl)
        a.加载扩展接口实现类
        b.存储扩展接口，并缓存起来
    4.getDefaultExtension方法：查找默认的扩展实现
    5.addExtension方法：扩展接口的实现类
    6.getAdaptiveExtension方法：获得自适应扩展对象，也就是接口的适配器对象
        a.如果是标记到实例类上，则进行依赖注入（实现类注入到适配器类中）
        b.如果是方法中，则产生一个代理的适配器类
    7.createExtension方法：通过扩展名创建扩展接口实现类的【对象】
    8.injectExtension方法：向创建的拓展（对象）注入其依赖的属性
    9.getExtensionClass方法：获得扩展名对应的扩展实现类
    10.loadExtensionClasses方法：从配置文件中，加载拓展实现类数组
    11.loadDirectory方法：从一个配置文件中，加载拓展实现类数组
    12.loadResource方法：加载文件中的内容
    13.loadClass方法：根据配置文件中的value加载扩展类
    16.createAdaptiveExtensionClass方法：创建适配器类，类似于dubbo动态生成的Transporter$Adpative这样的类
    总结：3 3 3 3点（wrapper）
    
####二.dubbo的注册中心
    dubbo默认有4种服务注册中心dubbo、multicast、zookeeper、redis
    注意：
        URL的作用（URL-->多个listener）
          1.dubbo是以总线模式来时刻传递和保存配置信息的，也就是配置信息都被放在URL上进行传递，随时可以取得相关配置信息
          2.URL有别的作用就是作为类似于节点（Node）的作用
    1.RegistryService：该接口是注册中心模块的服务接口，提供了注册、取消注册、订阅、取消订阅以及查询符合条件的已注册数据
        注册，如果看懂我上面说的url的作用，那么就很清楚该方法的作用了，这里强调一点，就是注释中讲到的允许URI相同但参数不同的URL并存，不能覆盖，也就是说url值必须唯一的，不能有一模一样。
        void register(URL url);
        //取消注册，该方法也很简单，就是取消注册，也就是商品生产者不在销售该商品， 需要把东西从自动售卖机上取下来，栏目也要取出，这里强调按全URL匹配取消注册。
        void unregister(URL url);
        订阅，这里不是根据全URL匹配订阅的，而是根据条件去订阅，也就是说可以订阅多个服务。listener是用来监听处理注册数据变更的事件。
        void subscribe(URL url, NotifyListener listener);
        取消订阅，这是按照全URL匹配去取消订阅的。
        void unsubscribe(URL url, NotifyListener listener);
        查询注册列表，通过url进行条件查询所匹配的所有URL集合。
        List<URL> lookup(URL url);
    2.Registry
        继承了一个Node接口，代表URL的节点信息
    3.RegistryFactory：这个接口是注册中心的工厂接口，用来返回注册中心的对象
        @SPI("dubbo") //优先去匹配DubboRegistryFactory
        public interface RegistryFactory {
            @Adaptive({"protocol"}) //通过url中的protocol进行动态的适配注册中心
            Registry getRegistry(URL url);
        }
    4.NotifyListener：该接口只有一个notify方法，通知监听器。当收到服务变更通知时触发（consumer触发）
    ----------------------------------------------------------------------------------------
    1.AbstractRegistry实现的是Registry接口，是Registry的抽象类。为了减轻注册中心的压力，在该类中实现了把本地URL缓存到property文件中的机制，并且实现了注册中心的注册、订阅等方法
          properties：properties的数据跟本地文件的数据同步，当启动时，会从文件中读取数据到properties，而当properties中数据变化时，会写入到file。而properties是一个key对应一个列表，比如说key就是消费者的url，而值就是服务提供者列表、路由规则列表、配置规则列表。就是类似属性notified的含义。需要注意的是properties有一个特殊的key为registies，记录的是注册中心列表。
          lastCacheChanged：因为每次写入file都是全部覆盖的写入，不是增量的去写入到文件，所以需要有这个版本号来避免老版本覆盖新版本。
          notified：跟properties的区别是第一数据来源不是文件，而是从注册中心中读取，第二个notified根据分类把同一类的值做了聚合。  
           file-->properties-->notifyListener
           1.doSaveProperties
           2.getCacheUrls
           3.lookup ：返回所有的URL信息
           4.register && unregister ：注册（添加到内存中）注销（内存中remove）
           5.subscribe && unsubscribe (url->listern的内存中添加 | 删除)URL-->监听器多个consumer-->多个provider
           6.recover //把内存缓存中的subscribed取出来遍历进行订阅
           7.notify
           8.saveProperties
           9.destroy：该方法在JVM关闭时调用，进行取消注册和订阅的操作
    2.FailbackRegistry [3点]
        1.继承AbstractRegistry，然后注册失败后调用super.register进行注册到内存中
        2.然后通过内存的扩展接口，将注册信息持久化
        3.定时器进行持久化的重启注册，这里的重试针对的是扩展的，内存默认是能够注册成功了
    3.AbstractRegistryFactory：该类实现了RegistryFactory接口，抽象了createRegistry方法，它实现了Registry的容器管理
        1.先通过URL中参数获取，是否存在register的注册中心
        2.如果不存在register，那么就根据URL参数来创建一个注册中心
    4.ProviderConsumerRegTable:记录的服务提供者、消费者、注册中心中间的调用链
    5.RegistryStatusChecker：注册中心状态检测，并返回注册状态和注册中心地址
    6.RegistryProtocol：RegistryProtocol是基于注册中心发现服务提供者的实现协议
    7.RegistryDirectory：
        a.注册中心服务，维护着所有可用的远程Invoker或者本地的Invoker
        b.订阅时和服务提供方数据有变动时回调消费方的NotifyListener服务的notify方法，回调接口传入所有服务的提供方的url地址然后将urls转化为invokers
    //总结：6点
    -----------------------------------------------------------------------------------------------
####注册中心[dubbo] 
    DubboRegistry [4点]（注册中心是一个Invoker->Node(URL | Registry)）
        1.构造方法修改registryUrl
        2.将之前的getRegistered()-->failedRegistered中定时进行向下扩展实现类
        3.dubbo-register的向下扩展，本质还是调用register-service的方法内存存储
    DubboRegistryFactory：dubbo的注册中心创建初始化
        1.RegistryDirectory：表示含有多个invoker
        2.Invoker<RegistryService> registryInvoker = cluster.join(directory);通过集群多化单
####注册中心[multicast]
        multicast其实是用到了MulticastSocket来实现的
    MulticastRegistry：
        1.构造方法：
            a.线程中做的工作是根据接收到的消息来判定是什么请求，作出对应的操作，只要mutilcastSocket没有断开，就一直接收消息，内部的实现体现在receive方法中，下文会展开讲述。
            b.定时清理任务是清理过期的注册的服务。通过两次socket的尝试来判定是否过期
    MulticastRegistryFactory:
        @Override
            public Registry createRegistry(URL url) {
                return new MulticastRegistry(url);
            }
####注册中心[redis]
        map中的key为URL地址，map中的value为过期时间，用于判断脏数据，脏数据由监控中心删除
    RedisRegistry: [3点]
         1.notifier.start();//开启线程
         2.jedis.psubscribe(new NotifySub(jedisPool), service); // blocking
         3.NotifySub.message-->调用父类的进行监听器的回调
         4.jedis.publish(key, Constants.UNREGISTER);
    RedisRegistryFactory：
         @Override
            protected Registry createRegistry(URL url) {
                return new RedisRegistry(url);
            }
####注册中心[zookeeper]
    zookeeper的存储dubbo的结构：dubbo -> service -> type(consuemr,provider) ->url（ip地址）
        dubbo的Root层是根目录，通过<dubbo:registry group="dubbo" />的“group”来设置zookeeper的根节点，缺省值是“dubbo”。
        Service层是服务接口的全名。
        Type层是分类，一共有四种分类，分别是providers（服务提供者列表）、consumers（服务消费者列表）、routes（路由规则列表）、configurations（配置规则列表）。
        URL层：根据不同的Type目录：可以有服务提供者 URL 、服务消费者 URL 、路由规则 URL 、配置规则 URL 。不同的Type关注的URL不同。
    ZookeeperRegistry：  
        所有Service层发起的订阅中的ChildListener是在在 Service 层发生变更时，才会做出解码，用anyServices属性判断是否是新增的服务，最后调用父类的subscribe订阅。而指定的Service层发起的订阅是在URL层发生变更的时候，调用notify，回调回调NotifyListener的逻辑，做到通知服务变更。
        所有Service层发起的订阅中客户端创建的节点是Service节点，该节点为持久节点，而指定的Service层发起的订阅中创建的节点是Type节点，该节点也是持久节点。这里补充一下zookeeper的持久节点是节点创建后，就一直存在，直到有删除操作来主动清除这个节点，不会因为创建该节点的客户端会话失效而消失。而临时节点的生命周期和客户端会话绑定。也就是说，如果客户端会话失效，那么这个节点就会自动被清除掉。注意，这里提到的是会话失效，而非连接断开。另外，在临时节点下面不能创建子节点。
        指定的Service层发起的订阅中调用了两次notify，第一次是增量的通知，也就是只是通知这次增加的服务节点，而第二个是全量的通知。
        zkClient的childChanged-->dubbo的listener
    ZookeeperRegistryFactory：
        private ZookeeperTransporter zookeeperTransporter;
        
            public void setZookeeperTransporter(ZookeeperTransporter zookeeperTransporter) {
                this.zookeeperTransporter = zookeeperTransporter;
            }
        
            @Override
            public Registry createRegistry(URL url) {
                return new ZookeeperRegistry(url, zookeeperTransporter);
            }
###三.dubbo-remoting模块
        buffer包：缓冲在NIO框架中是很重要的存在，各个NIO框架都实现了自己相应的缓存操作。这个buffer包下包括了缓冲区的接口以及抽象
        exchange包：信息交换层，其中封装了请求响应模式，在传输层之上重新封装了 Request-Response 语义，为了满足RPC的需求。这层可以认为专注在Request和Response携带的信息上。该层是RPC调用的通讯基础之一。
        telnet包：dubbo支持通过telnet命令来进行服务治理，该包下就封装了这些通用指令的逻辑实现。
        transport包：网络传输层，它只负责单向消息传输，是对 Mina, Netty, Grizzly 的抽象，它也可以扩展 UDP 传输。该层是RPC调用的通讯基础之一。
        最外层的源码：该部分我会在下面之间给出介绍
        ---------------------------------------------------------------------
    Transport层
        Endpoint:
            1.前三个方法是获得该端本身的一些属性，
            2.两个send方法是发送消息，其中第二个方法多了一个sent的参数，为了区分是否是第一次发送消息。
            3.后面几个方法是提供了关闭通道的操作以及判断通道是否关闭的操作
        Channel:
            channel是client和server的传输桥梁。channel和client是一一对应的，也就是一个client对应一个channel，但是channel和server是多对一对关系，也就是一个server可以对应多个channel
        ChannelHandler：负责channel中的逻辑处理
        Client：
            interface Client extends Endpoint, Channel, Resetable
        Server：
            interface Server extends Endpoint, Resetable
        Codec && Codec2：编解码器
            编码器是讲应用程序的数据转化为网络格式
            解码器是讲网络格式转化为应用程序
            1.Codec2是一个可扩展的接口，因为有@SPI注解。
            2.用到了Adaptive机制，首先去url中寻找key为codec的value，来加载url携带的配置中指定的codec的实现。
            3.该接口中有个枚举类型DecodeResult，因为解码过程中，需要解决 TCP 拆包、粘包的场景，所以增加了这两种解码结果，关于TCP 拆包、粘包的场景我就不多解释，不懂得朋友可以google一下。
        Decodeable：可解码的接口
            第一个是在调用真正的decode方法实现的时候会有一些校验，判断是否可以解码，并且对解码失败会有一些消息设置
            第二个是被用来message核对用的
        Dispatcher：调度器接口
            该接口是一个可扩展接口，并且默认实现AllDispatcher，也就是所有消息都派发到线程池，包括请求，响应，连接事件，断开事件，心跳等。
            1.该接口是一个可扩展接口，并且默认实现AllDispatcher，也就是所有消息都派发到线程池，包括请求，响应，连接事件，断开事件，心跳等
            2.用了Adaptive注解，也就是按照URL中配置来加载实现类，后面两个参数是为了兼容老版本，如果这是三个key对应的值都为空，就选择AllDispatcher来实现
        Transporter：传输接口
            1.该接口是一个可扩展的接口，并且默认实现NettyTransporter。
            2.用了dubbo SPI扩展机制中的Adaptive注解，加载对应的bind方法，使用url携带的server或者transporter属性值，加载对应的connect方法，使用url携带的client或者transporter属性值
        Transporters：对Transporter的包装
            1.该类用到了设计模式的外观模式，通过该类的包装，我们就不会看到内部具体的实现细节，这样降低了程序的复杂度，也提高了程序的可维护性。比如这个类，包装了调用各种实现Transporter接口的方法，通过getTransporter来获得Transporter的实现对象，具体实现哪个实现类，取决于url中携带的配置信息，如果url中没有相应的配置，则默认选择@SPI中的默认值netty。
            2.bind和connect方法分别有两个重载方法，其中的操作只是把把字符串的url转化为URL对象。
            3.静态代码块中检测了一下jar包是否有重复。
        AbstractPeer:实现ChannelHandler接口并且有在属性中还有一个handler，下面很多实现方法也是直接调用了handler方法，这种模式叫做装饰模式，这样做可以对装饰对象灵活的增强功能。对装饰模式不懂的朋友可以google一下。有很多例子介绍。
                     在该类中有closing和closed属性，在Endpoint中有很多关于关闭通道的操作，会有关闭中和关闭完成的状态区分，在该类中就缓存了这两个属性来判断关闭的状态
        AbstractEndpoint:编解码器以及两个超时时间
        AbstractServer：
        AbstractClient：
        AbstractChannel：
        ChannelHandlerDelegate：装饰者
        AbstractChannelHandlerDelegate：
        DecodeHandler：解码处理器
        MultiMessageHandler：多消息处理器
        WrappedChannelHandler：
        ExecutionChannelHandler：
    -------------------------------------------------------------------------
    Exchange层
        ExchangeChannel：该接口是信息交换通道接口
        HeaderExchangeChannel：是基于协议头的信息交换通道
        ExchangeClient：
        HeaderExchangeClient：
        Request：
        Response：
###四.dubbo-rpc模块
    背景：当我部署在A服务器上的应用想要调用部署在B服务器上的应用等方法，由于不存在同一个内存空间，不能直接调用
    Invoker：该接口是实体域，它是dubbo的核心模型，其他模型都向它靠拢，或者转化成它，它代表了一个可执行体，可以向它发起invoke调用，这个有可能是一个本地的实现，也可能是一个远程的实现，也可能是一个集群的实现。它代表了一次调用
    Invocation：是会话域，它持有调用过程中的变量，比如方法名，参数等
    Exporter：该接口是暴露服务的接口（产生Invoker），定义了两个方法分别是获得invoker和取消暴露服务
    ExporterListener：接口是服务暴露的监听器接口，定义了两个方法是暴露和取消暴露
    Protocol：关键的是【服务暴露】和【服务引用】两个方法
    Filter：接口是invoker调用时过滤器接口，其中就只有一个invoke方法。在该方法中对调用进行过滤
    InvokerListener：在【服务引用】的时候进行监听
    ProxyFactory：该接口是代理工厂接口，它也是个可扩展接口，默认实现javassist，dubbo提供两种动态代理方法分别是javassist/jdk，该接口定义了三个方法，前两个方法是通过invoker创建代理，最后一个是通过代理来获得invoke

    Result：实体域执行invoke的结果接口
    RpcContext：该类就是【远程调用的上下文】，贯穿着整个调用
    RpcInvocation：实现了Invocation接口，是rpc的会话域，其中的方法比较简单，主要是封装了上述的属性
    RpcResult：实现了Result接口，是rpc的结果实现类
    RpcStatus：类是rpc的一些状态监控，其中封装了许多的计数器，用来记录rpc调用的状态
    StaticContext：类是系统上下文，仅供内部使用
    EchoService：回声服务接口，定义了一个一个回声测试的方法，回声测试用于检测服务是否可用，回声测试按照正常请求流程执行，能够测试整个调用是否通畅，可用于监控，所有服务自动实现该接口，只需将任意服务强制转化为EchoService，就可以用了
    GenericService：该接口是通用的服务接口，同样定义了一个类似invoke的方法
    总计 8,4,3
    远程调用——Filter
        在【服务发现】和【服务引用】中都会进行一些过滤器过滤
        AccessLogFilter：对记录日志的过滤器,它所做的工作就是把引用服务或者暴露服务的【调用链信息】写入到文件中
        ActiveLimitFilter：该类时对于每个服务的每个方法的最大可并行调用数量限制的过滤器，它是在服务消费者侧的过滤
        ClassLoaderFilter：先切换成当前的线程锁携带的类加载器，然后调用结束后，再切换回原先的类加载器
        CompatibleFilter：过滤器是做兼容性的过滤器
        ConsumerContextFilter：该过滤器做的是在当前的RpcContext中记录本地调用的一次状态信息。
        ContextFilter：该过滤器做的是初始化rpc上下文
        DeprecatedFilter：该过滤器的作用是调用了废弃的方法时打印错误日志
        EchoFilter：如果调用的方法是回声测试的方法 则直接返回结果，否则 调用下一个调用链。
        ExecuteLimitFilter:该过滤器是限制最大可并行执行请求数，该过滤器是服务提供者侧，而上述讲到的ActiveLimitFilter是在消费者侧的限制
        GenericFilter:该过滤器就是对于泛化调用的请求和结果进行反序列化和序列化的操作，它是服务提供者侧的
        GenericImplFilter:该过滤器也是对于泛化调用的序列化检查和处理，它是消费者侧的过滤器
        TimeoutFilter:该过滤器是当服务调用超时的时候，记录告警日志
        TokenFilter:该过滤器提供了token的验证功能
        TpsLimitFilter:该过滤器的作用是对TPS限流
    远程调用——Listener
        ListenerInvokerWrapper：该类实现了Invoker，是服务引用监听器的包装类
        InvokerListenerAdapter：该类是服务引用监听器的适配类，没有做实际的操作。
        ExporterListenerAdapter：该类是服务暴露监听器的适配类，没有做实际的操作
    远程调用——Protocol
        AbstractProtocol：该类是协议的抽象类，实现了Protocol接口，其中实现了一些公共的方法，抽象方法在它的子类AbstractProxyProtocol中定义。
        AbstractProxyProtocol:其中利用了代理工厂对AbstractProtocol中的两个集合进行了填充，并且对异常做了处理。
        AbstractInvoker:该类是invoker的抽象方法，因为协议被夹在服务引用和服务暴露中间，无论什么协议都有一些通用的Invoker和exporter的方法实现，而该类就是实现了Invoker的公共方法，而把doInvoke抽象出来，让子类只关注这个方法。
        InvokerWrapper：该类是Invoker的包装类，其中用到类装饰模式，不过并没有实现实际的功能增强
        ProtocolFilterWrapper：该类实现了Protocol接口，其中也用到了装饰模式，是对Protocol的装饰，是在服务引用和暴露的方法上加上了过滤器功能。
        ProtocolListenerWrapper：该类也实现了Protocol，也是装饰了Protocol接口，但是它是在服务引用和暴露过程中加上了监听器的功能。
    远程调用——dubbo协议
           Dubbo 缺省协议采用单一长连接和 NIO 异步通讯，适合于小数据量大并发的服务调用，以及服务消费者机器数远大于服务提供者机器数的情况。反之，Dubbo 缺省协议不适合传送大数据量的服务，比如传文件，传视频等，除非请求量很低
        DubboInvoker：调用方法的三种模式，分别是异步发送、单向发送和同步发送
###集群
    dubbo的集群涉及到以下几部分内容：[6点]
        目录：Directory可以看成是多个Invoker的集合，但是它的值会随着注册中心中服务变化推送而动态变化，那么Invoker以及如何动态变化就是一个重点内容。
        集群容错：Cluster 将 Directory 中的多个 Invoker 伪装成一个 Invoker，对上层透明，伪装过程包含了容错逻辑，调用失败后，重试另一个。
        路由：dubbo路由规则，路由规则决定了一次dubbo服务调用的目标服务器，路由规则分两种：条件路由规则和脚本路由规则，并且支持可拓展。
        负载均衡策略：dubbo支持的所有负载均衡策略算法。
        配置：根据url上的配置规则生成配置信息
        分组聚合：合并返回结果。
        本地伪装：mork通常用于服务降级，mock只在出现非业务异常(比如超时，网络异常等)时执行
    directory-->List<Invoker>-->router-->invoker-->balanne--->Invoker-->远程调用
    1.cluster: 默认实现FailoverCluster
        Failsafe Cluster：失败安全，出现异常时，直接忽略。失败安全就是当调用过程中出现异常时，FailsafeClusterInvoker 仅会打印异常，而不会抛出异常。适用于写入审计日志等操作
        Failover Cluster：失败自动切换，当调用出现失败的时候，会自动切换集群中其他服务器，来获得invoker重试，通常用于读操作，但重试会带来更长延迟。一般都会设置重试次数。
        Failfast Cluster：只会进行一次调用，失败后立即抛出异常。适用于幂等操作，比如新增记录。
        Failback Cluster：失败自动恢复，在调用失败后，返回一个空结果给服务提供者。并通过定时任务对失败的调用记录并且重传，适合执行消息通知等操作。
        Forking Cluster：会在线程池中运行多个线程，来调用多个服务器，只要一个成功即返回。通常用于实时性要求较高的读操作，但需要浪费更多服务资源。一般会设置最大并行数。
        Available Cluster：调用第一个可用的服务器，仅仅应用于多注册中心。
        Broadcast Cluster：广播调用所有提供者，逐个调用，在循环调用结束后，只要任意一台报错就报错。通常用于通知所有提供者更新缓存或日志等本地资源信息
        Mergeable Cluster：该部分在分组聚合讲述。
        MockClusterWrapper：该部分在本地伪装讲述
    Configurator：就是配置规则，选取集群那个Invoker，通过规则来选举
    ConfiguratorFactory：产生一个Configurator对象
    Directory：该接口是目录接口，Directory 代表了多个 Invoker，并且它的值会随着注册中心的服务变更推送而变化 。一个服务类型对应一个Directory。定义的两个方法也比较好理解
    LoadBalance：该接口是负载均衡的接口，dubbo也提供了四种负载均衡策略，也会在下面文章讲解。
    Merger：该接口是分组聚合，将某对象数组合并为一个对象。
    Router：该接口是路由规则的接口，定义的两个方法，第一个方法是获得路由规则的url，第二个方法是筛选出跟规则匹配的Invoker集合。
    RouterFactory：产生一个Router接口
        AbstractClusterInvoker
         *  这里的Invoker本质是cluster中合并的虚拟集群的invoker
         *          |
         *   AbstractClusterInvoker
         * 该类实现了Invoker接口，是集群Invoker的抽象类。
         *
         *                                      Invoker
         *  Cluster(directory-》invoker)         |
         *                              AbstractClusterInvoker:选择一个Invoker
    2.AbstractConfigurator：集群的配置规则
        OverrideConfigurator：覆盖配置
        AbsentConfigurator：没有就添加配置（占时未用）
    3.AbstractDirectory：
    4.AbstractLoadBalance
        RandomLoadBalance：基于权重随机算法
        LeastActiveLoadBalance：基于最少活跃调用数算法
        ConsistentHashLoadBalance：基于 hash 一致性
        RoundRobinLoadBalance：基于加权轮询算法
    5.ConditionRouter：该类是基于条件表达式的路由实现类。关于给予条件表达式的路由规则
####dubbo-config [9点]
    1.AbstractConfig:配置解析的工具方法、公共方法
    2.AbstractMethodConfig：封装了一些方法级别的相关属性 
    3.AbstractInterfaceConfig：封装了接口契约需要的属性 
    4.AbstractReferenceConfig：主要是引用实例的配置
    5.AbstractServiceConfig
    
    //dubbo自身的
    ServiceConfig
    ReferenceConfig -->ReferenceConfigCache
    RegistryConfig
    
    //这两个是spring的
    ServiceBean -->@Service
    ReferenceBean -->@Reference
    
    URL格式： protocol://username:password@host:port/path?key=value&key=value
    
    dubbo配置
        xml
        注解
        api
    zookeeper的多集群多节点配置 |
    rpcContext:只是对一次的远程调用的数据
    
    [重点]spring整合dubbo
        xml->serviceConfig->beanDefition
           ->serviceBean-->export
           
           ->referenceBean->get