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
// 会话列表类型筛选：chat=聊天 / project=项目 / task=任务，默认取当前会话的类型
var sessionTypeFilter = 'chat';
// 运行中的会话ID集合：初始渲染时取自后端 running 字段，运行期由 WebSocket 事件维护
var runningSessionIds = {};

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

// ================= 向上滚动加载更早的历史消息 =================
var historyLoadState = { oldestId: null, oldestTime: null, allLoaded: false, loading: false };
var HISTORY_PAGE_SIZE = 50;
var HISTORY_INITIAL_LIMIT = 100; // 与后端首页初始加载条数(ChatController)一致

/** 初始化历史分页游标并监听滚动 */
function initHistoryScroll() {
    var messagesDiv = document.getElementById('chatMessages');
    if (!messagesDiv) return;
    // 初始加载不满一页说明已是全部历史
    if (typeof initialHistoryCount === 'number' && initialHistoryCount < HISTORY_INITIAL_LIMIT) {
        historyLoadState.allLoaded = true;
    }
    // 游标取消息区第一个带标记的直接子元素（消息或工具行）
    var first = firstHistoryAnchor(messagesDiv);
    if (first) {
        historyLoadState.oldestId = Number(first.getAttribute('data-msg-id'));
        historyLoadState.oldestTime = first.getAttribute('data-create-time');
    } else {
        historyLoadState.allLoaded = true;
    }
    messagesDiv.addEventListener('scroll', onHistoryScroll);
}

function onHistoryScroll() {
    var messagesDiv = document.getElementById('chatMessages');
    if (!messagesDiv) return;
    if (messagesDiv.scrollTop <= 40 && !historyLoadState.loading && !historyLoadState.allLoaded && historyLoadState.oldestId != null) {
        loadOlderMessages();
    }
}

/** 顶部加载指示器 */
function showHistoryTopLoader(show) {
    var messagesDiv = document.getElementById('chatMessages');
    if (!messagesDiv) return;
    var loader = document.getElementById('historyTopLoader');
    if (show) {
        if (!loader) {
            loader = document.createElement('div');
            loader.id = 'historyTopLoader';
            loader.style.cssText = 'text-align:center;color:#888;font-size:12px;padding:6px 0;';
            loader.textContent = '正在加载更早的消息...';
            messagesDiv.insertBefore(loader, messagesDiv.firstChild);
        }
    } else if (loader) {
        loader.remove();
    }
}

/** 拉取并前插更早的历史消息 */
function loadOlderMessages() {
    var messagesDiv = document.getElementById('chatMessages');
    if (!messagesDiv || !currentSessionId) return;
    historyLoadState.loading = true;
    showHistoryTopLoader(true);
    var prevHeight = messagesDiv.scrollHeight;
    var prevScrollTop = messagesDiv.scrollTop;
    fetch('/api/session/' + encodeURIComponent(currentSessionId) + '/history/before?beforeTime='
        + encodeURIComponent(historyLoadState.oldestTime) + '&beforeId=' + historyLoadState.oldestId
        + '&limit=' + HISTORY_PAGE_SIZE)
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code !== 200) {
                historyLoadState.allLoaded = true;
                return;
            }
            var data = res.data || {};
            var list = data.list || [];
            if (list.length === 0) {
                historyLoadState.allLoaded = true;
                return;
            }
            // 接口按时间倒序返回：反转为正序后前插
            list.reverse();
            prependHistoryMessages(list);
            // 渲染 markdown（只补新增的 agent 消息）
            renderAllMessages();
            // 保持滚动位置：追加高度补偿
            messagesDiv.scrollTop = messagesDiv.scrollHeight - prevHeight + prevScrollTop;
            // 更新游标为当前最早一条
            var oldest = list[0];
            historyLoadState.oldestId = oldest.id;
            historyLoadState.oldestTime = formatHistoryIsoTime(oldest.createTime);
            if (!data.hasMore) {
                historyLoadState.allLoaded = true;
            }
        })
        .catch(function(e) {
            // 网络异常时不标记结束，允许用户再次滚动重试
            console.error('加载更早历史消息失败:', e);
        })
        .finally(function() {
            historyLoadState.loading = false;
            showHistoryTopLoader(false);
        });
}

/** 消息区第一个带游标标记的直接子元素（.message 或 .tool-inline-row） */
function firstHistoryAnchor(messagesDiv) {
    var children = messagesDiv.children;
    for (var i = 0; i < children.length; i++) {
        if (children[i].hasAttribute && children[i].hasAttribute('data-msg-id')) {
            return children[i];
        }
    }
    return null;
}

/** 将正序历史消息前插到消息区，并同步前插工具调用到右侧工具执行列表 */
function prependHistoryMessages(list) {
    var messagesDiv = document.getElementById('chatMessages');
    var toolExecList = document.getElementById('toolExecList');
    // 插入参照：现有最早的直接子元素（保持不变，每个新节点依次插到它前面即为正序）
    var insertRef = firstHistoryAnchor(messagesDiv);
    var toolFragment = document.createDocumentFragment();

    // 与服务端 buildChatFlow 一致：连续 tool_call 合并为一行扳手图标
    var toolRow = null;
    list.forEach(function(chat) {
        if (chat.messageType === 'tool_call') {
            if (!toolRow) {
                toolRow = document.createElement('div');
                toolRow.className = 'tool-inline-row';
                // 行本身携带首个工具的游标，供下一次分页定位锚点
                toolRow.setAttribute('data-msg-id', chat.id);
                toolRow.setAttribute('data-create-time', formatHistoryIsoTime(chat.createTime));
            }
            var icon = document.createElement('span');
            icon.className = 'tool-inline-icon';
            icon.setAttribute('data-tool-call-id', chat.toolCallId);
            icon.title = chat.toolName || '';
            icon.textContent = '🔧';
            icon.onclick = function() { scrollToToolCall(this); };
            toolRow.appendChild(icon);
            if (toolExecList) {
                toolFragment.appendChild(buildToolCallStaticNode(chat));
            }
        } else {
            // 非工具消息结束当前工具分组：先把整组工具行插到参照前
            if (toolRow) {
                if (insertRef) {
                    messagesDiv.insertBefore(toolRow, insertRef);
                } else {
                    messagesDiv.appendChild(toolRow);
                }
                toolRow = null;
            }
            var node = buildHistoryMessageNode(chat);
            if (insertRef) {
                messagesDiv.insertBefore(node, insertRef);
            } else {
                messagesDiv.appendChild(node);
            }
        }
    });
    // 最后一组工具行收尾
    if (toolRow) {
        if (insertRef) {
            messagesDiv.insertBefore(toolRow, insertRef);
        } else {
            messagesDiv.appendChild(toolRow);
        }
    }
    // 右侧工具列表：前插更早的工具调用（保持正序）
    if (toolExecList && toolFragment.childNodes.length > 0) {
        toolExecList.insertBefore(toolFragment, toolExecList.firstChild);
    }
}

/** ISO 时间字符串（LocalDateTime 序列化格式），用于游标传递 */
function formatHistoryIsoTime(createTime) {
    if (!createTime) return null;
    var s = String(createTime).replace(' ', 'T');
    return s.split('.')[0];
}

/** 构造单条历史消息 DOM（与服务端 Thymeleaf 渲染结构保持一致） */
function buildHistoryMessageNode(chat) {
    var div = document.createElement('div');
    div.className = 'message ' + (chat.role === 'user' ? 'user' : 'agent');
    div.setAttribute('data-msg-id', chat.id);
    div.setAttribute('data-create-time', formatHistoryIsoTime(chat.createTime));

    var agentName = (chat.agent && chat.agent.name) ? chat.agent.name : 'Agent';
    var label = chat.role === 'user' ? '你' : agentName;
    var timeText = formatMessageTime(new Date(formatHistoryIsoTime(chat.createTime)));

    var type = chat.messageType;
    if (type === 'attachment') {
        appendLabel(div, label);
        var arr= chat.content.split(',');
        var fileType=arr[0];
        var id=arr[1];
        var originalName=arr[2];
        var url=arr[3];
        if(fileType === 'image'){
            var img = document.createElement('img');
            img.className = 'message-content message-image';
            img.src = url;
            img.setAttribute('data-is-agent', chat.role === 'agent');
            img.alt = originalName;
            div.appendChild(img);
        }else{
            var a = document.createElement('a');
            a.className = 'message-content message-'+fileType;
            a.href = url;
            a.setAttribute('data-is-agent', chat.role === 'agent');
            a.title = originalName;
            a.text = originalName;
            div.appendChild(a);
        }
        div.appendChild(buildHistoryFooter(timeText, null));
    } else if (type === 'thinking') {
        appendLabel(div, agentName + ' (思考)');
        var think = document.createElement('div');
        think.className = 'message-content thinking-content';
        think.textContent = chat.content;
        div.appendChild(think);
        div.appendChild(buildHistoryFooter(timeText, chat.content));
    } else if (type === 'error' || type === 'warn') {
        var inner = document.createElement('div');
        inner.className = type === 'error' ? 'error-message' : 'warn-message';
        appendLabel(inner, agentName + (type === 'error' ? ' (错误)' : ' (警告)'));
        var errContent = document.createElement('div');
        errContent.className = 'message-content error-content';
        errContent.textContent = chat.content;
        inner.appendChild(errContent);
        inner.appendChild(buildHistoryFooter(timeText, chat.content));
        div.appendChild(inner);
    } else {
        // text 及其它类型默认按文本处理
        appendLabel(div, label);
        var content = document.createElement('div');
        content.className = 'message-content';
        content.setAttribute('data-is-agent', chat.role === 'agent');
        content.textContent = chat.content;
        div.appendChild(content);
        div.appendChild(buildHistoryFooter(timeText, chat.content));
    }
    return div;
}

function appendLabel(parent, text) {
    var label = document.createElement('div');
    label.className = 'message-label';
    label.textContent = text;
    parent.appendChild(label);
}

/** 消息底部：时间 + 复制按钮 */
function buildHistoryFooter(timeText, content) {
    var footer = document.createElement('div');
    footer.className = 'message-footer';
    var timeDiv = document.createElement('div');
    timeDiv.className = 'message-time';
    timeDiv.textContent = timeText;
    footer.appendChild(timeDiv);
    if (content != null) {
        var copyBtn = document.createElement('button');
        copyBtn.className = 'message-copy-btn';
        copyBtn.title = '复制消息';
        copyBtn.setAttribute('data-content', content);
        copyBtn.innerHTML = '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>';
        copyBtn.onclick = function() { copyMessageContent(this); };
        footer.appendChild(copyBtn);
    }
    return footer;
}

/** 构造右侧工具执行列表的静态工具调用项（与服务端渲染结构保持一致） */
function buildToolCallStaticNode(chat) {
    var status = chat.toolCallStatus;
    var finished = status === 'executed' || status === 'rejected' || status === 'failed';

    var container = document.createElement('div');
    container.className = 'tool-call-container-static';
    var callDiv = document.createElement('div');
    callDiv.className = 'tool-call';
    callDiv.setAttribute('data-tool-call-id', chat.toolCallId);
    callDiv.setAttribute('data-status', status);

    var header = document.createElement('div');
    header.className = 'tool-call-header';

    var icon = document.createElement('span');
    icon.className = 'tool-call-icon' + (finished ? ' completed' : '');
    var iconMap = { started: '⚙', executed: '✅', failed: '❌', rejected: '🚫', approval: '⏳' };
    icon.textContent = iconMap[status] || '🔧';
    header.appendChild(icon);

    var name = document.createElement('span');
    name.className = 'tool-call-name';
    name.textContent = chat.toolName || '';
    header.appendChild(name);

    var statusEl = document.createElement('span');
    statusEl.className = 'tool-call-status' + (finished ? ' completed' : '');
    if (status === 'started') {
        statusEl.textContent = '执行中...';
    } else if (status === 'executed') {
        statusEl.textContent = (chat.toolExecutionTime != null)
            ? '已完成(' + (chat.toolExecutionTime / 1000).toFixed(1) + 's)' : '已完成';
    } else if (status === 'failed') {
        statusEl.textContent = '执行失败';
    } else if (status === 'approval') {
        statusEl.textContent = '等待审批';
    } else if (status === 'rejected') {
        statusEl.textContent = '拒绝执行';
    } else {
        statusEl.textContent = status || '未知';
    }
    header.appendChild(statusEl);

    if (finished) {
        var toggle = document.createElement('span');
        toggle.className = 'tool-call-toggle';
        toggle.textContent = '▼';
        header.appendChild(toggle);
    }
    callDiv.appendChild(header);

    var body = document.createElement('div');
    body.className = 'tool-call-body' + (status === 'started' ? '' : ' collapsed');
    if (chat.toolArguments) {
        var args = document.createElement('div');
        args.className = 'tool-call-args';
        args.innerHTML = '<div class="args-label">参数:</div><pre class="args-content">' + escapeHtml(chat.toolArguments) + '</pre>';
        body.appendChild(args);
    }
    if (chat.content) {
        var result = document.createElement('div');
        result.className = 'tool-call-result';
        result.innerHTML = '<div class="result-label">结果:</div><pre class="result-content">' + escapeHtml(chat.content) + '</pre>';
        body.appendChild(result);
    }
    callDiv.appendChild(body);

    if (status === 'approval') {
        var footer = document.createElement('div');
        footer.className = 'tool-call-footer';
        footer.innerHTML = '<span class="tool-call-footer-text">⚠️ 此工具调用需要审批</span>'
            + '<div class="tool-call-footer-btns">'
            + '<button type="button" class="tool-call-approve-btn">通过</button>'
            + '<button type="button" class="tool-call-reject-btn">拒绝</button>'
            + '</div>';
        var approveBtn = footer.querySelector('.tool-call-approve-btn');
        var rejectBtn = footer.querySelector('.tool-call-reject-btn');
        approveBtn.setAttribute('data-session-id', chat.sessionId);
        approveBtn.setAttribute('data-call-id', chat.toolCallId);
        approveBtn.onclick = function() { handleApprovalClick(this, true); };
        rejectBtn.setAttribute('data-session-id', chat.sessionId);
        rejectBtn.setAttribute('data-call-id', chat.toolCallId);
        rejectBtn.onclick = function() { handleApprovalClick(this, false); };
        callDiv.appendChild(footer);
    }
    container.appendChild(callDiv);
    return container;
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

        // 会话隔离：后端按用户广播，非当前会话的运行事件（任务/项目会话后台运行）不更新当前界面；
        // session-title 仍需更新左侧会话列表标题；received/done/error/task-done 维护会话列表的运行loading图标
        if (data.sessionId && data.sessionId !== currentSessionId) {
            if (data.type === 'session-title') {
                updateSessionTitle(data.sessionId, data.content);
            } else if (data.type === 'received') {
                setSessionRunning(data.sessionId, true);
            } else if (data.type === 'done' || data.type === 'error' || data.type === 'task-done') {
                setSessionRunning(data.sessionId, false);
            }
            return;
        }

        if (data.type !== 'received' && data.type !== 'session-title' && data.type !== 'user_message' && data.type !== 'token_usage') {
            removeLoadingMessage();
        }

        if (data.type === 'received') {
            showLoadingMessage();
            setSessionRunning(data.sessionId || currentSessionId, true);
            // 会话开始运行即禁用输入区（覆盖任务看板/项目迭代/其他标签页触发的运行，
            // 本地 sendMessage 的禁用是幂等的）
            disableInput();
        } else if (data.type === 'user_message') {
            // 用户消息回显：后端入库后推送（含任务/项目会话广播），统一渲染到消息列表
            handleUserMessageEcho(data);
        } else if (data.type === 'chunk') {
            handleStreamingChunk(data.content, requestId);
        } else if (data.type === 'tool_call') {
            // 工具调用开始：刷新工具执行统计（会话总数/执行器已执行/上限）
            if (data.status === 'started') {
                loadToolStats();
            }
            handleToolCall(data, requestId);
        } else if (data.type === 'thinking') {
            handleThinking(data, requestId);
        } else if (data.type === 'done') {
            setSessionRunning(data.sessionId || currentSessionId, false);
            handleStreamingDone(data.message, data.response, requestId);
        } else if (data.type === 'session-title') {
            updateSessionTitle(data.sessionId, data.content);
        } else if (data.type === 'task-done') {
            setSessionRunning(data.sessionId || currentSessionId, false);
            var msgState = streamingMessages[requestId];
            if (msgState && msgState.currentStreamingMessage) {
                msgState.currentStreamingMessage.appendChild(createMessageFooter());
                msgState.currentStreamingMessage = null;
            }
            enableInput();
        } else if (data.type === 'error') {
            setSessionRunning(data.sessionId || currentSessionId, false);
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

        // 消息流中同步渲染扳手图标：连续工具调用合并同一行
        appendToolInlineIcon(messagesDiv, data);

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
                fetch('/api/session/'+encodeURIComponent(data.sessionId || currentSessionId)+'/tool/stop'+ '?callId=' + data.toolCallId, {
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
                postToolApproval(data.sessionId, data.toolCallId, true).then(function() {
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
                postToolApproval(data.sessionId, data.toolCallId, false).then(function() {
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

/**
 * 在消息流中渲染工具调用扳手图标。
 * 与历史渲染保持一致：若消息流最后一个元素已是图标行（连续工具调用），复用同一行；否则新建一行。
 */
function appendToolInlineIcon(messagesDiv, data) {
    if (!messagesDiv) return;
    // 已渲染过该工具调用的图标则跳过（同一工具调用会有多个状态事件）
    if (messagesDiv.querySelector('.tool-inline-icon[data-tool-call-id="' + data.toolCallId + '"]')) {
        return;
    }
    var row = messagesDiv.lastElementChild;
    if (!row || !row.classList || !row.classList.contains('tool-inline-row')) {
        row = document.createElement('div');
        row.className = 'tool-inline-row';
        messagesDiv.appendChild(row);
    }
    var inlineName = data.toolName || 'Unknown Tool';
    if (data.toolDescriptions && data.toolDescriptions.length > 0) {
        inlineName = data.toolDescriptions[0] || inlineName;
    }
    var icon = document.createElement('span');
    icon.className = 'tool-inline-icon';
    icon.setAttribute('data-tool-call-id', data.toolCallId);
    icon.title = inlineName; // 鼠标移上显示工具名称
    icon.textContent = '🔧';
    icon.onclick = function() {
        scrollToToolCall(icon);
    };
    row.appendChild(icon);
}

/**
 * 点击消息流中的扳手图标：右侧工具执行列表滑动到对应工具调用项并展开详情。
 */
function scrollToToolCall(el) {
    var callId = el.getAttribute('data-tool-call-id');
    if (!callId) return;
    var toolCall = document.querySelector('.tool-call[data-tool-call-id="' + callId + '"]');
    if (!toolCall) return;

    // 仅滚动工具执行列表容器，避免整页滚动
    var container = document.getElementById('toolExecList');
    if (container) {
        var containerRect = container.getBoundingClientRect();
        var itemRect = toolCall.getBoundingClientRect();
        container.scrollTop += itemRect.top - containerRect.top - 10;
    }

    // 展开详情
    var body = toolCall.querySelector('.tool-call-body');
    if (body) {
        body.classList.remove('collapsed');
        body.classList.add('open');
    }
    var toggle = toolCall.querySelector('.tool-call-toggle');
    if (toggle) {
        toggle.classList.add('open');
    }

    // 高亮定位到的工具调用项，便于识别
    toolCall.classList.remove('flash');
    void toolCall.offsetWidth; // 重新触发动画
    toolCall.classList.add('flash');
}

/** 用户消息回显：后端入库后推送的用户消息通知（含图片附件），按原直发结构渲染到消息列表 */
function handleUserMessageEcho(data) {
    var messagesDiv = document.getElementById('chatMessages');

    // 图片附件：每张图片作为独立的消息记录
    var files = data.files || [];
    files.forEach(function(f) {
        if (!f || !f.url) return;
        var imageMessageDiv = document.createElement('div');
        imageMessageDiv.className = 'message user';

        var imageLabel = document.createElement('div');
        imageLabel.className = 'message-label';
        imageLabel.textContent = '你';
        imageMessageDiv.appendChild(imageLabel);

        if(f.type == 'image'){
            var img = document.createElement('img');
            img.src = f.url;
            img.setAttribute('data-is-agent', 'false');
            img.setAttribute('data-attachment-id', f.id);
            img.className = 'message-content message-image';
            img.alt = f.originalName;
            imageMessageDiv.appendChild(img);
        }else{
            var a = document.createElement('a');
            a.className = 'message-content message-'+f.type;
            a.href = f.url;
            a.setAttribute('data-is-agent', 'false');
            a.setAttribute('data-attachment-id', f.id);
            a.title = f.originalName;
            a.text=f.originalName;
            imageMessageDiv.appendChild(a);
        }

        imageMessageDiv.appendChild(createMessageFooter(''));

        messagesDiv.appendChild(imageMessageDiv);
    });

    // 文本消息作为独立的消息记录
    if (data.content && String(data.content).trim() !== '') {
        var textMessageDiv = document.createElement('div');
        textMessageDiv.className = 'message user';

        var textLabel = document.createElement('div');
        textLabel.className = 'message-label';
        textLabel.textContent = '你';
        textMessageDiv.appendChild(textLabel);

        var textContent = document.createElement('div');
        textContent.className = 'message-content';
        textContent.textContent = data.content;
        textMessageDiv.appendChild(textContent);

        textMessageDiv.appendChild(createMessageFooter(data.content));

        messagesDiv.appendChild(textMessageDiv);
    }

    var emptyState = document.getElementById('chatHistoryEmptyState');
    if (emptyState) {
        emptyState.classList.add('hide');
    }
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
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
            // 用户消息不再本地直渲染：后端入库后通过 user_message 通知统一渲染（多端实时可见，含图片附件）

            var deepBtn = document.getElementById('deepThinkBtn');
            var filesPayload = attachedFiles.map(function(f) {
                return { url: f.url, type: f.type,id:f.id,originalName:f.name };
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
    startLockCountdown();
}

function enableInput() {
    var wrapper = document.querySelector('.chat-input-wrapper');
    var input = document.getElementById('messageInput');
    var sendBtn = document.getElementById('sendBtn');
    var runningBtn = document.getElementById('runningBtn');
    if (wrapper) wrapper.classList.remove('disabled');
    if (sendBtn) sendBtn.classList.remove('hide');
    if (runningBtn) runningBtn.classList.add('hide');
    stopLockCountdown();
    if (input) input.focus();
}

// ===== 执行器可重置锁（看门狗）剩余时间倒计时 =====
var lockRemainingSeconds = 0;
var lockElapsedSeconds = 0;
var lockPollTimer = null;
var lockCountdownTimer = null;

/**
 * 启动剩余时间倒计时：每5秒查询一次后端剩余时间和已运行时长，前端每秒本地递增/递减展示
 */
function startLockCountdown() {
    if (lockCountdownTimer) return;
    updateLockRemaining();
    lockPollTimer = setInterval(updateLockRemaining, 5000);
    lockCountdownTimer = setInterval(function() {
        if (lockRemainingSeconds > 0) {
            lockRemainingSeconds--;
        }
        if (lockElapsedSeconds >= 0) {
            lockElapsedSeconds++;
        }
        renderLockCountdown();
    }, 1000);
}

/**
 * 停止倒计时并清空显示
 */
function stopLockCountdown() {
    if (lockPollTimer) clearInterval(lockPollTimer);
    if (lockCountdownTimer) clearInterval(lockCountdownTimer);
    lockPollTimer = null;
    lockCountdownTimer = null;
    lockRemainingSeconds = 0;
    lockElapsedSeconds = 0;
    renderLockCountdown();
}

/**
 * 查询后端看门狗剩余时间和已运行时长并刷新本地值
 */
function updateLockRemaining() {
    if (!currentSessionId) return;
    fetch('/api/session/' + encodeURIComponent(currentSessionId) + '/lock-remaining')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code === 200 && resp.data) {
                lockRemainingSeconds = resp.data.remainingSeconds || 0;
                if (resp.data.elapsedSeconds > 0) {
                    lockElapsedSeconds = resp.data.elapsedSeconds;
                }
                renderLockCountdown();
            }
        })
        .catch(function() { /* 静默失败，下次轮询重试 */ });
}

/**
 * 渲染已运行时长（第一行）和剩余时间（第二行）到运行中按钮
 */
function renderLockCountdown() {
    var elapsedEl = document.getElementById('runningElapsed');
    if (elapsedEl) {
        elapsedEl.textContent = lockElapsedSeconds > 0 ? ('(' + lockElapsedSeconds + 's)') : '';
    }
    var el = document.getElementById('runningCountdown');
    if (!el) return;
    el.textContent = lockRemainingSeconds > 0 ? ('超时(' + lockRemainingSeconds + 's)') : '';
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
// 今日用量汇总基准（来自 daily-stats 接口），收到 token 消息时在此基准上累加增量
var tokenDailyStats = { inputTokens: 0, outputTokens: 0, totalTokens: 0 };

function handleTokenUsageMessage(data) {
    if (!currentAgentId) return;
    if (data.sessionId !== currentSessionId) return;

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

    // 今日汇总在 daily-stats 基准上累加本次用量（图表数据为最近30条滑动窗口，不能作为全天汇总依据）
    tokenDailyStats.inputTokens += entry.inputTokens || 0;
    tokenDailyStats.outputTokens += entry.outputTokens || 0;
    tokenDailyStats.totalTokens += entry.totalTokens || 0;
    updateTokenTitle(tokenDailyStats.inputTokens, tokenDailyStats.outputTokens, tokenDailyStats.totalTokens);
}

function loadTokenUsage(minId) {
    if (!currentAgentId) return;
    var url = '/api/token-usage/today?sessionId=' + currentSessionId;
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
    var statsUrl = '/api/token-usage/daily-stats?startTime=' + encodeURIComponent(startStr) + '&endTime=' + encodeURIComponent(endStr) + '&sessionId=' + currentSessionId;
    fetch(statsUrl).then(function(r) { return r.json(); }).then(function(sres) {
        if (sres.code === 200 && sres.data && sres.data.length > 0) {
            var today = sres.data[0];
            // 以接口全天汇总为基准，后续 token 消息在此基础上累加增量
            tokenDailyStats = {
                inputTokens: today.inputTokens || 0,
                outputTokens: today.outputTokens || 0,
                totalTokens: today.totalTokens || 0
            };
            updateTokenTitle(tokenDailyStats.inputTokens, tokenDailyStats.outputTokens, tokenDailyStats.totalTokens);
        }
    });
}

/**
 * 工具调用统计：会话累计调用总数 / 执行器已执行 / 执行器上限
 * 页面加载与工具调用开始（status=started）时刷新
 */
function loadToolStats() {
    if (!currentSessionId) return;
    fetch('/api/session/' + encodeURIComponent(currentSessionId) + '/tool-stats')
        .then(function(r) { return r.json(); })
        .then(function(res) {
            var el = document.getElementById('toolExecStats');
            if (!el) return;
            if (res.code !== 200 || !res.data) { el.textContent = ''; return; }
            var total = res.data.sessionTotal || 0;
            var executed = res.data.executedCount || 0;
            var max = res.data.maxToolInvocations || 0;
            // 上限为0表示不限制
            var maxText = max > 0 ? max : '∞';
            el.textContent = '共' + total + '次 · 本次' + executed + '/' + maxText;
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

    // 默认选中当前会话所属的类型（无会话时默认聊天）
    var currentSession = (initialChatSessions || []).find(function(s) { return s.sessionId === currentSessionId; });
    sessionTypeFilter = currentSession ? sessionFilterTypeOf(currentSession.bizType) : 'chat';
    var initialFilterBox = document.getElementById('sessionTypeFilter');
    if (initialFilterBox) {
        initialFilterBox.querySelectorAll('.session-type-tab').forEach(function(tab) {
            tab.classList.toggle('active', tab.getAttribute('data-type') === sessionTypeFilter);
        });
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
        // 初始化向上滚动加载更早历史消息
        initHistoryScroll();
    }
    var input = document.getElementById('messageInput');
    if (input) {
        input.focus();
    }

    if (currentAgentId) {
        connectWebSocket();
        loadTokenUsage();
        loadToolStats();
    }

    // 页面加载时会话已在运行：启动看门狗剩余时间倒计时
    var initRunningBtn = document.getElementById('runningBtn');
    if (initRunningBtn && !initRunningBtn.classList.contains('hide')) {
        startLockCountdown();
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
                                    defaultModelName = model.modelAlias || model.modelName;
                                }
                                html += '<div class="model-sub-item' + activeClass + '" data-model-id="' + model.id + '" data-model-name="' + escapeHtml(model.modelAlias || model.modelName) + '">';
                                html += '<span class="model-sub-item-check">✓</span>';
                                html += '<span class="model-sub-item-name">' + escapeHtml(model.modelAlias || model.modelName) + '</span>';
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

/**
 * 会话业务类型归一为筛选类型：按 AgentExecutorBizTypeEnum 的 value 判断
 * workflowTaskChat→task / projectChat→project / 其他→chat
 * 兼容历史数据（旧版写入的 workflow-task-chat / project-chat）
 */
function sessionFilterTypeOf(bizType) {
    if (bizType === 'workflowTaskChat' || bizType === 'workflow-task-chat') return 'task';
    if (bizType === 'projectChat' || bizType === 'project-chat') return 'project';
    return 'chat';
}

/**
 * 切换会话列表类型筛选：更新选项选中态并重新渲染列表。
 */
function filterSessionsByType(type) {
    if (type !== 'chat' && type !== 'project' && type !== 'task') return;
    if (sessionTypeFilter === type) return;
    sessionTypeFilter = type;

    var filterBox = document.getElementById('sessionTypeFilter');
    if (filterBox) {
        filterBox.querySelectorAll('.session-type-tab').forEach(function(tab) {
            tab.classList.toggle('active', tab.getAttribute('data-type') === type);
        });
    }

    renderSessionList(initialChatSessions);
}

/**
 * 维护会话运行状态：更新 runningSessionIds 并通过分类标签的流光边框标记运行状态
 */
function setSessionRunning(sessionId, running) {
    if (!sessionId) return;
    if (running) {
        runningSessionIds[sessionId] = true;
    } else {
        delete runningSessionIds[sessionId];
    }
    var container = document.getElementById('sessionList');
    if (!container) return;
    var item = container.querySelector('.session-list-item[data-session-id="' + escapeHtml(sessionId) + '"]');
    if (!item) return;
    var tag = item.querySelector('.session-tag');
    if (tag) {
        if (running) {
            tag.classList.add('session-tag-running');
        } else {
            tag.classList.remove('session-tag-running');
        }
    }
}

function renderSessionList(sessions) {
    var container = document.getElementById('sessionList');
    if (!container) return;

    // 按当前筛选类型过滤会话
    var filtered = (sessions || []).filter(function(s) {
        return sessionFilterTypeOf(s.bizType) === sessionTypeFilter;
    });

    if (filtered.length === 0) {
        var typeLabel = sessionTypeFilter === 'project' ? '项目' : (sessionTypeFilter === 'task' ? '任务' : '聊天');
        container.innerHTML = '<div class="session-list-empty">暂无' + typeLabel + '会话</div>';
        return;
    }

    var html = '';
    filtered.forEach(function(s) {
        // 初始渲染时从后端 running 字段播种运行状态
        if (s.running) {
            runningSessionIds[s.sessionId] = true;
        }
        var activeClass = (s.sessionId === currentSessionId) ? ' active' : '';
        var isTask = s.bizType === 'workflowTaskChat' || s.bizType === 'workflow-task-chat';
        var isProject = s.bizType === 'projectChat' || s.bizType === 'project-chat';
        var isChat = !isTask && !isProject;
        var taskClass = isTask ? ' session-list-item-task' : '';
        var projectClass = isProject ? ' session-list-item-project' : '';
        var title = s.title || '未命名会话';
        var timeStr = formatSessionTime(s.lastUpdateTime || s.createTime);
        // 运行中：分类标签附加流光边框类以作标记
        var runClass = runningSessionIds[s.sessionId] ? ' session-tag-running' : '';
        // 任务会话标识标签
        var taskBadge = isTask ? '<span class="session-tag session-tag-task' + runClass + '" title="任务会话">任务</span>' : '';
        // 项目会话标识标签
        var projectBadge = isProject ? '<span class="session-tag session-tag-project' + runClass + '" title="项目会话">项目</span>' : '';
        // 聊天会话标识标签
        var chatBadge = isChat ? '<span class="session-tag session-tag-chat' + runClass + '" title="聊天">聊天</span>' : '';
        html += '<div class="session-list-item' + activeClass + taskClass + projectClass + '" data-session-id="' + escapeHtml(s.sessionId) + '" data-id="' + (s.id || '') + '" data-biz-type="' + (s.bizType || '') + '">';
        html += taskBadge + projectBadge + chatBadge;
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

    // 新会话由首页聊天创建，属聊天类型：当前筛选非聊天时不插入，避免类型错乱
    if (sessionTypeFilter !== 'chat') {
        return;
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

    // 补充分类标签：新会话通常为聊天类型，与列表项结构保持一致
    if (!item.querySelector('.session-tag')) {
        var chatTag = document.createElement('span');
        chatTag.className = 'session-tag session-tag-chat';
        chatTag.setAttribute('title', '聊天');
        chatTag.textContent = '聊天';
        item.insertBefore(chatTag, item.firstChild);
    }
    // 运行中：给分类标签附加流光边框类
    if (runningSessionIds[sessionId]) {
        var tag = item.querySelector('.session-tag');
        if (tag) {
            tag.classList.add('session-tag-running');
        }
    }

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
    postToolApproval(sessionId, callId, allowed).then(function() {
        footer.remove();
    }).catch(function() {
        btn.disabled = false;
        if (approveBtn) approveBtn.disabled = false;
        if (rejectBtn) rejectBtn.disabled = false;
    });
}

/**
 * 提交工具调用审批请求
 * @param sessionId  会话ID（为空时回退到 currentSessionId）
 * @param callId     工具调用ID
 * @param allowed    true=通过, false=拒绝
 * @returns {Promise<Response>}
 */
function postToolApproval(sessionId, callId, allowed) {
    var url = '/api/session/' + encodeURIComponent(sessionId || currentSessionId)
        + '/tool/approval'
        + '?callId=' + encodeURIComponent(callId)
        + '&allowed=' + allowed;
    return fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
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
    formData.append('files', file);
    formData.append('source', 'chat');

    // 创建预览占位（上传中）
    var tempId = 'file_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6);
    var previewItem = createPreviewItem(tempId, null, true);
    document.getElementById('filePreviewArea').appendChild(previewItem);

    fetch('/api/attachments/upload', {
        method: 'POST',
        body: formData
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        // 移除占位
        removePreviewItem(tempId);
        if (resp.code === 200 && resp.data && resp.data.length > 0) {
            var att = resp.data[0];
            var fileInfo = {
                url: att.url,
                type: att.fileType,
                name: att.originalName,
                id: att.id
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
