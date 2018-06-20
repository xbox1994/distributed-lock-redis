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
        log.info("user1: I will use lock1 2s, so lock1 has been locked now!");
        redisLock.lock(lock1Key, UUID.randomUUID().toString());
        Thread.sleep(2000);
        log.info("user1: lock1 has been unlocked!");
        redisLock.unlock(lock1Key);
    }

    @Test
    public void should_release_lock_automatically_when_use_timeout_lock() throws InterruptedException {
        log.info("user2: I will use lock1, so lock1 has been locked now!");
        redisLock.lock(lock1Key, UUID.randomUUID().toString(), 3000);
        log.info("user2: lock1 has been unlocked automatically!");
    }
}
