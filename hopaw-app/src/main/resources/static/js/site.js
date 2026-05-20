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
