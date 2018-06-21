# distributed-top.tywang.opensource.lock-redis
本项目是以学习的目的来一步一步实现基于Redis实现的分布式锁，下面就是以本项目为例的分布式锁实现教程

# 简介
分布式锁在分布式系统中非常常见，比如对公共资源进行操作，如卖车票，同一时刻只能有一个节点将某个特定座位的票卖出去；如避免缓存失效带来的大量请求访问数据库的问题

# 设计
这非常像一道面试题：如何实现一个分布式锁？在简介中，基本上已经对这个分布式工具提出了一些需求，你可以不着急看下面的答案，自己思考一下分布式锁应该如何实现？

首先我们需要一个简单的答题套路：需求分析、系统设计、实现方式、缺点不足

## 需求分析
1. 能够在高并发的分布式的系统中应用
2. 需要实现锁的基本特性：一旦某个锁被分配出去，那么其他的节点无法再进入这个锁所管辖范围内的资源；失效机制避免无限时长的锁与死锁
3. 进一步实现锁的高级特性和JUC并发工具类似功能更好：可重入、阻塞与非阻塞、公平与非公平、JUC的并发工具（Semaphore, CountDownLatch, CyclicBarrier）

## 系统设计
转换成设计是如下几个要求：

1. 对加锁、解锁的过程需要是高性能、原子性的
2. 需要在某个分布式节点都能访问到的公共平台上进行锁状态的操作
3. 锁状态的备份还原

所以，我们分析出系统的构成应该要有**锁状态存储模块**、**连接存储模块的连接池模块**、**锁内部逻辑模块**

### 锁状态存储模块
分布式锁的存储有三种常见实现，因为能满足实现锁的这些条件：高性能加锁解锁、操作的原子性、是分布式系统中不同节点都可以访问的公共平台：

1. 数据库（利用主键唯一规则、MySQL行锁）
2. 基于Redis的NX、EX参数
3. Zookeeper临时有序节点

由于锁常常是在高并发的情况下才会使用到的分布式控制工具，所以使用数据库实现会对数据库造成一定的压力，连接池爆满问题，所以不推荐数据库实现；我们还需要维护Zookeeper集群，实现起来还是比较复杂的。如果不是原有系统就依赖Zookeeper，同时压力不大的情况下。一般不使用Zookeeper实现分布式锁。所以缓存实现分布式锁还是比较常见的，因为**缓存比较轻量、缓存的响应快、吞吐高、还有自动失效的机制保证锁一定能释放**。

### 连接池模块
可使用JedisPool实现，如果后期性能不佳，可考虑参照HikariCP自己实现

### 锁内部逻辑模块

* 基本功能：加锁、解锁、超时释放
* 高级功能：可重入、阻塞与非阻塞、公平与非公平、JUC并发工具功能

## 实现方式
存储模块使用Redis，连接池模块暂时使用JedisPool，锁的内部逻辑将从基本功能开始，逐步实现高级功能，下面就是各种功能实现的具体思路与代码了。

### 加锁、解锁、超时释放
NX是Redis提供的一个原子操作，如果指定key存在，那么NX失败，如果不存在会进行set操作并返回成功。我们可以利用这个来实现一个分布式的锁，主要思路就是，set成功表示获取锁，set失败表示获取失败，失败后需要重试。再加上EX参数可以让该key在超时之后自动删除。

下面是一个阻塞锁的加锁操作，但会导致并发问题，如果超过超时时间但是业务还没执行完，那么其他进程就会执行业务代码，至于如何改进，下文会讲到，现在先简单来做：

```java
public void lock(String key, String request, int timeout) throws InterruptedException {
    Jedis jedis = jedisPool.getResource();

    while (timeout >= 0) {
        String result = jedis.set(LOCK_PREFIX + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, DEFAULT_EXPIRE_TIME);
        if (LOCK_MSG.equals(result)) {
            jedis.close();
            return;
        }
        Thread.sleep(DEFAULT_SLEEP_TIME);
        timeout -= DEFAULT_SLEEP_TIME;
    }
}
```

然后添加一个解锁操作：

```java
public void unlock(String key) {
    Jedis jedis = jedisPool.getResource();
    jedis.del(LOCK_PREFIX + key);
    jedis.close();
}
```

### 来用测试梭一把

此时我们可以来写个测试来试试有没有达到我们想要的效果，上面的代码都写在src/main/java下的RedisLock里，下面的测试代码需要写在src/test/java里，因为单元测试只是测试代码的逻辑，无法测试真实连接Redis之后的表现，也没办法体验到**被锁住带来的紧张又刺激的快感**，所以本项目中主要以集成测试为主，如果你想试试带Mock的单元测试，可以看看[这篇文章](https://crossoverjie.top/2018/03/29/distributed-lock/distributed-lock-redis/)。

那么集成测试会需要依赖一个Redis实例，为了避免你在本地去装个Redis来跑测试，我用到了一个嵌入式的Redis工具以及如下代码来帮我们New一个Redis实例，尽情去连接吧 ~ 代码可参看EmbeddedRedis类。另外，集成测试使用到了Spring，是不是倍感亲切？相当于也提供了一个集成Spring的例子。

```java
@Configuration
public class EmbeddedRedis implements ApplicationRunner {

    private static RedisServer redisServer;

    @PreDestroy
    public void stopRedis() {
        redisServer.stop();
    }

    @Override
    public void run(ApplicationArguments applicationArguments) {
        redisServer = RedisServer.builder().setting("bind 127.0.0.1").setting("requirepass test").build();
        redisServer.start();
    }
}
```

对于需要考虑并发的代码下的测试是比较难且比较难以达到检测代码质量的目的的，因为测试用例会用到多线程的环境，不一定能百分百通过且难以重现，但本项目的分布式锁是一个比较简单的并发场景，所以我会尽可能保证测试是有意义的。

我第一个测试用例是想测试一下锁的互斥能力，能否在A拿到锁之后，B就无法立即拿到锁：

```java
@Test
public void testLockWait() throws InterruptedException {
    Thread t = new Thread(() -> {
        try {
            redisLock.lock(lock1Key, UUID.randomUUID().toString());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    });
    t.start();
    t.join();

    long startTime = System.currentTimeMillis();
    redisLock.lock(lock1Key, UUID.randomUUID().toString(), 3000);
    assertThat(System.currentTimeMillis() - startTime).isBetween(2500L, 3500L);
}
```

但这仅仅测试了加锁操作时候的互斥性，但是没有测试解锁是否会成功以及解锁之后原来等待锁的进程会继续进行，所以你可以参看一下testLockAndUnlock方法是如何测试的。不要觉得写测试很简单，**想清楚测试的各种情况，设计测试情景并实现**并不容易。然而以后写的测试不会单独拿出来讲，毕竟本文想关注的还是分布式锁的实现嘛。


# 参考
https://www.jianshu.com/p/c2b4aa7a12f1  
https://crossoverjie.top/2018/03/29/distributed-lock/distributed-lock-redis/  
https://www.jianshu.com/p/de67ae50f919