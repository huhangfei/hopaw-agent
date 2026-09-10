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

// ===== 深度思考等级滑块 =====
// 等级元数据：固定从低到高排序，color 驱动滑块填充与按钮发光随等级加深的视觉体验
var THINKING_LEVELS = [
    {code: 'none',    name: '无',   color: '#94a3b8'},
    {code: 'minimal', name: '极轻', color: '#7dd3fc'},
    {code: 'low',     name: '低',   color: '#38bdf8'},
    {code: 'medium',  name: '中',   color: '#667eea'},
    {code: 'high',    name: '高',   color: '#8b5cf6'},
    {code: 'xhigh',   name: '极高', color: '#ec4899'},
    {code: 'max',     name: '最大', color: '#ef4444'}
];
var currentThinkingLevels = [];   // 当前模型支持的等级（已按从低到高排序）
var currentThinkingLevel = null;  // 当前选中等级 code
var thinkingLevelFromModelDefault = false; // 会话/智能体均未配置等级：待模型加载后取支持列表末位
var thinkingModelById = {};       // 模型ID → 模型数据（含 supportedThinkingLevelsArray）

/**
 * hex 颜色转 rgba 字符串（等级发光颜色用）
 */
function hexToRgba(hex, alpha) {
    var h = (hex || '').replace('#', '');
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    var r = parseInt(h.substring(0, 2), 16);
    var g = parseInt(h.substring(2, 4), 16);
    var b = parseInt(h.substring(4, 6), 16);
    return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
}

function openAttachmentPreview(id) {
    var overlay = document.createElement('div');
    overlay.className = 'attachment-preview-overlay';
    overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };

    var wrapper = document.createElement('div');
    wrapper.className = 'attachment-preview-wrapper';

    var closeBtn = document.createElement('button');
    closeBtn.className = 'attachment-preview-close';
    closeBtn.textContent = '×';
    closeBtn.onclick = function() { overlay.remove(); };
    wrapper.appendChild(closeBtn);

    var iframe = document.createElement('iframe');
    iframe.src = '/attachment-preview/' + id;
    wrapper.appendChild(iframe);

    overlay.appendChild(wrapper);
    document.body.appendChild(overlay);
}
// 会话列表类型筛选：chat=聊天 / project=项目 / task=任务，默认取当前会话的类型
var sessionTypeFilter = 'chat';
// 运行中的会话ID集合：初始渲染时取自后端 running 字段，运行期由 WebSocket 事件维护
var runningSessionIds = {};

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

function createMessageFooter(messageText, requestId) {
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

    // 用户消息关联请求编号时展示 bug 图标：点击查看该次请求的完整请求/响应日志
    if (requestId) {
        var bugBtn = document.createElement('button');
        bugBtn.className = 'message-bug-btn';
        bugBtn.title = '查看该请求的请求/响应日志';
        bugBtn.innerHTML = '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M20 8h-2.81c-.45-.78-1.07-1.45-1.82-1.96L17 4.41 15.59 3l-2.17 2.17C12.96 5.06 12.49 5 12 5c-.49 0-.96.06-1.41.15L8.41 3 7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81c1.04 1.79 2.97 3 5.19 3s4.15-1.21 5.19-3H20v-2h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20V8zm-6 8h-4v-2h4v2zm0-4h-4v-2h4v2z"/></svg>';
        bugBtn.onclick = function() { showRequestLogModal(requestId); };
        footer.appendChild(bugBtn);
    }

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
    copyTextToClipboard(btn, content);
}

/** 写入剪贴板并给出复制成功反馈（单条消息复制与回合整盒复制共用） */
function copyTextToClipboard(btn, content) {
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
    var messageContents = document.querySelectorAll('.message-content[data-raw-content], .thinking-content');
    messageContents.forEach(function(el) {
        var rawContent = el.getAttribute('data-raw-content');
        if (rawContent) {
            el.innerHTML = renderMarkdown(rawContent);
        }
    });
}

/* ================= Agent 回合大盒子：连续的 agent 消息（思考/文本/工具/错误/警告）归入同一容器，视觉上为一个整体 ================= */
var currentAgentTurn = null;

/** 当前选中的智能体名称（回合盒子头部标签用） */
function getCurrentAgentName() {
    var s = document.querySelector('.agent-select-toolbar');
    return s ? s.options[s.selectedIndex].text : 'Agent';
}

/**
 * 获取当前回合容器：
 * 1. 已有连接中的容器直接复用（同一轮输出持续追加）；
 * 2. 页面刷新后引用丢失时，若消息列表最后一个元素仍是回合盒子则复用（续接同一轮输出）；
 * 3. 否则新建（含智能体名称头部标签）并追加到消息列表末尾。
 */
function getAgentTurnContainer(messagesDiv) {
    if (currentAgentTurn && currentAgentTurn.isConnected && currentAgentTurn.parentNode === messagesDiv) {
        return currentAgentTurn;
    }
    var last = messagesDiv.lastElementChild;
    if (last && last.classList && last.classList.contains('agent-turn')) {
        currentAgentTurn = last;
        return currentAgentTurn;
    }
    currentAgentTurn = buildAgentTurnBox(getCurrentAgentName());
    messagesDiv.appendChild(currentAgentTurn);
    return currentAgentTurn;
}

/** 关闭当前回合：用户消息等非 agent 内容出现时调用，后续 agent 消息将开新盒子 */
function closeAgentTurn() {
    currentAgentTurn = null;
}

/** 构造 agent 回合大盒子（含头部智能体名称标签） */
function buildAgentTurnBox(agentName) {
    var box = document.createElement('div');
    box.className = 'agent-turn';
    var header = document.createElement('div');
    header.className = 'message-label agent-turn-label';
    header.textContent = agentName || 'Agent';
    box.appendChild(header);
    return box;
}

/** 把新拉取的回合盒子并入页面上已有的相邻回合盒子：保留已有头部标签，内容按原顺序插到最前，游标前移 */
function mergeAgentTurnIntoExisting(incoming, existing) {
    var frag = document.createDocumentFragment();
    while (incoming.firstChild) {
        var child = incoming.firstChild;
        if (child.classList && (child.classList.contains('agent-turn-label') || child.classList.contains('agent-turn-footer'))) {
            child.remove();
            continue;
        }
        frag.appendChild(child);
    }
    var label = existing.querySelector('.agent-turn-label');
    if (label && label.nextSibling) {
        existing.insertBefore(frag, label.nextSibling);
    } else {
        existing.appendChild(frag);
    }
    // 游标前移到新拉取的最早一条
    var id = incoming.getAttribute('data-msg-id');
    var time = incoming.getAttribute('data-create-time');
    if (id != null) { existing.setAttribute('data-msg-id', id); }
    if (time != null) { existing.setAttribute('data-create-time', time); }
}

/**
 * 回合盒子底部 footer：整个盒子仅一个时间（最后一条消息的时间）+ 一个复制按钮（复制整盒全部内容）。
 * 每有小节消息完成时调用：footer 始终保持在盒子末尾并刷新时间。
 */
function touchAgentTurnFooter(turnBox, timeText) {
    if (!turnBox) return;
    var footer = turnBox.querySelector('.agent-turn-footer');
    if (!footer) {
        footer = document.createElement('div');
        footer.className = 'message-footer agent-turn-footer';

        var timeDiv = document.createElement('div');
        timeDiv.className = 'message-time';
        footer.appendChild(timeDiv);

        var copyBtn = document.createElement('button');
        copyBtn.className = 'message-copy-btn';
        copyBtn.title = '复制全部内容';
        copyBtn.innerHTML = '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>';
        copyBtn.onclick = function() { copyAgentTurnContent(this); };
        footer.appendChild(copyBtn);
    }
    if (timeText) {
        var t = footer.querySelector('.message-time');
        if (t) { t.textContent = timeText; }
    }
    turnBox.appendChild(footer);
}

/** 小节消息追加进回合盒子：追加后保持盒子 footer 始终位于末尾 */
function appendToAgentTurn(box, node) {
    if (!box) return;
    box.appendChild(node);
    var footer = box.querySelector('.agent-turn-footer');
    if (footer) {
        box.appendChild(footer);
    }
}

/** 复制回合盒子内全部内容：按顺序拼接思考/文本/错误/警告等各小节 */
function copyAgentTurnContent(btn) {
    var box = btn.closest('.agent-turn');
    if (!box) return;
    var parts = [];
    box.querySelectorAll('.message').forEach(function(msg) {
        var contentEl = msg.querySelector('.message-content');
        if (!contentEl) return;
        var text = contentEl.getAttribute('data-raw-content');
        if (text == null || String(text).trim() === '') {
            text = contentEl.textContent;
        }
        text = (text || '').trim();
        if (text) { parts.push(text); }
    });
    copyTextToClipboard(btn, parts.join('\n\n'));
}

// ================= 思考消息收缩/展开 =================

/**
 * 构建思考小节（内容 + 展开/收起开关）：
 * 历史消息默认收起仅显示两行；实时输出时展开，完成后自动收起
 */
function buildThinkingSection(content, expanded) {
    var section = document.createElement('div');
    section.className = 'thinking-section' + (expanded ? ' expanded' : '');

    var think = document.createElement('div');
    think.className = 'message-content thinking-content';
    think.textContent = content;
    think.setAttribute('data-raw-content', content);
    section.appendChild(think);

    var toggle = document.createElement('span');
    toggle.className = 'thinking-toggle';
    toggle.textContent = expanded ? '收起' : '展开';
    toggle.onclick = function () {
        setThinkingExpanded(section, !section.classList.contains('expanded'));
    };
    section.appendChild(toggle);

    updateThinkingToggle(section);
    return section;
}

/** 设置思考小节展开/收起状态 */
function setThinkingExpanded(section, expanded) {
    if (!section) return;
    section.classList.toggle('expanded', expanded);
    updateThinkingToggle(section);
}

/** 同步开关文案；收起状态下内容不足两行时隐藏开关 */
function updateThinkingToggle(section) {
    var toggle = section.querySelector('.thinking-toggle');
    var content = section.querySelector('.thinking-content');
    if (!toggle || !content) return;
    if (section.classList.contains('expanded')) {
        toggle.textContent = '收起';
        toggle.style.display = '';
        return;
    }
    toggle.textContent = '展开';
    if (!section.isConnected) {
        // 尚未插入文档时高度不可测，插入后再判定
        requestAnimationFrame(function () { updateThinkingToggle(section); });
        return;
    }
    // 容差覆盖单段 margin-bottom（10px）：恰好两行的内容不显示展开按钮
    toggle.style.display = content.scrollHeight > content.clientHeight + 10 ? '' : 'none';
}


// ================= 会话历史加载（首次进入 + 向上滚动翻页） =================
var historyLoadState = { oldestId: null, oldestTime: null, allLoaded: false, loading: false };
var HISTORY_PAGE_SIZE = 50;
var HISTORY_INITIAL_LIMIT = 100; // 与后端首次加载接口单次上限一致

/** 初始化历史分页游标并监听滚动（游标在首次历史加载完成后设置） */
function initHistoryScroll() {
    var messagesDiv = document.getElementById('chatMessages');
    if (!messagesDiv) return;
    messagesDiv.addEventListener('scroll', onHistoryScroll);
}

/**
 * 页面首次进入会话：拉取最新一段历史并渲染。
 * 与向上翻页共用 prependHistoryMessages/buildHistoryMessageNode/buildToolCallStaticNode 渲染逻辑。
 */
function loadInitialHistory() {
    var messagesDiv = document.getElementById('chatMessages');
    if (!messagesDiv || !currentSessionId) return Promise.resolve();
    return fetch('/api/session/' + encodeURIComponent(currentSessionId) + '/history/latest?limit=' + HISTORY_INITIAL_LIMIT)
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code !== 200) {
                // 会话不存在（如新建未落库的会话）：视为无历史
                historyLoadState.allLoaded = true;
                return;
            }
            var data = res.data || {};
            var list = data.list || [];
            // 接口按时间倒序返回：反转为正序后渲染
            list.reverse();
            if (list.length > 0) {
                prependHistoryMessages(list);
                // 渲染 markdown（agent 消息）
                renderAllMessages();
                // 隐藏空状态提示
                var emptyState = document.getElementById('chatHistoryEmptyState');
                if (emptyState) emptyState.classList.add('hide');
                // 初始化向上翻页游标为当前最早一条
                var oldest = list[0];
                historyLoadState.oldestId = oldest.id;
                historyLoadState.oldestTime = formatHistoryIsoTime(oldest.createTime);
            }
            if (!data.hasMore) {
                historyLoadState.allLoaded = true;
            }
            // 首次加载滚动到底部
            messagesDiv.scrollTop = messagesDiv.scrollHeight;
            var toolExecList = document.getElementById('toolExecList');
            if (toolExecList) toolExecList.scrollTop = toolExecList.scrollHeight;
        })
        .catch(function(e) {
            console.error('加载会话历史失败:', e);
        });
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

    // 构建中的 agent 回合盒子与组内工具图标行：连续 agent 消息（含工具调用）合为一个视觉整体
    var turnBox = null;
    var toolRow = null;
    // 盒内最后一条消息的时间：整个盒子只在底部展示这一个时间
    var lastTurnTime = null;

    /** 把节点插到参照前（无参照则追加），保持正序 */
    function placeBeforeRef(node) {
        if (insertRef) {
            messagesDiv.insertBefore(node, insertRef);
        } else {
            messagesDiv.appendChild(node);
        }
    }

    /** 结束当前回合：工具行收尾进盒子，盒子落位（必要时与页面上相邻的回合盒子合并） */
    function closeTurn() {
        if (toolRow && turnBox) {
            turnBox.appendChild(toolRow);
            toolRow = null;
        }
        if (turnBox) {
            // 整盒唯一 footer：最后一条消息的时间 + 整盒复制按钮
            touchAgentTurnFooter(turnBox, lastTurnTime);
            // 分页边界合并：本次拉取的最早回合与页面现有最早回合相邻（同一轮输出被分页截断）时并成一个盒子
            if (insertRef && insertRef.classList && insertRef.classList.contains('agent-turn')) {
                mergeAgentTurnIntoExisting(turnBox, insertRef);
            } else {
                placeBeforeRef(turnBox);
            }
            turnBox = null;
            lastTurnTime = null;
        }
    }

    list.forEach(function(chat) {
        // 工具调用与 agent 消息同属一个回合；用户消息（文本/附件）结束当前回合
        var isAgentContent = chat.messageType === 'tool_call' || chat.role === 'agent';
        if (!isAgentContent) {
            closeTurn();
            placeBeforeRef(buildHistoryMessageNode(chat));
            return;
        }
        if (!turnBox) {
            turnBox = buildAgentTurnBox(chat.agent && chat.agent.name ? chat.agent.name : 'Agent');
            // 回合盒子游标 = 组内最早一条消息（供下一次分页定位锚点/边界合并）
            turnBox.setAttribute('data-msg-id', chat.id);
            turnBox.setAttribute('data-create-time', formatHistoryIsoTime(chat.createTime));
        }
        lastTurnTime = formatMessageTime(new Date(formatHistoryIsoTime(chat.createTime)));
        if (chat.messageType === 'tool_call') {
            if (!toolRow) {
                toolRow = document.createElement('div');
                toolRow.className = 'tool-inline-row';
            }
            var icon = document.createElement('span');
            icon.className = 'tool-inline-icon';
            icon.setAttribute('data-tool-call-id', chat.toolCallId);
            icon.title = chat.toolName || '';
            renderToolInlineIconContent(icon, chat.toolName);
            icon.onclick = function() { scrollToToolCall(this); };
            toolRow.appendChild(icon);
            if (toolExecList) {
                toolFragment.appendChild(buildToolCallStaticNode(chat));
            }
        } else {
            if (toolRow) {
                turnBox.appendChild(toolRow);
                toolRow = null;
            }
            turnBox.appendChild(buildHistoryMessageNode(chat));
        }
    });
    closeTurn();
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
    // 流式消息编号：与实时推送的 messageNo 对应，页面刷新后可凭编号续接追加片段
    if (chat.messageNo) {
        div.setAttribute('data-message-no', chat.messageNo);
    }

    var isAgent = chat.role === 'user' ? false : true;
    // agent 消息归入回合大盒子：名称由盒子头部标签展示，小节仅保留类型标签或无标签
    var label = isAgent ? null : '你';
    var timeText = formatMessageTime(new Date(formatHistoryIsoTime(chat.createTime)));

    var type = chat.messageType;
    if (type === 'attachment') {
        if (label) { appendLabel(div, label); }
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
            img.setAttribute('data-attachment-id', id);
            img.alt = originalName;
            img.style.cursor = 'pointer';
            img.onclick = function() { openAttachmentPreview(id); };
            div.appendChild(img);
        }else{
            var a = document.createElement('a');
            a.className = 'message-content message-'+fileType;
            a.href = 'javascript:void(0)';
            a.setAttribute('data-is-agent', chat.role === 'agent');
            a.setAttribute('data-attachment-id', id);
            a.title = originalName;
            a.text = originalName;
            a.onclick = function() { openAttachmentPreview(id); };
            div.appendChild(a);
        }
        // agent 小节无独立 footer（整盒底部统一展示时间与复制）
        if (!isAgent) { div.appendChild(buildHistoryFooter(timeText, null)); }
    } else if (type === 'thinking') {
        appendLabel(div, '(思考)');
        // 历史思考消息默认收起，仅显示两行，点击展开
        div.appendChild(buildThinkingSection(chat.content, false));
    } else if (type === 'error' || type === 'warn') {
        var inner = document.createElement('div');
        inner.className = type === 'error' ? 'error-message' : 'warn-message';
        appendLabel(inner, type === 'error' ? '(错误)' : '(警告)');
        var errContent = document.createElement('div');
        errContent.className = 'message-content error-content';
        errContent.textContent = chat.content;
        errContent.setAttribute('data-raw-content', chat.content);
        inner.appendChild(errContent);
        div.appendChild(inner);
    } else {
        // text 及其它类型默认按文本处理
        if (label) { appendLabel(div, label); }
        var content = document.createElement('div');
        content.className = 'message-content';
        content.setAttribute('data-is-agent', chat.role === 'agent');
        content.setAttribute('data-raw-content', chat.content);
        if (chat.role === 'user') {
            content.innerHTML = renderMarkdown(chat.content);
        } else {
            content.textContent = chat.content;
        }
        div.appendChild(content);
        // agent 小节无独立 footer（整盒底部统一展示时间与复制）
        if (!isAgent) { div.appendChild(buildHistoryFooter(timeText, chat.content, chat.requestId)); }
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
function buildHistoryFooter(timeText, content, requestId) {
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
    // 用户消息关联请求编号时展示 bug 图标：点击查看该次请求的完整请求/响应日志
    if (requestId) {
        var bugBtn = document.createElement('button');
        bugBtn.className = 'message-bug-btn';
        bugBtn.title = '查看该请求的请求/响应日志';
        bugBtn.innerHTML = '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M20 8h-2.81c-.45-.78-1.07-1.45-1.82-1.96L17 4.41 15.59 3l-2.17 2.17C12.96 5.06 12.49 5 12 5c-.49 0-.96.06-1.41.15L8.41 3 7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81c1.04 1.79 2.97 3 5.19 3s4.15-1.21 5.19-3H20v-2h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20V8zm-6 8h-4v-2h4v2zm0-4h-4v-2h4v2z"/></svg>';
        bugBtn.onclick = function() { showRequestLogModal(requestId); };
        footer.appendChild(bugBtn);
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

/* ================= 用户消息导航圆点 ================= */

/** 导航状态：分组数据 + 滚动校准 rAF 句柄 */
var userMsgNavState = { groups: [], rafId: null };

/**
 * 收集用户消息分组：DOM 中连续的 .message.user 元素（一次发言的文本+附件）归为一组，对应一个圆点
 */
function collectUserMsgGroups(messagesDiv) {
    var groups = [];
    var current = null;
    var children = messagesDiv.children;
    for (var i = 0; i < children.length; i++) {
        var el = children[i];
        if (el.classList.contains('message') && el.classList.contains('user')) {
            if (!current) {
                current = [];
                groups.push(current);
            }
            current.push(el);
        } else {
            current = null;
        }
    }
    return groups;
}

/**
 * 构建圆点悬停弹层内容：图片显示缩略图、附件显示名称、文本截断展示
 */
function buildUserMsgPopupContent(group, popup) {
    var hasContent = false;
    group.forEach(function(el) {
        var contents = el.querySelectorAll('.message-content');
        Array.prototype.forEach.call(contents, function(c) {
            if (c.tagName === 'IMG') {
                // 图片附件：缩略图预览，点击复用附件预览弹窗
                var img = document.createElement('img');
                img.className = 'user-nav-popup-image';
                img.src = c.src;
                img.alt = c.alt || '图片';
                var attachId = c.getAttribute('data-attachment-id');
                if (attachId) {
                    img.onclick = function(e) {
                        e.stopPropagation();
                        openAttachmentPreview(attachId);
                    };
                }
                popup.appendChild(img);
                hasContent = true;
            } else if (c.tagName === 'A') {
                // 其他附件：显示附件名称，点击复用附件预览弹窗
                var name = c.getAttribute('title') || c.textContent || '附件';
                var attach = document.createElement('div');
                attach.className = 'user-nav-popup-attach';
                attach.textContent = '📎 ' + name;
                var id = c.getAttribute('data-attachment-id');
                if (id) {
                    attach.onclick = function(e) {
                        e.stopPropagation();
                        openAttachmentPreview(id);
                    };
                }
                popup.appendChild(attach);
                hasContent = true;
            } else {
                // 文本消息：最多展示 4 行
                var text = (c.getAttribute('data-raw-content') || c.textContent || '').trim();
                if (text !== '') {
                    var textDiv = document.createElement('div');
                    textDiv.className = 'user-nav-popup-text';
                    textDiv.textContent = text;
                    popup.appendChild(textDiv);
                    hasContent = true;
                }
            }
        });
    });
    if (!hasContent) {
        var empty = document.createElement('div');
        empty.className = 'user-nav-popup-text';
        empty.textContent = '(无内容)';
        popup.appendChild(empty);
    }

    // 右下角显示消息时间
    var lastEl = group[group.length - 1];
    var timeEl = lastEl ? lastEl.querySelector('.message-time') : null;
    if (timeEl) {
        var timeDiv = document.createElement('div');
        timeDiv.className = 'user-nav-popup-time';
        timeDiv.textContent = timeEl.textContent;
        popup.appendChild(timeDiv);
    }
}

/**
 * 点击圆点：平滑滚动到对应用户消息并高亮闪烁
 */
function scrollToUserMsgGroup(group) {
    if (!group || group.length === 0) return;
    group[0].scrollIntoView({ behavior: 'smooth', block: 'center' });
    group.forEach(function(el) {
        el.classList.remove('user-msg-flash');
        void el.offsetWidth; // 重新触发动画
        el.classList.add('user-msg-flash');
        if (el._userMsgFlashTimer) {
            clearTimeout(el._userMsgFlashTimer);
        }
        el._userMsgFlashTimer = setTimeout(function() {
            el.classList.remove('user-msg-flash');
        }, 1300);
    });
}

/**
 * 重建导航圆点（消息增删后调用）
 */
function refreshUserMsgNav() {
    var messagesDiv = document.getElementById('chatMessages');
    var nav = document.getElementById('userMsgNav');
    if (!messagesDiv || !nav) return;
    userMsgNavState.groups = collectUserMsgGroups(messagesDiv);
    nav.innerHTML = '';
    if (userMsgNavState.groups.length === 0) {
        nav.classList.add('hide');
        return;
    }
    nav.classList.remove('hide');
    userMsgNavState.groups.forEach(function(group) {
        var dot = document.createElement('div');
        dot.className = 'user-msg-nav-dot';

        var popup = document.createElement('div');
        popup.className = 'user-msg-nav-popup';
        buildUserMsgPopupContent(group, popup);
        var arrow = document.createElement('span');
        arrow.className = 'user-msg-nav-popup-arrow';
        popup.appendChild(arrow);
        dot.appendChild(popup);

        dot.addEventListener('click', function() {
            scrollToUserMsgGroup(group);
        });
        nav.appendChild(dot);
    });
    updateUserMsgNavPositions();
}

/**
 * 重算圆点位置：按消息在内容中的比例定位（minimap 效果），过密时保证最小间距，过多时退化为均匀分布
 */
function updateUserMsgNavPositions() {
    var messagesDiv = document.getElementById('chatMessages');
    var nav = document.getElementById('userMsgNav');
    if (!messagesDiv || !nav) return;
    var groups = userMsgNavState.groups;
    var dots = nav.querySelectorAll('.user-msg-nav-dot');
    if (groups.length === 0 || dots.length !== groups.length) return;
    var railH = nav.clientHeight;
    if (railH <= 0) return;

    var n = groups.length;
    var topPad = 8;
    var usable = Math.max(railH - topPad * 2, 1);
    var minGap = 12;
    var positions = [];
    var i;

    if (n === 1) {
        positions[0] = topPad + usable / 2;
    } else if ((n - 1) * minGap > usable) {
        // 圆点过多：均匀分布
        for (i = 0; i < n; i++) {
            positions[i] = topPad + (usable / (n - 1)) * i;
        }
    } else {
        // 比例定位 + 前向最小间距约束（保持顺序不重叠）
        var scrollH = Math.max(messagesDiv.scrollHeight, 1);
        for (i = 0; i < n; i++) {
            var el = groups[i][0];
            var center = el.offsetTop + el.offsetHeight / 2;
            var pos = topPad + (center / scrollH) * usable;
            var lb = topPad + minGap * i;
            var rb = topPad + usable - minGap * (n - 1 - i);
            positions[i] = Math.min(Math.max(pos, lb), rb);
        }
    }

    for (i = 0; i < n; i++) {
        var dot = dots[i];
        dot.style.top = positions[i] + 'px';
        // 弹层相对圆点定位（transform 已含 translateY(-50%)，top 即弹层中心的圆点局部坐标）：
        // 默认对齐圆点中心(8px)，超出轨道范围时上下夹紧；箭头跟随圆点在弹层内的位置
        var popup = dot.querySelector('.user-msg-nav-popup');
        if (!popup) continue;
        var arrow = popup.querySelector('.user-msg-nav-popup-arrow');
        var popupH = popup.offsetHeight;
        if (popupH <= 0) continue;
        var dotTop = positions[i] - 8; // 圆点顶边在轨道内的位置（圆点高16、translateY(-50%)）
        var center = 8;
        var minC = popupH / 2 - dotTop;          // 弹层顶边不越出轨道顶部
        var maxC = railH - dotTop - popupH / 2;  // 弹层底边不越出轨道底部
        if (minC <= maxC) {
            center = Math.min(Math.max(center, minC), maxC);
        }
        popup.style.top = center + 'px';
        if (arrow) {
            var arrowCenter = 8 - center + popupH / 2;
            arrowCenter = Math.min(Math.max(arrowCenter, 8), Math.max(popupH - 8, 8));
            arrow.style.top = (arrowCenter - 4) + 'px';
        }
    }
}

/**
 * 初始化用户消息导航：消息增删重建圆点；滚动/尺寸变化/图片加载时校准位置
 */
function initUserMsgNav() {
    var messagesDiv = document.getElementById('chatMessages');
    var nav = document.getElementById('userMsgNav');
    if (!messagesDiv || !nav || typeof MutationObserver === 'undefined') return;

    // 消息直接子节点增删（历史加载、新消息、工具行）：重建圆点
    new MutationObserver(function() {
        refreshUserMsgNav();
    }).observe(messagesDiv, { childList: true });

    // 容器尺寸变化（窗口缩放等）：重算圆点位置
    if (typeof ResizeObserver !== 'undefined') {
        new ResizeObserver(function() {
            updateUserMsgNavPositions();
        }).observe(messagesDiv);
    }

    // 消息图片/弹层图片加载后高度变化：校准位置
    var handleImgLoad = function(e) {
        if (e.target && e.target.tagName === 'IMG') {
            updateUserMsgNavPositions();
        }
    };
    messagesDiv.addEventListener('load', handleImgLoad, true);
    nav.addEventListener('load', handleImgLoad, true);

    // 滚动时校准（流式输出、markdown 渲染等引起的高度漂移在此自校正）
    messagesDiv.addEventListener('scroll', function() {
        if (!userMsgNavState.rafId) {
            userMsgNavState.rafId = requestAnimationFrame(function() {
                userMsgNavState.rafId = null;
                updateUserMsgNavPositions();
            });
        }
    });

    refreshUserMsgNav();
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
// ── WebSocket 消息渲染队列 ──
// 所有类型消息先统一入队，由定时器每 10ms 批量取出分发；
// 批次内相邻的 chunk/thinking 流式片段（type/requestId/messageNo/status 均一致）合并为一条再经现有方法渲染，
// 降低高频片段逐条渲染（Markdown 解析 + DOM 刷新）的时间损耗；其余类型消息按原顺序原样分发
var wsMessageQueue = [];
var wsMessageQueueTimer = null;
var WS_MESSAGE_FLUSH_INTERVAL_MS = 10;

function enqueueWsMessage(data) {
    wsMessageQueue.push(data);
    if (wsMessageQueueTimer == null) {
        wsMessageQueueTimer = setInterval(flushWsMessageQueue, WS_MESSAGE_FLUSH_INTERVAL_MS);
    }
}

function flushWsMessageQueue() {
    if (wsMessageQueue.length === 0) {
        clearInterval(wsMessageQueueTimer);
        wsMessageQueueTimer = null;
        return;
    }
    // 入队时保存原始文本，统一在定时器批次内解析，onmessage 事件回调保持最轻量
    var rawBatch = wsMessageQueue.splice(0, wsMessageQueue.length);
    var batch = [];
    for (var r = 0; r < rawBatch.length; r++) {
        try {
            batch.push(JSON.parse(rawBatch[r]));
        } catch (e) {
            console.error('WebSocket 消息解析失败', e);
        }
    }
    var i = 0;
    while (i < batch.length) {
        var data = batch[i];
        var next = i + 1;
        // 仅合并相邻的同源流式片段（chunk/thinking）：status（partial/done）不同不能合并，
        // requestId / messageNo 不同分属不同请求或不同条消息，同样不能合并
        if (data.type === 'chunk' || data.type === 'thinking') {
            while (next < batch.length
                && batch[next].type === data.type
                && batch[next].status === data.status
                && batch[next].requestId === data.requestId
                && batch[next].messageNo === data.messageNo) {
                data.content = (data.content || '') + (batch[next].content || '');
                next++;
            }
        }
        try {
            dispatchWsMessage(data);
        } catch (e) {
            // 单条消息处理异常不影响批次内后续消息
            console.error('处理 WebSocket 消息失败', e);
        }
        i = next;
    }
}

function dispatchWsMessage(data) {
    var requestId = data.requestId;

    // 会话隔离：后端按用户广播，非当前会话的运行事件（任务/项目会话后台运行）不更新当前界面；
    // session-title 仍需更新左侧会话列表标题；received/error/task-done 维护会话列表的运行loading图标
    if (data.sessionId && data.sessionId !== currentSessionId) {
        if (data.type === 'session-title') {
            updateSessionTitle(data.sessionId, data.content, data.bizType);
        } else if (data.type === 'received') {
            setSessionRunning(data.sessionId, true);
        } else if (data.type === 'error' || data.type === 'task-done') {
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
        handleStreamingChunk(data, requestId);
    } else if (data.type === 'tool_call') {
        // 工具调用开始：刷新工具执行统计（会话总数/执行器已执行/上限）
        if (data.status === 'started') {
            loadToolStats();
        }
        handleToolCall(data, requestId);
    } else if (data.type === 'thinking') {
        handleThinking(data, requestId);
    } else if (data.type === 'session-title') {
        updateSessionTitle(data.sessionId, data.content, data.bizType);
    } else if (data.type === 'task-done') {
        setSessionRunning(data.sessionId || currentSessionId, false);
        var msgState = streamingMessages[requestId];
        if (msgState && msgState.currentStreamingMessage) {
            // 小节收尾：刷新整盒 footer（唯一时间 + 整盒复制）
            touchAgentTurnFooter(msgState.currentStreamingMessage.closest('.agent-turn'), formatMessageTime(new Date()));
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
}

function connectWebSocket() {
    var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    var wsUrl = protocol + '//' + window.location.host + '/ws/chat';

    ws = new WebSocket(wsUrl);

    ws.onopen = function() {
        console.log('WebSocket 连接已建立');
    };

    ws.onmessage = function(event) {
        // 所有消息（原始文本）统一入队，解析与分发统一由定时器批量完成，避免高频消息逐条处理的时间损耗
        enqueueWsMessage(event.data);
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

        // 消息流中同步渲染扳手图标：连续工具调用合并同一行，图标行归入当前回合大盒子
        appendToolInlineIcon(getAgentTurnContainer(messagesDiv), data);

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
            // 小节收尾：刷新整盒 footer（唯一时间 + 整盒复制）
            touchAgentTurnFooter(msgState.currentStreamingMessage.closest('.agent-turn'), formatMessageTime(new Date()));
            msgState.currentStreamingMessage = null;
        }
    }

    if (data.status !== 'running') {
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    }
    if (toolExecList) toolExecList.scrollTop = toolExecList.scrollHeight;
}

/**
 * 工具名 → 工具集图标映射（/tools/api/list 加载）。
 * 图标两种形式："<svg" 开头的 SVG 代码；否则为文件名/地址（拼 /icons/tools/ 前缀）。
 */
var toolIconMap = {};
var toolIconMapLoaded = false;

/** 加载工具集列表并建立 工具名→图标 映射 */
function loadToolIconMap() {
    return fetch('/tools/api/list')
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code === 200 && res.data) {
                res.data.forEach(function(toolSet) {
                    var icon = toolSet.icon;
                    if (!icon) return;
                    (toolSet.tools || []).forEach(function(tool) {
                        if (!tool || !tool.name) return;
                        toolIconMap[tool.name] = icon;
                        // 历史接口的 toolName 已被后端替换为首个工具描述，描述同样注册（与 getToolNameAndDescriptionMap 同源）
                        if (tool.descriptions && tool.descriptions.length > 0 && tool.descriptions[0]) {
                            toolIconMap[tool.descriptions[0]] = icon;
                        }
                    });
                });
            }
            toolIconMapLoaded = true;
        })
        .catch(function(e) {
            console.error('加载工具集图标映射失败:', e);
            toolIconMapLoaded = true;
        });
}

/** 默认通用图标（所有工具的兜底），视为"无专属图标" */
var TOOL_DEFAULT_ICON = 'agent-tool.svg';

/**
 * 按工具名渲染内联图标内容：工具集 SVG 图标（代码或地址），无专属图标时显示扳手
 */
function renderToolInlineIconContent(iconEl, toolName) {
    var icon = toolName ? toolIconMap[toolName] : null;
    if (!icon || icon === TOOL_DEFAULT_ICON) {
        iconEl.textContent = '🔧';
        return;
    }
    if (icon.indexOf('<svg') === 0) {
        // SVG 代码形式：直接内联
        iconEl.innerHTML = icon;
        return;
    }
    // 地址形式：文件名拼静态资源前缀；完整 URL/路径则原样使用
    var src = (icon.indexOf('http') === 0 || icon.indexOf('/') === 0) ? icon : ('/icons/tools/' + icon);
    var img = document.createElement('img');
    img.src = src;
    img.alt = '';
    img.onerror = function() {
        // 图标资源加载失败：回退扳手
        iconEl.textContent = '🔧';
    };
    iconEl.appendChild(img);
}

/**
 * 在消息流中渲染工具调用图标。
 * 与历史渲染保持一致：若消息流最后一个元素已是图标行（连续工具调用），复用同一行；否则新建一行。
 */
function appendToolInlineIcon(container, data) {
    if (!container) return;
    // 已渲染过该工具调用的图标则跳过（同一工具调用会有多个状态事件）
    if (container.querySelector('.tool-inline-icon[data-tool-call-id="' + data.toolCallId + '"]')) {
        return;
    }
    // footer 之前的最后一个内容元素：连续工具调用合并同一行
    var footer = container.querySelector('.agent-turn-footer');
    var last = footer ? footer.previousElementSibling : container.lastElementChild;
    var row;
    if (last && last.classList && last.classList.contains('tool-inline-row')) {
        row = last;
    } else {
        row = document.createElement('div');
        row.className = 'tool-inline-row';
        if (footer) {
            container.insertBefore(row, footer);
        } else {
            container.appendChild(row);
        }
    }
    var inlineName = data.toolName || 'Unknown Tool';
    if (data.toolDescriptions && data.toolDescriptions.length > 0) {
        inlineName = data.toolDescriptions[0] || inlineName;
    }
    var icon = document.createElement('span');
    icon.className = 'tool-inline-icon';
    icon.setAttribute('data-tool-call-id', data.toolCallId);
    icon.title = inlineName; // 鼠标移上显示工具名称
    renderToolInlineIconContent(icon, data.toolName);
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

    // 用户消息出现：关闭当前 agent 回合，后续 agent 消息开新盒子
    closeAgentTurn();

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
            img.style.cursor = 'pointer';
            img.onclick = function() { openAttachmentPreview(f.id); };
            imageMessageDiv.appendChild(img);
        }else{
            var a = document.createElement('a');
            a.className = 'message-content message-'+f.type;
            a.href = 'javascript:void(0)';
            a.setAttribute('data-is-agent', 'false');
            a.setAttribute('data-attachment-id', f.id);
            a.title = f.originalName;
            a.text=f.originalName;
            a.onclick = function() { openAttachmentPreview(f.id); };
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
        textContent.setAttribute('data-raw-content', data.content);
        textContent.innerHTML = renderMarkdown(data.content);
        textMessageDiv.appendChild(textContent);

        textMessageDiv.appendChild(createMessageFooter(data.content, data.requestId));

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
    var messagesDiv = document.getElementById('chatMessages');

    // 加载指示放入回合盒子（回合随 received 开启，后续思考/文本/工具追加进同一盒子）
    var turn = getAgentTurnContainer(messagesDiv);

    loadingMessageDiv = document.createElement('div');
    loadingMessageDiv.className = 'message agent loading-message';

    var loadingContent = document.createElement('div');
    loadingContent.className = 'message-content loading-content';
    loadingContent.innerHTML = '<span class="loading-dot"></span><span class="loading-dot"></span><span class="loading-dot"></span>';
    loadingMessageDiv.appendChild(loadingContent);

    appendToAgentTurn(turn, loadingMessageDiv);
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

    var msgState = streamingMessages[requestId];
    if (!msgState) {
        msgState = { currentStreamingMessage: null, streamingMarkdownContent: '', lastMessageType: null, thinkingContent: '', thinkingDiv: null };
        streamingMessages[requestId] = msgState;
    }

    if (data.status === 'partial' || data.status === 'done') {
        // 按消息编号定位思考消息 DOM：不存在则新建（页面刷新后正在输出的消息可凭编号续接）
        var thinkDiv = data.messageNo
            ? messagesDiv.querySelector('.message.thinking-message[data-message-no="' + data.messageNo + '"]')
            : null;
        var thinkingSection;
        if (!thinkDiv) {
            msgState.thinkingContent = '';
            msgState.lastMessageType = 'thinking';

            msgState.currentStreamingMessage = document.createElement('div');
            msgState.currentStreamingMessage.className = 'message agent thinking-message';
            msgState.currentStreamingMessage.setAttribute('data-request-id', requestId);
            if (data.messageNo) {
                msgState.currentStreamingMessage.setAttribute('data-message-no', data.messageNo);
            }

            // 回合盒子头部已展示智能体名称，小节仅保留类型标签
            var label = document.createElement('div');
            label.className = 'message-label';
            label.textContent = '(思考)';
            msgState.currentStreamingMessage.appendChild(label);

            // 实时输出期间保持展开，思考完成后自动收起
            thinkingSection = buildThinkingSection('', true);
            msgState.currentStreamingMessage.appendChild(thinkingSection);
            msgState.thinkingDiv = thinkingSection.querySelector('.thinking-content');

            // 思考小节归入当前回合大盒子
            appendToAgentTurn(getAgentTurnContainer(messagesDiv), msgState.currentStreamingMessage);
        } else {
            msgState.currentStreamingMessage = thinkDiv;
            msgState.thinkingDiv = thinkDiv.querySelector('.message-content.thinking-content');
            thinkingSection = msgState.thinkingDiv ? msgState.thinkingDiv.closest('.thinking-section') : null;
            msgState.lastMessageType = 'thinking';
        }

        if (data.status === 'done' && data.content) {
            // 消息结束：全量补全（中途进入页面/刷新缺少的片段在此补齐）
            msgState.thinkingContent = data.content;
        } else {
            // 增量片段：追加
            msgState.thinkingContent += (data.content || '');
        }
        msgState.thinkingDiv.innerHTML = renderMarkdown(msgState.thinkingContent);
        msgState.thinkingDiv.setAttribute('data-raw-content', msgState.thinkingContent);
        if (data.status === 'partial') {
            // 实时追加时保持展开，便于观察当前思考进度
            setThinkingExpanded(thinkingSection, true);
        }
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
        if (data.status === 'done' && msgState.currentStreamingMessage && msgState.lastMessageType === 'thinking') {
            // 思考完成：自动收起为两行
            setThinkingExpanded(thinkingSection, false);
            // 小节完成：刷新整盒 footer（唯一时间 + 整盒复制），小节自身不再有独立 footer
            touchAgentTurnFooter(msgState.currentStreamingMessage.closest('.agent-turn'), formatMessageTime(new Date()));
            msgState.currentStreamingMessage = null;
            msgState.thinkingContent = '';
            msgState.thinkingDiv = null;
            msgState.lastMessageType = null;
        }
    }
}

function handleStreamingChunk(data, requestId) {
    var messagesDiv = document.getElementById('chatMessages');

    var msgState = streamingMessages[requestId];
    if (!msgState) {
        msgState = { currentStreamingMessage: null, streamingMarkdownContent: '', lastMessageType: null };
        streamingMessages[requestId] = msgState;
    }

    // 按消息编号定位文本消息 DOM：不存在则新建（页面刷新后正在输出的消息可凭编号续接）
    var messageDiv = data.messageNo
        ? messagesDiv.querySelector('.message.agent:not(.thinking-message)[data-message-no="' + data.messageNo + '"]')
        : null;
    if (!messageDiv) {
        msgState.streamingMarkdownContent = '';
        msgState.lastMessageType = 'text';

        messageDiv = document.createElement('div');
        messageDiv.className = 'message agent';
        messageDiv.setAttribute('data-request-id', requestId);
        if (data.messageNo) {
            messageDiv.setAttribute('data-message-no', data.messageNo);
        }

        // 回合盒子头部已展示智能体名称，文本小节不再重复标签
        var newContentDiv = document.createElement('div');
        newContentDiv.className = 'message-content';
        messageDiv.appendChild(newContentDiv);

        // 文本小节归入当前回合大盒子
        appendToAgentTurn(getAgentTurnContainer(messagesDiv), messageDiv);
    }
    msgState.currentStreamingMessage = messageDiv;
    msgState.lastMessageType = 'text';

    // 消息节点内只维护一个内容容器（label/footer 均为 div，避免 :last-of-type 失配）
    var contentDiv = messageDiv.querySelector('.message-content');
    if (!contentDiv) {
        contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        messageDiv.appendChild(contentDiv);
    }

    if (data.status === 'done' && data.content != null) {
        // 消息结束：全量补全（中途进入页面/刷新缺少的片段在此补齐）
        msgState.streamingMarkdownContent = data.content;
    } else {
        // 增量片段：追加
        msgState.streamingMarkdownContent += (data.content || '');
    }

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
    // 记录原始内容：整盒复制时使用原始 Markdown 而非渲染后的文本
    contentDiv.setAttribute('data-raw-content', msgState.streamingMarkdownContent);

    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function handleStreamingError(errorMessage, requestId) {
    var messagesDiv = document.getElementById('chatMessages');

    var errorDiv = document.createElement('div');
    errorDiv.className = 'message agent error-message';
    if (requestId) {
        errorDiv.setAttribute('data-request-id', requestId);
    }

    // 回合盒子头部已展示智能体名称，小节仅保留类型标签
    var label = document.createElement('div');
    label.className = 'message-label';
    label.textContent = '(错误)';
    errorDiv.appendChild(label);

    var contentDiv = document.createElement('div');
    contentDiv.className = 'message-content error-content';
    contentDiv.textContent = errorMessage;
    contentDiv.setAttribute('data-raw-content', errorMessage);
    errorDiv.appendChild(contentDiv);

    // 错误小节归入当前回合大盒子，并刷新整盒 footer
    var turn = getAgentTurnContainer(messagesDiv);
    appendToAgentTurn(turn, errorDiv);
    touchAgentTurnFooter(turn, formatMessageTime(new Date()));
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
    enableInput();
}
function handleStreamingWarn(warnMessage, requestId) {
    var messagesDiv = document.getElementById('chatMessages');

    var warnDiv = document.createElement('div');
    warnDiv.className = 'message agent warn-message';
    if (requestId) {
        warnDiv.setAttribute('data-request-id', requestId);
    }

    // 回合盒子头部已展示智能体名称，小节仅保留类型标签
    var label = document.createElement('div');
    label.className = 'message-label';
    label.textContent = '(警告)';
    warnDiv.appendChild(label);

    var contentDiv = document.createElement('div');
    contentDiv.className = 'message-content warn-content';
    contentDiv.textContent = warnMessage;
    contentDiv.setAttribute('data-raw-content', warnMessage);
    warnDiv.appendChild(contentDiv);

    // 警告小节归入当前回合大盒子，并刷新整盒 footer
    var turn = getAgentTurnContainer(messagesDiv);
    appendToAgentTurn(turn, warnDiv);
    touchAgentTurnFooter(turn, formatMessageTime(new Date()));
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
                // 思考等级：开启深度思考时随消息发送（未开启时置空，由后端按智能体/模型配置兜底）
                reasoningEffort: (deepBtn && deepBtn.getAttribute('data-enabled') === 'true' && currentThinkingLevel)
                    ? currentThinkingLevel : null,
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
    var input = document.getElementById('messageInput');
    var sendBtn = document.getElementById('sendBtn');
    var runningBtn = document.getElementById('runningBtn');
    var headerTimer = document.getElementById('chatHeaderTimer');
    if (wrapper) wrapper.classList.add('disabled');
    if (input) input.disabled = true;
    if (sendBtn) sendBtn.classList.add('hide');
    if (runningBtn) runningBtn.classList.remove('hide');
    if (headerTimer) headerTimer.classList.remove('hide');
    startLockCountdown(false);
}

function enableInput() {
    var wrapper = document.querySelector('.chat-input-wrapper');
    var input = document.getElementById('messageInput');
    var sendBtn = document.getElementById('sendBtn');
    var runningBtn = document.getElementById('runningBtn');
    var headerTimer = document.getElementById('chatHeaderTimer');
    if (wrapper) wrapper.classList.remove('disabled');
    if (input) input.disabled = false;
    if (sendBtn) sendBtn.classList.remove('hide');
    if (runningBtn) runningBtn.classList.add('hide');
    if (headerTimer) headerTimer.classList.add('hide');
    stopLockCountdown();
    if (input) input.focus();
}

// ===== 执行器可重置锁（看门狗）剩余时间倒计时 =====
var lockRemainingSeconds = 0;
var lockElapsedSeconds = 0;
var lockPollTimer = null;
var lockCountdownTimer = null;
// 是否已观测到后端会话处于运行中：避免 sendMessage 禁用输入后、后端执行器尚未创建时误恢复输入
var lockObservedRunning = false;

/**
 * 启动剩余时间倒计时：每5秒查询一次后端剩余时间和已运行时长，前端每秒本地递增/递减展示
 * @param {boolean} initiallyRunning 启动时后端会话是否已在运行（页面加载时按后端渲染状态传入 true）
 */
function startLockCountdown(initiallyRunning) {
    if (lockCountdownTimer) return;
    lockObservedRunning = !!initiallyRunning;
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
    lockObservedRunning = false;
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
                if (resp.data.running === true) {
                    lockObservedRunning = true;
                    lockRemainingSeconds = resp.data.remainingSeconds || 0;
                    if (resp.data.elapsedSeconds > 0) {
                        lockElapsedSeconds = resp.data.elapsedSeconds;
                    }
                    renderLockCountdown();
                } else if (lockObservedRunning) {
                    // 会话已由运行转为结束，但未收到终止事件（如刷新/重连窗口错过 task-done）：恢复输入区
                    enableInput();
                }
                // running=false 且从未观测到运行时：保持现状，等待执行器真正启动
            }
        })
        .catch(function() { /* 静默失败，下次轮询重试 */ });
}

/**
 * 渲染已运行时长和剩余超时时间到 chat-header 的运行状态计时器
 * 同时驱动头部双色光晕：分界线 = 已用时间 / (已用时间 + 超时时间)
 */
function renderLockCountdown() {
    var elapsedEl = document.getElementById('runningElapsed');
    if (elapsedEl) {
        elapsedEl.textContent = lockElapsedSeconds > 0 ? ('(' + lockElapsedSeconds + 's)') : '';
    }
    var glow = document.getElementById('chatHeaderGlow');
    if (glow) {
        var total = lockElapsedSeconds + lockRemainingSeconds;
        if (lockRemainingSeconds > 0 && total > 0) {
            var pct = Math.min(100, lockElapsedSeconds / total * 100);
            glow.style.setProperty('--boundary', pct.toFixed(2) + '%');
            glow.classList.add('active');
        } else {
            glow.classList.remove('active');
        }
    }
    var el = document.getElementById('runningCountdown');
    if (!el) return;
    if (lockRemainingSeconds > 0) {
        el.textContent = '超时(' + lockRemainingSeconds + 's)';
        el.style.display = '';
    } else {
        el.textContent = '';
        el.style.display = 'none';
    }
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

// ================= 会话记忆查看 =================

/** 会话记忆列表缓存（详情弹框按 id 复用，避免二次请求） */
var sessionMemoryCache = [];

/** 记忆类型徽标文案 */
var SESSION_MEMORY_TYPE_NAMES = { system: '系统', user: '用户', ai: 'AI', toolResult: '工具结果', other: '其他' };
/** 记忆状态文案 */
var SESSION_MEMORY_STATUS_NAMES = { 0: '默认', 1: '任务结束', 2: '自动清理', 3: '手动清理' };

/** 打开会话记忆列表弹框：按当前会话编号拉取 chat_memory 解析后的各类型数据 */
function showSessionMemoryModal() {
    if (!currentSessionId) {
        showToast('当前无会话', 'error');
        return;
    }
    var listEl = document.getElementById('sessionMemoryList');
    if (!listEl) return;
    listEl.innerHTML = '<div class="session-memory-empty">加载中...</div>';
    document.getElementById('sessionMemoryModal').classList.add('active');

    fetch('/api/session/' + encodeURIComponent(currentSessionId) + '/memories')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code !== 200) {
                listEl.innerHTML = '<div class="session-memory-empty">' + (resp.msg || '加载失败') + '</div>';
                return;
            }
            sessionMemoryCache = resp.data || [];
            renderSessionMemoryList(listEl, sessionMemoryCache);
        })
        .catch(function(err) {
            listEl.innerHTML = '<div class="session-memory-empty">加载失败: ' + err.message + '</div>';
        });
}

/** 渲染记忆列表：每行展示类型/前20字符预览/时间，长文本点击详情查看全文 */
function renderSessionMemoryList(listEl, list) {
    listEl.innerHTML = '';
    if (!list.length) {
        listEl.innerHTML = '<div class="session-memory-empty">暂无记忆数据</div>';
        return;
    }
    list.forEach(function(item) {
        var row = document.createElement('div');
        row.className = 'session-memory-row';

        var badge = document.createElement('span');
        badge.className = 'session-memory-type type-' + (item.type || 'other');
        badge.textContent = SESSION_MEMORY_TYPE_NAMES[item.type] || item.type || '其他';
        row.appendChild(badge);

        var previewWrap = document.createElement('div');
        previewWrap.className = 'session-memory-preview-wrap';

        var preview = document.createElement('div');
        preview.className = 'session-memory-preview';
        // 单行完整展示，超出宽度省略号截断，全文点击详情查看
        preview.textContent = sessionMemoryPreviewText(item);
        previewWrap.appendChild(preview);

        // 记录中存在思考内容时，追加一行思考预览（单行超出省略）
        if (item.thinking) {
            var thinkLine = document.createElement('div');
            thinkLine.className = 'session-memory-thinking';
            thinkLine.textContent = '思考: ' + item.thinking;
            previewWrap.appendChild(thinkLine);
        }
        row.appendChild(previewWrap);

        var time = document.createElement('span');
        time.className = 'session-memory-time';
        time.textContent = item.createTime ? formatMessageTime(new Date(formatHistoryIsoTime(item.createTime))) : '';
        row.appendChild(time);

        var statusEl = document.createElement('span');
        statusEl.className = 'session-memory-status';
        statusEl.textContent = SESSION_MEMORY_STATUS_NAMES[item.status] || (item.status == null ? '' : item.status);
        row.appendChild(statusEl);

        var detailBtn = document.createElement('button');
        detailBtn.type = 'button';
        detailBtn.className = 'session-memory-detail-btn';
        detailBtn.textContent = '详情';
        detailBtn.onclick = function() { showSessionMemoryDetail(item.id); };
        row.appendChild(detailBtn);

        listEl.appendChild(row);
    });
}

/** 预览文本：按类型选取最有代表性的内容（思考内容单独成行展示） */
function sessionMemoryPreviewText(item) {
    var type = item.type || 'other';
    if (type === 'ai') {
        if (item.content) return item.content;
        if (item.toolName) return '[工具调用] ' + item.toolName;
        return '(空)';
    }
    if (type === 'toolResult') {
        return '[工具结果] ' + (item.toolName || '') + ' ' + (item.content || '');
    }
    return item.content || '(空)';
}

/** 打开记忆详情弹框：展示全文字段 */
function showSessionMemoryDetail(id) {
    var item = sessionMemoryCache.find(function(m) { return m.id === id; });
    if (!item) return;
    var body = document.getElementById('sessionMemoryDetailBody');
    body.innerHTML = '';

    function addField(label, text, cls) {
        if (text == null || String(text).trim() === '') return;
        var wrap = document.createElement('div');
        wrap.className = 'session-memory-detail-field' + (cls ? ' ' + cls : '');
        var labelEl = document.createElement('div');
        labelEl.className = 'session-memory-detail-label';
        labelEl.textContent = label;
        var valueEl = document.createElement('div');
        valueEl.className = 'session-memory-detail-value';
        valueEl.textContent = text;
        wrap.appendChild(labelEl);
        wrap.appendChild(valueEl);
        body.appendChild(wrap);
    }

    addField('类型', SESSION_MEMORY_TYPE_NAMES[item.type] || item.type);
    addField('时间', item.createTime ? formatMessageTime(new Date(formatHistoryIsoTime(item.createTime))) : '');
    addField('状态', SESSION_MEMORY_STATUS_NAMES[item.status] || item.status);
    if (item.type === 'toolResult') {
        addField('工具', item.toolName, item.error ? 'session-memory-detail-error' : '');
    }
    if (item.type === 'ai' && item.toolName) {
        addField('工具调用', item.toolName);
        addField('调用参数', item.toolArguments);
    }
    if (item.thinking) { addField('思考', item.thinking); }
    addField('内容', item.content, item.error ? 'session-memory-detail-error' : '');
    document.getElementById('sessionMemoryDetailModal').classList.add('active');
}

function hideSessionMemoryModal() {
    document.getElementById('sessionMemoryModal').classList.remove('active');
}

function hideSessionMemoryDetailModal() {
    document.getElementById('sessionMemoryDetailModal').classList.remove('active');
}

// ================= 请求日志（模型请求响应明细排查） =================

/** 当前列表弹框的过滤请求编号：bug 图标进入时按单次请求过滤 */
var requestLogFilterRequestId = null;

/**
 * 打开请求日志列表弹框
 * @param requestId 可选：按单次请求过滤（用户消息 bug 图标进入）
 */
function showRequestLogModal(requestId) {
    if (!currentSessionId) {
        showToast('当前无会话', 'error');
        return;
    }
    requestLogFilterRequestId = requestId || null;
    document.getElementById('requestLogTitle').textContent = requestId ? '请求日志（单次请求）' : '请求日志';
    // 按请求过滤时隐藏一键清理按钮（避免误解为只清理该请求）
    document.getElementById('requestLogClearBtn').style.display = requestId ? 'none' : '';
    var listEl = document.getElementById('requestLogList');
    listEl.innerHTML = '<div class="session-memory-empty">加载中...</div>';
    document.getElementById('requestLogModal').classList.add('active');
    loadRequestLogs();
}

function loadRequestLogs() {
    var listEl = document.getElementById('requestLogList');
    var url = '/api/session/' + encodeURIComponent(currentSessionId) + '/request-logs';
    if (requestLogFilterRequestId) {
        url += '?requestId=' + encodeURIComponent(requestLogFilterRequestId);
    }
    fetch(url)
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code !== 200) {
                listEl.innerHTML = '<div class="session-memory-empty">' + (resp.msg || '加载失败') + '</div>';
                return;
            }
            renderRequestLogList(listEl, resp.data || []);
        })
        .catch(function(err) {
            listEl.innerHTML = '<div class="session-memory-empty">加载失败: ' + err.message + '</div>';
        });
}

/** 渲染请求日志列表：状态/时间/来源/模型/Token/耗时，点击行查看完整请求响应 JSON */
function renderRequestLogList(listEl, list) {
    listEl.innerHTML = '';
    if (!list.length) {
        listEl.innerHTML = '<div class="session-memory-empty">暂无请求日志</div>';
        return;
    }
    list.forEach(function(item) {
        var row = document.createElement('div');
        row.className = 'request-log-row' + (item.status === 'error' ? ' request-log-row-error' : '');
        row.title = '点击查看请求/响应详情';

        var statusEl = document.createElement('span');
        statusEl.className = 'request-log-status ' + (item.status === 'error' ? 'status-error' : 'status-success');
        statusEl.textContent = item.status === 'error' ? '失败' : '成功';
        row.appendChild(statusEl);

        var main = document.createElement('div');
        main.className = 'request-log-main';
        var line1 = document.createElement('div');
        line1.className = 'request-log-line';
        line1.textContent = (item.modelName || '未知模型') + (item.source ? ' · ' + item.source : '');
        main.appendChild(line1);
        var line2 = document.createElement('div');
        line2.className = 'request-log-sub';
        var meta = [];
        if (item.totalTokens != null) { meta.push('tokens ' + item.totalTokens + '（进 ' + (item.inputTokens || 0) + ' / 出 ' + (item.outputTokens || 0) + '）'); }
        if (item.costMs != null) { meta.push((item.costMs / 1000).toFixed(2) + 's'); }
        if (item.requestId) { meta.push('请求 ' + item.requestId); }
        line2.textContent = meta.join(' · ');
        main.appendChild(line2);
        row.appendChild(main);

        var time = document.createElement('span');
        time.className = 'request-log-time';
        time.textContent = item.createTime ? formatMessageTime(new Date(formatHistoryIsoTime(item.createTime))) : '';
        row.appendChild(time);

        row.onclick = function() { showRequestLogDetail(item.id); };
        listEl.appendChild(row);
    });
}

/** 打开请求日志详情：完整请求/响应 JSON 格式化展示 */
function showRequestLogDetail(id) {
    fetch('/api/session/' + encodeURIComponent(currentSessionId) + '/request-logs/' + id)
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code !== 200 || !resp.data) {
                showToast(resp.msg || '日志不存在', 'error');
                return;
            }
            renderRequestLogDetail(resp.data);
        })
        .catch(function(err) {
            showToast('加载失败: ' + err.message, 'error');
        });
}

function renderRequestLogDetail(item) {
    var body = document.getElementById('requestLogDetailBody');
    body.innerHTML = '';

    function addMeta(label, text) {
        if (text == null || String(text).trim() === '') return;
        var el = document.createElement('div');
        el.className = 'request-log-detail-meta';
        el.textContent = label + '：' + text;
        body.appendChild(el);
    }
    function addJsonBlock(title, jsonText) {
        if (!jsonText) return;
        var titleEl = document.createElement('div');
        titleEl.className = 'request-log-detail-title';
        titleEl.textContent = title;
        body.appendChild(titleEl);
        var pre = document.createElement('pre');
        pre.className = 'request-log-json';
        try {
            pre.textContent = JSON.stringify(JSON.parse(jsonText), null, 2);
        } catch (e) {
            // 非 JSON 内容直接原文展示
            pre.textContent = jsonText;
        }
        body.appendChild(pre);
    }

    addMeta('状态', item.status === 'error' ? '失败' : '成功');
    addMeta('模型', item.modelName);
    addMeta('来源', item.source);
    addMeta('请求编号', item.requestId);
    addMeta('耗时', item.costMs != null ? (item.costMs / 1000).toFixed(2) + 's' : '');
    addMeta('Token', item.totalTokens != null
        ? '总 ' + item.totalTokens + '（输入 ' + (item.inputTokens || 0) + ' / 输出 ' + (item.outputTokens || 0) + '）' : '');
    addMeta('时间', item.createTime ? formatMessageTime(new Date(formatHistoryIsoTime(item.createTime))) : '');

    if (item.errorText) {
        var errEl = document.createElement('div');
        errEl.className = 'request-log-detail-error';
        errEl.textContent = '错误：' + item.errorText;
        body.appendChild(errEl);
    }

    addJsonBlock('请求', item.requestJson);
    addJsonBlock('响应', item.responseJson);
    document.getElementById('requestLogDetailModal').classList.add('active');
}

/** 一键清理本会话全部请求日志 */
function clearRequestLogs() {
    showConfirm('确定要清理本会话的全部请求日志吗？').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/session/' + encodeURIComponent(currentSessionId) + '/request-logs', { method: 'DELETE' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.code === 200) {
                    showToast('已清理', 'info');
                    loadRequestLogs();
                } else {
                    showToast(resp.msg || '清理失败', 'error');
                }
            })
            .catch(function(err) {
                showToast('清理失败: ' + err.message, 'error');
            });
    });
}

function hideRequestLogModal() {
    document.getElementById('requestLogModal').classList.remove('active');
}

function hideRequestLogDetailModal() {
    document.getElementById('requestLogDetailModal').classList.remove('active');
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
        // 初始化向上滚动加载更早历史消息
        initHistoryScroll();
        // 先加载工具集图标映射，再渲染历史（内联工具图标需按工具名取工具集图标）
        loadToolIconMap().finally(function() {
            // 首次进入会话：JS 拉取并渲染历史消息（与向上翻页复用同一套渲染逻辑）
            loadInitialHistory().finally(function() {
                // 初始化用户消息导航圆点
                initUserMsgNav();
                // 历史加载完成后再订阅实时数据，确保 chunks 到达时历史已渲染
                if (currentAgentId) {
                    connectWebSocket();
                    loadTokenUsage();
                    loadToolStats();
                }
            });
        });
    }
    var input = document.getElementById('messageInput');
    if (input) {
        input.focus();
    }

    // 页面加载时会话已在运行：启动看门狗剩余时间倒计时
    var initRunningBtn = document.getElementById('runningBtn');
    if (initRunningBtn && !initRunningBtn.classList.contains('hide')) {
        startLockCountdown(true);
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
    var thinkingDropdown = document.getElementById('thinkingDropdown');
    if (deepBtn) {
        // 思考等级优先级：会话 > 智能体 > 模型列表末位
        var currentSession = (initialChatSessions || []).find(function(s) { return s.sessionId === currentSessionId; });
        if (currentSession && currentSession.thinkingLevel) {
            // 会话有思考等级，使用会话的
            currentThinkingLevel = currentSession.thinkingLevel;
        } else if (deepBtn.getAttribute('data-default-level')) {
            // 智能体有配置，使用智能体的
            currentThinkingLevel = deepBtn.getAttribute('data-default-level');
        } else {
            // 会话/智能体均未配置：先以 max 占位渲染，模型加载完成后取支持列表的最后一个
            thinkingLevelFromModelDefault = true;
            currentThinkingLevel = 'max';
        }
        // 先以全量等级初始化滑块与按钮发光，模型数据加载后按 supportedThinkingLevelsArray 重建
        currentThinkingLevels = THINKING_LEVELS.slice();
        renderThinkingLevelSlider();
        updateThinkingLevelUI(true);
        // 初始开启态同步到容器（悬停展开滑块的条件）
        if (thinkingDropdown && deepBtn.classList.contains('active')) {
            thinkingDropdown.classList.add('active');
        }
        deepBtn.addEventListener('click', function() {
            // 当前模型不支持思考：按钮置灰不可用
            if (thinkingDropdown && thinkingDropdown.classList.contains('no-support')) return;
            var current = this.getAttribute('data-enabled') === 'true';
            var newEnabled = !current;
            this.setAttribute('data-enabled', newEnabled);
            this.classList.toggle('active', newEnabled);
            if (thinkingDropdown) {
                thinkingDropdown.classList.toggle('active', newEnabled);
            }
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
                                // 缓存模型数据：思考等级滑块从模型读取 supportedThinkingLevelsArray
                                thinkingModelById[model.id] = model;
                                var activeClass = '';
                                if (defaultModelId && defaultModelId === model.id) {
                                    activeClass = ' active';
                                    defaultModelName = model.modelAlias || model.modelName;
                                }
                                html += '<div class="model-sub-item' + activeClass + '" data-model-id="' + model.id + '" data-model-name="' + escapeHtml(model.modelAlias || model.modelName) + '">';
                                html += '<span class="model-sub-item-check">✓</span>';
                                html += '<span class="model-sub-item-name">' + escapeHtml(model.modelAlias || model.modelName) + '</span>';
                                // 标签：支持思考 + 能力类型
                                var capNames = {text: '文本', image: '图片', audio: '音频', video: '视频', document: '文档'};
                                if (model.supportThinking) {
                                    html += '<span class="model-tag model-tag-thinking">思考</span>';
                                }
                                if (model.capabilities) {
                                    model.capabilities.split(',').forEach(function(cap) {
                                        cap = cap.trim();
                                        if (cap && capNames[cap]) {
                                            html += '<span class="model-tag model-tag-cap">' + capNames[cap] + '</span>';
                                        }
                                    });
                                }
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

    // 切换模型后同步思考等级滑块（等级列表来自模型的 supportedThinkingLevelsArray）
    syncThinkingLevelsFromModel(thinkingModelById[currentModelId]);

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
 * 按等级 code 查找当前模型支持的等级元数据
 */
function findThinkingLevelMeta(code) {
    for (var i = 0; i < currentThinkingLevels.length; i++) {
        if (currentThinkingLevels[i].code === code) return currentThinkingLevels[i];
    }
    return null;
}

/**
 * 同步深度思考等级：依据当前选中模型的 supportedThinkingLevelsArray 重建滑块。
 * 模型不支持思考时置灰按钮并强制关闭；当前等级不在列表中时回退 high（或中间档）。
 */
function syncThinkingLevelsFromModel(model) {
    var dropdown = document.getElementById('thinkingDropdown');
    var btn = document.getElementById('deepThinkBtn');
    if (!dropdown || !btn) return;

    // 模型不支持思考：置灰禁用并强制关闭（后端同样会强制关闭思考）
    if (model && model.supportThinking === false) {
        dropdown.classList.add('no-support');
        btn.setAttribute('title', '当前模型不支持思考');
        btn.setAttribute('data-enabled', 'false');
        btn.classList.remove('active');
        dropdown.classList.remove('active');
        return;
    }
    dropdown.classList.remove('no-support');

    // 等级列表：取模型配置的 supportedThinkingLevelsArray，按固定顺序从低到高排列并去重
    var arr = (model && model.supportedThinkingLevelsArray) || [];
    var seen = {};
    var levels = [];
    THINKING_LEVELS.forEach(function(meta) {
        for (var i = 0; i < arr.length; i++) {
            if (arr[i] && arr[i].trim() === meta.code && !seen[meta.code]) {
                levels.push(meta);
                seen[meta.code] = true;
                break;
            }
        }
    });
    // 模型未配置等级时展示全部（后端会按模型扩展参数兜底约束）
    if (levels.length === 0) {
        levels = THINKING_LEVELS.slice();
    }
    currentThinkingLevels = levels;

    if (thinkingLevelFromModelDefault) {
        // 会话/智能体均未配置等级：取模型支持列表的最后一个
        currentThinkingLevel = levels[levels.length - 1].code;
        thinkingLevelFromModelDefault = false;
    } else if (!findThinkingLevelMeta(currentThinkingLevel)) {
        // 当前等级不在新列表中：回退 high，仍不存在则取中间档
        var fallback = findThinkingLevelMeta('high');
        currentThinkingLevel = fallback ? fallback.code : levels[Math.floor(levels.length / 2)].code;
    }
    renderThinkingLevelSlider();
    updateThinkingLevelUI(true);
}

/**
 * 渲染思考等级滑块：使用 noUiSlider 实现平滑拖拽选择
 */
var thinkingNoUiSlider = null;

function renderThinkingLevelSlider() {
    var track = document.getElementById('thinkingLevelTrack');
    if (!track) return;
    track.innerHTML = '';

    var n = currentThinkingLevels.length;
    if (n === 0) return;

    // 创建 noUiSlider 容器
    var sliderContainer = document.createElement('div');
    sliderContainer.className = 'thinking-level-noui';
    track.appendChild(sliderContainer);

    // 创建标签容器
    var labelsContainer = document.createElement('div');
    labelsContainer.className = 'thinking-level-noui-labels';
    currentThinkingLevels.forEach(function(meta, i) {
        var label = document.createElement('span');
        label.className = 'thinking-level-noui-label';
        label.textContent = meta.name;
        label.setAttribute('data-index', i);
        // 绝对定位到滑块手柄的百分比位置，保证标签与圆点中心对齐
        label.style.left = n > 1 ? (i / (n - 1) * 100) + '%' : '50%';
        label.addEventListener('click', function() {
            // set() 不触发 change 事件，直接走 selectThinkingLevel 统一更新滑块、标签与按钮发光
            selectThinkingLevel(meta.code);
        });
        labelsContainer.appendChild(label);
    });
    track.appendChild(labelsContainer);

    // 销毁旧实例
    if (thinkingNoUiSlider) {
        thinkingNoUiSlider.destroy();
        thinkingNoUiSlider = null;
    }

    // 找到当前选中等级的索引
    var selectedIndex = 0;
    for (var i = 0; i < currentThinkingLevels.length; i++) {
        if (currentThinkingLevels[i].code === currentThinkingLevel) {
            selectedIndex = i;
            break;
        }
    }

    // 初始化 noUiSlider
    noUiSlider.create(sliderContainer, {
        start: selectedIndex,
        connect: 'lower',
        step: 1,
        range: {
            'min': 0,
            'max': n - 1
        },
        tooltips: false,
        behaviour: 'tap-drag'
    });

    thinkingNoUiSlider = sliderContainer.noUiSlider;

    // 更新滑块颜色
    updateThinkingLevelSliderColor(selectedIndex);

    // 滑块变化事件
    thinkingNoUiSlider.on('update', function(values, handle) {
        var idx = Math.round(parseFloat(values[0]));
        var meta = currentThinkingLevels[idx];
        if (meta) {
            updateThinkingLevelSliderColor(idx);
        }
    });

    thinkingNoUiSlider.on('change', function(values, handle) {
        var idx = Math.round(parseFloat(values[0]));
        var meta = currentThinkingLevels[idx];
        if (meta) {
            selectThinkingLevel(meta.code);
        }
    });

    thinkingNoUiSlider.on('slide', function(values, handle) {
        var idx = Math.round(parseFloat(values[0]));
        updateThinkingLevelSliderColor(idx);
    });

    thinkingNoUiSlider.on('hover', function(values, handle) {
        var idx = Math.round(parseFloat(values[0]));
        updateThinkingLevelSliderColor(idx);
    });
}

/**
 * 更新 noUiSlider 滑块颜色（轨道填充 + 滑块手柄）
 */
function updateThinkingLevelSliderColor(index) {
    var meta = currentThinkingLevels[index];
    if (!meta) return;

    var sliderContainer = document.querySelector('.thinking-level-noui');
    if (!sliderContainer) return;

    // 更新轨道填充颜色
    var origin = sliderContainer.querySelector('.noUi-connect');
    if (origin) {
        origin.style.background = meta.color;
        origin.style.boxShadow = '0 0 8px ' + hexToRgba(meta.color, 0.5);
    }

    // 更新滑块手柄颜色
    var handle = sliderContainer.querySelector('.noUi-handle');
    if (handle) {
        handle.style.background = meta.color;
        handle.style.borderColor = meta.color;
        handle.style.boxShadow = '0 0 10px ' + hexToRgba(meta.color, 0.6);
    }

    // 更新标签样式
    var labels = document.querySelectorAll('.thinking-level-noui-label');
    labels.forEach(function(label, i) {
        var isSelected = i === index;
        label.classList.toggle('selected', isSelected);
        if (isSelected) {
            label.style.color = meta.color;
            label.style.fontWeight = '600';
        } else {
            label.style.color = '';
            label.style.fontWeight = '';
        }
    });

    // 更新上方标签（如果有的话）
    var currentEl = document.getElementById('thinkingLevelCurrent');
    if (currentEl) {
        currentEl.textContent = meta.name;
        currentEl.style.color = meta.color;
        currentEl.style.background = hexToRgba(meta.color, 0.12);
    }
}

/**
 * 仅更新滑块填充（悬停预览 / 移出恢复共用）：保持接口兼容
 */
function updateThinkingLevelFill(levelCode) {
    // noUiSlider 模式下不需要此函数，保留接口兼容
}

function previewThinkingLevel(levelCode) {
    // noUiSlider 模式下不需要此函数，保留接口兼容
}

/**
 * 选中思考等级：更新滑块位置、标签样式、当前等级徽标与按钮发光
 */
function selectThinkingLevel(levelCode) {
    if (!findThinkingLevelMeta(levelCode)) return;
    currentThinkingLevel = levelCode;
    updateThinkingLevelUI();
}

/**
 * 刷新思考等级整体状态：滑块位置、标签样式、徽标、按钮随等级加深的发光。
 * restoreOnly=true 时仅恢复展示（如模型切换后初始化），不触发切换脉冲。
 */
function updateThinkingLevelUI(restoreOnly) {
    var meta = findThinkingLevelMeta(currentThinkingLevel) || currentThinkingLevels[0];
    if (!meta) return;
    var idx = currentThinkingLevels.indexOf(meta);

    // 更新 noUiSlider 滑块位置
    if (thinkingNoUiSlider) {
        thinkingNoUiSlider.set(idx, false); // false = 不触发事件
    }

    // 更新滑块颜色和标签
    updateThinkingLevelSliderColor(idx);

    // 浮层标题：当前等级徽标
    var currentEl = document.getElementById('thinkingLevelCurrent');
    if (currentEl) {
        currentEl.textContent = meta.name;
        currentEl.style.color = meta.color;
        currentEl.style.background = hexToRgba(meta.color, 0.1);
    }

    // 按钮随等级加深：颜色变深、发光半径增大（CSS 变量驱动 .active 样式）
    var btn = document.getElementById('deepThinkBtn');
    if (btn) {
        btn.style.setProperty('--deep-color', meta.color);
        btn.style.setProperty('--deep-glow', (4 + idx * 2) + 'px');
        btn.style.setProperty('--deep-glow-color', hexToRgba(meta.color, 0.45));
        btn.setAttribute('title', '深度思考：' + meta.name);
        if (!restoreOnly) {
            // 切换脉冲：重置动画强化"加深"的切换体验
            btn.classList.remove('level-pulse');
            void btn.offsetWidth;
            btn.classList.add('level-pulse');
        }
    }
}

/**
 * 会话业务类型归一为筛选类型：按 AgentExecutorBizTypeEnum 的 value 判断
 * workflowTaskChat→task / projectChat→project / 其他→chat
 * 兼容历史数据（旧版写入的 workflow-task-chat / project-chat）
 */
function sessionFilterTypeOf(bizType) {
    if (bizType === 'workflowTaskChat') return 'task';
    if (bizType === 'projectChat') return 'project';
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

function updateSessionTitle(sessionId, newTitle, bizType) {
    // 当前会话：同步更新消息区头部标题
    if (sessionId === currentSessionId) {
        var headerDesc = document.getElementById('chat-header-desc');
        if (headerDesc) {
            headerDesc.textContent = newTitle || '未命名会话';
        }
    }

    var container = document.getElementById('sessionList');
    if (!container) return;

    // 同步初始会话列表数据：新会话（含非当前筛选类型）补录进 initialChatSessions，已存在则更新标题；
    // 切换筛选类型时 renderSessionList 依据该数组渲染，保证实时到达的新会话不丢失（运行状态取自 runningSessionIds）
    var knownSession = (initialChatSessions || []).find(function(s) { return s.sessionId === sessionId; });
    if (knownSession) {
        if (newTitle) {
            knownSession.title = newTitle;
        }
    } else {
        initialChatSessions.unshift({
            sessionId: sessionId,
            title: newTitle || '未命名会话',
            bizType: bizType || '',
            lastUpdateTime: Date.now(),
            running: !!runningSessionIds[sessionId]
        });
    }

    var existingItem = container.querySelector('.session-list-item[data-session-id="' + escapeHtml(sessionId) + '"]');
    if (existingItem) {
        var titleSpan = existingItem.querySelector('.session-list-item-title');
        if (titleSpan) {
            titleSpan.textContent = newTitle || '未命名会话';
        }
        return;
    }

    // 根据 bizType 确定会话类型
    var filterType = sessionFilterTypeOf(bizType || '');

    // 类型与当前筛选不符时不插入 DOM（数据已保留在 initialChatSessions，切换类型时可渲染）
    if (sessionTypeFilter !== filterType) {
        return;
    }

    var emptyEl = container.querySelector('.session-list-empty');
    if (emptyEl) {
        emptyEl.remove();
    }

    var item = document.createElement('div');
    item.className = 'session-list-item' + (sessionId === currentSessionId ? ' active' : '');
    item.setAttribute('data-session-id', sessionId);
    item.setAttribute('data-biz-type', bizType || '');

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

    // 根据类型添加分类标签和样式
    var isTask = filterType === 'task';
    var isProject = filterType === 'project';
    if (isTask) {
        item.classList.add('session-list-item-task');
        var tag = document.createElement('span');
        tag.className = 'session-tag session-tag-task';
        tag.setAttribute('title', '任务会话');
        tag.textContent = '任务';
        item.insertBefore(tag, item.firstChild);
    } else if (isProject) {
        item.classList.add('session-list-item-project');
        var tag = document.createElement('span');
        tag.className = 'session-tag session-tag-project';
        tag.setAttribute('title', '项目会话');
        tag.textContent = '项目';
        item.insertBefore(tag, item.firstChild);
    } else {
        var tag = document.createElement('span');
        tag.className = 'session-tag session-tag-chat';
        tag.setAttribute('title', '聊天');
        tag.textContent = '聊天';
        item.insertBefore(tag, item.firstChild);
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
    var editSessionId = item.getAttribute('data-session-id');

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
                // 当前会话：同步更新消息区头部标题
                if (editSessionId === currentSessionId) {
                    var headerDesc = document.getElementById('chat-header-desc');
                    if (headerDesc) {
                        headerDesc.textContent = newTitle;
                    }
                }
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
        toolCallPermission: currentToolCallPermission || 'smart_call',
        thinkingLevel: currentThinkingLevel || null
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

function createPreviewItem(id, url, uploading, fileInfo) {
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
    } else if (fileInfo && fileInfo.type !== 'image') {
        item.classList.add('file-preview-non-image');
        var icon = document.createElement('div');
        icon.className = 'preview-file-icon';
        var icons = { video: '🎬', audio: '🎵', file: '📄' };
        icon.textContent = icons[fileInfo.type] || '📄';
        item.appendChild(icon);

        var name = document.createElement('div');
        name.className = 'preview-file-name';
        name.textContent = fileInfo.name;
        name.title = fileInfo.name;
        item.appendChild(name);

        var removeBtn = document.createElement('button');
        removeBtn.className = 'preview-remove';
        removeBtn.textContent = 'X';
        removeBtn.onclick = function(e) {
            e.stopPropagation();
            removeFileById(id);
        };
        item.appendChild(removeBtn);
    } else {
        var img = document.createElement('img');
        img.src = url;
        item.appendChild(img);

        var removeBtn = document.createElement('button');
        removeBtn.className = 'preview-remove';
        removeBtn.textContent = 'X';
        removeBtn.onclick = function(e) {
            e.stopPropagation();
            removeFileById(id);
        };
        item.appendChild(removeBtn);
    }

    return item;
}

function renderFilePreview(fileInfo) {
    var id = 'file_' + fileInfo.url.replace(/[^a-zA-Z0-9]/g, '_');
    var item = createPreviewItem(id, fileInfo.url, false, fileInfo);
    item.setAttribute('data-file-id', id);
    item.setAttribute('data-file-url', fileInfo.url);
    document.getElementById('filePreviewArea').appendChild(item);
    updatePreviewAreaVisibility();
}

function removePreviewItem(id) {
    var item = document.querySelector('.file-preview-item[data-file-id="' + id + '"]');
    if (item) item.remove();
    updatePreviewAreaVisibility();
}

function removeFileById(id) {
    var item = document.querySelector('.file-preview-item[data-file-id="' + id + '"]');
    if (item) {
        var url = item.getAttribute('data-file-url');
        if (url) {
            attachedFiles = attachedFiles.filter(function(f) { return f.url !== url; });
        }
        item.remove();
    }
    updatePreviewAreaVisibility();
}

function removeFileByUrl(url) {
    attachedFiles = attachedFiles.filter(function(f) { return f.url !== url; });
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
