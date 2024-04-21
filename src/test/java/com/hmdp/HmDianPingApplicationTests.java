package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    CacheClient cacheClient;
    @Resource
    ShopServiceImpl service;

    @Resource
    RedisIdWorker redisIdWorker;

    ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testID() throws InterruptedException {
        //同步辅助类，允许线程等待其他线程完成操作，这里设定300个线程并发
        CountDownLatch latch = new CountDownLatch(300);
        //定义生成id任务，每个线程执行生成100个测试ID
        Runnable task = () -> {
            for (int i = 0; i < 100; i++){
                long id = redisIdWorker.nextId("test");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++){
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));

    }

    @Test
    void testSaveShop(){
        Shop shop = service.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }


}
