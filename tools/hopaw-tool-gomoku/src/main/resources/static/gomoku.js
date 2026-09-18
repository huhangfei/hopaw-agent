/**
 * 五子棋工具插件前端逻辑。
 *
 * 通过 PluginHook 监听后端 @Tool 下发的指令：
 *   - action=start        打开棋盘、初始化指定大小的空棋盘（payload.size）
 *   - action=place        渲染一枚棋子（payload.x/y/piece/status）
 *   - action=request-move 进入「等待用户落子」状态，用户点击空格后回传坐标
 *   - action=close        关闭棋盘、还原布局
 *
 * 棋子标识：piece=1 为 LLM(黑/X)，piece=2 为用户(白/O)。
 * 用户落子回传格式："x,y"。
 */
(function () {
    'use strict';

    var boardEl = null;
    var statusEl = null;
    var closeBtn = null;
    var restartBtn = null;

    var size = 15;
    var cells = [];           // size x size 的格子 DOM
    var boardState = [];      // size x size：0=空 1=黑 2=白
    var active = false;       // 棋盘是否展开
    var waiting = false;      // 是否等待用户落子
    var pendingRequestId = null; // 当前等待落子对应的 requestId
    var currentGameId = null;
    var lastMove = null;      // {x,y} 最后一手，用于高亮

    // 侧边栏状态记录（用于关闭时恢复）
    var panelMiniAtStart = false;
    var panelMiniForced = null;

    function findEls() {
        boardEl = document.querySelector('[data-gomoku-board]');
        statusEl = document.querySelector('[data-gomoku-status]');
        closeBtn = document.querySelector('[data-gomoku-close]');
        restartBtn = document.querySelector('[data-gomoku-restart]');
    }

    function setStatus(text, cls) {
        if (!statusEl) return;
        statusEl.textContent = text;
        statusEl.className = 'plugin-gomoku-status';
        if (cls) statusEl.classList.add(cls);
    }

    function isToolPanelMini() {
        var panel = document.getElementById('toolPanel');
        return !!(panel && panel.classList.contains('tool-panel-mini'));
    }

    function openPanel() {
        var wrapper = document.querySelector('.chat-wrapper');
        if (wrapper) wrapper.classList.add('gomoku-active');

        panelMiniAtStart = isToolPanelMini();
        if (typeof window.toggleToolPanelMini === 'function') {
            window.toggleToolPanelMini(true);
        }
        panelMiniForced = true;
        active = true;
    }

    function closePanel() {
        var wrapper = document.querySelector('.chat-wrapper');
        if (wrapper) wrapper.classList.remove('gomoku-active');

        var currentMini = isToolPanelMini();
        if (panelMiniForced !== null && currentMini === panelMiniForced) {
            if (typeof window.toggleToolPanelMini === 'function') {
                window.toggleToolPanelMini(panelMiniAtStart);
            }
        }
        panelMiniForced = null;
        active = false;
        waiting = false;
        pendingRequestId = null;
    }

    /** 构建棋盘网格 DOM。 */
    function buildBoard(n) {
        size = n;
        cells = [];
        boardState = [];
        boardEl.innerHTML = '';
        boardEl.style.gridTemplateColumns = 'repeat(' + n + ', 32px)';
        boardEl.style.gridTemplateRows = 'repeat(' + n + ', 32px)';

        for (var r = 0; r < n; r++) {
            boardState[r] = [];
            for (var c = 0; c < n; c++) {
                boardState[r][c] = 0;
                var cell = document.createElement('button');
                cell.type = 'button';
                cell.className = 'plugin-gomoku-cell';
                cell.dataset.x = String(c);
                cell.dataset.y = String(r);
                cell.addEventListener('click', onCellClick);
                cells.push(cell);
                boardEl.appendChild(cell);
            }
        }
        lastMove = null;
    }

    function cellAt(x, y) {
        return cells[y * size + x];
    }

    /** 在指定坐标渲染一枚棋子。 */
    function renderPiece(x, y, piece) {
        if (x < 0 || x >= size || y < 0 || y >= size) return;
        boardState[y][x] = piece;
        var cell = cellAt(x, y);
        if (!cell) return;
        cell.innerHTML = '';
        var p = document.createElement('span');
        p.className = 'plugin-gomoku-piece ' + (piece === 1 ? 'black' : 'white');
        cell.appendChild(p);
        cell.disabled = true;

        // 清除旧的高亮
        if (lastMove) {
            var prev = cellAt(lastMove.x, lastMove.y);
            if (prev) {
                var pp = prev.querySelector('.plugin-gomoku-piece');
                if (pp) pp.classList.remove('last');
            }
        }
        lastMove = { x: x, y: y };
        p.classList.add('last');
    }

    function onCellClick(ev) {
        if (!waiting || !pendingRequestId) return;
        var cell = ev.currentTarget;
        var x = parseInt(cell.dataset.x, 10);
        var y = parseInt(cell.dataset.y, 10);
        if (boardState[y][x] !== 0) return;

        // 本地先渲染用户棋子，等待后端确认（后端会再下发 place 同步，幂等）
        renderPiece(x, y, 2);
        waiting = false;
        setStatus('等待对方', 'active');

        var rid = pendingRequestId;
        pendingRequestId = null;
        if (window.PluginHook && rid) {
            window.PluginHook.report(rid, x + ',' + y);
        }
    }

    function handleStart(payload) {
        var n = (payload && payload.size) ? payload.size : 15;
        n = Math.max(9, Math.min(19, n));
        openPanel();
        buildBoard(n);
        setStatus('你的回合(黑)', 'active');
    }

    function handlePlace(payload) {
        if (!payload) return;
        renderPiece(payload.x, payload.y, payload.piece);
        var status = payload.status;
        if (status === 1) {
            setStatus('你赢了', 'win');
        } else if (status === 2) {
            setStatus('对方赢了', 'lose');
        } else if (status === 3) {
            setStatus('和棋', 'draw');
        } else {
            setStatus('你的回合(黑)', 'active');
        }
    }

    function handleRequestMove(requestId) {
        waiting = true;
        pendingRequestId = requestId;
        setStatus('轮到你落子(白)', 'active');
    }

    function handleCommand(cmd) {
        if (!cmd || cmd.toolName !== 'gomoku') return;
        currentGameId = cmd.gameId || currentGameId;

        if (cmd.action === 'start') {
            handleStart(cmd.payload);
        } else if (cmd.action === 'place') {
            if (!active) { openPanel(); }
            handlePlace(cmd.payload);
        } else if (cmd.action === 'request-move') {
            if (!active) { openPanel(); }
            handleRequestMove(cmd.requestId);
        } else if (cmd.action === 'close') {
            if (active) closePanel();
            setStatus('待命');
        }
    }

    function onClose() {
        if (!active) return;
        closePanel();
        setStatus('待命');
    }

    function onRestart() {
        // 仅清空本地棋盘并回到待命；重新开局由 LLM 调用 startGame 触发
        if (boardEl && size > 0) {
            buildBoard(size);
        }
        setStatus('待命');
    }

    function bindButtons() {
        if (closeBtn) closeBtn.addEventListener('click', onClose);
        if (restartBtn) restartBtn.addEventListener('click', onRestart);
    }

    function init() {
        findEls();
        bindButtons();
        if (window.PluginHook) {
            window.PluginHook.onCommand(handleCommand);
        }
    }

    if (window.PluginLoader) {
        window.PluginLoader.onReady(init);
    } else {
        init();
    }
})();
