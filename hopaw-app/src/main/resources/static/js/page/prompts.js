/* ========== 提示词管理 ========== */

var promptState = { page: 1, size: 12, keyword: '', tag: '', sortBy: 'heat' };

(function() {
    loadPrompts();
    loadPromptTags();
})();

function loadPrompts() {
    var grid = document.getElementById('promptsGrid');
    if (!grid.children.length) {
        grid.innerHTML = '<div class="prompts-empty">加载中...</div>';
    }

    var params = new URLSearchParams({
        page: promptState.page,
        size: promptState.size,
        keyword: promptState.keyword,
        tag: promptState.tag,
        sortBy: promptState.sortBy
    });

    fetch('/api/prompts/page?' + params.toString())
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code !== 200 || !resp.data) {
                grid.innerHTML = '<div class="prompts-empty">加载失败</div>';
                return;
            }
            var list = resp.data.list || [];
            var total = resp.data.total || 0;
            if (!list.length) {
                grid.innerHTML = '<div class="prompts-empty">暂无提示词，点击右上角新建</div>';
                renderPromptPagination(total);
                return;
            }
            renderPromptGrid(list);
            renderPromptPagination(total);
        })
        .catch(function() {
            grid.innerHTML = '<div class="prompts-empty">加载失败</div>';
        });
}

function renderPromptGrid(list) {
    var grid = document.getElementById('promptsGrid');
    var html = '';
    for (var i = 0; i < list.length; i++) {
        var p = list[i];
        var tags = (p.tags || '').split(',').filter(function(t) { return t.trim(); });
        var tagsHtml = '';
        for (var j = 0; j < tags.length; j++) {
            tagsHtml += '<span class="prompt-tag">' + escapeHtml(tags[j].trim()) + '</span>';
        }
        var heatClass = p.heat > 0 ? '' : ' zero';
        html += '<div class="prompt-card" data-id="' + p.id + '">' +
            '<div class="prompt-card-top">' +
                '<div class="prompt-card-name" title="' + escapeHtml(p.name) + '">' + escapeHtml(p.name) + '</div>' +
                '<span class="prompt-card-heat' + heatClass + '">🔥 ' + (p.heat || 0) + '</span>' +
            '</div>' +
            '<div class="prompt-card-content">' + escapeHtml(p.content || '') + '</div>' +
            '<div class="prompt-card-tags">' + tagsHtml + '</div>' +
            '<div class="prompt-card-footer">' +
                '<span class="prompt-card-time">' + formatTime(p.createTime) + '</span>' +
                '<div class="prompt-card-actions">' +
                    '<button class="btn-copy" onclick="copyPrompt(' + p.id + ')">复制</button>' +
                    '<button onclick="editPrompt(' + p.id + ')">编辑</button>' +
                    '<button class="btn-delete" onclick="deletePrompt(' + p.id + ')">删除</button>' +
                '</div>' +
            '</div>' +
        '</div>';
    }
    grid.innerHTML = html;
}

function renderPromptPagination(total) {
    var el = document.getElementById('promptsPagination');
    var totalPages = Math.ceil(total / promptState.size);
    if (totalPages <= 1) { el.innerHTML = ''; return; }
    var html = '';
    if (promptState.page > 1) {
        html += '<button class="prompts-page-btn" onclick="goPromptPage(' + (promptState.page - 1) + ')">上一页</button>';
    }
    html += '<span class="prompts-page-info">' + promptState.page + ' / ' + totalPages + '</span>';
    if (promptState.page < totalPages) {
        html += '<button class="prompts-page-btn" onclick="goPromptPage(' + (promptState.page + 1) + ')">下一页</button>';
    }
    el.innerHTML = html;
}

function goPromptPage(page) {
    promptState.page = page;
    loadPrompts();
}

/* 搜索 */
document.getElementById('promptKeyword').addEventListener('input', debounce(function() {
    promptState.keyword = this.value.trim();
    promptState.page = 1;
    loadPrompts();
}, 300));

document.getElementById('promptTagFilter').addEventListener('change', function() {
    promptState.tag = this.value;
    promptState.page = 1;
    loadPrompts();
});

document.getElementById('promptSortBy').addEventListener('change', function() {
    promptState.sortBy = this.value;
    promptState.page = 1;
    loadPrompts();
});

/* 加载标签 */
function loadPromptTags() {
    fetch('/api/prompts/tags')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code !== 200 || !resp.data) return;
            var tagSet = {};
            var tags = [];
            for (var i = 0; i < resp.data.length; i++) {
                var arr = (resp.data[i] || '').split(',');
                for (var j = 0; j < arr.length; j++) {
                    var t = arr[j].trim();
                    if (t && !tagSet[t]) { tagSet[t] = true; tags.push(t); }
                }
            }
            var sel = document.getElementById('promptTagFilter');
            sel.innerHTML = '<option value="">全部标签</option>';
            for (var k = 0; k < tags.length; k++) {
                sel.innerHTML += '<option value="' + escapeHtml(tags[k]) + '">' + escapeHtml(tags[k]) + '</option>';
            }
        });
}

/* 弹框 */
function showPromptModal(id) {
    document.getElementById('promptEditId').value = id || '';
    document.getElementById('promptModalTitle').textContent = id ? '编辑提示词' : '新建提示词';
    document.getElementById('promptName').value = '';
    document.getElementById('promptContent').value = '';
    document.getElementById('promptTags').value = '';
    if (id) {
        fetch('/api/prompts/' + id)
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.code === 200 && resp.data) {
                    document.getElementById('promptName').value = resp.data.name || '';
                    document.getElementById('promptContent').value = resp.data.content || '';
                    document.getElementById('promptTags').value = resp.data.tags || '';
                }
            });
    }
    document.getElementById('promptModal').classList.add('active');
}

function hidePromptModal() {
    document.getElementById('promptModal').classList.remove('active');
}

function savePrompt() {
    var id = document.getElementById('promptEditId').value;
    var name = document.getElementById('promptName').value.trim();
    var content = document.getElementById('promptContent').value.trim();
    var tags = document.getElementById('promptTags').value.trim();

    if (!name) { showToast('请输入名称', 'error'); return; }
    if (!content) { showToast('请输入内容', 'error'); return; }

    var body = { name: name, content: content, tags: tags };
    var url = id ? '/api/prompts/' + id : '/api/prompts';
    var method = id ? 'PUT' : 'POST';

    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        if (resp.code === 200) {
            hidePromptModal();
            showToast(id ? '修改成功' : '创建成功', 'success');
            loadPrompts();
            loadPromptTags();
        } else {
            showToast(resp.msg || '操作失败', 'error');
        }
    })
    .catch(function(err) {
        showToast('操作失败: ' + err.message, 'error');
    });
}

function editPrompt(id) {
    showPromptModal(id);
}

function deletePrompt(id) {
    showConfirm('确定删除此提示词？').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/prompts/' + id, { method: 'DELETE' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.code === 200) {
                    showToast('删除成功', 'success');
                    loadPrompts();
                    loadPromptTags();
                } else {
                    showToast(resp.msg || '删除失败', 'error');
                }
            });
    });
}

function copyPrompt(id) {
    fetch('/api/prompts/' + id)
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code !== 200 || !resp.data) return;
            var text = resp.data.content || '';
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(text).then(function() {
                    showToast('已复制到剪贴板', 'success');
                    fetch('/api/prompts/' + id + '/copy', { method: 'POST' });
                    loadPrompts();
                });
            } else {
                var ta = document.createElement('textarea');
                ta.value = text;
                document.body.appendChild(ta);
                ta.select();
                document.execCommand('copy');
                document.body.removeChild(ta);
                showToast('已复制到剪贴板', 'success');
                fetch('/api/prompts/' + id + '/copy', { method: 'POST' });
                loadPrompts();
            }
        });
}

/* 工具函数 */
function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function formatTime(timeStr) {
    if (!timeStr) return '';
    return String(timeStr).replace('T', ' ').substring(0, 16);
}

function debounce(fn, delay) {
    var timer;
    return function() {
        var ctx = this, args = arguments;
        clearTimeout(timer);
        timer = setTimeout(function() { fn.apply(ctx, args); }, delay);
    };
}
