package com.hmdp.tool;

import com.hmdp.dto.AgentToolDefinitionDTO;
import com.hmdp.dto.ReviewStatsDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * 评价与内容 Agent 工具。
 *
 * <p>当前项目的“网友评价”主要来自探店笔记和评论。这个工具把内容侧数据汇总成
 * Agent 易理解的指标：笔记数、点赞数、评论数、近期内容摘要和互动等级。</p>
 */
@Component
public class ReviewAgentTool implements AgentToolDescriptor {

    @Resource
    private IBlogService blogService;
    @Resource
    private IBlogCommentsService blogCommentsService;

    @Override
    public AgentToolDefinitionDTO definition() {
        return new AgentToolDefinitionDTO()
                .setName("review_content_tool")
                .setDisplayName("评价内容分析工具")
                .setDescription("查询店铺探店笔记和评论，汇总内容数量、点赞、评论和互动等级。")
                .setCategory("review")
                .setAccessLevel("read")
                .setRequireMerchantConfirm(false)
                .setWriteDatabase(false)
                .setInputSchema("{\"shopId\":\"店铺ID\"}")
                .setOutputSchema("ReviewStatsDTO：笔记数、点赞数、评论数、近期内容摘要、互动等级")
                .setRiskLevel("low")
                .setExamples(Collections.singletonList("商家询问评价和内容表现时，调用该工具生成内容侧诊断"));
    }

    /**
     * 查询店铺探店笔记。
     */
    public List<Blog> queryShopBlogs(Long shopId) {
        return blogService.query().eq("shop_id", shopId).orderByDesc("create_time").list();
    }

    /**
     * 查询探店笔记下的有效评论。
     */
    public List<BlogComments> queryComments(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> blogIds = blogs.stream().map(Blog::getId).collect(Collectors.toList());
        return blogCommentsService.query()
                .in("blog_id", blogIds)
                .eq("status", 0)
                .orderByDesc("create_time")
                .list();
    }

    /**
     * 汇总内容互动数据。
     */
    public ReviewStatsDTO buildReviewAnalysis(List<Blog> blogs, List<BlogComments> comments) {
        int liked = 0;
        int blogCommentCount = 0;
        List<String> recentContents = new ArrayList<>();
        for (Blog blog : blogs) {
            liked += blog.getLiked() == null ? 0 : blog.getLiked();
            blogCommentCount += blog.getComments() == null ? 0 : blog.getComments();
            if (recentContents.size() < 3 && blog.getContent() != null) {
                recentContents.add(trim(blog.getContent(), 60));
            }
        }

        ReviewStatsDTO result = new ReviewStatsDTO();
        result.setBlogCount(blogs == null ? 0 : blogs.size());
        result.setLikedCount(liked);
        result.setCommentCount(blogCommentCount + (comments == null ? 0 : comments.size()));
        result.setRecentContents(recentContents);
        result.setEngagementLevel(resolveEngagementLevel(
                result.getBlogCount(),
                result.getLikedCount(),
                result.getCommentCount()
        ));
        return result;


    }

    private String resolveEngagementLevel(int blogCount, int likedCount, int commentCount) {
        int score = blogCount * 2 + likedCount + commentCount * 2;
        if (score >= 30) {
            return "高";
        }
        if (score >= 10) {
            return "中";
        }
        return "低";
    }

    private String trim(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
