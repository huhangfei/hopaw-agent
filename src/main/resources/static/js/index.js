var currentAgentId = null;
var ws = null;
var isStreaming = false;
var currentStreamingMessage = null;
var streamingMarkdownContent = '';
var lastMessageType = null;
var streamingMessages = {};

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

if (typeof marked !== 'undefined') {
    marked.setOptions({
        breaks: true,
        gfm: true
    });
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
        
        if (data.type === 'chunk') {
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
    var agentName = document.querySelector('.chat-header h2') ? document.querySelector('.chat-header h2').textContent : 'Agent';
    
    var msgState = streamingMessages[responseId];
    if (!msgState) {
        msgState = { currentStreamingMessage: null, streamingMarkdownContent: '', lastMessageType: null };
        streamingMessages[responseId] = msgState;
    }
    
    if (data.status === 'starting') {
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

        var toolCallDiv = document.createElement('div');
        toolCallDiv.className = 'tool-call';
        toolCallDiv.setAttribute('data-tool-call-id', data.toolCallId);
        toolCallDiv.setAttribute('data-status', 'starting');

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
        toolCallStatus.textContent = '执行中...';
        toolCallHeader.appendChild(toolCallStatus);

        toolCallDiv.appendChild(toolCallHeader);

        // Create collapsible body wrapper (open during execution)
        var bodyDiv = document.createElement('div');
        bodyDiv.className = 'tool-call-body open';
        toolCallDiv.appendChild(bodyDiv);

        if (data.arguments) {
            var argsDiv = document.createElement('div');
            argsDiv.className = 'tool-call-args';
            argsDiv.innerHTML = '<div class="args-label">参数:</div><pre class="args-content">' +
                escapeHtml(JSON.stringify(data.arguments, null, 2)) + '</pre>';
            bodyDiv.appendChild(argsDiv);
        }

        toolCallContainer.appendChild(toolCallDiv);
        messagesDiv.appendChild(msgState.currentStreamingMessage);
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    } else if (data.status === 'executed') {
        var toolCallDiv = document.querySelector('.tool-call[data-tool-call-id="' + data.toolCallId + '"]');
        if (toolCallDiv) {
            toolCallDiv.setAttribute('data-status', 'executed');

            var statusEl = toolCallDiv.querySelector('.tool-call-status');
            if (statusEl) {
                statusEl.textContent = '执行完成';
                statusEl.classList.add('completed');
            }

            var iconEl = toolCallDiv.querySelector('.tool-call-icon');
            if (iconEl) {
                iconEl.style.animation = 'none';
                iconEl.textContent = '✅';
            }

            // Find or create collapsible body
            var bodyDiv = toolCallDiv.querySelector('.tool-call-body');
            if (!bodyDiv) {
                bodyDiv = document.createElement('div');
                bodyDiv.className = 'tool-call-body';
                toolCallDiv.appendChild(bodyDiv);
            }

            if (data.result) {
                var resultDiv = document.createElement('div');
                resultDiv.className = 'tool-call-result';
                resultDiv.innerHTML = '<div class="result-label">结果:</div><pre class="result-content">' +
                    escapeHtml(data.result) + '</pre>';
                bodyDiv.appendChild(resultDiv);
            }

            // Add toggle button if body has any content
            if (bodyDiv.children.length > 0) {
                var toggleBtn = document.createElement('span');
                toggleBtn.className = 'tool-call-toggle';
                toggleBtn.textContent = '▼';
                statusEl.parentNode.appendChild(toggleBtn);

                // Collapse body by default after execution
                bodyDiv.classList.remove('open');
                bodyDiv.classList.add('collapsed');
            }
        }

        if (msgState.currentStreamingMessage) {
            var timeDiv = document.createElement('div');
            timeDiv.className = 'message-time';
            timeDiv.textContent = formatMessageTime(new Date());
            msgState.currentStreamingMessage.appendChild(timeDiv);
            msgState.currentStreamingMessage = null;
        }

        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    }
}

function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function handleThinking(data, responseId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = document.querySelector('.chat-header h2') ? document.querySelector('.chat-header h2').textContent : 'Agent';
    
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
    var agentName = document.querySelector('.chat-header h2') ? document.querySelector('.chat-header h2').textContent : 'Agent';
    
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
    var agentName = document.querySelector('.chat-header h2') ? document.querySelector('.chat-header h2').textContent : 'Agent';

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

function showEditModal(id, name, description, tools, maxMemoryRecords, maxToolInvocations, aiModelId, enableThinking) {
    document.getElementById('editAgentId').value = id;
    document.getElementById('editAgentName').value = name;
    document.getElementById('editAgentDescription').value = description;
    document.getElementById('editMaxMemoryRecords').value = maxMemoryRecords || 20;
    document.getElementById('editMaxToolInvocations').value = maxToolInvocations || 10;
    var editEnableCb = document.getElementById('editEnableThinkingCheckbox');
    editEnableCb.checked = enableThinking !== false;
    document.getElementById('editEnableThinking').value = editEnableCb.checked ? 'true' : 'false';

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
            return response.text();
        }).then(function(data) {
            if (data === 'ok') {
                //location.reload();
            }
        });
    });
}

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

window.onload = function() {
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
