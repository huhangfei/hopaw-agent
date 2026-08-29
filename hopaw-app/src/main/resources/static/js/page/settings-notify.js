/* ========== 设置页：通知渠道管理 ========== */
var NOTIFY_CHANNEL_TYPES = {
    dingtalk: '钉钉群',
    feishu: '飞书',
    email: '邮件',
    webhook: 'Webhook'
};
var notifyChannelsCache = [];    // 通知渠道缓存
var notifyChannelEditing = null; // 当前编辑的渠道（null=新增）

document.addEventListener('DOMContentLoaded', function () {
    loadNotifyChannelList();
});

/** 加载当前用户通知渠道并渲染列表 */
function loadNotifyChannelList() {
    fetch('/api/notify/channels')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            notifyChannelsCache = res.code === 200 ? (res.data || []) : [];
            renderNotifyChannelList();
        })
        .catch(function (err) {
            console.error('加载通知渠道失败:', err);
        });
}

/** 渲染渠道列表 */
function renderNotifyChannelList() {
    var list = document.getElementById('notifyChannelList');
    if (!list) return;
    var html = '';
    if (!notifyChannelsCache.length) {
        html = '<div class="notify-channel-empty">暂无通知渠道，点击下方按钮新增</div>';
    }
    notifyChannelsCache.forEach(function (ch) {
        var typeLabel = NOTIFY_CHANNEL_TYPES[ch.type] || ch.type;
        html += '<div class="notify-channel-item">' +
            '<div class="notify-channel-info">' +
            '<span class="notify-channel-name">' + escapeHtml(ch.name) + '</span>' +
            '<span class="notify-channel-type">' + typeLabel + '</span>' +
            (ch.enabled === false ? '<span class="notify-channel-disabled">已停用</span>' : '') +
            '</div>' +
            '<div class="notify-channel-actions">' +
            '<button class="btn-channel" onclick="testNotifyChannel(' + ch.id + ')">测试</button>' +
            '<button class="btn-channel" onclick="editNotifyChannel(' + ch.id + ')">编辑</button>' +
            '<button class="btn-channel btn-channel-danger" onclick="deleteNotifyChannel(' + ch.id + ')">删除</button>' +
            '</div>' +
            '</div>';
    });
    list.innerHTML = html;
}

/** 新增渠道：展开表单 */
function openNotifyChannelForm() {
    notifyChannelEditing = null;
    document.getElementById('notifyChannelId').value = '';
    document.getElementById('notifyChannelName').value = '';
    document.getElementById('notifyChannelType').value = 'dingtalk';
    document.getElementById('notifyChannelEnabled').checked = true;
    onNotifyChannelTypeChange();
    document.getElementById('notifyChannelFormBox').style.display = 'block';
    document.getElementById('notifyChannelAddBtn').style.display = 'none';
}

/** 编辑渠道：展开表单并回填 */
function editNotifyChannel(id) {
    var ch = null;
    for (var i = 0; i < notifyChannelsCache.length; i++) {
        if (notifyChannelsCache[i].id === id) { ch = notifyChannelsCache[i]; break; }
    }
    if (!ch) return;
    notifyChannelEditing = ch;
    document.getElementById('notifyChannelId').value = ch.id;
    document.getElementById('notifyChannelName').value = ch.name || '';
    document.getElementById('notifyChannelType').value = ch.type || 'dingtalk';
    document.getElementById('notifyChannelEnabled').checked = ch.enabled !== false;
    onNotifyChannelTypeChange();
    // 回填配置值
    var cfg = {};
    try { cfg = JSON.parse(ch.config || '{}') || {}; } catch (e) { cfg = {}; }
    var box = document.getElementById('notifyChannelConfigBox');
    Object.keys(cfg).forEach(function (key) {
        var el = box.querySelector('[data-cfg="' + key + '"]');
        if (el && (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT')) {
            el.value = typeof cfg[key] === 'object' ? JSON.stringify(cfg[key]) : String(cfg[key]);
        }
    });
    document.getElementById('notifyChannelFormBox').style.display = 'block';
    document.getElementById('notifyChannelAddBtn').style.display = 'none';
}

/** 关闭渠道表单 */
function closeNotifyChannelForm() {
    notifyChannelEditing = null;
    document.getElementById('notifyChannelFormBox').style.display = 'none';
    document.getElementById('notifyChannelAddBtn').style.display = '';
}

/** 通知方式切换：渲染对应类型的配置输入项 */
function onNotifyChannelTypeChange() {
    var type = document.getElementById('notifyChannelType').value;
    var box = document.getElementById('notifyChannelConfigBox');
    var html = '';
    if (type === 'dingtalk') {
        html = notifyCfgInput('webhookUrl', 'Webhook 地址 *', '钉钉群机器人的完整 Webhook 地址（https://oapi.dingtalk.com/robot/send?access_token=xxx）') +
            notifyCfgInput('secret', '加签密钥', 'SEC 开头的加签密钥（可选，不填则不加签）');
    } else if (type === 'feishu') {
        html = notifyCfgInput('webhookUrl', 'Webhook 地址 *', '飞书群机器人的完整 Webhook 地址（https://open.feishu.cn/open-apis/bot/v2/hook/xxx）') +
            notifyCfgInput('secret', '签名密钥', '飞书机器人签名校验密钥（可选）');
    } else if (type === 'email') {
        html = notifyCfgInput('receivers', '收件邮箱 *', '多个邮箱用逗号分隔，如 a@b.com,c@d.com；SMTP 服务器在「邮件配置」中设置');
    } else {
        html = notifyCfgInput('url', 'Webhook 地址 *', '接收通知的 HTTP 地址，将以 POST JSON 发送 {title, content}') +
            notifyCfgInput('headers', '自定义请求头（JSON，可选）', '如 {"Authorization":"Bearer xxx"}');
    }
    box.innerHTML = html;
}

/** 生成单个配置输入项 HTML */
function notifyCfgInput(key, label, placeholder) {
    return '<div class="form-group"><label>' + label + '</label>' +
        '<input type="text" class="settings-input" data-cfg="' + key + '" placeholder="' + escapeHtml(placeholder) + '"></div>';
}

/** 保存渠道（新增/更新） */
function saveNotifyChannel() {
    var id = document.getElementById('notifyChannelId').value;
    var name = document.getElementById('notifyChannelName').value.trim();
    var type = document.getElementById('notifyChannelType').value;
    if (!name) {
        showToast('请输入渠道名称', 'error');
        return;
    }
    // 收集配置
    var cfg = {};
    var requiredKey = (type === 'email') ? 'receivers' : (type === 'webhook') ? 'url' : 'webhookUrl';
    var box = document.getElementById('notifyChannelConfigBox');
    try {
        box.querySelectorAll('[data-cfg]').forEach(function (el) {
            var key = el.getAttribute('data-cfg');
            var val = (el.value || '').trim();
            if (!val) return;
            if (key === 'headers') {
                cfg[key] = JSON.parse(val);
            } else {
                cfg[key] = val;
            }
        });
    } catch (e) {
        showToast('自定义请求头不是合法 JSON', 'error');
        return;
    }
    if (!cfg[requiredKey]) {
        showToast('请填写必填的配置项', 'error');
        return;
    }
    var payload = {
        name: name,
        type: type,
        config: JSON.stringify(cfg),
        enabled: document.getElementById('notifyChannelEnabled').checked
    };
    var url = id ? '/api/notify/channels/' + id : '/api/notify/channels';
    var method = id ? 'PUT' : 'POST';
    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast(id ? '渠道已更新' : '渠道已创建', 'success');
                closeNotifyChannelForm();
                loadNotifyChannelList();
            } else {
                showToast(res.msg || '保存失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('保存通知渠道失败:', err);
            showToast('保存失败', 'error');
        });
}

/** 删除渠道（二次确认） */
function deleteNotifyChannel(id) {
    var ch = null;
    for (var i = 0; i < notifyChannelsCache.length; i++) {
        if (notifyChannelsCache[i].id === id) { ch = notifyChannelsCache[i]; break; }
    }
    showConfirm('确定删除通知渠道「' + (ch ? ch.name : id) + '」？删除后引用该渠道的项目将不再向其发送通知。')
        .then(function (ok) {
            if (!ok) return;
            fetch('/api/notify/channels/' + id, { method: 'DELETE' })
                .then(function (r) { return r.json(); })
                .then(function (res) {
                    if (res.code === 200) {
                        showToast('渠道已删除', 'success');
                        notifyChannelsCache = notifyChannelsCache.filter(function (c) { return c.id !== id; });
                        renderNotifyChannelList();
                    } else {
                        showToast(res.msg || '删除失败', 'error');
                    }
                })
                .catch(function (err) {
                    console.error('删除通知渠道失败:', err);
                    showToast('删除失败', 'error');
                });
        });
}

/** 测试发送：向渠道发送一条测试通知 */
function testNotifyChannel(id) {
    showToast('正在发送测试通知...', 'info');
    fetch('/api/notify/channels/' + id + '/test', { method: 'POST' })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast('测试通知已发送', 'success');
            } else {
                showToast('发送失败：' + (res.msg || '未知原因'), 'error');
            }
        })
        .catch(function (err) {
            console.error('测试通知渠道失败:', err);
            showToast('测试发送失败', 'error');
        });
}
