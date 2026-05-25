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
    var messageContents = document.querySelectorAll('.message-content[data-is-agent="true"], .thinking-content');
    messageContents.forEach(function(el) {
        var textContent = el.getAttribute('data-raw-content') || el.textContent;
        el.innerHTML = renderMarkdown(textContent);
    });
}

function setCurrentAgentId(agentId) {
    currentAgentId = agentId;
}
var lastDataType='';
function connectWebSocket(agentId) {
    var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    var wsUrl = protocol + '//' + window.location.host + '/ws/chat/' + agentId;

    ws = new WebSocket(wsUrl);

    ws.onopen = function() {
        console.log('WebSocket 连接已建立, agentId:', agentId);
    };
    
    ws.onmessage = function(event) {
        var data = JSON.parse(event.data);
        var requestId = data.requestId;

        if (data.type !== 'received') {
            removeLoadingMessage();
        }

        if (data.type === 'received') {
            showLoadingMessage();
        } else if (data.type === 'chunk') {
            handleStreamingChunk(data.content, requestId);
        } else if (data.type === 'tool_call') {
            handleToolCall(data, requestId);
        } else if (data.type === 'thinking') {
            handleThinking(data, requestId);
        } else if (data.type === 'done') {
            handleStreamingDone(data.message, data.response, requestId);
        } else if (data.type === 'task-done') {
            loadTokenUsage(lastTokenId || undefined);
        } else if (data.type === 'error') {
            handleStreamingError(data.content || data.message, requestId);
        }
        if(lastDataType!=data.type){
            lastDataType=data.type;
            loadTokenUsage(lastTokenId || undefined);
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

function handleToolCall(data, requestId) {
    var messagesDiv = document.getElementById('chatMessages');
    var toolExecList = document.getElementById('toolExecList');

    var msgState = streamingMessages[requestId];
    if (!msgState) {
        msgState = { currentStreamingMessage: null, streamingMarkdownContent: '', lastMessageType: null, toolCallArgsBuffer: {}, toolCallResultBuffer: {} };
        streamingMessages[requestId] = msgState;
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

        var toolCallContainer = document.createElement('div');
        toolCallContainer.className = 'tool-call-container';

        toolCallDiv = document.createElement('div');
        toolCallDiv.className = 'tool-call';
        toolCallDiv.setAttribute('data-tool-call-id', data.toolCallId);
        toolCallContainer.appendChild(toolCallDiv);

        var toolCallHeader = document.createElement('div');
        toolCallHeader.className = 'tool-call-header';

        var toolIcon = document.createElement('span');
        toolIcon.className = 'tool-call-icon';
        toolIcon.textContent = '⚙';
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

        toolExecList.appendChild(toolCallContainer);
        toolExecList.scrollTop = toolExecList.scrollHeight;
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
        if (iconEl) { iconEl.textContent = '⚙'; iconEl.style.animation = ''; }

        // 启动计时器
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

    } else if (data.status === 'stoppable') {
        // Show stop button
        if (!toolCallDiv.querySelector('.tool-call-stop-btn')) {
            var stopBtn = document.createElement('button');
            stopBtn.className = 'tool-call-stop-btn';
            stopBtn.title = '停止工具';
            stopBtn.innerHTML = '<svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>';
            stopBtn.onclick = function(e) {
                e.stopPropagation();
                e.preventDefault();
                fetch('/agent/tool/stop', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: 'agentId=' + currentAgentId + '&callId=' + data.toolCallId
                });
            };
            var headerEl = toolCallDiv.querySelector('.tool-call-header');
            if (headerEl) {
                var nameEl = headerEl.querySelector('.tool-call-name');
                if (nameEl) nameEl.parentNode.insertBefore(stopBtn, nameEl.nextSibling);
                else headerEl.appendChild(stopBtn);
            }
        }

    } else if (data.status === 'executed') {
        delete msgState.toolCallArgsBuffer[data.toolCallId];
        delete msgState.toolCallResultBuffer[data.toolCallId];

        // Remove stop button
        var stopBtn = toolCallDiv.querySelector('.tool-call-stop-btn');
        if (stopBtn) stopBtn.remove();

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
    if (toolExecList) toolExecList.scrollTop = toolExecList.scrollHeight;
}

function showLoadingMessage() {
    if (loadingMessageDiv) return;
    var agentName = (function(){ var s = document.querySelector('.chat-header .agent-select'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();
    var messagesDiv = document.getElementById('chatMessages');

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

function handleThinking(data, requestId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.chat-header .agent-select'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();
    
    var msgState = streamingMessages[requestId];
    if (!msgState) {
        msgState = { currentStreamingMessage: null, streamingMarkdownContent: '', lastMessageType: null, thinkingContent: '', thinkingDiv: null };
        streamingMessages[requestId] = msgState;
    }
    
    if (data.status === 'partial') {
        if (!msgState.currentStreamingMessage || msgState.lastMessageType !== 'thinking') {
            msgState.thinkingContent = '';
            msgState.lastMessageType = 'thinking';
            
            msgState.currentStreamingMessage = document.createElement('div');
            msgState.currentStreamingMessage.className = 'message agent thinking-message';
            msgState.currentStreamingMessage.setAttribute('data-request-id', requestId);
            
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

function handleStreamingChunk(content, requestId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.chat-header .agent-select'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();
    
    var msgState = streamingMessages[requestId];
    if (!msgState) {
        msgState = { currentStreamingMessage: null, streamingMarkdownContent: '', lastMessageType: null };
        streamingMessages[requestId] = msgState;
    }
    
    if (!msgState.currentStreamingMessage || msgState.lastMessageType !== 'text') {
        msgState.streamingMarkdownContent = '';
        msgState.lastMessageType = 'text';
        
        msgState.currentStreamingMessage = document.createElement('div');
        msgState.currentStreamingMessage.className = 'message agent';
        msgState.currentStreamingMessage.setAttribute('data-request-id', requestId);
        
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

function handleStreamingDone(userMessage, response, requestId) {
    var agentName = (function(){ var s = document.querySelector('.chat-header .agent-select'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();
    var msgState = streamingMessages[requestId];
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
    
    delete streamingMessages[requestId];
    isStreaming = false;
    enableInput();
}

function handleStreamingError(errorMessage, requestId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.chat-header .agent-select'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();

    var errorDiv = document.createElement('div');
    errorDiv.className = 'message agent error-message';
    if (requestId) {
        errorDiv.setAttribute('data-request-id', requestId);
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
                message: message,
                skills: getSelectedSkills()
            };

            ws.send(JSON.stringify(payload));
        })
        .catch(function(err) {
            showToast('检查运行状态失败: ' + err.message, 'error');
        });
}

function disableInput() {
    var wrapper = document.querySelector('.chat-input-wrapper');
    var sendBtn = document.getElementById('sendBtn');
    var runningBtn = document.getElementById('runningBtn');
    if (wrapper) wrapper.classList.add('disabled');
    if (sendBtn) sendBtn.classList.add('hide');
    if (runningBtn) runningBtn.classList.remove('hide');
}

function enableInput() {
    var wrapper = document.querySelector('.chat-input-wrapper');
    var input = document.getElementById('messageInput');
    var sendBtn = document.getElementById('sendBtn');
    var runningBtn = document.getElementById('runningBtn');
    if (wrapper) wrapper.classList.remove('disabled');
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

function forceStopAgent(agentId) {
    showConfirm('确定要强停智能体吗？强停后将移除执行器并刷新页面。').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/agent/force-stop', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'id=' + agentId
        }).then(function(r) { return r.json(); }).then(function(res) {
            if (res.code === 200) {
                window.location.href = '/?agentId=' + agentId;
            } else {
                showToast(res.msg || '强停失败', 'warning');
            }
        });
    });
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


var lastTokenId = 0;
var tokenChartData = [];
var tokenChart = null;

function loadTokenUsage(minId) {
    if (!currentAgentId) return;
    var url = '/api/token-usage/today?agentId=' + currentAgentId+"&source=chat";
    if (minId) url += '&minId=' + minId;
    fetch(url).then(function(r) { return r.json(); }).then(function(res) {
        if (res.code === 200 && res.data) {
            if (minId && res.data.length > 0) {
                // Remove oldest N items, append new N items (sliding window)
                var newCount = res.data.length;
                tokenChartData = tokenChartData.slice(newCount).concat(res.data.reverse());
            } else if (!minId) {
                tokenChartData = res.data.reverse();
            }
            if (tokenChartData.length > 0) {
                lastTokenId = tokenChartData[tokenChartData.length - 1].id;
            }
            renderTokenChart(tokenChartData);
        }
    });

    // Fetch daily summary stats
    var now = new Date();
    var pad = function(n) { return String(n).padStart(2, '0'); };
    var startStr = now.getFullYear() + '-' + pad(now.getMonth()+1) + '-' + pad(now.getDate()) + ' 00:00:00';
    var endStr = now.getFullYear() + '-' + pad(now.getMonth()+1) + '-' + pad(now.getDate()) + ' 23:59:59';
    var statsUrl = '/api/token-usage/daily-stats?startTime=' + encodeURIComponent(startStr) + '&endTime=' + encodeURIComponent(endStr) + '&agentId=' + currentAgentId+'&source=chat';
    fetch(statsUrl).then(function(r) { return r.json(); }).then(function(sres) {
        if (sres.code === 200 && sres.data && sres.data.length > 0) {
            var today = sres.data[0];
            updateTokenTitle(today.inputTokens || 0, today.outputTokens || 0, today.totalTokens || 0);
        }
    });
}

function renderTokenChart(data) {
    var container = document.getElementById('tokenUsage');
    if (!container) return;

    if (!data || data.length === 0) {
        if (tokenChart) {
            tokenChart.destroy();
            tokenChart = null;
        }
        container.innerHTML = '<div style="padding:14px;text-align:center;color:#999;font-size:12px;">今日暂无用量</div>';
        return;
    }

    var labels = data.map(function(d) {
        return d.createTime ? d.createTime.substring(11, 16) : '';
    });
    var inputData = data.map(function(d) { return d.inputTokens || 0; });
    var outputData = data.map(function(d) { return d.outputTokens || 0; });

    if (tokenChart) {
        tokenChart.data.labels = labels;
        tokenChart.data.datasets[0].data = inputData;
        tokenChart.data.datasets[1].data = outputData;
        tokenChart.update('active');
    } else {
        container.innerHTML = '<canvas id="tokenUsageChart"></canvas>';
        var canvas = document.getElementById('tokenUsageChart');
        var isDark = document.body.classList.contains('dark-theme');

        tokenChart = new Chart(canvas, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: '输入',
                        data: inputData,
                        backgroundColor: '#2196F3',
                        borderRadius: 3
                    },
                    {
                        label: '输出',
                        data: outputData,
                        backgroundColor: '#4CAF50',
                        borderRadius: 3
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: {
                    duration: 600,
                    easing: 'easeOutQuart'
                },
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        callbacks: {
                            label: function(ctx) {
                                return ctx.dataset.label + ': ' + ctx.raw.toLocaleString();
                            },
                            footer: function(items) {
                                var total = items.reduce(function(sum, item) { return sum + item.raw; }, 0);
                                return '总量: ' + total.toLocaleString();
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        stacked: true,
                        grid: { display: false },
                        ticks: {
                            font: { size: 9 },
                            color: isDark ? '#888' : '#999',
                            maxRotation: 45
                        }
                    },
                    y: {
                        stacked: true,
                        beginAtZero: true,
                        ticks: {
                            font: { size: 9 },
                            color: isDark ? '#888' : '#999',
                            callback: function(v) {
                                return v >= 1000 ? (v / 1000).toFixed(0) + 'k' : v;
                            }
                        },
                        grid: { color: isDark ? '#2d2d44' : '#f0f0f0' }
                    }
                },
                interaction: {
                    intersect: false,
                    mode: 'index'
                }
            }
        });
    }

    var titleEl = document.getElementById('tokenLastStats');
    if (titleEl && data.length > 0) {
        var last = data[data.length - 1];
        titleEl.innerHTML = '最新:<span style="color:#2196F3;">↑ ' + formatTokenCount(last.inputTokens || 0) + '</span>'
            + ' <span style="color:#4CAF50;">↓ ' + formatTokenCount(last.outputTokens || 0) + '</span>';
    }
}

function updateTokenTitle(input, output, total) {
    var el = document.getElementById('tokenDailyStats');
    if (el) {
        el.innerHTML = '今日:<span style="color:#2196F3;">↑ ' + formatTokenCount(input) + '</span>'
            + ' <span style="color:#4CAF50;">↓ ' + formatTokenCount(output) + '</span>'
            + ' <span style="color:#666;margin-left:4px;">| 总 ' + formatTokenCount(total) + '</span>';
    }
}


function formatTokenCount(n) {
    if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
    return n.toString();
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
        if (toolExecList) toolExecList.scrollTop = toolExecList.scrollHeight;
    }
    var input = document.getElementById('messageInput');
    if (input) {
        input.focus();
    }

    if (currentAgentId) {
        connectWebSocket(currentAgentId);
        loadTokenUsage();
    }

    loadChatSkills();

    var skillBtn = document.getElementById('skillSelectBtn');
    if (skillBtn) {
        skillBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            var menu = document.getElementById('skillsDropdownMenu');
            menu.classList.toggle('open');
        });
    }

    document.addEventListener('click', function(e) {
        var dropdown = document.getElementById('skillsDropdown');
        if (dropdown && !dropdown.contains(e.target)) {
            var menu = document.getElementById('skillsDropdownMenu');
            if (menu) menu.classList.remove('open');
        }
    });
    
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

function loadChatSkills() {
    fetch('/skills/api/list')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code !== 200 || !resp.data) return;
            var list = document.getElementById('skillsCheckboxList');
            if (!list) return;
            if (resp.data.length === 0) {
                list.innerHTML = '<div class="skills-dropdown-empty">暂无可用技能</div>';
                return;
            }
            var html = '';
            resp.data.forEach(function(s) {
                var name = s.name || s.folderName;
                html += '<label class="skill-checkbox-item">' +
                    '<input type="checkbox" value="' + escapeHtml(name) + '" data-folder="' + escapeHtml(s.folderName || '') + '">' +
                    '<span class="skill-checkbox-item-name">' + escapeHtml(name) + '</span>' +
                    '</label>';
            });
            list.innerHTML = html;

            var checkboxes = list.querySelectorAll('input[type="checkbox"]');
            checkboxes.forEach(function(cb) {
                cb.addEventListener('change', updateSkillBtnState);
            });
        });
}

function updateSkillBtnState() {
    var selected = getSelectedSkills();
    var btn = document.getElementById('skillSelectBtn');
    if (!btn) return;
    if (selected.length > 0) {
        btn.classList.add('has-selected');
        btn.textContent = '';
        var svg = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>';
        btn.innerHTML = svg + ' 技能 (' + selected.length + ')';
    } else {
        btn.classList.remove('has-selected');
        var svg = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>';
        btn.innerHTML = svg + ' 技能';
    }
}

function getSelectedSkills() {
    var list = document.getElementById('skillsCheckboxList');
    if (!list) return [];
    var checkboxes = list.querySelectorAll('input[type="checkbox"]:checked');
    var names = [];
    checkboxes.forEach(function(cb) {
        names.push(cb.getAttribute('data-folder'));
    });
    return names;
}
