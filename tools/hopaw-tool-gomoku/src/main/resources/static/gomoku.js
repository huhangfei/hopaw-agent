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
 *
 * 状态缓存：对局状态会持久化到 localStorage（key 带插件专属命名空间，避免与其他插件冲突），
 * 刷新页面后自动恢复棋盘、棋子、布局与等待落子状态。
 */
(function () {
    'use strict';

    /** 缓存 key：带插件专属命名空间前缀，插件隔离，避免与其他插件/页面覆盖。 */
    var STORAGE_KEY = 'hopaw.plugin.gomoku.state';

    var boardEl = null;
    var boardWrapEl = null;
    var statusEl = null;
    var closeBtn = null;
    var restartBtn = null;
    var timerEl = null;
    var timerInterval = null; // 倒计时定时器
    var timerDeadline = 0;    // 倒计时截止时间戳(ms)
    var boardResizeObserver = null; // 棋盘容器尺寸监听（展开过渡/窗口变化时自动重算格子）

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
        boardWrapEl = document.querySelector('.plugin-gomoku-board-wrap');
        statusEl = document.querySelector('[data-gomoku-status]');
        closeBtn = document.querySelector('[data-gomoku-close]');
        restartBtn = document.querySelector('[data-gomoku-restart]');
        timerEl = document.querySelector('[data-gomoku-timer]');
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
        // 槽位展开有 0.3s flex 过渡，boardWrap 尺寸变化由 ResizeObserver 自动触发重算，
        // 这里无需再额外定时校准
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
        resizeBoard();

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

    /**
     * 按容器可用空间计算格子边长：取宽高较小者均分给 n 格，
     * 限制在 [16, 44]px，保证棋盘尽量撑满且格子保持正方形。
     * 前提：面板已通过 flex:1 撑满槽位宽度，boardWrap.clientWidth 即真实可用宽。
     */
    function resizeBoard() {
        if (!boardEl || !boardWrapEl || !size || size <= 0) return;
        // 可用宽高需扣除棋盘自身 padding(8*2) + border(1*2) 及格线 gap
        var gapTotal = (size - 1) * 1;
        var chrome = 18 + gapTotal;
        var availW = boardWrapEl.clientWidth - chrome;
        var availH = boardWrapEl.clientHeight - chrome;
        // 任一方向不可测（如槽位尚未展开）时不落子计算，避免钳到最小值
        if (availW <= 0 || availH <= 0) return;
        var cell = Math.floor(Math.min(availW, availH) / size);
        cell = Math.max(16, Math.min(cell, 44));
        boardEl.style.gridTemplateColumns = 'repeat(' + size + ', ' + cell + 'px)';
        boardEl.style.gridTemplateRows = 'repeat(' + size + ', ' + cell + 'px)';
    }

    /** 监听棋盘容器尺寸变化（槽位展开过渡、窗口缩放等），自动重算格子。 */
    function watchBoardResize() {
        if (boardResizeObserver || !boardWrapEl) return;
        if (typeof ResizeObserver === 'undefined') return;
        boardResizeObserver = new ResizeObserver(function () {
            resizeBoard();
        });
        boardResizeObserver.observe(boardWrapEl);
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

    /** 启动等待用户落子的倒计时。 */
    function startTimer(timeoutSec) {
        stopTimer();
        if (!timerEl || !timeoutSec || timeoutSec <= 0) return;
        timerDeadline = Date.now() + timeoutSec * 1000;
        timerEl.hidden = false;
        renderTimer();
        timerInterval = setInterval(renderTimer, 1000);
    }

    function renderTimer() {
        if (!timerEl) return;
        var remain = Math.max(0, Math.ceil((timerDeadline - Date.now()) / 1000));
        var mm = Math.floor(remain / 60);
        var ss = remain % 60;
        var text = mm > 0 ? (mm + ':' + (ss < 10 ? '0' : '') + ss) : String(ss);
        timerEl.innerHTML = '剩余落子时间 <span class="plugin-gomoku-timer-num">' + text + '</span> 秒';
        timerEl.classList.toggle('urgent', remain <= 10);
        if (remain <= 0) {
            stopTimer();
        }
    }

    /** 停止并隐藏倒计时。 */
    function stopTimer() {
        if (timerInterval) {
            clearInterval(timerInterval);
            timerInterval = null;
        }
        if (timerEl) {
            timerEl.hidden = true;
        }
    }

    // =====================================================================
    // 状态缓存（localStorage，插件隔离 key，刷新后恢复）
    // =====================================================================

    /** 序列化当前对局状态并写入缓存。 */
    function saveState() {
        if (!size || size <= 0) return;
        var state = {
            size: size,
            board: boardState,
            gameId: currentGameId || null,
            lastMove: lastMove || null,
            active: !!active,
            waiting: !!waiting,
            pendingRequestId: pendingRequestId || null,
            // 倒计时绝对截止时间戳；未在倒计时时存 null
            timerDeadline: (waiting && timerInterval) ? timerDeadline : null
        };
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
        } catch (e) {
            // localStorage 不可用（隐私模式等）时静默失败，不影响对局
        }
    }

    /** 从缓存读取状态，无有效数据返回 null。 */
    function loadState() {
        try {
            var raw = localStorage.getItem(STORAGE_KEY);
            if (!raw) return null;
            var state = JSON.parse(raw);
            if (!state || typeof state.size !== 'number' || state.size < 9 || state.size > 19) {
                return null;
            }
            return state;
        } catch (e) {
            return null;
        }
    }

    /** 清除缓存。 */
    function clearState() {
        try {
            localStorage.removeItem(STORAGE_KEY);
        } catch (e) {
            // 忽略
        }
    }

    /** 仅清除当前最后一手的高亮标记。 */
    function clearLastHighlight() {
        var marked = boardEl.querySelectorAll('.plugin-gomoku-piece.last');
        for (var i = 0; i < marked.length; i++) {
            marked[i].classList.remove('last');
        }
    }

    /** 刷新后恢复对局：重建棋盘、渲染棋子、恢复布局与等待状态。返回是否恢复成功。 */
    function restoreState() {
        var saved = loadState();
        if (!saved) return false;

        size = saved.size;
        currentGameId = saved.gameId || null;

        // 恢复布局（若刷新前棋盘是展开状态）
        if (saved.active) {
            openPanel();
        }

        buildBoard(size);

        // 恢复棋子
        var board = saved.board;
        if (Array.isArray(board)) {
            for (var r = 0; r < size && r < board.length; r++) {
                var row = board[r];
                if (!Array.isArray(row)) continue;
                for (var c = 0; c < size && c < row.length; c++) {
                    if (row[c] === 1 || row[c] === 2) {
                        renderPiece(c, r, row[c]);
                    }
                }
            }
        }

        // 修正最后一手高亮（renderPiece 逐颗渲染会留下错误高亮）
        clearLastHighlight();
        if (saved.lastMove && saved.lastMove.x != null && saved.lastMove.y != null) {
            lastMove = { x: saved.lastMove.x, y: saved.lastMove.y };
            var lmCell = cellAt(lastMove.x, lastMove.y);
            if (lmCell) {
                var lmPiece = lmCell.querySelector('.plugin-gomoku-piece');
                if (lmPiece) lmPiece.classList.add('last');
            }
        } else {
            lastMove = null;
        }

        // 恢复等待落子状态 + 倒计时（仅当尚未过期）
        if (saved.waiting && saved.pendingRequestId) {
            waiting = true;
            pendingRequestId = saved.pendingRequestId;
            setStatus('轮到你落子(白)', 'active');
            if (saved.timerDeadline && saved.timerDeadline > Date.now()) {
                timerDeadline = saved.timerDeadline;
                timerEl.hidden = false;
                renderTimer();
                timerInterval = setInterval(renderTimer, 1000);
            } else {
                stopTimer();
            }
        } else {
            waiting = false;
            pendingRequestId = null;
            setStatus('对局进行中');
        }

        return true;
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
        stopTimer();
        setStatus('等待对方', 'active');

        var rid = pendingRequestId;
        pendingRequestId = null;
        saveState();
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
        saveState();
    }

    function handlePlace(payload) {
        if (!payload) return;
        renderPiece(payload.x, payload.y, payload.piece);
        var status = payload.status;
        // status: 1=LLM(黑)胜 2=用户(白)胜 3=和棋 —— 注意以用户视角展示
        if (status === 1) {
            setStatus('对方赢了', 'lose');
        } else if (status === 2) {
            setStatus('你赢了', 'win');
        } else if (status === 3) {
            setStatus('和棋', 'draw');
        } else {
            // 对局继续，LLM 已落子，轮到用户：若带了 pendingRequestId 则进入等待状态
            if (payload.pendingRequestId) {
                waiting = true;
                pendingRequestId = payload.pendingRequestId;
                setStatus('轮到你落子(白)', 'active');
                var timeoutSec = payload.timeout || 60;
                startTimer(timeoutSec);
            } else {
                setStatus('你的回合(黑)', 'active');
            }
        }
        saveState();
    }

    function handleRequestMove(requestId, payload) {
        waiting = true;
        pendingRequestId = requestId;
        setStatus('轮到你落子(白)', 'active');
        var timeoutSec = (payload && payload.timeout) ? payload.timeout : 60;
        startTimer(timeoutSec);
        saveState();
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
            handleRequestMove(cmd.requestId, cmd.payload);
        } else if (cmd.action === 'close') {
            if (active) closePanel();
            stopTimer();
            setStatus('待命');
            clearState();
        }
    }

    function onClose() {
        if (!active) return;
        closePanel();
        stopTimer();
        setStatus('待命');
        clearState();
    }

    function onRestart() {
        // 仅清空本地棋盘并回到待命；重新开局由 LLM 调用 startGame 触发
        if (boardEl && size > 0) {
            buildBoard(size);
        }
        stopTimer();
        setStatus('待命');
        clearState();
    }

    function bindButtons() {
        if (closeBtn) closeBtn.addEventListener('click', onClose);
        if (restartBtn) restartBtn.addEventListener('click', onRestart);
    }

    function init() {
        findEls();
        bindButtons();
        // 窗口缩放/槽位展开过渡时 boardWrap 尺寸都会变，统一由 ResizeObserver 驱动重算
        watchBoardResize();
        if (window.PluginHook) {
            window.PluginHook.onCommand(handleCommand);
        }
        // 刷新后恢复上次未结束的对局
        restoreState();
    }

    if (window.PluginLoader) {
        window.PluginLoader.onReady(init);
    } else {
        init();
    }
})();
