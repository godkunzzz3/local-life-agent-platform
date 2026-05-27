package com.hmdp.agent;

import com.hmdp.dto.AgentToolDefinitionDTO;
import com.hmdp.tool.AgentToolDescriptor;
import com.hmdp.tool.AgentToolRegistry;
import com.hmdp.tool.OperationDiagnosisToolDescriptor;
import com.hmdp.tool.OrderAgentTool;
import com.hmdp.tool.ReviewAgentTool;
import com.hmdp.tool.ShopAgentTool;
import com.hmdp.tool.VoucherAgentTool;
import com.hmdp.tool.VoucherAnalysisToolDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolRegistrySecurityTest {

    @Test
    void shouldListAllToolsIncludingWriteTool() {
        AgentToolRegistry registry = buildRegistry();

        List<AgentToolDefinitionDTO> definitions = registry.listDefinitions();

        Set<String> names = definitions.stream()
                .map(AgentToolDefinitionDTO::getName)
                .collect(Collectors.toSet());
        Set<String> modelToolNames = definitions.stream()
                .map(AgentToolDefinitionDTO::getModelToolName)
                .collect(Collectors.toSet());

        assertTrue(names.contains("voucher_campaign_tool"));
        assertTrue(modelToolNames.contains("getShopProfile"));
        assertTrue(modelToolNames.contains("getShopOrderStats"));
        assertTrue(modelToolNames.contains("getShopVouchers"));
        assertTrue(modelToolNames.contains("getShopReviewSummary"));
        assertTrue(modelToolNames.contains("getOperationDiagnosis"));
    }

    @Test
    void shouldListOnlyReadonlyToolsForModelCallable() {
        AgentToolRegistry registry = buildRegistry();

        List<AgentToolDefinitionDTO> definitions = registry.listModelCallableDefinitions();

        assertFalse(definitions.isEmpty());
        for (AgentToolDefinitionDTO definition : definitions) {
            assertEquals(Boolean.TRUE, definition.getModelCallable());
            assertTrue("readonly".equals(definition.getToolType()) || "read".equals(definition.getAccessLevel()));
            assertEquals(Boolean.FALSE, definition.getWriteDatabase());
            assertEquals(Boolean.FALSE, definition.getRequireMerchantConfirm());
            assertFalse("draft_only".equals(definition.getExecutionPolicy()));
            assertFalse("confirm_required".equals(definition.getExecutionPolicy()));
            assertFalse("human_confirm".equals(definition.getExecutionPolicy()));
        }
    }

    @Test
    void shouldNotExposeVoucherCampaignToolToModel() {
        AgentToolRegistry registry = buildRegistry();

        List<AgentToolDefinitionDTO> definitions = registry.listModelCallableDefinitions();

        assertFalse(definitions.stream()
                .anyMatch(definition -> "voucher_campaign_tool".equals(definition.getName())));
        assertFalse(definitions.stream()
                .anyMatch(definition -> "createCampaignDraft".equals(definition.getModelToolName())));
        assertFalse(definitions.stream()
                .anyMatch(definition -> Boolean.TRUE.equals(definition.getWriteDatabase())));
    }

    @Test
    void shouldMarkVoucherCampaignToolAsWriteAndNotModelCallable() {
        AgentToolDefinitionDTO definition = new VoucherAgentTool().definition();

        assertEquals("voucher_campaign_tool", definition.getName());
        assertEquals(Boolean.FALSE, definition.getModelCallable());
        assertEquals(Boolean.TRUE, definition.getWriteDatabase());
        assertEquals(Boolean.TRUE, definition.getRequireMerchantConfirm());
        assertEquals("draft_only", definition.getExecutionPolicy());
        assertTrue("draft".equals(definition.getToolType()) || "write".equals(definition.getAccessLevel()));
        assertNotNull(definition.getConfirmReason());
    }

    private AgentToolRegistry buildRegistry() {
        List<AgentToolDescriptor> descriptors = Arrays.asList(
                new ShopAgentTool(),
                new OrderAgentTool(),
                new ReviewAgentTool(),
                new VoucherAnalysisToolDescriptor(),
                new OperationDiagnosisToolDescriptor(),
                new VoucherAgentTool()
        );
        return new AgentToolRegistry(descriptors);
    }
}
