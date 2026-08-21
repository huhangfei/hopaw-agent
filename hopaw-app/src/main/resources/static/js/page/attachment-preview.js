/**
 * 附件预览模块
 *
 * 职责：
 *  1. AttachmentPreview.open(id) - 在新标签页打开独立附件预览页
 *  2. AttachmentPreview.renderInfo(item, container) - 渲染附件元信息（大小/类型/来源/时间）
 *
 * 预览体（preview-body）渲染逻辑已提取到公共组件 file-preview.js，由 attachment-preview.html
 * 通过 iframe 嵌入 /file-preview?url=...&name=... 复用。
 */
var AttachmentPreview = (function () {

    /** 文件类型 → 图标 */
    var FILE_TYPE_ICONS = {
        image: '🖼️', video: '🎬', audio: '🎵',
        pdf: '📄', markdown: '📝', text: '📃', file: '📦'
    };

    /** 来源 → 中文标签 */
    var SOURCE_LABELS = {
        upload: '附件上传',
        chat: '会话文件',
        task: '任务附件',
        project: '项目附件'
    };

    /**
     * 在新标签页打开独立预览页
     * @param {number} id 附件ID
     */
    function open(id) {
        window.open('/attachment-preview/' + id, '_blank');
    }

    /**
     * 渲染附件元信息（大小/类型/来源/时间）
     */
    function renderInfo(item, container) {
        if (!item || !container) return;
        var html = '<div class="preview-info">' +
            '<span class="info-item">大小: ' + escapeHtml(formatFileSize(item.fileSize)) + '</span>' +
            '<span class="info-item">类型: ' + escapeHtml(item.fileExtension || '') + '</span>' +
            '<span class="info-item">来源: ' + escapeHtml(SOURCE_LABELS[item.source] || item.source || '') + '</span>' +
            '<span class="info-item">创建时间: ' + escapeHtml(formatTime(item.createTime)) + '</span>' +
            '<span class="info-item">更新时间: ' + escapeHtml(formatTime(item.updateTime)) + '</span>' +
            '</div>';
        container.innerHTML = html;
    }

    /* ====== 工具函数（模块内私有） ====== */

    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatFileSize(bytes) {
        if (!bytes || bytes <= 0) return '0 B';
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
    }

    function formatTime(timeStr) {
        if (!timeStr) return '';
        var t = String(timeStr).replace('T', ' ');
        return t.substring(0, 16);
    }

    return {
        open: open,
        renderInfo: renderInfo,
        FILE_TYPE_ICONS: FILE_TYPE_ICONS,
        SOURCE_LABELS: SOURCE_LABELS
    };
})();
