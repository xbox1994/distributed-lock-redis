package top.tywang.opensource.lock;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import top.tywang.opensource.MainApplication;

import java.util.UUID;

import static org.assertj.core.api.Java6Assertions.assertThat;


@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MainApplication.class)
public class JedisConnectionTest {
    @Autowired
    private RedisLock redisLock;

    private String lock1Key = "lock1";

    @Before
    public void resetRedisStatus() {
        redisLock.unlock(lock1Key);
    }

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
        redisLock.lock(lock1Key, UUID.randomUUID().toString(), 1000);
        assertThat(System.currentTimeMillis() - startTime).isBetween(500L, 1500L);
    }

    @Test
    public void testLockAndUnlock() throws InterruptedException {
        final boolean[] t1Done = {false};

        Thread t1 = new Thread(() -> {
            try {
                redisLock.lock(lock1Key, UUID.randomUUID().toString());
                Assert.assertTrue(redisLock.isLocked(lock1Key));
                Thread.sleep(1000);
                t1Done[0] = true;
                redisLock.unlock(lock1Key);
                Assert.assertFalse(redisLock.isLocked(lock1Key));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t1.start();

        Thread.sleep(100); // sleep for A get lock

        Thread t2 = new Thread(() -> {
            try {
                Assert.assertFalse(t1Done[0]);
                redisLock.lock(lock1Key, UUID.randomUUID().toString());
                Assert.assertTrue(t1Done[0]);
                Thread.sleep(1000);
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
