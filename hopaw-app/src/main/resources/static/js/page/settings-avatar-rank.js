/* ========== 设置页：虚拟人排行 ========== */

(function() {
    loadAvatarRankings();
})();

function loadAvatarRankings() {
    fetch('/api/avatar/rankings')
        .then(function(r) { return r.json(); })
        .then(function(data) {
            renderAvatarRankings(data || []);
        })
        .catch(function(e) {
            console.error('加载虚拟人排行失败:', e);
            renderAvatarRankings([]);
        });
}

function renderAvatarRankings(list) {
    var grid = document.getElementById('avatarRankGrid');
    var empty = document.getElementById('avatarRankEmpty');
    var countEl = document.getElementById('avatarRankCount');
    if (!grid) return;

    countEl.textContent = '共 ' + list.length + ' 个虚拟人';

    if (!list.length) {
        grid.innerHTML = '';
        empty.style.display = '';
        return;
    }
    empty.style.display = 'none';

    var html = '';
    for (var i = 0; i < list.length; i++) {
        var r = list[i];
        var rank = i + 1;
        var medal = getMedal(rank);
        var rankClass = rank <= 3 ? 'rank-card-top' : '';
        var avatarHtml = '';
        if (r.agentAvatar) {
            avatarHtml = '<img src="' + escapeAttr(r.agentAvatar) + '" alt="头像" class="rank-card-avatar-img">';
        } else {
            avatarHtml = '<svg viewBox="0 0 24 24" fill="currentColor" class="rank-card-avatar-icon"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"/></svg>';
        }

        html += '<div class="rank-card ' + rankClass + '">'
            + '<div class="rank-card-rank">' + medal + '</div>'
            + '<div class="rank-card-avatar">' + avatarHtml + '</div>'
            + '<div class="rank-card-info">'
            +   '<div class="rank-card-name">' + escapeHtml(r.agentName || '智能体') + '</div>'
            +   '<div class="rank-card-user">' + escapeHtml(r.userId || '') + '</div>'
            + '</div>'
            + '<div class="rank-card-right">'
            +   '<span class="rank-level-badge">Lv.' + r.level + '</span>'
            +   '<div class="rank-card-title">' + escapeHtml(r.title || '') + '</div>'
            +   '<div class="rank-card-tokens">' + formatTokens(r.totalTokens || 0) + ' Token</div>'
            + '</div>'
            + '</div>';
    }
    grid.innerHTML = html;
}

function getMedal(rank) {
    if (rank === 1) return '<span class="medal medal-gold" title="第1名">🥇</span>';
    if (rank === 2) return '<span class="medal medal-silver" title="第2名">🥈</span>';
    if (rank === 3) return '<span class="medal medal-bronze" title="第3名">🥉</span>';
    return '<span class="medal medal-normal">' + rank + '</span>';
}

function formatTokens(tokens) {
    if (tokens >= 100000000) return (tokens / 100000000).toFixed(1) + '亿';
    if (tokens >= 10000) return (tokens / 10000).toFixed(1) + '万';
    return String(tokens);
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeAttr(str) {
    if (!str) return '';
    return str.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
