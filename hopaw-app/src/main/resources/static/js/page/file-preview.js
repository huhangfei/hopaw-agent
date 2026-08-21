/**
 * 文件预览公共组件
 *
 * 用途：作为独立页面被 iframe 嵌套，仅负责文件内容展示和下载。
 * 接收参数：url（文件内容的 HTTP URL）、name（文件名，用于类型识别和下载命名）
 *
 * 使用方式：
 *   1. 直接访问 /file-preview?url=xxx&name=xxx.pdf
 *   2. 通过 iframe 嵌套：<iframe src="/file-preview?url=...&name=...">
 */
var FilePreview = (function () {

    /** 文件类型 → 图标 */
    var FILE_TYPE_ICONS = {
        image: '🖼️', video: '🎬', audio: '🎵',
        pdf: '📄', markdown: '📝', text: '📃', file: '📦'
    };

    /**
     * 按文件名扩展名识别文件类型
     */
    function detectFileType(name) {
        if (!name) return 'file';
        var dotIdx = name.lastIndexOf('.');
        var ext = dotIdx >= 0 ? name.substring(dotIdx).toLowerCase() : '';
        switch (ext) {
            case '.png': case '.jpg': case '.jpeg': case '.gif':
            case '.bmp': case '.webp': case '.svg':
                return 'image';
            case '.mp4': case '.webm': case '.ogg': case '.mov':
            case '.avi': case '.mkv':
                return 'video';
            case '.mp3': case '.wav': case '.flac': case '.aac': case '.m4a':
                return 'audio';
            case '.pdf':
                return 'pdf';
            case '.md': case '.markdown':
                return 'markdown';
            case '.txt': case '.log': case '.csv': case '.json': case '.xml':
            case '.yml': case '.yaml': case '.html': case '.css': case '.js':
            case '.java': case '.py': case '.sql': case '.sh': case '.bat':
            case '.properties': case '.ini': case '.conf':
                return 'text';
            default:
                return 'file';
        }
    }

    /**
     * 按文件类型路由到对应渲染器，将预览内容渲染到指定容器
     * @param {string} url  文件内容 URL
     * @param {string} name 文件名
     * @param {HTMLElement} container 渲染目标容器
     */
    function render(url, name, container) {
        if (!url || !container) {
            if (container) {
                container.innerHTML = '<div class="preview-unsupported"><div class="file-icon">📦</div><div>缺少文件 URL</div></div>';
            }
            return;
        }
        var type = detectFileType(name);
        var html = '<div class="preview-container">';
        switch (type) {
            case 'image':    html += renderImage(url, name); break;
            case 'video':    html += renderVideo(url); break;
            case 'audio':    html += renderAudio(url); break;
            case 'pdf':      html += renderPdf(url); break;
            case 'markdown': html += renderMarkdown(); break;
            case 'text':     html += renderText(); break;
            default:         html += renderUnsupported(type); break;
        }
        html += renderDownload(url, name);
        html += '</div>';
        container.innerHTML = html;

        // markdown / text 需要异步加载文件内容
        if (type === 'markdown') {
            loadTextContent(url, 'previewMarkdownContent', true);
        } else if (type === 'text') {
            loadTextContent(url, 'previewTextContent', false);
        }
    }

    /* ====== 各类型渲染器（解耦，便于单独维护/扩展） ====== */

    function renderImage(url, name) {
        return '<img src="' + url + '" alt="' + escapeAttr(name || '') + '">';
    }

    function renderVideo(url) {
        return '<video controls src="' + url + '"></video>';
    }

    function renderAudio(url) {
        return '<audio controls src="' + url + '"></audio>';
    }

    function renderPdf(url) {
        return '<iframe class="preview-pdf" src="' + url + '"></iframe>';
    }

    function renderMarkdown() {
        return '<div class="preview-markdown" id="previewMarkdownContent">加载中...</div>';
    }

    function renderText() {
        return '<div class="preview-text" id="previewTextContent">加载中...</div>';
    }

    function renderUnsupported(type) {
        return '<div class="preview-unsupported">' +
            '<div class="file-icon">' + (FILE_TYPE_ICONS[type] || '📦') + '</div>' +
            '<div>该文件类型不支持在线预览</div>' +
            '</div>';
    }

    function renderDownload(url, name) {
        return '<div class="preview-download-wrap">' +
            '<a class="preview-download" href="' + url + '" target="_blank" download="' + escapeAttr(name || '') + '">下载文件</a>' +
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

    /* ====== 工具函数（模块内私有） ====== */

    function escapeAttr(str) {
        if (str === null || str === undefined) return '';
        return String(str).replace(/"/g, '&quot;');
    }

    return {
        render: render,
        detectFileType: detectFileType,
        FILE_TYPE_ICONS: FILE_TYPE_ICONS
    };
})();
