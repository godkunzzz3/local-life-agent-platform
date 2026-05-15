package com.hmdp.service;

import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result saveBlog(Blog blog);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result queryHotBlog(Integer current);

    /**
     * 查询关注人的探店笔记 Feed 流。
     *
     * <p>这里使用滚动分页而不是普通页码分页，因为 Feed 流是按时间倒序不断追加的。
     * 前端每次带上上一页最小时间戳和偏移量，后端从 Redis ZSet 中继续向后取。</p>
     */
    Result queryBlogOfFollow(Long max, Integer offset);

    Result queryBlogOfUser(Long userId, Integer current);
}
