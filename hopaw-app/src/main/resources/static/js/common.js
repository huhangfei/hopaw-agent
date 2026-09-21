function setThemeCookie(theme) {
    var expires = new Date();
    expires.setFullYear(expires.getFullYear() + 1);
    document.cookie = 'theme=' + theme + ';path=/;expires=' + expires.toUTCString() + ';SameSite=Lax';
}

function toggleTheme() {
    var body = document.body;
    var isDark = body.classList.toggle('dark-theme');
    var theme = isDark ? 'dark' : 'light';
    setThemeCookie(theme);
    var sunPath = document.querySelector('.sun-path');
    var moonPath = document.querySelector('.moon-path');
    if (isDark) {
        if (sunPath) sunPath.style.display = 'none';
        if (moonPath) moonPath.style.display = 'block';
    } else {
        if (sunPath) sunPath.style.display = 'block';
        if (moonPath) moonPath.style.display = 'none';
    }
}

function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * 格式化 Token 数量（紧凑显示，避免长数字撑破窄容器）：
 *   < 1000    → 原样数字，如 998
 *   < 10000   → 保留 1 位小数的 k（千），如 1234 → 1.2k
 *   >= 10000  → 保留 1 位小数的 w（万），如 12345 → 1.2w，1234567 → 123.5w
 * 负数按绝对值判定单位并保留符号（-12345 → -1.2w）；非法值按 0 处理。
 * 全站统一入口，勿在页面脚本内重复实现。
 */
function formatTokenCount(n) {
    var v = Number(n);
    if (!isFinite(v)) { return '0'; }
    var sign = v < 0 ? '-' : '';
    var abs = Math.abs(v);
    if (abs >= 10000) { return sign + (abs / 10000).toFixed(1) + 'w'; }
    if (abs >= 1000) { return sign + (abs / 1000).toFixed(1) + 'k'; }
    return sign + String(abs);
}

// marked v15 全局配置：注册 hooks.preprocess 统一转义单波浪线，防止 ~text~ 被误判为删除线
document.addEventListener('DOMContentLoaded', function() {
    if (typeof marked !== 'undefined') {
        marked.setOptions({ breaks: true, gfm: true });
        marked.use({
            hooks: {
                preprocess: function(md) {
                    // 附件标记 [attachment:id:name:url] 替换为占位符，防止被 marked 解析为链接
                    var attachmentMap = {};
                    var counter = 0;
                    md = md.replace(/\[attachment:(\d+):([^:]+):([^\]]+)\]/g, function(match, id, name, url) {
                        var key = '\u0000ATTACH_' + counter + '\u0000';
                        attachmentMap[key] = { id: id, name: name, url: url };
                        counter++;
                        return key;
                    });
                    // 转义单波浪线
                    md = md.replace(/(?<!~)~(?!~)/g, '&tilde;');
                    // 还原附件标记为 HTML
                    for (var key in attachmentMap) {
                        var a = attachmentMap[key];
                        var html = '<a href="javascript:void(0)" class="attachment-link" data-attachment-id="' + a.id + '" onclick="openAttachmentPreview(' + a.id + ')">' + escapeHtml(a.name) + '</a>';
                        md = md.replace(key, html);
                    }
                    return md;
                }
            },
            renderer: {
                link: function(token) {
                    var href = token.href || '';
                    var title = token.title ? ' title="' + token.title + '"' : '';
                    var text = token.text || '';
                    return '<a href="' + href + '"' + title + ' target="_blank" rel="noopener noreferrer">' + text + '</a>';
                }
            }
        });
    }

    // 全局 fetch 拦截：401/403 时自动跳转登录页（带回跳地址）
    var _originalFetch = window.fetch;
    window.fetch = function() {
        return _originalFetch.apply(this, arguments).then(function(response) {
            if (response.status === 401 || response.status === 403) {
                var url = window.location.pathname;
                if (url !== '/login') {
                    window.location.href = '/login?redirect=' + encodeURIComponent(url);
                }
            }
            return response;
        });
    };
});