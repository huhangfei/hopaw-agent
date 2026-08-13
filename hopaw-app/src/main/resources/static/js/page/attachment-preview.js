/**
 * 附件预览公共模块
 *
 * 用途：
 *  1. 各业务页面调用 AttachmentPreview.open(id) 在新标签页打开独立预览页（推荐，避免重复代码）。
 *  2. 独立预览页调用 AttachmentPreview.render(item, container) 按 fileType 路由到不同渲染器渲染预览内容。
 *
 * 按文件类型路由到不同渲染器（renderImage / renderVideo / ... / renderUnsupported），实现解耦。
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
     * 按文件类型路由到对应渲染器，将预览内容渲染到指定容器
     * @param {Object} item 附件对象
     * @param {HTMLElement} container 渲染目标容器
     */
    function render(item, container) {
        if (!item || !container) return;
        var type = item.fileType || 'file';
        var html = '<div class="preview-container">';
        switch (type) {
            case 'image':    html += renderImage(item); break;
            case 'video':    html += renderVideo(item); break;
            case 'audio':    html += renderAudio(item); break;
            case 'pdf':      html += renderPdf(item); break;
            case 'markdown': html += renderMarkdown(item); break;
            case 'text':     html += renderText(item); break;
            default:         html += renderUnsupported(item); break;
        }
        html += renderDownload(item);
        html += '</div>';
        container.innerHTML = html;

        // markdown / text 需要异步加载文件内容
        if (type === 'markdown') {
            loadTextContent(item.url, 'previewMarkdownContent', true);
        } else if (type === 'text') {
            loadTextContent(item.url, 'previewTextContent', false);
        }
    }

    /* ====== 各类型渲染器（解耦，便于单独维护/扩展） ====== */

    function renderImage(item) {
        return '<img src="' + item.url + '" alt="' + escapeAttr(item.originalName) + '">';
    }

    function renderVideo(item) {
        return '<video controls src="' + item.url + '"></video>';
    }

    function renderAudio(item) {
        return '<audio controls src="' + item.url + '"></audio>';
    }

    function renderPdf(item) {
        return '<iframe class="preview-pdf" src="' + item.url + '"></iframe>';
    }

    function renderMarkdown(item) {
        return '<div class="preview-markdown" id="previewMarkdownContent">加载中...</div>';
    }

    function renderText(item) {
        return '<div class="preview-text" id="previewTextContent">加载中...</div>';
    }

    function renderUnsupported(item) {
        var type = item.fileType || 'file';
        return '<div class="preview-unsupported">' +
            '<div class="file-icon">' + (FILE_TYPE_ICONS[type] || '📦') + '</div>' +
            '<div>该文件类型不支持在线预览</div>' +
            '</div>';
    }

    function renderDownload(item) {
        return '<div class="preview-download-wrap">' +
            '<a class="preview-download" href="' + item.url + '" target="_blank" download="' + escapeAttr(item.originalName) + '">下载文件</a>' +
            '</div>';
    }

    /**
     * 异步加载文本/Markdown 文件内容
     */
    function loadTextContent(url, elId, isMarkdown) {
        fetch(url)
            .then(function (r) { return r.text(); })
            .then(function (text) {
                var el = document.getElementById(elId);
                if (!el) return;
                if (isMarkdown && window.marked) {
                    el.innerHTML = marked.parse(text);
                } else {
                    el.textContent = text;
                }
            })
            .catch(function () {
                var el = document.getElementById(elId);
                if (el) el.textContent = '加载失败';
            });
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

    function escapeAttr(str) {
        if (str === null || str === undefined) return '';
        return String(str).replace(/"/g, '&quot;');
    }

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
        render: render,
        renderInfo: renderInfo,
        FILE_TYPE_ICONS: FILE_TYPE_ICONS,
        SOURCE_LABELS: SOURCE_LABELS
    };
})();
