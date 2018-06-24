package top.tywang.opensource.lock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import top.tywang.opensource.MainApplication;

import java.util.UUID;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MainApplication.class)
public class RedisLockTest {
    @Autowired
    private RedisLock redisLock;

    private String lock1Key = "lock1";

    private String lock1Value = UUID.randomUUID().toString();

    @Before
    public void resetRedisStatus() {
        redisLock.flushAll();
    }

    @Test
    public void shouldWaitWhenOneUsingBlockedLockAndTheOtherOneWantToUse() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                redisLock.lock(lock1Key, lock1Value);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t.start();
        t.join();

        long startTime = System.currentTimeMillis();
        redisLock.lock(lock1Key, lock1Value, 1000);
        assertThat(System.currentTimeMillis() - startTime).isBetween(500L, 1500L);
    }

    @Test
    public void shouldReturnFalseWhenOneUsingNonBlockedLockAndTheOtherOneWantToUse() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                redisLock.tryLock(lock1Key, lock1Value);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t.start();
        t.join();

        assertFalse(redisLock.tryLock(lock1Key, lock1Value));
    }

    @Test
    public void shouldResumeCurrentTaskAfterOtherProcessReleaseLock() throws InterruptedException {
        final boolean[] t1Done = {false};

        Thread t1 = new Thread(() -> {
            try {
                redisLock.lock(lock1Key, lock1Value);
                assertTrue(redisLock.isLocked(lock1Key));
                Thread.sleep(1000);
                t1Done[0] = true;
                redisLock.unlock(lock1Key, lock1Value);
                assertFalse(redisLock.isLocked(lock1Key));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t1.start();

        Thread.sleep(100); // wait t1 get lock1

        Thread t2 = new Thread(() -> {
            try {
                assertFalse(t1Done[0]);
                redisLock.lock(lock1Key, lock1Value);
                assertTrue(t1Done[0]);
                Thread.sleep(1000);
                redisLock.unlock(lock1Key, lock1Value);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t2.start();

        t1.join();
        t2.join();
    }

    @Test
    public void shouldReturnTrueWhenReleaseOwnLock() throws InterruptedException {
        redisLock.lock(lock1Key, lock1Value);
        assertTrue(redisLock.unlock(lock1Key, lock1Value));
    }

    @Test
    public void shouldReturnFalseWhenReleaseOthersLock() throws InterruptedException {
        redisLock.lock(lock1Key, lock1Value);
        assertFalse(redisLock.unlock(lock1Key, "other lock's value"));
    }

}
