// 附件来源统一管理（与后端 com.agent.hopaw.infra.constant.AttachmentSourceEnum 保持一致）
// 新增/修改来源时，同步维护此文件与后端枚举即可。
var ATTACHMENT_SOURCE_OPTIONS = [
    { value: '', label: '全部来源' },
    { value: 'upload', label: '附件上传' },
    { value: 'chat', label: '会话文件' },
    { value: 'agentTool', label: '智能体工具' }
];

var ATTACHMENT_SOURCE_LABELS = {
    upload: '附件上传',
    chat: '会话文件',
    agentTool: '智能体工具'
};

window.AttachmentsSourceCode = {
    UPLOAD: 'upload',
    CHAT: 'chat',
    AGENT_TOOL: 'agentTool'
};

window.AttachmentSource = {
    labels: ATTACHMENT_SOURCE_LABELS,
    /** 获取来源中文文案；未知来源返回原始 code */
    label: function (code) {
        return ATTACHMENT_SOURCE_LABELS[code] || code || '';
    },
    /** 渲染来源下拉选项（可选），保留已有的 “全部来源” 首项 */
    renderFilterOptions: function (selectEl, selectedValue) {
        if (!selectEl) return;
        selectEl.innerHTML = '';
        ATTACHMENT_SOURCE_OPTIONS.forEach(function (opt) {
            var option = document.createElement('option');
            option.value = opt.value;
            option.textContent = opt.label;
            if (selectedValue !== undefined && String(opt.value) === String(selectedValue)) {
                option.selected = true;
            }
            selectEl.appendChild(option);
        });
    },
    /** 渲染来源下拉选项（不含“全部来源”，用于编辑/单值选择） */
    renderValueOptions: function (selectEl, selectedValue) {
        if (!selectEl) return;
        selectEl.innerHTML = '';
        ATTACHMENT_SOURCE_OPTIONS.forEach(function (opt) {
            if (opt.value === '') return;
            var option = document.createElement('option');
            option.value = opt.value;
            option.textContent = opt.label;
            if (selectedValue !== undefined && String(opt.value) === String(selectedValue)) {
                option.selected = true;
            }
            selectEl.appendChild(option);
        });
    }
};