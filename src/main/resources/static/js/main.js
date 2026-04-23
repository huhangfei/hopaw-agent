var currentAgentId = null;
var ws = null;
var isStreaming = false;
var currentStreamingMessage = null;
var streamingMarkdownContent = '';
var lastMessageType = null;

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

function connectWebSocket() {
    var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    var wsUrl = protocol + '//' + window.location.host + '/ws/chat';
    
    ws = new WebSocket(wsUrl);
    
    ws.onopen = function() {
        console.log('WebSocket 连接已建立');
    };
    
    ws.onmessage = function(event) {
        var data = JSON.parse(event.data);
        
        if (data.type === 'chunk') {
            handleStreamingChunk(data.content);
        } else if (data.type === 'tool_call') {
            handleToolCall(data);
        } else if (data.type === 'done') {
            handleStreamingDone(data.message, data.response);
        } else if (data.type === 'error') {
            handleStreamingError(data.message);
        }
    };
    
    ws.onclose = function() {
        console.log('WebSocket 连接已关闭');
        setTimeout(function() {
            connectWebSocket();
        }, 3000);
    };
    
    ws.onerror = function(error) {
        console.error('WebSocket 错误:', error);
    };
}

function handleToolCall(data) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = document.querySelector('.chat-header h2') ? document.querySelector('.chat-header h2').textContent : 'Agent';
    
    if (data.status === 'starting') {
        streamingMarkdownContent = '';
        lastMessageType = 'tool_call';
        
        currentStreamingMessage = document.createElement('div');
        currentStreamingMessage.className = 'message agent tool-call-message';
        
        var label = document.createElement('div');
        label.className = 'message-label';
        label.textContent = agentName;
        currentStreamingMessage.appendChild(label);
        
        var toolCallContainer = document.createElement('div');
        toolCallContainer.className = 'tool-call-container';
        currentStreamingMessage.appendChild(toolCallContainer);
        
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
        
        if (data.arguments) {
            var argsDiv = document.createElement('div');
            argsDiv.className = 'tool-call-args';
            argsDiv.innerHTML = '<div class="args-label">参数:</div><pre class="args-content">' + 
                escapeHtml(JSON.stringify(data.arguments, null, 2)) + '</pre>';
            toolCallDiv.appendChild(argsDiv);
        }
        
        toolCallContainer.appendChild(toolCallDiv);
        messagesDiv.appendChild(currentStreamingMessage);
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
            
            if (data.result) {
                var resultDiv = document.createElement('div');
                resultDiv.className = 'tool-call-result';
                resultDiv.innerHTML = '<div class="result-label">结果:</div><pre class="result-content">' + 
                    escapeHtml(data.result) + '</pre>';
                toolCallDiv.appendChild(resultDiv);
            }
        }
        
        if (currentStreamingMessage) {
            var timeDiv = document.createElement('div');
            timeDiv.className = 'message-time';
            timeDiv.textContent = formatMessageTime(new Date());
            currentStreamingMessage.appendChild(timeDiv);
            currentStreamingMessage = null;
        }
        
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    }
}

function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function handleStreamingChunk(content) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = document.querySelector('.chat-header h2') ? document.querySelector('.chat-header h2').textContent : 'Agent';
    
    if (!currentStreamingMessage || lastMessageType !== 'text') {
        streamingMarkdownContent = '';
        lastMessageType = 'text';
        
        currentStreamingMessage = document.createElement('div');
        currentStreamingMessage.className = 'message agent';
        
        var label = document.createElement('div');
        label.className = 'message-label';
        label.textContent = agentName;
        currentStreamingMessage.appendChild(label);
        
        var contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        currentStreamingMessage.appendChild(contentDiv);
        
        messagesDiv.appendChild(currentStreamingMessage);
    }
    
    var contentDiv = currentStreamingMessage.querySelector('.message-content:last-of-type');
    if (!contentDiv) {
        contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        currentStreamingMessage.appendChild(contentDiv);
    }
    
    streamingMarkdownContent += content;
    
    try {
        if (typeof marked !== 'undefined') {
            var html = marked.parse(streamingMarkdownContent);
            contentDiv.innerHTML = html;
        } else {
            contentDiv.textContent = streamingMarkdownContent;
        }
    } catch (e) {
        contentDiv.textContent = streamingMarkdownContent;
    }
    
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function handleStreamingDone(userMessage, response) {
    if (currentStreamingMessage) {
        var contentDiv = currentStreamingMessage.querySelector('.message-content:last-of-type');
        if (contentDiv) {
            contentDiv.setAttribute('data-raw-content', streamingMarkdownContent);
            
            try {
                if (typeof marked !== 'undefined') {
                    contentDiv.innerHTML = marked.parse(streamingMarkdownContent);
                } else {
                    contentDiv.textContent = streamingMarkdownContent;
                }
            } catch (e) {
                contentDiv.textContent = streamingMarkdownContent;
            }
        }
        
        var timeDiv = document.createElement('div');
        timeDiv.className = 'message-time';
        timeDiv.textContent = formatMessageTime(new Date());
        currentStreamingMessage.appendChild(timeDiv);
        
        currentStreamingMessage = null;
        streamingMarkdownContent = '';
        lastMessageType = null;
    }
    
    isStreaming = false;
    enableInput();
}

function handleStreamingError(errorMessage) {
    if (currentStreamingMessage) {
        var contentDiv = currentStreamingMessage.querySelector('.streaming-content');
        contentDiv.textContent += '\n(错误: ' + errorMessage + ')';
        currentStreamingMessage = null;
    }
    
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
    var button = document.getElementById('sendBtn');
    if (input) input.disabled = true;
    if (button) button.disabled = true;
}

function enableInput() {
    var input = document.getElementById('messageInput');
    var button = document.getElementById('sendBtn');
    if (input) input.disabled = false;
    if (button) button.disabled = false;
    if (input) input.focus();
}

function selectAgent(agentId) {
    window.location.href = '/?agentId=' + agentId;
}

function clearHistory(agentId) {
    if (confirm('确定要清空对话历史吗？')) {
        window.location.href = '/chat/clear?agentId=' + agentId;
    }
}

function deleteAgent(agentId) {
    if (!confirm('确定要删除这个 Agent 吗？')) {
        return;
    }
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

function showAddModal() {
    document.getElementById('addAgentModal').classList.add('active');
}

function hideAddModal() {
    document.getElementById('addAgentModal').classList.remove('active');
}

function showEditModal(id, name, description, tools) {
    document.getElementById('editAgentId').value = id;
    document.getElementById('editAgentName').value = name;
    document.getElementById('editAgentDescription').value = description;
    
    var checkboxes = document.querySelectorAll('.edit-tool-checkbox');
    checkboxes.forEach(function(checkbox) {
        checkbox.checked = tools && tools.indexOf(checkbox.value) !== -1;
    });
    
    document.getElementById('editAgentModal').classList.add('active');
    
    event.stopPropagation();
}

function hideEditModal() {
    document.getElementById('editAgentModal').classList.remove('active');
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
        connectWebSocket();
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
};
