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

// marked v15 全局配置：注册 hooks.preprocess 统一转义单波浪线，防止 ~text~ 被误判为删除线
document.addEventListener('DOMContentLoaded', function() {
    if (typeof marked !== 'undefined') {
        marked.setOptions({ breaks: true, gfm: true });
        marked.use({
            hooks: {
                preprocess: function(md) {
                    return md.replace(/(?<!~)~(?!~)/g, '&tilde;');
                }
            }
        });
    }
});