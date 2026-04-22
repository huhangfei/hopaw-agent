var currentAgentId = null;
var ws = null;
var isStreaming = false;
var currentStreamingMessage = null;

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

function handleStreamingChunk(content) {
    if (!currentStreamingMessage) {
        currentStreamingMessage = document.createElement('div');
        currentStreamingMessage.className = 'message agent';
        
        var label = document.createElement('div');
        label.className = 'message-label';
        label.textContent = document.querySelector('.chat-header h2') ? document.querySelector('.chat-header h2').textContent : 'Agent';
        currentStreamingMessage.appendChild(label);
        
        var contentDiv = document.createElement('div');
        contentDiv.className = 'streaming-content';
        currentStreamingMessage.appendChild(contentDiv);
        
        var messagesDiv = document.getElementById('chatMessages');
        messagesDiv.appendChild(currentStreamingMessage);
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    }
    
    var contentDiv = currentStreamingMessage.querySelector('.streaming-content');
    contentDiv.textContent += content;
    
    var messagesDiv = document.getElementById('chatMessages');
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function handleStreamingDone(userMessage, response) {
    if (currentStreamingMessage) {
        var timeDiv = document.createElement('div');
        timeDiv.className = 'message-time';
        var now = new Date();
        timeDiv.textContent = now.getHours().toString().padStart(2, '0') + ':' + 
                              now.getMinutes().toString().padStart(2, '0') + ':' + 
                              now.getSeconds().toString().padStart(2, '0');
        currentStreamingMessage.appendChild(timeDiv);
        
        currentStreamingMessage = null;
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
    var now = new Date();
    userTime.textContent = now.getHours().toString().padStart(2, '0') + ':' + 
                           now.getMinutes().toString().padStart(2, '0') + ':' + 
                           now.getSeconds().toString().padStart(2, '0');
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
