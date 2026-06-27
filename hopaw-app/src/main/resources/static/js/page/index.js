var currentAgentId = null;
var ws = null;
var currentStreamingMessage = null;
var streamingMarkdownContent = '';
var lastMessageType = null;
var streamingMessages = {};
var toolCallTimers = {};
var loadingMessageDiv = null;
var currentModelId = null;
var currentSessionId = null;
var currentToolCallPermission = 'smart_call';
var attachedFiles = []; // { url, type, name }

if (typeof marked !== 'undefined') {
    marked.setOptions({
        breaks: true,
        gfm: true
    });

    // 重写 del 规则：只匹配 ~~text~~（双波浪线），防止单个 ~ 被误解析为删除线
    marked.use({
        extensions: [{
            name: 'del',
            level: 'inline',
            start: function(src) { return src.indexOf('~~'); },
            tokenizer: function(src) {
                var match = src.match(/^~~([^\s~])((?:[^\\]|\\.)*?[^\s~])?~~(?=[^~]|$)/);
                if (match) {
                    return {
                        type: 'del',
                        raw: match[0],
                        tokens: this.lexer.inlineTokens(match[1] + (match[2] || ''))
                    };
                }
            },
            renderer: function(token) {
                return '<del>' + this.parser.parseInline(token.tokens) + '</del>';
            }
        }]
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

function createMessageFooter(messageText) {
    var footer = document.createElement('div');
    footer.className = 'message-footer';

    var timeDiv = document.createElement('div');
    timeDiv.className = 'message-time';
    timeDiv.textContent = formatMessageTime(new Date());
    footer.appendChild(timeDiv);

    var copyBtn = document.createElement('button');
    copyBtn.className = 'message-copy-btn';
    copyBtn.setAttribute('title', '复制消息');
    if (messageText) {
        copyBtn.setAttribute('data-content', messageText);
    }
    copyBtn.innerHTML = '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>';
    copyBtn.onclick = function() { copyMessageContent(this); };
    footer.appendChild(copyBtn);

    return footer;
}

function copyMessageContent(btn) {
    var content = btn.getAttribute('data-content');
    // fallback: 从父级 message 中查找 message-content
    if (!content) {
        var msgEl = btn.closest('.message');
        if (msgEl) {
            var contentEl = msgEl.querySelector('.message-content');
            if (contentEl) {
                content = contentEl.getAttribute('data-raw-content') || contentEl.textContent;
            }
        }
    }
    if (!content) return;

    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(content).then(function() {
            btn.classList.add('copied');
            setTimeout(function() { btn.classList.remove('copied'); }, 1500);
        }).catch(function() {
            fallbackCopy(btn, content);
        });
    } else {
        fallbackCopy(btn, content);
    }
}

function fallbackCopy(btn, content) {
    var textarea = document.createElement('textarea');
    textarea.value = content;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
        document.execCommand('copy');
        btn.classList.add('copied');
        setTimeout(function() { btn.classList.remove('copied'); }, 1500);
    } catch (e) {
        console.error('复制失败:', e);
    }
    document.body.removeChild(textarea);
}

function renderAllMessages() {
    var messageContents = document.querySelectorAll('.message-content[data-is-agent="true"], .thinking-content');
    messageContents.forEach(function(el) {
        var textContent = el.getAttribute('data-raw-content') || el.textContent;
        el.innerHTML = renderMarkdown(textContent);
    });
}

function setCurrentAgentId(agentId) {
    var previousAgentId = currentAgentId;
    currentAgentId = agentId;
    // 智能体切换：同步刷新虚拟人配置并重新连接虚拟人 WebSocket
    if (previousAgentId !== agentId && window.AvatarBridge) {
        try {
            if (typeof window.AvatarBridge.onAgentChanged === 'function') {
                window.AvatarBridge.onAgentChanged(agentId);
            }
        } catch (e) {
            console.warn('通知虚拟人智能体切换失败', e);
        }
    }
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
        var requestId = data.requestId;

        if (data.type !== 'received' && data.type !== 'session-title'&& data.type !== 'token_usage') {
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
        } else if (data.type === 'session-title') {
            updateSessionTitle(data.sessionId, data.content);
        } else if (data.type === 'task-done') {
            var msgState = streamingMessages[requestId];
            if (msgState && msgState.currentStreamingMessage) {
                msgState.currentStreamingMessage.appendChild(createMessageFooter());
                msgState.currentStreamingMessage = null;
            }
            enableInput();
        } else if (data.type === 'error') {
            handleStreamingError(data.content || data.message, requestId);
        }  else if (data.type === 'warn') {
            handleStreamingWarn(data.content || data.message, requestId);
        } else if (data.type === 'token_usage') {
            handleTokenUsageMessage(data);
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
        var toolNameValue=data.toolName || 'Unknown Tool';
        if(data.toolDescriptions && data.toolDescriptions.length > 0){
            toolNameValue =data.toolDescriptions[0] || toolNameValue;
        }
        toolName.textContent =toolNameValue;
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

    } else if (data.status === 'started') {
        delete msgState.toolCallArgsBuffer[data.toolCallId];

        toolCallDiv.setAttribute('data-status', 'started');
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
                fetch('/api/session/'+encodeURIComponent(data.sessionId || currentSessionId)+'/tool/stop'+ '&callId=' + data.toolCallId, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
                });
            };
            var headerEl = toolCallDiv.querySelector('.tool-call-header');
            if (headerEl) {
                var nameEl = headerEl.querySelector('.tool-call-name');
                if (nameEl) nameEl.parentNode.insertBefore(stopBtn, nameEl.nextSibling);
                else headerEl.appendChild(stopBtn);
            }
        }

    } else if (data.status === 'approval') {
        toolCallDiv.setAttribute('data-status', 'approval');
        if (statusEl) { statusEl.textContent = '等待审批'; statusEl.classList.remove('completed'); }
        if (iconEl) { iconEl.textContent = '⏳'; iconEl.style.animation = ''; }

        var existingFooter = toolCallDiv.querySelector('.tool-call-footer');
        if (!existingFooter) {
            var footerDiv = document.createElement('div');
            footerDiv.className = 'tool-call-footer';

            var footerText = document.createElement('span');
            footerText.className = 'tool-call-footer-text';
            footerText.textContent = '⚠️ 此工具调用需要审批';
            footerDiv.appendChild(footerText);

            var btnGroup = document.createElement('div');
            btnGroup.className = 'tool-call-footer-btns';

            var approveBtn = document.createElement('button');
            approveBtn.className = 'tool-call-approve-btn';
            approveBtn.textContent = '通过';
            approveBtn.onclick = function(e) {
                e.stopPropagation();
                e.preventDefault();
                approveBtn.disabled = true;
                rejectBtn.disabled = true;
                fetch('/api/session/'+ encodeURIComponent(data.sessionId || currentSessionId)+'/tool/approval'+ '&callId=' + encodeURIComponent(data.toolCallId) + '&allowed=true', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                }).then(function() {
                    footerDiv.remove();
                }).catch(function() {
                    approveBtn.disabled = false;
                    rejectBtn.disabled = false;
                });
            };

            var rejectBtn = document.createElement('button');
            rejectBtn.className = 'tool-call-reject-btn';
            rejectBtn.textContent = '拒绝';
            rejectBtn.onclick = function(e) {
                e.stopPropagation();
                e.preventDefault();
                approveBtn.disabled = true;
                rejectBtn.disabled = true;
                fetch('/api/session/'+ encodeURIComponent(data.sessionId || currentSessionId)+'/tool/approval'+ '&callId=' + encodeURIComponent(data.toolCallId) + '&allowed=false', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                }).then(function() {
                    footerDiv.remove();
                }).catch(function() {
                    approveBtn.disabled = false;
                    rejectBtn.disabled = false;
                });
            };

            btnGroup.appendChild(approveBtn);
            btnGroup.appendChild(rejectBtn);
            footerDiv.appendChild(btnGroup);
            toolCallDiv.appendChild(footerDiv);
        }

    } else if (data.status === 'rejected') {
        var existingFooter = toolCallDiv.querySelector('.tool-call-footer');
        if (existingFooter) existingFooter.remove();

        toolCallDiv.setAttribute('data-status', 'rejected');
        if (statusEl) { statusEl.textContent = '已拒绝'; statusEl.classList.add('completed'); }
        if (iconEl) { iconEl.style.animation = 'none'; iconEl.textContent = '🚫'; }

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
            msgState.currentStreamingMessage.appendChild(createMessageFooter());
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
    var agentName = (function(){ var s = document.querySelector('.agent-select-toolbar'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();
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


function handleThinking(data, requestId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.agent-select-toolbar'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();
    
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
            msgState.currentStreamingMessage.appendChild(createMessageFooter());
            msgState.currentStreamingMessage = null;
            msgState.thinkingContent = '';
            msgState.thinkingDiv = null;
            msgState.lastMessageType = null;
        }
    }
}

function handleStreamingChunk(content, requestId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.agent-select-toolbar'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();
    
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
    var agentName = (function(){ var s = document.querySelector('.agent-select-toolbar'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();
    var msgState = streamingMessages[requestId];
    if (!msgState || !msgState.currentStreamingMessage) {
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
    
    msgState.currentStreamingMessage.appendChild(createMessageFooter());
    
    delete streamingMessages[requestId];
    enableInput();
}

function handleStreamingError(errorMessage, requestId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.agent-select-toolbar'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();

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

    errorDiv.appendChild(createMessageFooter(errorMessage));

    messagesDiv.appendChild(errorDiv);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
    enableInput();
}
function handleStreamingWarn(warnMessage, requestId) {
    var messagesDiv = document.getElementById('chatMessages');
    var agentName = (function(){ var s = document.querySelector('.agent-select-toolbar'); return s ? s.options[s.selectedIndex].text : 'Agent'; })();

    var warnDiv = document.createElement('div');
    warnDiv.className = 'message agent warn-message';
    if (requestId) {
        warnDiv.setAttribute('data-request-id', requestId);
    }

    var label = document.createElement('div');
    label.className = 'message-label';
    label.textContent = agentName + ' (警告)';
    warnDiv.appendChild(label);

    var contentDiv = document.createElement('div');
    contentDiv.className = 'message-content warn-content';
    contentDiv.textContent = warnMessage;
    warnDiv.appendChild(contentDiv);

    warnDiv.appendChild(createMessageFooter(warnMessage));

    messagesDiv.appendChild(warnDiv);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
    enableInput();
}

function sendMessage() {
    var input = document.getElementById('messageInput');
    var message = input.value.trim();

    if (!message || !currentAgentId) {
        return;
    }

    if (!currentModelId) {
        showToast('请先选择一个模型', 'warning');
        return;
    }

    fetch('/api/session/' + currentSessionId + '/running')
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code === 200 && res.data === true) {
                showToast('任务还在运行中，请先停止', 'warning');
                disableInput();
                return;
            }

            input.value = '';
            disableInput();
            var messagesDiv = document.getElementById('chatMessages');

            // 显示图片附件 - 每张图片作为独立的消息记录
            var imageFiles = attachedFiles.filter(function(f) { return f.type === 'image'; });
            imageFiles.forEach(function(f) {
                var imageMessageDiv = document.createElement('div');
                imageMessageDiv.className = 'message user';

                var imageLabel = document.createElement('div');
                imageLabel.className = 'message-label';
                imageLabel.textContent = '你';
                imageMessageDiv.appendChild(imageLabel);

                var img = document.createElement('img');
                img.src = f.url;
                img.className = 'message-content message-image';
                imageMessageDiv.appendChild(img);

                imageMessageDiv.appendChild(createMessageFooter(''));

                messagesDiv.appendChild(imageMessageDiv);
            });

            // 文本消息作为独立的消息记录
            if (message && message.trim() !== '') {
                var textMessageDiv = document.createElement('div');
                textMessageDiv.className = 'message user';

                var textLabel = document.createElement('div');
                textLabel.className = 'message-label';
                textLabel.textContent = '你';
                textMessageDiv.appendChild(textLabel);

                var textContent = document.createElement('div');
                textContent.className = 'message-content';
                textContent.textContent = message;
                textMessageDiv.appendChild(textContent);

                textMessageDiv.appendChild(createMessageFooter(message));

                messagesDiv.appendChild(textMessageDiv);
            }

            messagesDiv.scrollTop = messagesDiv.scrollHeight;

            var deepBtn = document.getElementById('deepThinkBtn');
            var filesPayload = attachedFiles.map(function(f) {
                return { url: f.url, type: f.type };
            });
            var payload = {
                sessionId: currentSessionId,
                agentId: currentAgentId,
                message: message,
                skills: getSelectedSkills(),
                aiModelId: currentModelId,
                enableThinking: deepBtn ? deepBtn.getAttribute('data-enabled') === 'true' : true,
                toolCallPermission: currentToolCallPermission,
                files: filesPayload
            };
            clearAttachedFiles();
            var emptyState = document.getElementById('chatHistoryEmptyState');
            if(emptyState){
                emptyState.classList.add("hide");
            }
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

function selectAgent(selectElement) {
    setCurrentAgentId(selectElement.value);
    const index = selectElement.selectedIndex;

    // 如果下标不为 -1（表示有选项被选中），则获取该 option 元素
    if (index !== -1) {
        const selectedOption = selectElement.options[index];
        // 同样使用 getAttribute 读取你代码中的 'data' 属性
        const dataValue = selectedOption.getAttribute('data-desc');
        var descEl = document.getElementById('chat-header-desc');
        descEl.innerHTML=dataValue;
    }
}

function clearHistory(sessionId) {
    showConfirm('确定要清空对话历史吗？').then(function(confirmed) {
        if (confirmed) {
            fetch('/api/session/' + encodeURIComponent(sessionId) + '/clear' , { method: 'POST' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.code === 200) {
                    window.location.href = '/?sessionId=' + encodeURIComponent(sessionId);
                } else {
                    showToast(resp.msg || '清空历史失败', 'error');
                }
            })
            .catch(function(err) {
                showToast('清空历史失败: ' + err.message, 'error');
            });
        }
    });
}

function deleteSession(sessionId) {
    showConfirm('确定要删除该会话吗？此操作不可恢复。').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/session/' + encodeURIComponent(sessionId) + '/delete-by-session-id', { method: 'DELETE' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.code === 200) {
                    showToast('会话已删除', 'info');
                    setTimeout(function() {
                        window.location.href = '/';
                    }, 1000);
                } else {
                    showToast(resp.msg || '删除失败', 'error');
                }
            })
            .catch(function(err) {
                showToast('删除失败: ' + err.message, 'error');
            });
    });
}

function forceStopAgent(sessionId) {
    showConfirm('确定要强停智能体吗？强停后将移除执行器并刷新页面。').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/session/'+ encodeURIComponent(sessionId)+'/force-stop', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        }).then(function(r) { return r.json(); }).then(function(res) {
            if (res.code === 200) {
                window.location.href = '/?sessionId=' + sessionId;
            } else {
                showToast(res.msg || '强停失败', 'warning');
            }
        });
    });
}

function stopCurrentSession() {
    showConfirm('确定要停止运行吗？').then(function(confirmed) {
        if (!confirmed) return;
        var agentId = document.querySelector('input[name="agentId"]').value;
        fetch('/api/session/'+ encodeURIComponent(currentSessionId)+'/stop', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            }
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

function handleTokenUsageMessage(data) {
    if (!currentAgentId) return;
    if (data.sessionId !== currentSessionId) return;
    if (data.source !== 'chat') return;

    var entry = {
        id: data.id,
        inputTokens: data.inputTokens || 0,
        outputTokens: data.outputTokens || 0,
        totalTokens: data.totalTokens || 0,
        createTime: data.createTime || ''
    };
    tokenChartData.push(entry);
    lastTokenId = data.id;

    renderTokenChart(tokenChartData);

    var inputSum = 0, outputSum = 0, totalSum = 0;
    for (var i = 0; i < tokenChartData.length; i++) {
        inputSum += tokenChartData[i].inputTokens || 0;
        outputSum += tokenChartData[i].outputTokens || 0;
        totalSum += tokenChartData[i].totalTokens || 0;
    }
    updateTokenTitle(inputSum, outputSum, totalSum);
}

function loadTokenUsage(minId) {
    if (!currentAgentId) return;
    var url = '/api/token-usage/today?sessionId=' + currentSessionId+"&source=chat";
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
    var statsUrl = '/api/token-usage/daily-stats?startTime=' + encodeURIComponent(startStr) + '&endTime=' + encodeURIComponent(endStr) + '&sessionId=' + currentSessionId+'&source=chat';
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

    var sessionIdInput = document.getElementById('currentSessionId');
    if (sessionIdInput && sessionIdInput.value) {
        currentSessionId = sessionIdInput.value;
    }
    if (!currentSessionId && initialCurrentSessionId) {
        currentSessionId = initialCurrentSessionId;
    }

    if (initialToolCallPermission) {
        currentToolCallPermission = initialToolCallPermission;
        selectToolPermission(initialToolCallPermission);
    }

    renderSessionList(initialChatSessions);


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
        connectWebSocket();
        loadTokenUsage();
    }

    loadChatSkills();
    loadModelSelector();

    var skillBtn = document.getElementById('skillSelectBtn');
    if (skillBtn) {
        skillBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            var menu = document.getElementById('skillsDropdownMenu');
            menu.classList.toggle('open');
        });
    }

    // 附件按钮
    var attachBtn = document.getElementById('attachBtn');
    var fileInput = document.getElementById('fileInput');
    if (attachBtn && fileInput) {
        attachBtn.addEventListener('click', function() {
            fileInput.click();
        });
        fileInput.addEventListener('change', function() {
            handleFiles(fileInput.files);
            fileInput.value = '';
        });
    }

    var modelBtn = document.getElementById('modelSelectBtn');
    if (modelBtn) {
        modelBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            var menu = document.getElementById('modelDropdownMenu');
            var skillsMenu = document.getElementById('skillsDropdownMenu');
            if (skillsMenu) skillsMenu.classList.remove('open');
            
            // 检查菜单是否已经打开
            var isOpen = menu.classList.contains('open');
            if (isOpen) {
                // 直接关闭
                menu.classList.remove('open');
                return;
            }
            
            // 先显示菜单以便获取正确的尺寸
            menu.style.display = 'block';
            menu.style.visibility = 'hidden';
            
            // 计算菜单位置
            var rect = modelBtn.getBoundingClientRect();
            menu.style.left = rect.left + 'px';
            menu.style.top = (rect.top - 6 - menu.offsetHeight) + 'px';
            // 检查是否超出屏幕顶部，如果是则显示在按钮下方
            if (rect.top - 6 - menu.offsetHeight < 0) {
                menu.style.top = (rect.bottom + 6) + 'px';
            }
            // 确保菜单不会超出屏幕右侧
            if (rect.left + menu.offsetWidth > window.innerWidth) {
                menu.style.left = (window.innerWidth - menu.offsetWidth - 10) + 'px';
            }
            
            // 恢复可见性并打开菜单
            menu.style.visibility = 'visible';
            menu.classList.add('open');
        });
    }

    document.addEventListener('click', function(e) {
        var dropdown = document.getElementById('skillsDropdown');
        if (dropdown && !dropdown.contains(e.target)) {
            var menu = document.getElementById('skillsDropdownMenu');
            if (menu) menu.classList.remove('open');
        }
        var modelDropdown = document.getElementById('modelDropdown');
        if (modelDropdown && !modelDropdown.contains(e.target)) {
            var menu = document.getElementById('modelDropdownMenu');
            if (menu) {
                menu.classList.remove('open');
                menu.style.display = ''; // 重置内联样式
                menu.style.visibility = '';
            }
        }
        var toolPermissionDropdown = document.getElementById('toolPermissionDropdown');
        if (toolPermissionDropdown && !toolPermissionDropdown.contains(e.target)) {
            var menu = document.getElementById('toolPermissionDropdownMenu');
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
        // 粘贴截图处理
        input.addEventListener('paste', function(e) {
            var items = e.clipboardData && e.clipboardData.items;
            if (!items) return;
            for (var i = 0; i < items.length; i++) {
                if (items[i].type.indexOf('image') === 0) {
                    e.preventDefault();
                    var blob = items[i].getAsFile();
                    uploadFile(blob);
                    break;
                }
            }
        });
    }

    var deepBtn = document.getElementById('deepThinkBtn');
    if (deepBtn) {
        deepBtn.addEventListener('click', function() {
            var current = this.getAttribute('data-enabled') === 'true';
            var newEnabled = !current;
            this.setAttribute('data-enabled', newEnabled);
            this.classList.toggle('active', newEnabled);
        });
    }

    var toolPermissionBtn = document.getElementById('toolPermissionBtn');
    if (toolPermissionBtn) {
        toolPermissionBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            var menu = document.getElementById('toolPermissionDropdownMenu');
            menu.classList.toggle('open');
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

            var selectedSkillsArr = (initialSelectedSkills && typeof initialSelectedSkills === 'string')
                ? initialSelectedSkills.split(',').map(function(s) { return s.trim(); })
                : [];
            var checkboxes = list.querySelectorAll('input[type="checkbox"]');
            checkboxes.forEach(function(cb) {
                var folder = cb.getAttribute('data-folder') || '';
                if (selectedSkillsArr.indexOf(folder) !== -1) {
                    cb.checked = true;
                }
                cb.addEventListener('change', updateSkillBtnState);
            });
            updateSkillBtnState();
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

function loadModelSelector() {
    var providerList = document.getElementById('modelProviderList');
    if (!providerList) return;

    // 获取默认选中的模型ID
    var selectedModelIdInput = document.getElementById('selectedAiModelId');
    var defaultModelId = null;
    if (selectedModelIdInput && selectedModelIdInput.value) {
        defaultModelId = parseInt(selectedModelIdInput.value);
    }

    fetch('/api/models/all')
        .then(function(r) { return r.json(); })
        .then(function(allModels) {
            fetch('/api/providers')
                .then(function(r) { return r.json(); })
                .then(function(providers) {
                    var configuredProviders = providers.filter(function(p) {
                        return p.apiKey && p.url;
                    });

                    if (configuredProviders.length === 0) {
                        providerList.innerHTML = '<div class="model-dropdown-empty">暂无已配置的模型提供商</div>';
                        return;
                    }

                    var defaultModelName = null;
                    var html = '';
                    configuredProviders.forEach(function(provider) {
                        var models = allModels[provider.id] || [];
                        html += '<div class="model-provider-item">';
                        html += '<span class="model-provider-item-name">' + escapeHtml(provider.name) + '</span>';
                        html += '<span class="model-provider-item-arrow">▶</span>';
                        html += '<div class="model-sub-menu">';
                        if (models.length === 0) {
                            html += '<div class="model-dropdown-empty">暂无模型</div>';
                        } else {
                            models.forEach(function(model) {
                                var activeClass = '';
                                if (defaultModelId && defaultModelId === model.id) {
                                    activeClass = ' active';
                                    defaultModelName = model.modelName;
                                }
                                html += '<div class="model-sub-item' + activeClass + '" data-model-id="' + model.id + '" data-model-name="' + escapeHtml(model.modelName) + '">';
                                html += '<span class="model-sub-item-check">✓</span>';
                                html += '<span class="model-sub-item-name">' + escapeHtml(model.modelName) + '</span>';
                                html += '</div>';
                            });
                        }
                        html += '</div>';
                        html += '</div>';
                    });
                    providerList.innerHTML = html;

                    var subItems = providerList.querySelectorAll('.model-sub-item');
                    subItems.forEach(function(item) {
                        item.addEventListener('click', function(e) {
                            e.stopPropagation();
                            var modelId = this.getAttribute('data-model-id');
                            var modelName = this.getAttribute('data-model-name');
                            selectModel(modelId, modelName);
                        });
                    });
                    
                    // 为每个供应商项添加子菜单位置检测
                    var providerItems = providerList.querySelectorAll('.model-provider-item');
                    providerItems.forEach(function(item) {
                        item.addEventListener('mouseenter', function() {
                            var subMenu = this.querySelector('.model-sub-menu');
                            if (subMenu) {
                                var itemRect = this.getBoundingClientRect();
                                
                                // 检查子菜单向右展开是否会超出屏幕右侧
                                if (itemRect.right + subMenu.offsetWidth > window.innerWidth) {
                                    subMenu.classList.add('right-edge');
                                } else {
                                    subMenu.classList.remove('right-edge');
                                }
                                
                                // 检查子菜单是否会超出屏幕底部
                                if (itemRect.top + subMenu.offsetHeight > window.innerHeight) {
                                    subMenu.style.top = 'auto';
                                    subMenu.style.bottom = '0';
                                } else {
                                    subMenu.style.top = '0';
                                    subMenu.style.bottom = 'auto';
                                }
                            }
                        });
                    });

                    // 如果有默认模型ID，设置默认选中
                    if (defaultModelId && defaultModelName) {
                        selectModel(defaultModelId, defaultModelName);
                    }
                });
        });
}

function selectModel(modelId, modelName) {
    currentModelId = parseInt(modelId);
    var nameSpan = document.getElementById('selectedModelName');
    var btn = document.getElementById('modelSelectBtn');
    if (nameSpan) {
        nameSpan.textContent = modelName;
    }
    if (btn) {
        btn.classList.add('has-selected');
    }

    var allSubItems = document.querySelectorAll('.model-sub-item');
    allSubItems.forEach(function(item) {
        if (item.getAttribute('data-model-id') == modelId) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });

    var menu = document.getElementById('modelDropdownMenu');
    if (menu) {
        menu.classList.remove('open');
        menu.style.display = ''; // 重置内联样式
        menu.style.visibility = '';
    }
}

function renderSessionList(sessions) {
    var container = document.getElementById('sessionList');
    if (!container) return;

    if (!sessions || sessions.length === 0) {
        container.innerHTML = '<div class="session-list-empty">暂无会话</div>';
        return;
    }

    var html = '';
    sessions.forEach(function(s) {
        var activeClass = (s.sessionId === currentSessionId) ? ' active' : '';
        var title = s.title || '未命名会话';
        var timeStr = formatSessionTime(s.lastUpdateTime || s.createTime);
        html += '<div class="session-list-item' + activeClass + '" data-session-id="' + escapeHtml(s.sessionId) + '" data-id="' + (s.id || '') + '">';
        html += '<span class="session-list-item-title">' + escapeHtml(title) + '</span>';
        html += '<span class="session-list-item-time">' + escapeHtml(timeStr) + '</span>';
        html += '<button class="session-edit-btn" onclick="event.stopPropagation();showEditSessionTitle(this,' + (s.id || 0) + ')" title="编辑标题"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg></button>';
        html += '</div>';
    });
    container.innerHTML = html;

    var items = container.querySelectorAll('.session-list-item');
    items.forEach(function(item) {
        item.addEventListener('click', function() {
            var sessionId = this.getAttribute('data-session-id');
            if (sessionId && sessionId !== currentSessionId) {
                window.location.href = '/?sessionId=' + sessionId;
            }
        });
    });

    var activeItem = container.querySelector('.session-list-item.active');
    if (activeItem) {
        activeItem.scrollIntoView({ block: 'nearest' });
    }
}

function updateSessionTitle(sessionId, newTitle) {
    var container = document.getElementById('sessionList');
    if (!container) return;

    var existingItem = container.querySelector('.session-list-item[data-session-id="' + escapeHtml(sessionId) + '"]');
    if (existingItem) {
        var titleSpan = existingItem.querySelector('.session-list-item-title');
        if (titleSpan) {
            titleSpan.textContent = newTitle || '未命名会话';
        }
        return;
    }

    var emptyEl = container.querySelector('.session-list-empty');
    if (emptyEl) {
        emptyEl.remove();
    }

    var item = document.createElement('div');
    item.className = 'session-list-item' + (sessionId === currentSessionId ? ' active' : '');
    item.setAttribute('data-session-id', sessionId);

    var titleSpan = document.createElement('span');
    titleSpan.className = 'session-list-item-title';
    titleSpan.textContent = newTitle || '未命名会话';
    item.appendChild(titleSpan);

    var timeSpan = document.createElement('span');
    timeSpan.className = 'session-list-item-time';
    timeSpan.textContent = '刚刚';
    item.appendChild(timeSpan);

    var editBtn = document.createElement('button');
    editBtn.className = 'session-edit-btn';
    editBtn.setAttribute('title', '编辑标题');
    editBtn.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>';
    item.appendChild(editBtn);

    item.addEventListener('click', function() {
        if (sessionId !== currentSessionId) {
            window.location.href = '/?sessionId=' + sessionId;
        }
    });

    container.insertBefore(item, container.firstChild);
}

function showEditSessionTitle(btn, id) {
    var item = btn.closest('.session-list-item');
    var titleSpan = item.querySelector('.session-list-item-title');
    var oldTitle = titleSpan.textContent;

    var input = document.createElement('input');
    input.type = 'text';
    input.className = 'session-title-input';
    input.value = oldTitle;
    input.setAttribute('data-id', id);

    titleSpan.replaceWith(input);
    input.focus();
    input.select();

    var saved = false;
    function save() {
        if (saved) return;
        saved = true;
        var newTitle = input.value.trim();
        if (!newTitle || newTitle === oldTitle) {
            // 空值或未改，恢复
            input.replaceWith(titleSpan);
            return;
        }
        fetch('/api/session/update-title?id=' + id + '&title=' + encodeURIComponent(newTitle), {
            method: 'POST'
        }).then(function(res) {
            return res.json();
        }).then(function(data) {
            if (data.code === 200) {
                titleSpan.textContent = newTitle;
                input.replaceWith(titleSpan);
            } else {
                showToast('保存失败', 'error');
                input.replaceWith(titleSpan);
            }
        }).catch(function() {
            showToast('网络错误', 'error');
            input.replaceWith(titleSpan);
        });
    }

    input.addEventListener('blur', save);
    input.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            input.blur();
        } else if (e.key === 'Escape') {
            saved = true;
            input.replaceWith(titleSpan);
        }
    });
}

function formatSessionTime(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    if (isNaN(d.getTime())) return '';
    var now = new Date();
    var diffMs = now - d;
    var diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return '刚刚';
    if (diffMin < 60) return diffMin + '分钟前';
    var diffHour = Math.floor(diffMin / 60);
    if (diffHour < 24) return diffHour + '小时前';
    var diffDay = Math.floor(diffHour / 24);
    if (diffDay < 7) return diffDay + '天前';
    var month = d.getMonth() + 1;
    var day = d.getDate();
    return month + '/' + day;
}

function createNewSession() {
    var deepBtn = document.getElementById('deepThinkBtn');
    var payload = {
        agentId: currentAgentId || null,
        aiModelId: currentModelId || null,
        enableThinking: deepBtn ? deepBtn.getAttribute('data-enabled') === 'true' : true,
        skillNames: getSelectedSkills().join(',') || null,
        toolCallPermission: currentToolCallPermission || 'smart_call'
    };

    fetch('/api/session/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        if (resp.code === 200 && resp.data) {
            window.location.href = '/?sessionId=' + resp.data;
        } else {
            showToast(resp.msg || '创建会话失败', 'error');
        }
    })
    .catch(function(err) {
        showToast('创建会话失败: ' + err.message, 'error');
    });
}

function editCurrentAgent(){
    showEditAgentModal(currentAgentId);
}

function selectToolPermission(value) {
    currentToolCallPermission = value;
    var labelMap = {
        'user_control': '用户控制',
        'smart_call': '智能调用',
        'auto': '完全自动'
    };
    var label = document.getElementById('toolPermissionLabel');
    if (label) {
        label.textContent = labelMap[value] || value;
    }
    var btn = document.getElementById('toolPermissionBtn');
    if (btn) {
        btn.classList.remove('user_control', 'smart_call', 'auto');
        btn.classList.add(value);
    }
    var checks = document.querySelectorAll('.tool-permission-item-check');
    checks.forEach(function(check) {
        check.style.opacity = '0';
    });
    var activeCheck = document.getElementById('check_' + value);
    if (activeCheck) {
        activeCheck.style.opacity = '1';
    }
    var menu = document.getElementById('toolPermissionDropdownMenu');
    if (menu) {
        menu.classList.remove('open');
    }
}

function handleApprovalClick(btn, allowed) {
    var sessionId = btn.getAttribute('data-session-id');
    var callId = btn.getAttribute('data-call-id');
    var footer = btn.closest('.tool-call-footer');
    var approveBtn = footer.querySelector('.tool-call-approve-btn');
    var rejectBtn = footer.querySelector('.tool-call-reject-btn');
    btn.disabled = true;
    if (approveBtn) approveBtn.disabled = true;
    if (rejectBtn) rejectBtn.disabled = true;
    fetch('/api/session/tool/approval', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'sessionId=' + encodeURIComponent(sessionId || currentSessionId) + '&callId=' + encodeURIComponent(callId) + '&allowed=' + allowed
    }).then(function() {
        footer.remove();
    }).catch(function() {
        btn.disabled = false;
        if (approveBtn) approveBtn.disabled = false;
        if (rejectBtn) rejectBtn.disabled = false;
    });
}

// ========== 文件附件功能 ==========

function handleFiles(fileList) {
    for (var i = 0; i < fileList.length; i++) {
        uploadFile(fileList[i]);
    }
}

function uploadFile(file) {
    var formData = new FormData();
    formData.append('file', file);

    // 创建预览占位（上传中）
    var tempId = 'file_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6);
    var previewItem = createPreviewItem(tempId, null, true);
    document.getElementById('filePreviewArea').appendChild(previewItem);

    fetch('/api/upload', {
        method: 'POST',
        body: formData
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        // 移除占位
        removePreviewItem(tempId);
        if (resp.code === 200) {
            var fileInfo = {
                url: resp.data.url,
                type: resp.data.type,
                name: file.name
            };
            attachedFiles.push(fileInfo);
            renderFilePreview(fileInfo);
        } else {
            showToast('文件上传失败: ' + (resp.msg || '未知错误'), 'error');
        }
    })
    .catch(function(err) {
        removePreviewItem(tempId);
        showToast('文件上传失败: ' + err.message, 'error');
    });
}

function createPreviewItem(id, url, uploading) {
    var item = document.createElement('div');
    item.className = 'file-preview-item';
    item.setAttribute('data-file-id', id);

    if (uploading) {
        var overlay = document.createElement('div');
        overlay.className = 'preview-uploading';
        var spinner = document.createElement('div');
        spinner.className = 'preview-spinner';
        overlay.appendChild(spinner);
        item.appendChild(overlay);
    } else {
        var img = document.createElement('img');
        img.src = url;
        item.appendChild(img);

        var removeBtn = document.createElement('button');
        removeBtn.className = 'preview-remove';
        removeBtn.textContent = 'X';
        removeBtn.onclick = function(e) {
            e.stopPropagation();
            removeFileByUrl(url);
        };
        item.appendChild(removeBtn);
    }

    return item;
}

function renderFilePreview(fileInfo) {
    var id = 'file_' + fileInfo.url.replace(/[^a-zA-Z0-9]/g, '_');
    var item = createPreviewItem(id, fileInfo.url, false);
    document.getElementById('filePreviewArea').appendChild(item);
    // 更新预览区域可见性
    updatePreviewAreaVisibility();
}

function removePreviewItem(id) {
    var item = document.querySelector('.file-preview-item[data-file-id="' + id + '"]');
    if (item) item.remove();
    updatePreviewAreaVisibility();
}

function removeFileByUrl(url) {
    attachedFiles = attachedFiles.filter(function(f) { return f.url !== url; });
    // 移除预览元素
    var items = document.querySelectorAll('.file-preview-item');
    items.forEach(function(item) {
        var img = item.querySelector('img');
        if (img && img.src.indexOf(url) !== -1) {
            item.remove();
        }
    });
    updatePreviewAreaVisibility();
}

function updatePreviewAreaVisibility() {
    var area = document.getElementById('filePreviewArea');
    if (area && area.children.length === 0) {
        area.style.display = 'none';
    } else if (area) {
        area.style.display = '';
    }
}

function clearAttachedFiles() {
    attachedFiles = [];
    var area = document.getElementById('filePreviewArea');
    if (area) {
        area.innerHTML = '';
        area.style.display = 'none';
    }
}