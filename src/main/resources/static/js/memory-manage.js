var currentAgentId = '';
var currentMemoryId = null;
var allMemories = [];
var modalMode = 'add'; // 'add' | 'edit'
var modalParentId = null;
var modalEditId = null;

function loadTree() {
    var agentId = document.getElementById('agentSelect').value;
    if (!agentId) {
        showToast('请选择智能体', 'warning');
        return;
    }
    currentAgentId = agentId;
    currentMemoryId = null;
    document.getElementById('editorContent').innerHTML = '<div class="empty-state">请在左侧选择一条记忆</div>';

    fetch('/api/memory-manage/tree?agentId=' + encodeURIComponent(agentId))
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code === 200) {
                allMemories = res.data || [];
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
    if (allMemories.length === 0) {
        container.innerHTML = '<div class="empty-state">暂无记忆数据</div>';
        return;
    }
    var tree = buildTree(allMemories, null);
    container.innerHTML = '';
    for (var i = 0; i < tree.length; i++) {
        container.appendChild(createTreeNode(tree[i]));
    }
}

function createTreeNode(node) {
    var li = document.createElement('li');
    li.className = 'tree-node';
    li.setAttribute('data-id', node.id);
    li.setAttribute('data-parent-id', node.parentId || '');

    var content = document.createElement('div');
    content.className = 'tree-node-content';
    content.draggable = true;
    content.setAttribute('data-id', node.id);

    // toggle arrow
    var toggle = document.createElement('span');
    toggle.className = 'tree-toggle' + (node.children && node.children.length > 0 ? '' : ' empty');
    if (node.children && node.children.length > 0) {
        toggle.classList.add('expanded');
    }
    toggle.textContent = '▶';
    toggle.addEventListener('click', function(e) {
        e.stopPropagation();
        toggleNode(li, toggle);
    });
    content.appendChild(toggle);

    // label
    var label = document.createElement('span');
    label.className = 'tree-label';
    var text = node.memory || '';
    if (text.length > 40) {
        text = text.substring(0, 40) + '...';
    }
    label.textContent = text;
    label.title = node.memory;
    content.appendChild(label);

    // actions
    var actions = document.createElement('span');
    actions.className = 'tree-actions';
    var addBtn = document.createElement('button');
    addBtn.textContent = '+';
    addBtn.title = '添加子节点';
    addBtn.addEventListener('click', function(e) {
        e.stopPropagation();
        openAddModal(node.id);
    });
    actions.appendChild(addBtn);
    content.appendChild(actions);

    // click to select
    content.addEventListener('click', function(e) {
        selectNode(node, content);
    });

    // drag events
    content.addEventListener('dragstart', function(e) {
        e.dataTransfer.setData('text/plain', node.id.toString());
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
        var draggedId = parseInt(e.dataTransfer.getData('text/plain'));
        if (draggedId && draggedId !== node.id) {
            moveNode(draggedId, node.id);
        }
    });

    li.appendChild(content);

    // children
    if (node.children && node.children.length > 0) {
        var childrenUl = document.createElement('ul');
        childrenUl.className = 'tree-children';
        for (var i = 0; i < node.children.length; i++) {
            childrenUl.appendChild(createTreeNode(node.children[i]));
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
    // highlight
    var all = document.querySelectorAll('.tree-node-content.selected');
    for (var i = 0; i < all.length; i++) {
        all[i].classList.remove('selected');
    }
    contentEl.classList.add('selected');
    // load editor
    loadEditor(node);
}

function loadEditor(node) {
    var container = document.getElementById('editorContent');
    var timeStr = node.updateTime || node.createTime || '';
    container.innerHTML =
        '<div class="editor-meta">' +
            '<span>ID: ' + node.id + '</span>' +
            '<span>用户: ' + (node.userId || '-') + '</span>' +
            '<span>更新时间: ' + timeStr + '</span>' +
        '</div>' +
        '<textarea class="editor-textarea" id="editorTextarea">' + escapeHtml(node.memory || '') + '</textarea>' +
        '<div class="editor-actions">' +
            '<button class="btn btn-primary" onclick="saveMemory()">保存</button>' +
            '<button class="btn btn-outline" onclick="openAddModal(' + node.id + ')">新增子节点</button>' +
            '<button class="btn btn-danger" onclick="deleteMemory(' + node.id + ')">删除</button>' +
        '</div>';
}

function saveMemory() {
    if (!currentMemoryId) return;
    var textarea = document.getElementById('editorTextarea');
    var content = textarea ? textarea.value : '';
    fetch('/api/memory-manage/' + currentMemoryId, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ memory: content })
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
    if (!confirm('确定删除此记忆？其子节点将变为根节点。')) return;
    fetch('/api/memory-manage/' + id, { method: 'DELETE' })
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code === 200) {
                showToast('删除成功', 'success');
                currentMemoryId = null;
                document.getElementById('editorContent').innerHTML = '<div class="empty-state">请在左侧选择一条记忆</div>';
                loadTree();
            } else {
                showToast(res.msg || '删除失败', 'error');
            }
        })
        .catch(function(err) {
            showToast('网络错误: ' + err.message, 'error');
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

function addRootMemory() {
    modalMode = 'add';
    modalParentId = null;
    modalEditId = null;
    document.getElementById('modalTitle').textContent = '新增根记忆';
    document.getElementById('modalMemoryContent').value = '';
    document.getElementById('memoryModal').style.display = 'flex';
}

function openAddModal(parentId) {
    modalMode = 'add';
    modalParentId = parentId;
    modalEditId = null;
    document.getElementById('modalTitle').textContent = '新增子记忆';
    document.getElementById('modalMemoryContent').value = '';
    document.getElementById('memoryModal').style.display = 'flex';
}

function closeModal() {
    document.getElementById('memoryModal').style.display = 'none';
}

function saveModalMemory() {
    var content = document.getElementById('modalMemoryContent').value.trim();
    if (!content) {
        showToast('请输入记忆内容', 'warning');
        return;
    }
    if (!currentAgentId) {
        showToast('请先选择智能体', 'warning');
        return;
    }
    fetch('/api/memory-manage', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            agentId: currentAgentId,
            memory: content,
            parentId: modalParentId
        })
    })
    .then(function(r) { return r.json(); })
    .then(function(res) {
        if (res.code === 200) {
            showToast('新增成功', 'success');
            closeModal();
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
