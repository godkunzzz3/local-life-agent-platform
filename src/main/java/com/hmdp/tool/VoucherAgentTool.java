package com.hmdp.tool;

import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.entity.AgentCampaignDraft;
import com.hmdp.entity.AgentSuggestion;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券 Agent 工具。
 *
 * <p>负责优惠券查询、券结构分析、活动草稿生成，以及草稿确认后创建真实优惠券。
 * 后续接入大模型时，创建真实活动仍然只能在商家确认后由业务流程调用。</p>
 */
@Component
public class VoucherAgentTool {

    @Resource
    private IVoucherService voucherService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 查询店铺所有优惠券。
     */
    public List<Voucher> queryShopVouchers(Long shopId) {
        return voucherService.query().eq("shop_id", shopId).list();
    }

    /**
     * 查询券集合中的秒杀券扩展信息。
     */
    public List<SeckillVoucher> querySeckillVouchers(List<Long> voucherIds) {
        if (voucherIds == null || voucherIds.isEmpty()) {
            return Collections.emptyList();
        }
        return seckillVoucherService.query().in("voucher_id", voucherIds).list();
    }

    /**
     * 汇总优惠券结构。
     */
    public Map<String, Object> buildVoucherAnalysis(List<Voucher> vouchers, List<SeckillVoucher> seckillVouchers) {
        int normal = 0;
        int seckill = 0;
        int online = 0;
        for (Voucher voucher : vouchers) {
            if (voucher.getType() != null && voucher.getType() == 1) {
                seckill++;
            } else {
                normal++;
            }
            if (voucher.getStatus() != null && voucher.getStatus() == 1) {
                online++;
            }
        }

        int seckillStock = 0;
        for (SeckillVoucher seckillVoucher : seckillVouchers) {
            seckillStock += seckillVoucher.getStock() == null ? 0 : seckillVoucher.getStock();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalVouchers", vouchers.size());
        result.put("onlineVouchers", online);
        result.put("normalVouchers", normal);
        result.put("seckillVouchers", seckill);
        result.put("seckillStock", seckillStock);
        result.put("hasSeckill", seckill > 0);
        return result;
    }

    /**
     * 根据 Agent 建议构建活动草稿。
     */
    public AgentCampaignDraft buildCampaignDraft(AgentSuggestion suggestion, Shop shop,
                                                 MerchantCampaignDraftRequest request, Long draftId) {
        String draftType = normalizeDraftType(request == null ? null : request.getDraftType());
        LocalDateTime beginTime = request != null && request.getBeginTime() != null
                ? request.getBeginTime()
                : LocalDateTime.now().plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endTime = request != null && request.getEndTime() != null
                ? request.getEndTime()
                : beginTime.plusDays("seckill".equals(draftType) ? 2 : 30);

        long defaultActualValue = resolveDefaultActualValue(shop);
        long defaultPayValue = "seckill".equals(draftType)
                ? Math.max(100L, Math.round(defaultActualValue * 0.59))
                : Math.max(100L, Math.round(defaultActualValue * 0.80));
        String typeName = "seckill".equals(draftType) ? "秒杀券" : "代金券";

        return new AgentCampaignDraft()
                .setId(draftId)
                .setSuggestionId(suggestion.getId())
                .setShopId(suggestion.getShopId())
                .setDraftType(draftType)
                .setTitle(firstNotBlank(request == null ? null : request.getTitle(), shop.getName() + typeName))
                .setSubTitle(firstNotBlank(request == null ? null : request.getSubTitle(), "Agent推荐活动，适合短期验证转化"))
                .setPayValue(request != null && request.getPayValue() != null ? request.getPayValue() : defaultPayValue)
                .setActualValue(request != null && request.getActualValue() != null ? request.getActualValue() : defaultActualValue)
                .setStock(request != null && request.getStock() != null ? request.getStock() : ("seckill".equals(draftType) ? 80 : null))
                .setBeginTime(beginTime)
                .setEndTime(endTime)
                .setRules(firstNotBlank(request == null ? null : request.getRules(), buildDefaultRules(draftType)))
                .setReason(firstNotBlank(suggestion.getSummary(), suggestion.getContent()))
                .setStatus(1);
    }

    /**
     * 草稿确认后创建真实优惠券。
     */
    public Voucher createVoucherFromDraft(AgentCampaignDraft draft) {
        Voucher voucher = new Voucher()
                .setShopId(draft.getShopId())
                .setTitle(draft.getTitle())
                .setSubTitle(draft.getSubTitle())
                .setRules(draft.getRules())
                .setPayValue(draft.getPayValue())
                .setActualValue(draft.getActualValue())
                .setStatus(1);
        if ("seckill".equals(draft.getDraftType())) {
            voucher.setType(1)
                    .setStock(draft.getStock() == null ? 50 : draft.getStock())
                    .setBeginTime(draft.getBeginTime())
                    .setEndTime(draft.getEndTime());
            voucherService.addSeckillVoucher(voucher);
        } else {
            voucher.setType(0);
            voucherService.save(voucher);
        }
        return voucher;
    }

    /**
     * 草稿返回给前端前统一转 Map，避免 JS Long 精度问题。
     */
    public Map<String, Object> draftToMap(AgentCampaignDraft draft) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", draft.getId());
        row.put("draftId", String.valueOf(draft.getId()));
        row.put("suggestionId", String.valueOf(draft.getSuggestionId()));
        row.put("shopId", draft.getShopId());
        row.put("draftType", draft.getDraftType());
        row.put("draftTypeName", "seckill".equals(draft.getDraftType()) ? "秒杀券草稿" : "普通代金券草稿");
        row.put("title", draft.getTitle());
        row.put("subTitle", draft.getSubTitle());
        row.put("payValue", draft.getPayValue());
        row.put("actualValue", draft.getActualValue());
        row.put("stock", draft.getStock());
        row.put("beginTime", draft.getBeginTime());
        row.put("endTime", draft.getEndTime());
        row.put("rules", draft.getRules());
        row.put("reason", draft.getReason());
        row.put("status", draft.getStatus());
        row.put("statusName", resolveDraftStatusName(draft.getStatus()));
        row.put("createTime", draft.getCreateTime());
        row.put("updateTime", draft.getUpdateTime());
        return row;
    }

    private String normalizeDraftType(String draftType) {
        if ("voucher".equalsIgnoreCase(draftType)) {
            return "voucher";
        }
        return "seckill";
    }

    private long resolveDefaultActualValue(Shop shop) {
        Long avgPrice = shop.getAvgPrice();
        if (avgPrice == null || avgPrice <= 0) {
            return 10000L;
        }
        // 店铺均价字段按“元”保存，优惠券金额字段按“分”保存。
        long avgPriceFen = avgPrice * 100;
        long rounded = Math.round(avgPriceFen / 1000.0) * 1000L;
        return Math.max(3000L, rounded);
    }

    private String buildDefaultRules(String draftType) {
        if ("seckill".equals(draftType)) {
            return "{\"limitPerUser\":1,\"verify\":\"到店出示券码核销\",\"source\":\"agent\"}";
        }
        return "{\"verify\":\"到店出示券码核销\",\"source\":\"agent\"}";
    }

    private String firstNotBlank(String first, String fallback) {
        return first == null || first.trim().isEmpty() ? fallback : first.trim();
    }

    private String resolveDraftStatusName(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        switch (status) {
            case 1:
                return "待确认";
            case 2:
                return "已创建";
            case 3:
                return "已拒绝";
            case 4:
                return "已过期";
            default:
                return "未知状态";
        }
    }
}
