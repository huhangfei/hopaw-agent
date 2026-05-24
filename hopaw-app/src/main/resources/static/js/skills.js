var currentMode = 'view';
var currentFolder = null;

document.addEventListener('DOMContentLoaded', function() {
    loadSkills();
});

function loadSkills() {
    fetch('/skills/api/list')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code !== 200) {
                showToast('加载失败', 'error');
                return;
            }
            renderCards(resp.data);
        })
        .catch(function() {
            showToast('请求失败', 'error');
        });
}

function renderCards(skills) {
    var grid = document.getElementById('skillsGrid');
    if (!skills || skills.length === 0) {
        grid.innerHTML = '<div class="skills-empty">暂无技能，点击上方按钮添加</div>';
        return;
    }

    var html = '';
    skills.forEach(function(s) {
        var desc = s.description || '';
        if (desc.length > 80) {
            desc = desc.substring(0, 80) + '...';
        }
        html += '<div class="skill-card">' +
            '<div class="skill-card-body">' +
            '<div class="skill-card-name">' + escapeHtml(s.name || s.folderName) + '</div>' +
            '<div class="skill-card-desc">' + escapeHtml(desc || '无描述') + '</div>' +
            '</div>' +
            '<div class="skill-card-actions">' +
            '<button class="btn-card btn-view" onclick="viewSkill(\'' + escapeAttr(s.folderName) + '\')" title="查看">' +
            '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>' +
            '</button>' +
            '<button class="btn-card btn-edit" onclick="editSkill(\'' + escapeAttr(s.folderName) + '\')" title="修改">' +
            '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>' +
            '</button>' +
            '<button class="btn-card btn-delete" onclick="deleteSkill(\'' + escapeAttr(s.folderName) + '\', \'' + escapeAttr(s.name || s.folderName) + '\')" title="删除">' +
            '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>' +
            '</button>' +
            '</div>' +
            '</div>';
    });
    grid.innerHTML = html;
}

function openAddModal() {
    currentMode = 'add';
    currentFolder = null;
    document.getElementById('modalTitle').textContent = '添加技能';
    document.getElementById('modalModeHint').textContent = '';
    document.getElementById('skillName').value = '';
    document.getElementById('skillName').disabled = false;
    document.getElementById('skillDesc').value = '';
    document.getElementById('skillSlug').value = '';
    document.getElementById('skillSlug').disabled = false;
    document.getElementById('skillVersion').value = '';
    document.getElementById('skillHomepage').value = '';
    document.getElementById('skillChangelog').value = '';
    document.getElementById('skillMetadata').value = '';
    document.getElementById('skillContent').value = '';
    showContentTextarea();
    document.getElementById('btnSave').style.display = '';
    Modal.open('skillModal');
}

function viewSkill(folderName) {
    fetch('/skills/api/' + encodeURIComponent(folderName))
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code !== 200) {
                showToast(resp.msg || '加载失败', 'error');
                return;
            }
            var skill = resp.data;
            currentMode = 'view';
            currentFolder = folderName;
            document.getElementById('modalTitle').textContent = '查看技能';
            document.getElementById('modalModeHint').textContent = '查看模式';
            document.getElementById('skillName').value = skill.name || '';
            document.getElementById('skillName').disabled = true;
            document.getElementById('skillDesc').value = skill.description || '';
            document.getElementById('skillSlug').value = skill.slug || skill.folderName || '';
            document.getElementById('skillSlug').disabled = true;
            document.getElementById('skillVersion').value = skill.version || '';
            document.getElementById('skillHomepage').value = skill.homepage || '';
            document.getElementById('skillChangelog').value = skill.changelog || '';
            document.getElementById('skillMetadata').value = formatMetadata(skill.metadata);
            document.getElementById('skillContent').value = skill.content || '';
            showContentPreview(skill.content);
            document.getElementById('btnSave').style.display = 'none';
            Modal.open('skillModal');
        })
        .catch(function() {
            showToast('请求失败', 'error');
        });
}

function editSkill(folderName) {
    fetch('/skills/api/' + encodeURIComponent(folderName))
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code !== 200) {
                showToast(resp.msg || '加载失败', 'error');
                return;
            }
            var skill = resp.data;
            currentMode = 'edit';
            currentFolder = folderName;
            document.getElementById('modalTitle').textContent = '修改技能';
            document.getElementById('modalModeHint').textContent = '当前修改: ' + folderName;
            document.getElementById('skillName').value = skill.name || '';
            document.getElementById('skillName').disabled = false;
            document.getElementById('skillDesc').value = skill.description || '';
            document.getElementById('skillSlug').value = skill.slug || skill.folderName || '';
            document.getElementById('skillSlug').disabled = false;
            document.getElementById('skillVersion').value = skill.version || '';
            document.getElementById('skillHomepage').value = skill.homepage || '';
            document.getElementById('skillChangelog').value = skill.changelog || '';
            document.getElementById('skillMetadata').value = formatMetadata(skill.metadata);
            document.getElementById('skillContent').value = skill.content || '';
            showContentTextarea();
            document.getElementById('btnSave').style.display = '';
            Modal.open('skillModal');
        })
        .catch(function() {
            showToast('请求失败', 'error');
        });
}

function saveSkill() {
    var name = document.getElementById('skillName').value.trim();
    if (!name) {
        showToast('请输入技能名称', 'error');
        return;
    }

    var desc = document.getElementById('skillDesc').value.trim();
    var slug = document.getElementById('skillSlug').value.trim();
    var version = document.getElementById('skillVersion').value.trim();
    var homepage = document.getElementById('skillHomepage').value.trim();
    var changelog = document.getElementById('skillChangelog').value.trim();
    var metadataText = document.getElementById('skillMetadata').value.trim();
    var content = document.getElementById('skillContent').value;

    var metadata = null;
    if (metadataText) {
        metadata = {};
        var lines = metadataText.split('\n');
        lines.forEach(function(line) {
            var colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                var key = line.substring(0, colonIdx).trim();
                var value = line.substring(colonIdx + 1).trim();
                if (key && value) {
                    metadata[key] = value;
                }
            }
        });
    }

    var body = {
        name: name,
        description: desc,
        slug: slug || null,
        version: version || null,
        homepage: homepage || null,
        changelog: changelog || null,
        metadata: metadata,
        content: content
    };

    var url, method;
    if (currentMode === 'add') {
        url = '/skills/api';
        method = 'POST';
    } else {
        url = '/skills/api/' + encodeURIComponent(currentFolder);
        method = 'PUT';
    }

    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        if (resp.code === 200) {
            showToast(currentMode === 'add' ? '添加成功' : '修改成功', 'success');
            closeModal();
            loadSkills();
        } else {
            showToast(resp.msg || '操作失败', 'error');
        }
    })
    .catch(function() {
        showToast('请求失败', 'error');
    });
}

function deleteSkill(folderName, displayName) {
    showConfirm('确定要删除技能 "' + displayName + '" 吗？此操作不可恢复。').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/skills/api/' + encodeURIComponent(folderName), {
            method: 'DELETE'
        })
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code === 200) {
                showToast('删除成功', 'success');
                loadSkills();
            } else {
                showToast(resp.msg || '删除失败', 'error');
            }
        })
        .catch(function() {
            showToast('请求失败', 'error');
        });
    });
}

function closeModal() {
    Modal.close('skillModal');
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeAttr(str) {
    if (!str) return '';
    return str.replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

function formatMetadata(meta) {
    if (!meta) return '';
    var lines = [];
    for (var key in meta) {
        if (meta.hasOwnProperty(key)) {
            lines.push(key + ': ' + meta[key]);
        }
    }
    return lines.join('\n');
}

function showContentTextarea() {
    document.getElementById('skillContent').style.display = '';
    document.getElementById('skillContentPreview').style.display = 'none';
}

function showContentPreview(content) {
    document.getElementById('skillContent').style.display = 'none';
    var preview = document.getElementById('skillContentPreview');
    preview.style.display = 'block';
    var body = extractMarkdownBody(content);
    preview.innerHTML = marked.parse(body || '');
}

function extractMarkdownBody(content) {
    if (!content) return '';
    var trimmed = content.trimStart();
    if (!trimmed.startsWith('---')) return content;
    var firstEnd = content.indexOf('---', 3);
    if (firstEnd === -1) return content;
    return content.substring(firstEnd + 3).trim();
}