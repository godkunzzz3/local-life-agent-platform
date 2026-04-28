package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.SECKILL_BEGIN_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_END_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.hmdp.utils.RedisConstants.STREAM_ORDERS;
import static com.hmdp.utils.RedisConstants.STREAM_ORDERS_CONSUMER;
import static com.hmdp.utils.RedisConstants.STREAM_ORDERS_GROUP;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderServiceImpl.class);

    // 秒杀资格判断脚本：把查库存、一人一单、扣 Redis 库存、写 Stream 放在 Redis 内原子执行。
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 秒杀下单落库交给后台线程异步处理，请求线程只负责快速返回订单号。
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private volatile boolean running = true;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private IShopService shopService;
    @Resource
    private TransactionTemplate transactionTemplate;

    @PostConstruct
    private void init() {
        // 应用启动时准备 Stream 消费者组，并把数据库中已有秒杀券预热到 Redis。
        initStreamGroup();
        preloadSeckillVouchers();
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    @PreDestroy
    private void destroy() {
        // Spring Boot 停止时，Redis 连接池会先后关闭。这里主动让后台消费线程退出，避免关闭阶段继续读 Redis。
        running = false;
        SECKILL_ORDER_EXECUTOR.shutdownNow();
    }

    @Override
    public Result buyVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null || voucher.getStatus() == null || voucher.getStatus() != 1) {
            return Result.fail("优惠券不存在或已下架");
        }
        if (voucher.getType() != null && voucher.getType() == 1) {
            return Result.fail("秒杀券请走限时抢购入口");
        }

        VoucherOrder order = new VoucherOrder();
        order.setId(redisIdWorker.nextId("order"));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        // 当前项目没有接入真实支付网关，普通券购买按“余额支付成功”落单，方便前端立即在我的订单展示。
        order.setPayType(1);
        order.setStatus(2);
        order.setPayTime(java.time.LocalDateTime.now());
        save(order);
        return Result.ok(order.getId());
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 请求进来后只执行 Lua。Lua 返回 0 表示抢购资格成立，并已经把订单消息写入 stream.orders。
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId),
                String.valueOf(System.currentTimeMillis())
        );
        if (result == null) {
            return Result.fail("下单失败，请稍后重试");
        }
        int r = result.intValue();
        // 非 0 是 Lua 约定的失败码，避免在 Java 中再查 Redis 做多次网络往返。
        if (r != 0) {
            return Result.fail(getSeckillFailMessage(r));
        }
        return Result.ok(orderId);
    }

    @Override
    public Result queryMyOrders() {
        Long userId = UserHolder.getUser().getId();
        List<VoucherOrder> orders = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .list();
        if (orders.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (VoucherOrder order : orders) {
            Voucher voucher = voucherService.getById(order.getVoucherId());
            Shop shop = voucher == null || voucher.getShopId() == null ? null : shopService.getById(voucher.getShopId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", order.getId());
            // Long 型雪花订单号超过 JS 安全整数范围，前端展示必须使用字符串，避免尾号精度丢失。
            row.put("orderNo", String.valueOf(order.getId()));
            row.put("voucherCode", buildVoucherCode(order));
            row.put("voucherId", order.getVoucherId());
            row.put("status", order.getStatus());
            row.put("payType", order.getPayType());
            row.put("createTime", order.getCreateTime());
            row.put("payTime", order.getPayTime());
            row.put("voucherTitle", voucher == null ? "优惠券已下架" : voucher.getTitle());
            row.put("voucherSubTitle", voucher == null ? "" : voucher.getSubTitle());
            row.put("payValue", voucher == null ? 0 : voucher.getPayValue());
            row.put("actualValue", voucher == null ? 0 : voucher.getActualValue());
            row.put("voucherType", voucher == null ? 0 : voucher.getType());
            row.put("shopId", shop == null ? null : shop.getId());
            row.put("shopName", shop == null ? "未知商户" : shop.getName());
            row.put("shopImage", resolveCover(shop));
            result.add(row);
        }
        return Result.ok(result);
    }

    private String buildVoucherCode(VoucherOrder order) {
        // 企业里通常会单独落库 voucher_code 并加唯一索引；当前学习项目不改表结构，
        // 采用“订单号派生券码”的方式保证同一订单每次查询得到的核销码都一致。
        String raw = String.valueOf(order.getId());
        String compactCode = raw.length() > 12 ? raw.substring(raw.length() - 12) : raw;
        StringBuilder code = new StringBuilder("DP");
        for (int i = 0; i < compactCode.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                code.append(' ');
            }
            code.append(compactCode.charAt(i));
        }
        return code.toString();
    }

    private String resolveCover(Shop shop) {
        if (shop == null || shop.getImages() == null || shop.getImages().isEmpty()) {
            return "/imgs/icons/default-icon.png";
        }
        return shop.getImages().split(",")[0];
    }

    private String getSeckillFailMessage(int result) {
        switch (result) {
            case 1:
                return "库存不足";
            case 2:
                return "不能重复下单";
            case 3:
                return "秒杀尚未开始";
            case 4:
                return "秒杀已经结束";
            default:
                return "下单失败，请稍后重试";
        }
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    // XREADGROUP 读取新消息，">" 表示只读还没投递过的新订单。
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(STREAM_ORDERS_GROUP, STREAM_ORDERS_CONSUMER),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(STREAM_ORDERS, ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    handleRecord(list.get(0));
                } catch (Exception e) {
                    if (!running || Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    log.error("处理秒杀订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    // 消费失败或服务重启后，未 ACK 的消息会留在 pending-list，这里从 "0" 开始补偿处理。
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(STREAM_ORDERS_GROUP, STREAM_ORDERS_CONSUMER),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(STREAM_ORDERS, ReadOffset.from("0"))
                    );
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    handleRecord(list.get(0));
                } catch (Exception e) {
                    if (!running || Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    log.error("处理pending-list秒杀订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        private void handleRecord(MapRecord<String, Object, Object> record) {
            VoucherOrder voucherOrder = parseVoucherOrder(record.getValue());
            // 后台线程不走 Controller，需要用 TransactionTemplate 显式包住扣库存和保存订单。
            transactionTemplate.executeWithoutResult(status -> createVoucherOrder(voucherOrder));
            ack(record.getId());
        }

        private void ack(RecordId recordId) {
            stringRedisTemplate.opsForStream().acknowledge(STREAM_ORDERS, STREAM_ORDERS_GROUP, recordId);
        }
    }

    private void createVoucherOrder(VoucherOrder voucherOrder) {
        // 当前项目还没有接入真实支付系统。秒杀券在 Redis Lua 校验成功后即认为用户已抢购成功，
        // 因此异步落库时要显式写入“余额支付成功”，避免走数据库默认值变成“待支付”。
        voucherOrder.setPayType(1);
        voucherOrder.setStatus(2);
        voucherOrder.setPayTime(LocalDateTime.now());

        // Redis 已经挡住重复下单，这里再查一次数据库，防止 Redis 数据丢失或人工改数据导致重复订单。
        int count = query()
                .eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        if (count > 0) {
            log.error("用户已经购买过该优惠券，userId={}, voucherId={}", voucherOrder.getUserId(), voucherOrder.getVoucherId());
            return;
        }

        // 乐观扣减库存：只有 stock > 0 才允许减 1，避免高并发下出现数据库超卖。
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("秒杀券库存不足，voucherId={}", voucherOrder.getVoucherId());
            return;
        }
        save(voucherOrder);
    }

    private VoucherOrder parseVoucherOrder(Map<Object, Object> value) {
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(Long.valueOf(value.get("id").toString()));
        voucherOrder.setUserId(Long.valueOf(value.get("userId").toString()));
        voucherOrder.setVoucherId(Long.valueOf(value.get("voucherId").toString()));
        return voucherOrder;
    }

    private void initStreamGroup() {
        try {
            // MKSTREAM 可以在 stream.orders 不存在时自动创建 Stream，再创建消费者组 g1。
            stringRedisTemplate.execute((RedisCallback<Object>) connection -> connection.execute(
                    "XGROUP",
                    raw("CREATE"),
                    raw(STREAM_ORDERS),
                    raw(STREAM_ORDERS_GROUP),
                    raw("0"),
                    raw("MKSTREAM")
            ));
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                log.error("初始化秒杀订单Stream消费者组失败", e);
            }
        }
    }

    private byte[] raw(String value) {
        RedisSerializer<String> serializer = stringRedisTemplate.getStringSerializer();
        byte[] bytes = serializer.serialize(value);
        return bytes == null ? value.getBytes(StandardCharsets.UTF_8) : bytes;
    }

    private void preloadSeckillVouchers() {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        for (SeckillVoucher voucher : vouchers) {
            Long voucherId = voucher.getVoucherId();
            // setIfAbsent 避免重启应用时把 Redis 中已经扣减过的库存重新覆盖成数据库初始值。
            if (voucher.getStock() != null) {
                stringRedisTemplate.opsForValue().setIfAbsent(SECKILL_STOCK_KEY + voucherId, voucher.getStock().toString());
            }
            if (voucher.getBeginTime() != null) {
                stringRedisTemplate.opsForValue().setIfAbsent(SECKILL_BEGIN_KEY + voucherId, String.valueOf(toEpochMilli(voucher.getBeginTime())));
            }
            if (voucher.getEndTime() != null) {
                stringRedisTemplate.opsForValue().setIfAbsent(SECKILL_END_KEY + voucherId, String.valueOf(toEpochMilli(voucher.getEndTime())));
            }
        }
        // 把数据库里已有订单同步到 Redis 的购买记录集合，重启后仍能保证一人一单。
        List<VoucherOrder> orders = list();
        for (VoucherOrder order : orders) {
            if (order.getUserId() != null && order.getVoucherId() != null) {
                stringRedisTemplate.opsForSet().add(SECKILL_ORDER_KEY + order.getVoucherId(), order.getUserId().toString());
            }
        }
    }

    private long toEpochMilli(java.time.LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
