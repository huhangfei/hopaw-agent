var currentMcpId = null;

document.addEventListener('DOMContentLoaded', function () {
    loadMcpServers();
});

function loadMcpServers() {
    var grid = document.getElementById('mcpGrid');
    grid.innerHTML = '<div class="mcp-empty">加载中...</div>';

    fetch('/api/mcp')
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            if (resp.msg === 'success' && resp.data) {
                renderMcpCards(resp.data);
            } else {
                grid.innerHTML = '<div class="mcp-empty">暂无 MCP 服务器</div>';
            }
        })
        .catch(function (e) {
            console.error('加载 MCP 列表失败:', e);
            grid.innerHTML = '<div class="mcp-empty">加载失败</div>';
        });
}

function renderMcpCards(servers) {
    var grid = document.getElementById('mcpGrid');
    if (!servers || servers.length === 0) {
        grid.innerHTML = '<div class="mcp-empty">暂无 MCP 服务器，点击上方按钮添加</div>';
        return;
    }

    var html = '';
    servers.forEach(function (s) {
        var isEnabled = s.enabled === 1;
        var transportLabel = s.transportType === 'http' ? 'HTTP' : 'STDIO';
        var cardClass = isEnabled ? 'mcp-card enabled' : 'mcp-card disabled';
        var connInfo = '';
        if (s.transportType === 'http') {
            connInfo = '<span class="mcp-url">' + escapeHtml(s.url || '') + '</span>';
        } else {
            connInfo = '<span class="mcp-cmd">' + escapeHtml(s.command || '') + '</span>';
        }
        var desc = s.description || '';
        if (desc.length > 60) {
            desc = desc.substring(0, 60) + '...';
        }

        html += '<div class="' + cardClass + '">' +
            '<div class="mcp-card-body">' +
                '<div class="mcp-card-name">' + escapeHtml(s.name) + '</div>' +
                '<div class="mcp-card-meta">' +
                    '<span class="mcp-tag">' + transportLabel + '</span>' +
                    connInfo +
                '</div>' +
                (desc ? '<div class="mcp-desc">' + escapeHtml(desc) + '</div>' : '') +
            '</div>' +
            '<div class="mcp-card-actions">' +
                '<label class="mcp-toggle">' +
                    '<input type="checkbox" ' + (isEnabled ? 'checked' : '') + ' onchange="toggleMcpServer(' + s.id + ', this.checked)">' +
                    '<span class="toggle-slider"></span>' +
                '</label>' +
                '<button class="btn-card btn-edit-mcp" onclick="editMcpServer(' +
                    s.id + ',\'' + escapeAttr(s.name) + '\',\'' + escapeAttr(s.transportType) + '\',\'' +
                    escapeAttr(s.command || '') + '\',\'' + escapeAttr(s.url || '') + '\',\'' +
                    escapeAttr(s.description || '') + '\',\'' + escapeAttr(s.extParams || '') + '\')" title="编辑">&#9998;</button>' +
                '<button class="btn-card btn-delete-mcp" onclick="deleteMcpServer(' + s.id + ')" title="删除">&#10005;</button>' +
            '</div>' +
        '</div>';
    });
    grid.innerHTML = html;
}

function showMcpDialog() {
    currentMcpId = null;
    document.getElementById('mcpDialogTitle').textContent = '添加 MCP 服务器';
    document.getElementById('mcpName').value = '';
    document.getElementById('mcpTransportType').value = 'stdio';
    document.getElementById('mcpCommand').value = '';
    document.getElementById('mcpUrl').value = '';
    document.getElementById('mcpDescription').value = '';
    document.getElementById('mcpExtParams').value = '';
    document.getElementById('mcpId').value = '';
    onTransportTypeChange();
    Modal.open('mcpDialog');
}

function editMcpServer(id, name, transportType, command, url, description, extParams) {
    currentMcpId = id;
    document.getElementById('mcpDialogTitle').textContent = '编辑 MCP 服务器';
    document.getElementById('mcpName').value = name;
    document.getElementById('mcpTransportType').value = transportType || 'stdio';
    document.getElementById('mcpCommand').value = command || '';
    document.getElementById('mcpUrl').value = url || '';
    document.getElementById('mcpDescription').value = description || '';
    document.getElementById('mcpExtParams').value = extParams || '';
    document.getElementById('mcpId').value = id;
    onTransportTypeChange();
    Modal.open('mcpDialog');
}

function onTransportTypeChange() {
    var type = document.getElementById('mcpTransportType').value;
    document.getElementById('mcpCommandGroup').style.display = type === 'http' ? 'none' : '';
    document.getElementById('mcpUrlGroup').style.display = type === 'http' ? '' : 'none';
}

function saveMcpServer() {
    var name = document.getElementById('mcpName').value.trim();
    var transportType = document.getElementById('mcpTransportType').value;
    var command = document.getElementById('mcpCommand').value.trim();
    var url = document.getElementById('mcpUrl').value.trim();
    var description = document.getElementById('mcpDescription').value.trim();
    var extParams = document.getElementById('mcpExtParams').value.trim();

    if (!name) {
        showToast('请输入名称', 'error');
        return;
    }
    if (transportType === 'http' && !url) {
        showToast('请输入服务地址', 'error');
        return;
    }
    if (transportType === 'stdio' && !command) {
        showToast('请输入启动命令', 'error');
        return;
    }

    var body = {
        name: name,
        transportType: transportType,
        command: command,
        url: url,
        description: description,
        extParams: extParams
    };
    if (currentMcpId) {
        body.id = parseInt(currentMcpId);
    }

    var method = currentMcpId ? 'PUT' : 'POST';
    var apiUrl = currentMcpId ? '/api/mcp/' + currentMcpId : '/api/mcp';

    fetch(apiUrl, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            if (resp.msg === 'success') {
                showToast(currentMcpId ? '修改成功' : '添加成功', 'success');
                Modal.close('mcpDialog');
                loadMcpServers();
            } else {
                showToast('保存失败: ' + (resp.data || ''), 'error');
            }
        })
        .catch(function () {
            showToast('请求失败', 'error');
        });
}

function toggleMcpServer(id, checked) {
    fetch('/api/mcp/' + id + '/toggle', { method: 'PUT' })
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            if (resp.msg === 'success') {
                showToast(resp.data === 1 ? '已启用' : '已禁用', 'success');
            } else {
                showToast('操作失败', 'error');
            }
            loadMcpServers();
        })
        .catch(function () {
            showToast('请求失败', 'error');
            loadMcpServers();
        });
}

function deleteMcpServer(id) {
    if (!confirm('确定要删除该 MCP 服务器配置吗？')) {
        return;
    }
    fetch('/api/mcp/' + id, { method: 'DELETE' })
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            if (resp.msg === 'success') {
                showToast('删除成功', 'success');
                loadMcpServers();
            } else {
                showToast('删除失败', 'error');
            }
        })
        .catch(function () {
            showToast('请求失败', 'error');
        });
}

function escapeAttr(str) {
    if (!str) return '';
    return str.replace(/'/g, "\\'").replace(/"/g, '&quot;');
}