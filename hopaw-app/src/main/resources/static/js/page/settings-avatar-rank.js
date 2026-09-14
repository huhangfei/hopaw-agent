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
    var tbody = document.getElementById('avatarRankBody');
    var empty = document.getElementById('avatarRankEmpty');
    var countEl = document.getElementById('avatarRankCount');
    if (!tbody) return;

    countEl.textContent = '共 ' + list.length + ' 个虚拟人';

    if (!list.length) {
        tbody.innerHTML = '';
        empty.style.display = '';
        return;
    }
    empty.style.display = 'none';

    var html = '';
    for (var i = 0; i < list.length; i++) {
        var r = list[i];
        var rank = i + 1;
        var medal = getMedal(rank);
        var rankClass = rank <= 3 ? 'rank-top' : '';
        html += '<tr class="' + rankClass + '">'
            + '<td class="col-rank">' + medal + '</td>'
            + '<td class="col-user">' + escapeHtml(r.userId || '') + '</td>'
            + '<td class="col-agent">' + escapeHtml(r.agentName || String(r.agentId || '')) + '</td>'
            + '<td class="col-level"><span class="rank-level-badge">Lv.' + r.level + '</span></td>'
            + '<td class="col-title">' + escapeHtml(r.title || '') + '</td>'
            + '<td class="col-tokens">' + formatTokens(r.totalTokens || 0) + '</td>'
            + '</tr>';
    }
    tbody.innerHTML = html;
}

function getMedal(rank) {
    if (rank === 1) return '<span class="medal medal-gold" title="第1名">🥇 1</span>';
    if (rank === 2) return '<span class="medal medal-silver" title="第2名">🥈 2</span>';
    if (rank === 3) return '<span class="medal medal-bronze" title="第3名">🥉 3</span>';
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
