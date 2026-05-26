var currentMemoryId = null;
var currentMemoryType = null;
var currentNode = null;
var allMemories = [];
var memoryTypes = [];

function loadTree() {
    currentMemoryId = null;
    currentNode = null;
    document.getElementById('editorContent').innerHTML = '<div class="empty-state">请在左侧选择一条记忆</div>';

    fetch('/api/memory-manage/tree')
    .then(function(r) { return r.json(); })
    .then(function(res) {
        if (res.code === 200) {
            memoryTypes = res.data.types || [];
            allMemories = res.data.memories || [];
            renderTree();
        } else {
            showToast(res.msg || '加载失败', 'error');
        }
    })
    .catch(function(err) {
        showToast('网络错误: ' + err.message, 'error');
    });
}

function buildTree(list, parentId) {
    var result = [];
    for (var i = 0; i < list.length; i++) {
        var pid = list[i].parentId;
        if ((parentId === null && pid === null) || (parentId !== null && pid === parentId)) {
            var node = {
                id: list[i].id,
                memory: list[i].memory,
                summary: list[i].summary,
                memoryType: list[i].memoryType,
                agentId: list[i].agentId,
                parentId: list[i].parentId,
                userId: list[i].userId,
                createTime: list[i].createTime,
                updateTime: list[i].updateTime,
                children: buildTree(list, list[i].id)
            };
            result.push(node);
        }
    }
    return result;
}

function renderTree() {
    var container = document.getElementById('treeContainer');
    if (memoryTypes.length === 0 && allMemories.length === 0) {
        container.innerHTML = '<div class="empty-state">暂无记忆数据</div>';
        return;
    }
    container.innerHTML = '';
    var ul = document.createElement('ul');
    for (var t = 0; t < memoryTypes.length; t++) {
        var typeNode = memoryTypes[t];
        var typeMemories = [];
        for (var i = 0; i < allMemories.length; i++) {
            if (allMemories[i].memoryType === typeNode.code) {
                typeMemories.push(allMemories[i]);
            }
        }
        var tree = buildTree(typeMemories, null);
        var typeLi = createTypeNode(typeNode, tree);
        ul.appendChild(typeLi);
    }
    container.appendChild(ul);
}

function createTypeNode(typeInfo, children) {
    var li = document.createElement('li');
    li.className = 'tree-node type-node';

    var content = document.createElement('div');
    content.className = 'tree-node-content type-node-content';

    var toggle = document.createElement('span');
    toggle.className = 'tree-toggle' + (children.length > 0 ? ' expanded' : ' empty');
    toggle.textContent = '▶';
    toggle.addEventListener('click', function(e) {
        e.stopPropagation();
        toggleNode(li, toggle);
    });
    content.appendChild(toggle);

    var icon = document.createElement('span');
    icon.className = 'tree-type-icon';
    icon.textContent = '📁';
    content.appendChild(icon);

    var label = document.createElement('span');
    label.className = 'tree-label type-label';
    label.textContent = typeInfo.name + ' (' + typeInfo.code + ')';
    content.appendChild(label);

    var count = document.createElement('span');
    count.className = 'tree-type-count';
    count.textContent = typeInfo.count + ' 条';
    content.appendChild(count);

    li.appendChild(content);

    if (children.length > 0) {
        var childrenUl = document.createElement('ul');
        childrenUl.className = 'tree-children';
        for (var i = 0; i < children.length; i++) {
            childrenUl.appendChild(createTreeNode(children[i], typeInfo.code));
        }
        li.appendChild(childrenUl);
    }

    return li;
}

function createTreeNode(node, typeCode) {
    var li = document.createElement('li');
    li.className = 'tree-node';
    li.setAttribute('data-id', node.id);
    li.setAttribute('data-memory-type', node.memoryType || typeCode);

    var content = document.createElement('div');
    content.className = 'tree-node-content';
    content.draggable = true;
    content.setAttribute('data-id', node.id);
    content.setAttribute('data-memory-type', node.memoryType || typeCode);

    var toggle = document.createElement('span');
    toggle.className = 'tree-toggle' + (node.children && node.children.length > 0 ? ' expanded' : ' empty');
    toggle.textContent = '▶';
    toggle.addEventListener('click', function(e) {
        e.stopPropagation();
        toggleNode(li, toggle);
    });
    content.appendChild(toggle);

    var label = document.createElement('span');
    label.className = 'tree-label';
    var text = node.summary || node.memory || '';
    if (text.length > 36) {
        text = text.substring(0, 36) + '...';
    }
    label.textContent = text;
    label.title = (node.summary || '') + '\n' + (node.memory || '');
    content.appendChild(label);

    var actions = document.createElement('span');
    actions.className = 'tree-actions';
    var addBtn = document.createElement('button');
    addBtn.textContent = '+';
    addBtn.title = '添加子节点';
    addBtn.addEventListener('click', function(e) {
        e.stopPropagation();
        openAddModal(node);
    });
    actions.appendChild(addBtn);
    content.appendChild(actions);

    content.addEventListener('click', function(e) {
        selectNode(node, content);
    });

    // drag events
    content.addEventListener('dragstart', function(e) {
        e.dataTransfer.setData('text/plain', JSON.stringify({id: node.id, memoryType: node.memoryType || typeCode}));
        content.classList.add('dragging');
    });
    content.addEventListener('dragend', function(e) {
        content.classList.remove('dragging');
        clearDragOver();
    });
    content.addEventListener('dragover', function(e) {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        clearDragOver();
        content.classList.add('drag-over');
    });
    content.addEventListener('dragleave', function(e) {
        content.classList.remove('drag-over');
    });
    content.addEventListener('drop', function(e) {
        e.preventDefault();
        e.stopPropagation();
        content.classList.remove('drag-over');
        try {
            var dragData = JSON.parse(e.dataTransfer.getData('text/plain'));
            var draggedId = dragData.id;
            var draggedType = dragData.memoryType;
            var targetType = node.memoryType || typeCode;
            if (draggedId && draggedId !== node.id) {
                if (draggedType !== targetType) {
                    showToast('不能移动到不同记忆类型下', 'warning');
                    return;
                }
                moveNode(draggedId, node.id);
            }
        } catch (ex) {}
    });

    li.appendChild(content);

    if (node.children && node.children.length > 0) {
        var childrenUl = document.createElement('ul');
        childrenUl.className = 'tree-children';
        for (var i = 0; i < node.children.length; i++) {
            childrenUl.appendChild(createTreeNode(node.children[i], typeCode));
        }
        li.appendChild(childrenUl);
    }

    return li;
}

function toggleNode(li, toggle) {
    toggle.classList.toggle('expanded');
    var children = li.querySelector(':scope > .tree-children');
    if (children) {
        children.classList.toggle('collapsed');
    }
}

function clearDragOver() {
    var all = document.querySelectorAll('.tree-node-content.drag-over');
    for (var i = 0; i < all.length; i++) {
        all[i].classList.remove('drag-over');
    }
}

function selectNode(node, contentEl) {
    currentMemoryId = node.id;
    currentMemoryType = node.memoryType;
    currentNode = node;
    var all = document.querySelectorAll('.tree-node-content.selected');
    for (var i = 0; i < all.length; i++) {
        all[i].classList.remove('selected');
    }
    contentEl.classList.add('selected');
    loadEditor(node);
}

function loadEditor(node) {
    var container = document.getElementById('editorContent');
    var timeStr = node.updateTime || node.createTime || '';
    var safeSummary = escapeHtml(node.summary || '');
    var safeMemory = escapeHtml(node.memory || '');
    container.innerHTML =
        '<div class="editor-meta">' +
            '<span>ID: ' + node.id + '</span>' +
            '<span>用户: ' + (node.userId || '-') + '</span>' +
            '<span>更新时间: ' + timeStr + '</span>' +
        '</div>' +
        '<div class="editor-field">' +
            '<label>记忆类型</label>' +
            renderTypeSelect(node.memoryType || '') +
        '</div>' +
        '<div class="editor-field">' +
            '<label>概要</label>' +
            '<textarea class="editor-textarea editor-summary" id="editorSummary" rows="3" placeholder="记忆概要">' + safeSummary + '</textarea>' +
        '</div>' +
        '<div class="editor-field">' +
            '<label>记忆内容</label>' +
            '<textarea class="editor-textarea" id="editorTextarea" rows="6" placeholder="记忆内容">' + safeMemory + '</textarea>' +
        '</div>' +
        '<div class="editor-actions">' +
            '<button class="btn btn-primary" onclick="saveMemory()">保存</button>' +
            '<button class="btn btn-outline" onclick="openAddModal(currentNode)">新增子节点</button>' +
            '<button class="btn btn-danger" onclick="deleteMemory(' + node.id + ')">删除</button>' +
        '</div>';
}

function saveMemory() {
    if (!currentMemoryId) return;
    var summary = document.getElementById('editorSummary');
    var textarea = document.getElementById('editorTextarea');
    var memoryTypeSelect = document.getElementById('memoryTypeSelect');
    if (summary && !summary.value.trim()) {
        showToast('请输入概要', 'warning');
        return;
    }
    var body = {};
    if (summary) body.summary = summary.value.trim();
    if (textarea) body.memory = textarea.value;
    if (memoryTypeSelect) body.memoryType = memoryTypeSelect.value;
    fetch('/api/memory-manage/' + currentMemoryId, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
    .then(function(r) { return r.json(); })
    .then(function(res) {
        if (res.code === 200) {
            showToast('保存成功', 'success');
            loadTree();
        } else {
            showToast(res.msg || '保存失败', 'error');
        }
    })
    .catch(function(err) {
        showToast('网络错误: ' + err.message, 'error');
    });
}

function deleteMemory(id) {
    showConfirm('确定删除此记忆？其子节点将变为根节点。').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/memory-manage/' + id, { method: 'DELETE' })
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code === 200) {
                showToast('删除成功', 'success');
                currentMemoryId = null;
                currentNode = null;
                document.getElementById('editorContent').innerHTML = '<div class="empty-state">请在左侧选择一条记忆</div>';
                loadTree();
            } else {
                showToast(res.msg || '删除失败', 'error');
            }
        })
        .catch(function(err) {
            showToast('网络错误: ' + err.message, 'error');
        });
    });
}

function moveNode(draggedId, targetId) {
    fetch('/api/memory-manage/' + draggedId + '/move?newParentId=' + targetId, { method: 'PUT' })
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code === 200) {
                showToast('移动成功', 'success');
                loadTree();
            } else {
                showToast(res.msg || '移动失败', 'error');
            }
        })
        .catch(function(err) {
            showToast('网络错误: ' + err.message, 'error');
        });
}

function openAddModal(parentNode) {
    var container = document.getElementById('editorContent');
    var memoryType = parentNode.memoryType || currentMemoryType || '';
    var parentSummary = parentNode.summary || parentNode.memory || '';
    if (parentSummary.length > 30) parentSummary = parentSummary.substring(0, 30) + '...';
    container.innerHTML =
        '<div class="editor-meta">' +
            '<span>父节点: ' + parentSummary + '</span>' +
        '</div>' +
        '<div class="editor-field">' +
            '<label>记忆类型</label>' +
            renderTypeSelect(memoryType) +
        '</div>' +
        '<div class="editor-field">' +
            '<label>概要</label>' +
            '<textarea class="editor-textarea editor-summary" id="newSummary" rows="3" placeholder="新增记忆概要"></textarea>' +
        '</div>' +
        '<div class="editor-field">' +
            '<label>记忆内容</label>' +
            '<textarea class="editor-textarea" id="newMemory" rows="6" placeholder="新增记忆内容"></textarea>' +
        '</div>' +
        '<div class="editor-actions">' +
            '<button class="btn btn-primary" onclick="saveNewMemory(' + parentNode.id + ')">保存</button>' +
            '<button class="btn btn-outline" onclick="cancelAdd()">取消</button>' +
        '</div>';
}

function cancelAdd() {
    if (currentNode) {
        loadEditor(currentNode);
    } else {
        document.getElementById('editorContent').innerHTML = '<div class="empty-state">请在左侧选择一条记忆</div>';
    }
}

function saveNewMemory(parentId) {
    var summary = document.getElementById('newSummary').value.trim();
    var memory = document.getElementById('newMemory').value.trim();
    var memoryType = document.getElementById('memoryTypeSelect').value;
    if (!summary) {
        showToast('请输入概要', 'warning');
        return;
    }
    fetch('/api/memory-manage', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            memory: memory,
            summary: summary,
            memoryType: memoryType,
            parentId: parentId
        })
    })
    .then(function(r) { return r.json(); })
    .then(function(res) {
        if (res.code === 200) {
            showToast('新增成功', 'success');
            loadTree();
        } else {
            showToast(res.msg || '新增失败', 'error');
        }
    })
    .catch(function(err) {
        showToast('网络错误: ' + err.message, 'error');
    });
}

function addRootMemory() {
    var container = document.getElementById('editorContent');
    container.innerHTML =
        '<div class="editor-field">' +
            '<label>记忆类型</label>' +
            renderTypeSelect('expandKnowledge') +
        '</div>' +
        '<div class="editor-field">' +
            '<label>概要 <span style="color:red">*</span></label>' +
            '<textarea class="editor-textarea editor-summary" id="newSummary" rows="3" placeholder="记忆概要"></textarea>' +
        '</div>' +
        '<div class="editor-field">' +
            '<label>记忆内容</label>' +
            '<textarea class="editor-textarea" id="newMemory" rows="6" placeholder="记忆内容"></textarea>' +
        '</div>' +
        '<div class="editor-actions">' +
            '<button class="btn btn-primary" onclick="saveRootMemory()">保存</button>' +
            '<button class="btn btn-outline" onclick="cancelAdd()">取消</button>' +
        '</div>';
}

function saveRootMemory() {
    var summary = document.getElementById('newSummary').value.trim();
    var memory = document.getElementById('newMemory').value.trim();
    var memoryType = document.getElementById('memoryTypeSelect').value;
    if (!summary) {
        showToast('请输入概要', 'warning');
        return;
    }
    fetch('/api/memory-manage', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            memoryType: memoryType,
            summary: summary,
            memory: memory,
            parentId: null
        })
    })
    .then(function(r) { return r.json(); })
    .then(function(res) {
        if (res.code === 200) {
            showToast('新增成功', 'success');
            loadTree();
        } else {
            showToast(res.msg || '新增失败', 'error');
        }
    })
    .catch(function(err) {
        showToast('网络错误: ' + err.message, 'error');
    });
}

function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function renderTypeSelect(selectedType) {
    var html = '<select id="memoryTypeSelect" class="form-select">';
    for (var i = 0; i < memoryTypes.length; i++) {
        var t = memoryTypes[i];
        // 限制不能选择任务记录类型
        if (t.code === 'taskRecords') {
            continue;
        }
        var sel = t.code === selectedType ? ' selected' : '';
        html += '<option value="' + escapeHtml(t.code) + '"' + sel + '>' + escapeHtml(t.name) + ' (' + escapeHtml(t.code) + ')</option>';
    }
    html += '</select>';
    return html;
}

function escapeAttr(str) {
    return str.replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

// 页面加载时自动加载数据
document.addEventListener('DOMContentLoaded', function() {
    loadTree();
});
