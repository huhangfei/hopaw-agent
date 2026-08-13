/**
 * 附件管理页面脚本
 */
(function () {
    var currentPage = 1;
    var pageSize = 12;

    // 文件类型图标映射
    var fileTypeIcons = {
        image: '🖼️',
        video: '🎬',
        audio: '🎵',
        pdf: '📄',
        markdown: '📝',
        text: '📃',
        file: '📦'
    };

    var sourceLabels = {
        upload: '附件上传',
        chat: '会话文件',
        task: '任务附件',
        project: '项目附件'
    };

    // 初始化
    function init() {
        loadAttachments();
        bindEvents();
    }

    function bindEvents() {
        // 拖拽上传
        var container = document.querySelector('.attachments-container');
        container.addEventListener('dragover', function (e) {
            e.preventDefault();
        });
        container.addEventListener('drop', function (e) {
            e.preventDefault();
            if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                handleFileUpload(e.dataTransfer.files);
            }
        });
    }

    function getFilters() {
        return {
            keyword: document.getElementById('attachmentKeyword').value.trim(),
            source: document.getElementById('attachmentSourceFilter').value,
            tag: document.getElementById('attachmentTagFilter').value.trim(),
            fileType: document.getElementById('attachmentTypeFilter').value,
            page: currentPage,
            size: pageSize
        };
    }

    function loadAttachments() {
        var params = getFilters();
        var query = Object.keys(params)
            .filter(function (k) { return params[k] !== '' && params[k] != null; })
            .map(function (k) { return k + '=' + encodeURIComponent(params[k]); })
            .join('&');

        fetch('/api/attachments/page?' + query)
            .then(function (r) { return r.json(); })
            .then(function (resp) {
                if (resp.code === 200) {
                    renderList(resp.data.list, resp.data.total, resp.data.page, resp.data.size);
                } else {
                    showToast(resp.msg || '加载失败', 'error');
                }
            })
            .catch(function (err) {
                showToast('加载失败: ' + err.message, 'error');
            });
    }

    function renderList(list, total, page, size) {
        var container = document.getElementById('attachmentList');
        if (!list || list.length === 0) {
            container.innerHTML = '<div class="empty-state">' +
                '<div class="empty-icon">📂</div>' +
                '<div>暂无附件，点击右上角"批量上传"或拖拽文件到页面开始上传</div>' +
                '</div>';
            renderPagination(0, 1, size);
            return;
        }

        var html = list.map(function (item) {
            return buildCard(item);
        }).join('');
        container.innerHTML = html;
        renderPagination(total, page, size);
    }

    function buildCard(item) {
        var thumbHtml = buildThumb(item);
        var tagsHtml = '';
        if (item.tags) {
            var tags = item.tags.split(',').filter(function (t) { return t.trim(); });
            tagsHtml = tags.map(function (t) {
                return '<span class="attachment-tag">' + escapeHtml(t.trim()) + '</span>';
            }).join('');
        }

        var sourceLabel = sourceLabels[item.source] || item.source || '附件上传';
        var fileSize = formatFileSize(item.fileSize);
        var uploadTime = formatTime(item.createTime);

        return '<div class="attachment-card">' +
            '<div class="attachment-card-thumb" onclick="previewAttachment(' + item.id + ')">' +
            thumbHtml +
            '<span class="file-type-badge">' + escapeHtml(item.fileType || 'file') + '</span>' +
            '</div>' +
            '<div class="attachment-card-body">' +
            '<div class="attachment-card-name" onclick="previewAttachment(' + item.id + ')" title="' + escapeHtml(item.originalName) + '">' +
            escapeHtml(item.originalName) + '</div>' +
            '<div class="attachment-card-meta">' +
            '<span>' + fileSize + '</span>' +
            '<span>·</span>' +
            '<span>' + escapeHtml(sourceLabel) + '</span>' +
            '<span>·</span>' +
            '<span>' + escapeHtml(uploadTime) + '</span>' +
            '</div>' +
            (tagsHtml ? '<div class="attachment-card-tags">' + tagsHtml + '</div>' : '') +
            '</div>' +
            '<div class="attachment-card-actions">' +
            '<button class="btn btn-sm btn-secondary" onclick="previewAttachment(' + item.id + ')">预览</button>' +
            '<button class="btn btn-sm btn-secondary" onclick="editAttachment(' + item.id + ')">编辑</button>' +
            '<button class="btn btn-sm btn-danger" onclick="deleteAttachment(' + item.id + ')">删除</button>' +
            '</div>' +
            '</div>';
    }

    function buildThumb(item) {
        if (item.fileType === 'image') {
            return '<img src="' + item.url + '" alt="' + escapeHtml(item.originalName) + '" onerror="this.style.display=\'none\';this.parentElement.querySelector(\'.file-icon\').style.display=\'block\'">' +
                '<span class="file-icon" style="display:none">' + (fileTypeIcons[item.fileType] || '📦') + '</span>';
        }
        return '<span class="file-icon">' + (fileTypeIcons[item.fileType] || '📦') + '</span>';
    }

    function renderPagination(total, page, size) {
        var container = document.getElementById('attachmentPagination');
        var totalPages = Math.ceil(total / size);
        if (totalPages <= 1) {
            container.innerHTML = '<span class="page-info">共 ' + total + ' 个附件</span>';
            return;
        }

        var html = '';
        html += '<button onclick="goToPage(' + (page - 1) + ')" ' + (page <= 1 ? 'disabled' : '') + '>上一页</button>';

        var startPage = Math.max(1, page - 2);
        var endPage = Math.min(totalPages, page + 2);
        for (var i = startPage; i <= endPage; i++) {
            html += '<button class="' + (i === page ? 'active' : '') + '" onclick="goToPage(' + i + ')">' + i + '</button>';
        }

        html += '<button onclick="goToPage(' + (page + 1) + ')" ' + (page >= totalPages ? 'disabled' : '') + '>下一页</button>';
        html += '<span class="page-info">共 ' + total + ' 个 / ' + totalPages + ' 页</span>';
        container.innerHTML = html;
    }

    // 暴露给全局
    window.goToPage = function (page) {
        currentPage = page;
        loadAttachments();
    };

    window.doSearch = function () {
        currentPage = 1;
        loadAttachments();
    };

    window.handleFileUpload = function (files) {
        if (!files || files.length === 0) return;
        var formData = new FormData();
        for (var i = 0; i < files.length; i++) {
            formData.append('files', files[i]);
        }
        formData.append('source', 'upload');

        var progressEl = document.getElementById('attachmentUploadProgress');
        progressEl.style.display = 'flex';

        fetch('/api/attachments/upload', {
            method: 'POST',
            body: formData
        })
            .then(function (r) { return r.json(); })
            .then(function (resp) {
                progressEl.style.display = 'none';
                if (resp.code === 200) {
                    showToast('成功上传 ' + resp.data.length + ' 个附件', 'success');
                    // 清空 file input
                    document.getElementById('attachmentFileInput').value = '';
                    loadAttachments();
                } else {
                    showToast(resp.msg || '上传失败', 'error');
                }
            })
            .catch(function (err) {
                progressEl.style.display = 'none';
                showToast('上传失败: ' + err.message, 'error');
            });
    };

    window.previewAttachment = function (id) {
        fetch('/api/attachments/' + id)
            .then(function (r) { return r.json(); })
            .then(function (resp) {
                if (resp.code === 200) {
                    showPreviewModal(resp.data);
                } else {
                    showToast(resp.msg || '获取附件信息失败', 'error');
                }
            });
    };

    function showPreviewModal(item) {
        var modal = document.getElementById('attachmentPreviewModal');
        var title = document.getElementById('previewModalTitle');
        var body = document.getElementById('previewModalBody');

        title.textContent = item.originalName;

        var content = '<div class="preview-container">';
        var type = item.fileType;

        if (type === 'image') {
            content += '<img src="' + item.url + '" alt="' + escapeHtml(item.originalName) + '">';
        } else if (type === 'video') {
            content += '<video controls src="' + item.url + '"></video>';
        } else if (type === 'audio') {
            content += '<audio controls src="' + item.url + '"></audio>';
        } else if (type === 'pdf') {
            content += '<iframe class="preview-pdf" src="' + item.url + '"></iframe>';
        } else if (type === 'markdown') {
            content += '<div class="preview-markdown" id="previewMarkdownContent">加载中...</div>';
        } else if (type === 'text') {
            // 先显示加载中，再异步加载文件内容
            content += '<div class="preview-text" id="previewTextContent">加载中...</div>';
        } else {
            content += '<div class="preview-unsupported">' +
                '<div class="file-icon">' + (fileTypeIcons[type] || '📦') + '</div>' +
                '<div>该文件类型不支持在线预览</div>' +
                '</div>';
        }

        content += '<div class="preview-info">' +
            '<span class="info-item">大小: ' + formatFileSize(item.fileSize) + '</span>' +
            '<span class="info-item">类型: ' + escapeHtml(item.fileExtension || '') + '</span>' +
            '<span class="info-item">来源: ' + escapeHtml(sourceLabels[item.source] || item.source || '') + '</span>' +
            '<span class="info-item">创建时间: ' + escapeHtml(formatTime(item.createTime)) + '</span>' +
            '<span class="info-item">更新时间: ' + escapeHtml(formatTime(item.updateTime)) + '</span>' +
            '<a class="preview-download" href="' + item.url + '" target="_blank" download="' + escapeHtml(item.originalName) + '">下载文件</a>' +
            '</div>';
        content += '</div>';
        body.innerHTML = content;
        modal.style.display = 'flex';

        // 异步加载文本/markdown内容
        if (type === 'markdown') {
            fetch(item.url)
                .then(function (r) { return r.text(); })
                .then(function (text) {
                    var el = document.getElementById('previewMarkdownContent');
                    if (el) el.innerHTML = marked.parse(text);
                })
                .catch(function () {
                    var el = document.getElementById('previewMarkdownContent');
                    if (el) el.textContent = '加载失败';
                });
        } else if (type === 'text') {
            fetch(item.url)
                .then(function (r) { return r.text(); })
                .then(function (text) {
                    var el = document.getElementById('previewTextContent');
                    if (el) el.textContent = text;
                })
                .catch(function () {
                    var el = document.getElementById('previewTextContent');
                    if (el) el.textContent = '加载失败';
                });
        }
    }

    window.closePreviewModal = function (e) {
        if (e.target === e.currentTarget) {
            document.getElementById('attachmentPreviewModal').style.display = 'none';
        }
    };

    window.closePreviewModalDirect = function () {
        document.getElementById('attachmentPreviewModal').style.display = 'none';
    };

    window.editAttachment = function (id) {
        fetch('/api/attachments/' + id)
            .then(function (r) { return r.json(); })
            .then(function (resp) {
                if (resp.code === 200) {
                    var item = resp.data;
                    document.getElementById('editOriginalName').value = item.originalName || '';
                    document.getElementById('editSource').value = item.source || 'upload';
                    document.getElementById('editTags').value = item.tags || '';
                    document.getElementById('editRemark').value = item.remark || '';
                    document.getElementById('attachmentEditModal').setAttribute('data-id', id);
                    document.getElementById('attachmentEditModal').style.display = 'flex';
                } else {
                    showToast(resp.msg || '获取附件信息失败', 'error');
                }
            });
    };

    window.saveAttachmentEdit = function () {
        var modal = document.getElementById('attachmentEditModal');
        var id = modal.getAttribute('data-id');
        var data = {
            tags: document.getElementById('editTags').value.trim(),
            remark: document.getElementById('editRemark').value.trim(),
            source: document.getElementById('editSource').value
        };

        fetch('/api/attachments/' + id, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        })
            .then(function (r) { return r.json(); })
            .then(function (resp) {
                if (resp.code === 200) {
                    showToast('保存成功', 'success');
                    closeEditModalDirect();
                    loadAttachments();
                } else {
                    showToast(resp.msg || '保存失败', 'error');
                }
            })
            .catch(function (err) {
                showToast('保存失败: ' + err.message, 'error');
            });
    };

    window.closeEditModal = function (e) {
        if (e.target === e.currentTarget) {
            document.getElementById('attachmentEditModal').style.display = 'none';
        }
    };

    window.closeEditModalDirect = function () {
        document.getElementById('attachmentEditModal').style.display = 'none';
    };

    window.deleteAttachment = function (id) {
        showConfirmDialog('确认删除', '确定要删除这个附件吗？删除后文件将无法恢复。', function () {
            fetch('/api/attachments/' + id, {
                method: 'DELETE'
            })
                .then(function (r) { return r.json(); })
                .then(function (resp) {
                    if (resp.code === 200) {
                        showToast('删除成功', 'success');
                        loadAttachments();
                    } else {
                        showToast(resp.msg || '删除失败', 'error');
                    }
                })
                .catch(function (err) {
                    showToast('删除失败: ' + err.message, 'error');
                });
        });
    };

    // 工具函数
    function formatFileSize(bytes) {
        if (!bytes || bytes <= 0) return '0 B';
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
    }

    function formatTime(timeStr) {
        if (!timeStr) return '';
        var d = new Date(timeStr);
        if (isNaN(d.getTime())) return timeStr;
        var pad = function (n) { return n < 10 ? '0' + n : n; };
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
            ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // 确认弹窗（复用全局 confirm-dialog.js）
    function showConfirmDialog(title, message, onConfirm) {
        if (typeof window.showConfirmDialog === 'function') {
            window.showConfirmDialog(title, message, onConfirm);
        } else {
            if (confirm(message)) {
                onConfirm();
            }
        }
    }

    // 启动
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
