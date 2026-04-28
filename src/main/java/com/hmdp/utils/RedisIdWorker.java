package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private  static  final long BEGIN_TIMESTAMP = 1640995200L;
    //
    private  static  final int  COUNT_BITS = 32;
    @Resource
    StringRedisTemplate stringRedisTemplate;



    public  long nextId(String keyPrefix)
    {    //生成时间戳
        LocalDateTime now =LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp =nowSecond-BEGIN_TIMESTAMP;
        //生成序列号
        //获取当天日期
        String date  = now.format(DateTimeFormatter.ofPattern("yyyy:MM;dd"));
        //自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 👇👇👇 核心修复：手动判空抛出明确的异常 👇👇👇
        if (count == null) {
            throw new RuntimeException("Redis ID 生成失败！");
        }

        return  timestamp<< COUNT_BITS| count;


    }



    public static void  main(String[] args){


             LocalDateTime time = LocalDateTime.of(2022,1,1,0,0,0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second="+second);

    }

}
