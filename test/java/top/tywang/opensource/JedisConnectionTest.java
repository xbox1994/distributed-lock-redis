package top.tywang.opensource;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import top.tywang.opensource.lock.RedisLock;

import java.util.UUID;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MainApplication.class)
@Slf4j
public class JedisConnectionTest {
    @Autowired
    RedisLock redisLock;

    @Test
    public void testLock() throws InterruptedException {
        log.info("user1: I will use lock1, so lock1 has been locked now!");
        redisLock.lock("lock1", UUID.randomUUID().toString(), 3000);
        log.info("user1: lock1 has been unlocked!");
        log.info("user2: I will use lock1, so lock1 has been locked now!");
        redisLock.lock("lock1", UUID.randomUUID().toString(), 3000);
        log.info("user2: lock1 has been unlocked!");
    }
}
