package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @PostConstruct
    private void initFollowCache() {
        // 应用重启后 Redis 可能是空的，把数据库已有关注关系重新预热到 Set 中。
        List<Follow> follows = list();
        for (Follow follow : follows) {
            if (follow.getUserId() != null && follow.getFollowUserId() != null) {
                stringRedisTemplate.opsForSet().add(FOLLOW_KEY + follow.getUserId(), follow.getFollowUserId().toString());
            }
        }
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }
        String key = FOLLOW_KEY + userId;
        if (Boolean.TRUE.equals(isFollow)) {
            Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
            if (count != null && count > 0) {
                // 接口幂等：用户已经关注时再次点击关注，只补齐 Redis 缓存，不重复插入数据库。
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
                return Result.ok();
            }
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean success = save(follow);
            if (success) {
                // 关注关系写入 Redis Set，后续共同关注直接 SINTER 求交集。
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            boolean success = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (success) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1 = FOLLOW_KEY + userId;
        String key2 = FOLLOW_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
