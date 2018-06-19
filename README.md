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

## 加锁、解锁、超时释放
NX是Redis提供的一个原子操作，如果指定key存在，那么NX失败，如果不存在会进行set操作并返回成功。我们可以利用这个来实现一个分布式的锁，主要思路就是，set成功表示获取锁，set失败表示获取失败，失败后需要重试。再加上EX参数可以让该key在超时之后自动删除。

下面是一个阻塞锁的加锁操作：

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

# 参考
https://www.jianshu.com/p/c2b4aa7a12f1  
https://crossoverjie.top/2018/03/29/distributed-top.tywang.opensource.lock/distributed-top.tywang.opensource.lock-redis/  
https://www.jianshu.com/p/de67ae50f919