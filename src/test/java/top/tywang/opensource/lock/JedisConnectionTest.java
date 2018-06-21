package top.tywang.opensource.lock;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import top.tywang.opensource.MainApplication;

import java.util.UUID;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MainApplication.class)
public class JedisConnectionTest {
    @Autowired
    private RedisLock redisLock;

    private String lock1Key = "lock1";

    @Test
    public void testLockAndUnlock() throws InterruptedException {
        Thread t1 = new Thread(() -> {
            try {
                log.info("user1: I try to get lock!");
                redisLock.lock(lock1Key, UUID.randomUUID().toString());
                log.info("user1: I am working now!");
                Thread.sleep(1000);
                log.info("user1: I am done");
                redisLock.unlock(lock1Key);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t1.start();

        Thread.sleep(100); // sleep for A get lock

        Thread t2 = new Thread(() -> {
            try {
                log.info("user2: I try to get lock!");
                redisLock.lock(lock1Key, UUID.randomUUID().toString());
                log.info("user2: I am working now!");
                Thread.sleep(1000);
                log.info("user2: I am done");
                redisLock.unlock(lock1Key);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t2.start();

        t1.join();
        t2.join();


    }
}
