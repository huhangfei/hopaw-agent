var currentAgentId = null;
var ws = null;
var isStreaming = false;
var currentStreamingMessage = null;
var streamingMarkdownContent = '';
var lastMessageType = null;
var streamingMessages = {};
var toolCallTimers = {};
var loadingMessageDiv = null;

if (typeof marked !== 'undefined') {
    marked.setOptions({
        breaks: true,
        gfm: true
    });
}

function formatMessageTime(date) {
    var now = new Date();
    var isToday = date.getFullYear() === now.getFullYear() &&
                  date.getMonth() === now.getMonth() &&
                  date.getDate() === now.getDate();
    
    var hours = date.getHours().toString().padStart(2, '0');
    var minutes = date.getMinutes().toString().padStart(2, '0');
    var seconds = date.getSeconds().toString().padStart(2, '0');
    
    if (isToday) {
        return hours + ':' + minutes + ':' + seconds;
    } else {
        var year = date.getFullYear();
        var month = (date.getMonth() + 1).toString().padStart(2, '0');
        var day = date.getDate().toString().padStart(2, '0');
        return year + '-' + month + '-' + day + ' ' + hours + ':' + minutes + ':' + seconds;
    }
}

function renderMarkdown(content) {
    if (typeof marked !== 'undefined') {
        return marked.parse(content);
    }
    return content.replace(/\n/g, '<br>');
}

function renderAllMessages() {
    var messageContents = document.querySelectorAll('.message-content[data-is-agent="true"]');
    messageContents.forEach(function(el) {
        var textContent = el.getAttribute('data-raw-content') || el.textContent;
        el.innerHTML = renderMarkdown(textContent);
    });
}

function setCurrentAgentId(agentId) {
    currentAgentId = agentId;
}

function connectWebSocket(agentId) {
    var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    var wsUrl = protocol + '//' + window.location.host + '/ws/chat/' + agentId;

    ws = new WebSocket(wsUrl);

    ws.onopen = function() {
        console.log('WebSocket 连接已建立, agentId:', agentId);
    };
    
    ws.onmessage = function(event) {
        var data = JSON.parse(event.data);
        var responseId = data.responseId;

        if (data.type !== 'received') {
            removeLoadingMessage();
        }

        if (data.type === 'received') {
            showLoadingMessage();
        } else if (data.type === 'chunk') {
            handleStreamingChunk(data.content, responseId);
        } else if (data.type === 'tool_call') {
            handleToolCall(data, responseId);
        } else if (data.type === 'thinking') {
            handleThinking(data, responseId);
        } else if (data.type === 'done') {
            handleStreamingDone(data.message, data.response, responseId);
        } else if (data.type === 'error') {
            handleStreamingError(data.content || data.message, responseId);
        }
    };
    
    ws.onclose = function() {
        console.log('WebSocket 连接已关闭');
        setTimeout(function() {
            connectWebSocket(currentAgentId);
        }, 3000);
    };
    
    ws.onerror = function(error) {
        console.error('WebSocket 错误:', error);
    };
}

function handleToolCall(data, responseId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.chat-header .agent-select'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();

    var msgState = streamingMessages[responseId];
    if (!msgState) {
        msgState = { currentStreamingMessage: null, streamingMarkdownContent: '', lastMessageType: null, toolCallArgsBuffer: {}, toolCallResultBuffer: {} };
        streamingMessages[responseId] = msgState;
    }
    if (msgState.toolCallArgsBuffer == null) {
        msgState.toolCallArgsBuffer = {};
    }
    if (msgState.toolCallResultBuffer == null) {
        msgState.toolCallResultBuffer = {};
    }

    // Look up existing tool-call DOM element (may have been created by a prior status)
    var toolCallDiv = document.querySelector('.tool-call[data-tool-call-id="' + data.toolCallId + '"]');

    // ── create wrapper if nothing exists yet ──
    if (!toolCallDiv) {
        msgState.streamingMarkdownContent = '';
        msgState.lastMessageType = 'tool_call';

        msgState.currentStreamingMessage = document.createElement('div');
        msgState.currentStreamingMessage.className = 'message agent tool-call-message';
        msgState.currentStreamingMessage.setAttribute('data-response-id', responseId);

        var label = document.createElement('div');
        label.className = 'message-label';
        label.textContent = agentName;
        msgState.currentStreamingMessage.appendChild(label);

        var toolCallContainer = document.createElement('div');
        toolCallContainer.className = 'tool-call-container';
        msgState.currentStreamingMessage.appendChild(toolCallContainer);

        toolCallDiv = document.createElement('div');
        toolCallDiv.className = 'tool-call';
        toolCallDiv.setAttribute('data-tool-call-id', data.toolCallId);
        toolCallContainer.appendChild(toolCallDiv);

        var toolCallHeader = document.createElement('div');
        toolCallHeader.className = 'tool-call-header';

        var toolIcon = document.createElement('span');
        toolIcon.className = 'tool-call-icon';
        toolIcon.textContent = '🔧';
        toolCallHeader.appendChild(toolIcon);

        var toolName = document.createElement('span');
        toolName.className = 'tool-call-name';
        toolName.textContent = data.toolName || 'Unknown Tool';
        toolCallHeader.appendChild(toolName);

        var toolCallStatus = document.createElement('span');
        toolCallStatus.className = 'tool-call-status';
        toolCallHeader.appendChild(toolCallStatus);

        toolCallDiv.appendChild(toolCallHeader);

        var bodyDiv = document.createElement('div');
        bodyDiv.className = 'tool-call-body open';
        toolCallDiv.appendChild(bodyDiv);

        messagesDiv.appendChild(msgState.currentStreamingMessage);
    }

    // ── per-status updates ──
    var statusEl = toolCallDiv.querySelector('.tool-call-status');
    var iconEl = toolCallDiv.querySelector('.tool-call-icon');
    var bodyDiv = toolCallDiv.querySelector('.tool-call-body');

    if (data.status === 'preparing') {
        toolCallDiv.setAttribute('data-status', 'preparing');
        if (statusEl) { statusEl.textContent = '准备中...'; statusEl.classList.remove('completed'); }
        if (iconEl) { iconEl.textContent = '⚙️'; iconEl.style.animation = ''; }

        if (data.argumentsPartial != null) {
            var key = data.toolCallId;
            msgState.toolCallArgsBuffer[key] = (msgState.toolCallArgsBuffer[key] || '') + data.argumentsPartial;
            var argsDiv = bodyDiv.querySelector('.tool-call-args[data-partial-args]');
            if (!argsDiv) {
                argsDiv = document.createElement('div');
                argsDiv.className = 'tool-call-args';
                argsDiv.setAttribute('data-partial-args', data.toolCallId);
                argsDiv.innerHTML = '<div class="args-label">参数(准备中):</div><pre class="args-content"></pre>';
                bodyDiv.appendChild(argsDiv);
            }
            var preEl = argsDiv.querySelector('.args-content');
            preEl.textContent = msgState.toolCallArgsBuffer[key];
            preEl.scrollTop = preEl.scrollHeight;
        }

    } else if (data.status === 'starting') {
        delete msgState.toolCallArgsBuffer[data.toolCallId];

        toolCallDiv.setAttribute('data-status', 'starting');
        if (statusEl) { statusEl.textContent = '执行中...'; statusEl.classList.remove('completed'); }
        if (iconEl) { iconEl.textContent = '⚙️'; iconEl.style.animation = ''; }

        // 启动实时计时器
        var startTime = Date.now();
        var timerStatusEl = statusEl;
        var intervalId = setInterval(function() {
            var elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
            if (timerStatusEl) { timerStatusEl.textContent = '执行中... (' + elapsed + 's)'; }
        }, 100);
        toolCallTimers[data.toolCallId] = {startTime: startTime, intervalId: intervalId};

        // Remove old partial-args if any
        var oldArgsDiv = bodyDiv.querySelector('.tool-call-args[data-partial-args]');
        if (oldArgsDiv) oldArgsDiv.remove();

        if (data.arguments) {
            var argsDiv = document.createElement('div');
            argsDiv.className = 'tool-call-args';
            argsDiv.innerHTML = '<div class="args-label">参数:</div><pre class="args-content">' +
                escapeHtml(JSON.stringify(data.arguments, null, 2)) + '</pre>';
            bodyDiv.insertBefore(argsDiv, bodyDiv.firstChild);
        }

    } else if (data.status === 'running') {
        toolCallDiv.setAttribute('data-status', 'running');
        if (iconEl) { iconEl.textContent = '⚙️'; iconEl.style.animation = ''; }

        if (data.resultPartial != null) {
            var key = data.toolCallId;
            msgState.toolCallResultBuffer[key] = (msgState.toolCallResultBuffer[key] || '') + data.resultPartial;
            var resultDiv = bodyDiv.querySelector('.tool-call-result[data-partial-result]');
            if (!resultDiv) {
                resultDiv = document.createElement('div');
                resultDiv.className = 'tool-call-result';
                resultDiv.setAttribute('data-partial-result', data.toolCallId);
                resultDiv.innerHTML = '<div class="result-label">结果(运行中):</div><pre class="result-content"></pre>';
                bodyDiv.appendChild(resultDiv);
            }
            var preEl = resultDiv.querySelector('.result-content');
            preEl.textContent = msgState.toolCallResultBuffer[key];
            preEl.scrollTop = preEl.scrollHeight;
        }

    } else if (data.status === 'executed') {
        delete msgState.toolCallArgsBuffer[data.toolCallId];
        delete msgState.toolCallResultBuffer[data.toolCallId];

        var timerData = toolCallTimers[data.toolCallId];
        var elapsed = null;
        if (timerData) {
            clearInterval(timerData.intervalId);
            elapsed = ((Date.now() - timerData.startTime) / 1000).toFixed(1);
            delete toolCallTimers[data.toolCallId];
        }

        toolCallDiv.setAttribute('data-status', 'executed');
        if (statusEl) { statusEl.textContent = '执行完成' + (elapsed ? ' (' + elapsed + 's)' : ''); statusEl.classList.add('completed'); }
        if (iconEl) { iconEl.style.animation = 'none'; iconEl.textContent = '✅'; }

        if (data.result) {
            // Remove old partial-result if running already created one
            var oldResultDiv = bodyDiv.querySelector('.tool-call-result[data-partial-result]');
            if (oldResultDiv) oldResultDiv.remove();
            var resultDiv = document.createElement('div');
            resultDiv.className = 'tool-call-result';
            resultDiv.innerHTML = '<div class="result-label">结果:</div><pre class="result-content">' +
                escapeHtml(data.result) + '</pre>';
            bodyDiv.appendChild(resultDiv);
        }

        // Add toggle button if body has content
        if (bodyDiv.children.length > 0 && !toolCallDiv.querySelector('.tool-call-toggle')) {
            var toggleBtn = document.createElement('span');
            toggleBtn.className = 'tool-call-toggle';
            toggleBtn.textContent = '▼';
            statusEl.parentNode.appendChild(toggleBtn);

            bodyDiv.classList.remove('open');
            bodyDiv.classList.add('collapsed');
        }

        // Finalize message
        if (msgState.currentStreamingMessage) {
            var timeDiv = document.createElement('div');
            timeDiv.className = 'message-time';
            timeDiv.textContent = formatMessageTime(new Date());
            msgState.currentStreamingMessage.appendChild(timeDiv);
            msgState.currentStreamingMessage = null;
        }
    }

    if (data.status !== 'running') {
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    }
}

function showLoadingMessage() {
    if (loadingMessageDiv) return;
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.chat-header .agent-select'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();

    loadingMessageDiv = document.createElement('div');
    loadingMessageDiv.className = 'message agent loading-message';

    var label = document.createElement('div');
    label.className = 'message-label';
    label.textContent = agentName;
    loadingMessageDiv.appendChild(label);

    var loadingContent = document.createElement('div');
    loadingContent.className = 'message-content loading-content';
    loadingContent.innerHTML = '<span class="loading-dot"></span><span class="loading-dot"></span><span class="loading-dot"></span>';
    loadingMessageDiv.appendChild(loadingContent);

    messagesDiv.appendChild(loadingMessageDiv);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function removeLoadingMessage() {
    if (loadingMessageDiv) {
        loadingMessageDiv.remove();
        loadingMessageDiv = null;
    }
}

function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function handleThinking(data, responseId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.chat-header .agent-select'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();
    
    var msgState = streamingMessages[responseId];
    if (!msgState) {
        msgState = { currentStreamingMessage: null, streamingMarkdownContent: '', lastMessageType: null, thinkingContent: '', thinkingDiv: null };
        streamingMessages[responseId] = msgState;
    }
    
    if (data.status === 'partial') {
        if (!msgState.currentStreamingMessage || msgState.lastMessageType !== 'thinking') {
            msgState.thinkingContent = '';
            msgState.lastMessageType = 'thinking';
            
            msgState.currentStreamingMessage = document.createElement('div');
            msgState.currentStreamingMessage.className = 'message agent thinking-message';
            msgState.currentStreamingMessage.setAttribute('data-response-id', responseId);
            
            var label = document.createElement('div');
            label.className = 'message-label';
            label.textContent = agentName + ' (思考)';
            msgState.currentStreamingMessage.appendChild(label);
            
            var contentDiv = document.createElement('div');
            contentDiv.className = 'message-content thinking-content';
            msgState.currentStreamingMessage.appendChild(contentDiv);
            msgState.thinkingDiv = contentDiv;
            
            messagesDiv.appendChild(msgState.currentStreamingMessage);
        }
        
        msgState.thinkingContent += data.content;
        msgState.thinkingDiv.innerHTML = renderMarkdown(msgState.thinkingContent);
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    } else if (data.status === 'done') {
        msgState.thinkingContent += data.content;
        msgState.thinkingDiv.innerHTML = renderMarkdown(msgState.thinkingContent);
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
        if (msgState.currentStreamingMessage && msgState.lastMessageType === 'thinking') {
            var timeDiv = document.createElement('div');
            timeDiv.className = 'message-time';
            timeDiv.textContent = formatMessageTime(new Date());
            msgState.currentStreamingMessage.appendChild(timeDiv);
            msgState.currentStreamingMessage = null;
            msgState.thinkingContent = '';
            msgState.thinkingDiv = null;
            msgState.lastMessageType = null;
        }
    }
}

function handleStreamingChunk(content, responseId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.chat-header .agent-select'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();
    
    var msgState = streamingMessages[responseId];
    if (!msgState) {
        msgState = { currentStreamingMessage: null, streamingMarkdownContent: '', lastMessageType: null };
        streamingMessages[responseId] = msgState;
    }
    
    if (!msgState.currentStreamingMessage || msgState.lastMessageType !== 'text') {
        msgState.streamingMarkdownContent = '';
        msgState.lastMessageType = 'text';
        
        msgState.currentStreamingMessage = document.createElement('div');
        msgState.currentStreamingMessage.className = 'message agent';
        msgState.currentStreamingMessage.setAttribute('data-response-id', responseId);
        
        var label = document.createElement('div');
        label.className = 'message-label';
        label.textContent = agentName;
        msgState.currentStreamingMessage.appendChild(label);
        
        var contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        msgState.currentStreamingMessage.appendChild(contentDiv);
        
        messagesDiv.appendChild(msgState.currentStreamingMessage);
    }
    
    var contentDiv = msgState.currentStreamingMessage.querySelector('.message-content:last-of-type');
    if (!contentDiv) {
        contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        msgState.currentStreamingMessage.appendChild(contentDiv);
    }
    
    msgState.streamingMarkdownContent += content;
    
    try {
        if (typeof marked !== 'undefined') {
            var html = marked.parse(msgState.streamingMarkdownContent);
            contentDiv.innerHTML = html;
        } else {
            contentDiv.textContent = msgState.streamingMarkdownContent;
        }
    } catch (e) {
        contentDiv.textContent = msgState.streamingMarkdownContent;
    }
    
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function handleStreamingDone(userMessage, response, responseId) {
    var msgState = streamingMessages[responseId];
    if (!msgState || !msgState.currentStreamingMessage) {
        isStreaming = false;
        enableInput();
        return;
    }
    
    // var contentDiv = msgState.currentStreamingMessage.querySelector('.message-content:last-of-type');
    // if (contentDiv) {
    //     contentDiv.setAttribute('data-raw-content', msgState.streamingMarkdownContent);
    //
    //     try {
    //         if (typeof marked !== 'undefined') {
    //             contentDiv.innerHTML = marked.parse(msgState.streamingMarkdownContent);
    //         } else {
    //             contentDiv.textContent = msgState.streamingMarkdownContent;
    //         }
    //     } catch (e) {
    //         contentDiv.textContent = msgState.streamingMarkdownContent;
    //     }
    // }
    
    var timeDiv = document.createElement('div');
    timeDiv.className = 'message-time';
    timeDiv.textContent = formatMessageTime(new Date());
    msgState.currentStreamingMessage.appendChild(timeDiv);
    
    delete streamingMessages[responseId];
    isStreaming = false;
    enableInput();
}

function handleStreamingError(errorMessage, responseId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.chat-header .agent-select'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();

    var errorDiv = document.createElement('div');
    errorDiv.className = 'message agent error-message';
    if (responseId) {
        errorDiv.setAttribute('data-response-id', responseId);
    }

    var label = document.createElement('div');
    label.className = 'message-label';
    label.textContent = agentName + ' (错误)';
    errorDiv.appendChild(label);

    var contentDiv = document.createElement('div');
    contentDiv.className = 'message-content error-content';
    contentDiv.textContent = errorMessage;
    errorDiv.appendChild(contentDiv);

    var timeDiv = document.createElement('div');
    timeDiv.className = 'message-time';
    timeDiv.textContent = formatMessageTime(new Date());
    errorDiv.appendChild(timeDiv);

    messagesDiv.appendChild(errorDiv);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;

    isStreaming = false;
    enableInput();
}

function sendMessage() {
    var input = document.getElementById('messageInput');
    var message = input.value.trim();

    if (!message || !currentAgentId || isStreaming) {
        return;
    }

    fetch('/api/agent/' + currentAgentId + '/running')
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code === 200 && res.data === true) {
                showToast('智能体正在运行中，请先停止', 'warning');
                disableInput();
                return;
            }

            input.value = '';
            disableInput();
            isStreaming = true;

            var userMessageDiv = document.createElement('div');
            userMessageDiv.className = 'message user';

            var userLabel = document.createElement('div');
            userLabel.className = 'message-label';
            userLabel.textContent = '你';
            userMessageDiv.appendChild(userLabel);

            var userContent = document.createElement('div');
            userContent.textContent = message;
            userMessageDiv.appendChild(userContent);

            var userTime = document.createElement('div');
            userTime.className = 'message-time';
            userTime.textContent = formatMessageTime(new Date());
            userMessageDiv.appendChild(userTime);

            var messagesDiv = document.getElementById('chatMessages');
            messagesDiv.appendChild(userMessageDiv);
            messagesDiv.scrollTop = messagesDiv.scrollHeight;

            var payload = {
                agentId: currentAgentId.toString(),
                message: message
            };

            ws.send(JSON.stringify(payload));
        })
        .catch(function(err) {
            showToast('检查运行状态失败: ' + err.message, 'error');
        });
}

function disableInput() {
    var input = document.getElementById('messageInput');
    var sendBtn = document.getElementById('sendBtn');
    var runningBtn = document.getElementById('runningBtn');
    if (input) input.disabled = true;
    if (sendBtn) sendBtn.classList.add('hide');
    if (runningBtn) runningBtn.classList.remove('hide');
}

function enableInput() {
    var input = document.getElementById('messageInput');
    var sendBtn = document.getElementById('sendBtn');
    var runningBtn = document.getElementById('runningBtn');
    if (input) input.disabled = false;
    if (sendBtn) sendBtn.classList.remove('hide');
    if (runningBtn) runningBtn.classList.add('hide');
    if (input) input.focus();
}

function selectAgent(agentId) {
    window.location.href = '/?agentId=' + agentId;
}

function clearHistory(agentId) {
    showConfirm('确定要清空对话历史吗？').then(function(confirmed) {
        if (confirmed) {
            window.location.href = '/chat/clear?agentId=' + agentId;
        }
    });
}

function deleteAgent(agentId) {
    showConfirm('确定要删除这个 Agent 吗？').then(function(confirmed) {
        if (confirmed) {
         var form = document.createElement('form');
            form.method = 'POST';
            form.action = '/agent/delete';

            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'id';
            input.value = agentId;
            form.appendChild(input);

            document.body.appendChild(form);
            form.submit();
      }
    });
}

function loadProviders(providerSelect, modelSelect, selectedAiModelId) {
    fetch('/api/providers')
        .then(function(r) { return r.json(); })
        .then(function(providers) {
            providerSelect.innerHTML = '<option value="">选择提供商</option>';
            providers.forEach(function(p) {
                if (!p.apiKey || !p.url) return;
                var opt = document.createElement('option');
                opt.value = p.id;
                opt.textContent = p.name;
                providerSelect.appendChild(opt);
            });
            if (selectedAiModelId) {
                fetch('/api/models/' + selectedAiModelId)
                    .then(function(r) { return r.json(); })
                    .then(function(model) {
                        providerSelect.value = model.providerId;
                        var changeEvent = new Event('change');
                        providerSelect.dispatchEvent(changeEvent);
                        setTimeout(function() {
                            modelSelect.value = selectedAiModelId;
                        }, 100);
                    });
            }
        });
}

function setupCascading(providerSelect, modelSelect) {
    providerSelect.addEventListener('change', function() {
        var providerId = this.value;
        modelSelect.innerHTML = '<option value="">选择模型</option>';
        modelSelect.disabled = !providerId;
        if (!providerId) return;
        fetch('/api/providers/' + providerId + '/models')
            .then(function(r) { return r.json(); })
            .then(function(models) {
                models.forEach(function(m) {
                    var opt = document.createElement('option');
                    opt.value = m.id;
                    opt.textContent = m.modelName;
                    modelSelect.appendChild(opt);
                });
            });
    });
}

function selectAllTools(containerSelector) {
    document.querySelectorAll(containerSelector + ' input[type="checkbox"]').forEach(function(cb) {
        cb.checked = true;
    });
}

function deselectAllTools(containerSelector) {
    document.querySelectorAll(containerSelector + ' input[type="checkbox"]').forEach(function(cb) {
        cb.checked = false;
    });
}

function showAddModal() {
    Modal.open('addAgentModal');
    var providerSelect = document.getElementById('addProviderSelect');
    var modelSelect = document.getElementById('addModelSelect');
    modelSelect.disabled = true;
    loadProviders(providerSelect, modelSelect, null);
}

function hideAddModal() {
    Modal.close('addAgentModal');
}

function showEditModal(id, name, description, tools, maxMemoryRecords, maxToolInvocations, aiModelId, enableThinking, vectorToolSearch, vectorToolSearchMaxResults) {
    // 以深度思考按钮的实时状态为准，而非页面加载时的固化值
    var deepBtn = document.getElementById('deepThinkBtn');
    if (deepBtn) {
        enableThinking = deepBtn.getAttribute('data-enabled') === 'true';
    }
    document.getElementById('editAgentId').value = id;
    document.getElementById('editAgentName').value = name;
    document.getElementById('editAgentDescription').value = description;
    document.getElementById('editMaxMemoryRecords').value = maxMemoryRecords || 20;
    document.getElementById('editMaxToolInvocations').value = maxToolInvocations || 10;
    var editEnableCb = document.getElementById('editEnableThinkingCheckbox');
    editEnableCb.checked = enableThinking !== false;
    document.getElementById('editEnableThinking').value = editEnableCb.checked ? 'true' : 'false';

    var editVectorCb = document.getElementById('editVectorToolSearchCheckbox');
    editVectorCb.checked = vectorToolSearch !== false;
    document.getElementById('editVectorToolSearch').value = editVectorCb.checked ? 'true' : 'false';
    document.getElementById('editVectorToolSearchMaxResults').value = vectorToolSearchMaxResults || 5;
    document.getElementById('editVectorToolSearchResultsGroup').style.display = editVectorCb.checked ? '' : 'none';

    var checkboxes = document.querySelectorAll('.edit-tool-checkbox');
    checkboxes.forEach(function(checkbox) {
        checkbox.checked = tools && tools.indexOf(checkbox.value) !== -1;
    });

    var providerSelect = document.getElementById('editProviderSelect');
    var modelSelect = document.getElementById('editModelSelect');
    modelSelect.disabled = true;
    loadProviders(providerSelect, modelSelect, aiModelId);

    Modal.open('editAgentModal');
}

/**
 * 显示停止智能体确认对话框
 */
function hideEditModal() {
    Modal.close('editAgentModal');
}

function stopAgent() {
    showConfirm('确定要停止智能体运行吗？').then(function(confirmed) {
        if (!confirmed) return;
        var agentId = document.querySelector('input[name="agentId"]').value;
        fetch('/agent/stop', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: 'id=' + agentId
        }).then(function(response) {
            return response.json();
        }).then(function(res) {
            if (res.code === 200) {
                enableInput();
            } else {
                showToast(res.msg || '停止失败', 'warning');
                enableInput();
            }
        }).catch(function(err) {
            showToast('请求失败: ' + err.message, 'error');
            enableInput();
        });
    });
}

window.onload = function() {

    document.getElementById('addAgentModal').addEventListener('click', function(e) {
        if (e.target === this) {
            hideAddModal();
        }
    });

    document.getElementById('editAgentModal').addEventListener('click', function(e) {
        if (e.target === this) {
            hideEditModal();
        }
    });

    setupCascading(document.getElementById('addProviderSelect'), document.getElementById('addModelSelect'));
    setupCascading(document.getElementById('editProviderSelect'), document.getElementById('editModelSelect'));


    var messagesDiv = document.getElementById('chatMessages');
    if (messagesDiv) {
        renderAllMessages();
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    }
    var input = document.getElementById('messageInput');
    if (input) {
        input.focus();
    }

    if (currentAgentId) {
        connectWebSocket(currentAgentId);
    }
    
    var form = document.getElementById('chatForm');
    if (form) {
        form.addEventListener('submit', function(e) {
            e.preventDefault();
            sendMessage();
        });
    }
    
    if (input) {
        input.addEventListener('keypress', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }

    var deepBtn = document.getElementById('deepThinkBtn');
    if (deepBtn) {
        deepBtn.addEventListener('click', function() {
            var agentId = this.getAttribute('data-agent-id');
            var current = this.getAttribute('data-enabled') === 'true';
            var newEnabled = !current;
            fetch('/api/agents/' + agentId + '/thinking', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled: newEnabled })
            })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.msg === 'success') {
                    deepBtn.setAttribute('data-enabled', newEnabled);
                    deepBtn.classList.toggle('active', newEnabled);
                }
            });
        });
    }

    // Event delegation: toggle collapsible tool-call-body on ▼ click
    document.addEventListener('click', function(e) {
        var toggle = e.target.closest('.tool-call-toggle');
        if (toggle) {
            var toolCall = toggle.closest('.tool-call');
            if (toolCall) {
                var body = toolCall.querySelector('.tool-call-body');
                if (body) {
                    body.classList.toggle('collapsed');
                    body.classList.toggle('open');
                    toggle.classList.toggle('open');
                }
            }
        }
    });
};
